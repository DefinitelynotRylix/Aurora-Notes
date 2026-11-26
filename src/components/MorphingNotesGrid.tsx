import React, { useEffect, useLayoutEffect, useRef, useState } from 'react';
import {
  computeDropGlowRipple,
  computeFlipMorphTransform,
  DropGlowRippleSample,
  FlipRect,
} from '../utils/editorRules';

interface MorphingNotesGridProps {
  isGridView: boolean;
  itemIds?: string[];
  lockedItemIds?: string[];
  onHoldNote?: (id: string) => void;
  onReleaseHoldWithoutDrag?: (id: string) => void;
  onReorderNotes?: (orderedIds: string[]) => void;
  children: React.ReactNode;
}

interface RectBox {
  left: number;
  top: number;
  right: number;
  bottom: number;
  width: number;
  height: number;
  cx: number;
  cy: number;
}

const easeOutCubic = (t: number): number => 1 - Math.pow(1 - t, 3);

/**
 * Smoothly morphs child note cards between a 2-column Masonry Grid (`isGridView = true`)
 * and a Full-Width Single Column layout (`isGridView = false`), with interactive
 * Hold-to-Elevate, subtle wobbly physics, neighbor hover interaction, luminous drop corner
 * glow rippling to nearby notes, and Drag-to-Reorder in both Masonry and Single Column modes.
 *
 * Performance architecture:
 * - Grid <-> Column morph renders ONCE at target width and runs a true top-left FLIP
 *   scaleX/scaleY + translate3d GPU animation in requestAnimationFrame (zero width snap,
 *   zero per-frame React re-renders).
 * - Drag wobble, neighbor hover reaction, slot swap glides, smooth drop settle, and
 *   luminous corner glow waves mutate GPU transforms & overlays directly on DOM refs.
 */
export const MorphingNotesGrid: React.FC<MorphingNotesGridProps> = ({
  isGridView,
  itemIds,
  lockedItemIds,
  onHoldNote,
  onReleaseHoldWithoutDrag,
  onReorderNotes,
  children,
}) => {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const itemRefs = useRef<Record<string, HTMLDivElement | null>>({});
  const glowOverlayRefs = useRef<Record<string, HTMLDivElement | null>>({});
  const glowRingRefs = useRef<Record<string, HTMLDivElement | null>>({});
  const [containerWidth, setContainerWidth] = useState<number>(0);
  const [measureVersion, setMeasureVersion] = useState<number>(0);
  const [isDragActive, setIsDragActive] = useState<boolean>(false);

  const items = React.Children.toArray(children);
  const rawIds =
    itemIds && itemIds.length === items.length
      ? itemIds
      : items.map((child, idx) => String((child as React.ReactElement)?.key ?? idx));
  const idsKey = rawIds.join('\u0001');
  const resolvedIds = React.useMemo(() => rawIds, [idsKey]);

  const resolvedIdsRef = useRef<string[]>(resolvedIds);
  resolvedIdsRef.current = resolvedIds;

  const lockedKey = (lockedItemIds ?? []).join('\u0001');
  const lockedIdSet = React.useMemo(
    () => new Set<string>(lockedItemIds ?? []),
    [lockedKey]
  );
  const lockedIdSetRef = useRef<Set<string>>(lockedIdSet);
  lockedIdSetRef.current = lockedIdSet;

  const callbacksRef = useRef({
    onHoldNote,
    onReleaseHoldWithoutDrag,
    onReorderNotes,
  });
  callbacksRef.current = {
    onHoldNote,
    onReleaseHoldWithoutDrag,
    onReorderNotes,
  };

  // Cached DOM heights so animation frames never trigger forced synchronous layout reads
  const heightsRef = useRef<Record<string, number>>({});
  const liveOrderRef = useRef<string[]>(resolvedIds);
  const initialOrderRef = useRef<string[]>(resolvedIds);

  const draggedIdRef = useRef<string | null>(null);
  const droppingIdRef = useRef<string | null>(null);
  const isMorphAnimatingRef = useRef<boolean>(false);

  const animPosRef = useRef<Record<string, { x: number; y: number }>>({});
  const morphStartRectsRef = useRef<Record<string, FlipRect>>({});
  const targetSlotsByIdRef = useRef<Record<string, RectBox>>({});
  const slotBoundsRef = useRef<RectBox[]>([]);
  const cardBoundsRef = useRef<Record<string, RectBox>>({});
  const lastGridViewRef = useRef<boolean>(isGridView);
  const lastTotalHeightRef = useRef<number>(0);
  const lastDragRotRef = useRef<number>(0);

  const morphRafRef = useRef<number | null>(null);
  const dragRafRef = useRef<number | null>(null);
  const dropRafRef = useRef<number | null>(null);
  const resizeRafRef = useRef<number | null>(null);

  const holdTimerRef = useRef<number | null>(null);
  const pointerDownInfoRef = useRef<{
    pointerId: number;
    id: string;
    startClientX: number;
    startClientY: number;
  } | null>(null);

  const dragStateRef = useRef<{
    startX: number;
    startY: number;
    deltaX: number;
    deltaY: number;
    moved: boolean;
    elevationStartTime: number;
  }>({
    startX: 0,
    startY: 0,
    deltaX: 0,
    deltaY: 0,
    moved: false,
    elevationStartTime: 0,
  });

  // Time-windowed click suppression so releasing a long-press NEVER fires card onClick
  const suppressNextClickUntilRef = useRef<number>(0);

  // Sync liveOrderRef when upstream item order changes (outside active drag or drop glide)
  if (!draggedIdRef.current && !droppingIdRef.current) {
    liveOrderRef.current = resolvedIds;
  }

  const clearAllGlowOverlays = () => {
    const overlays = glowOverlayRefs.current;
    const rings = glowRingRefs.current;
    for (const id of Object.keys(overlays)) {
      const overlay = overlays[id];
      if (overlay) {
        overlay.style.opacity = '0';
        overlay.style.boxShadow = 'none';
        overlay.style.filter = 'none';
        overlay.dataset.glowIntensity = '0';
      }
      const ring = rings[id];
      if (ring) {
        ring.style.background = 'none';
      }
    }
  };

  /**
   * Renders a crisp luminous outline glow strictly along the card's rounded border
   * (zero interior corner circles). For nearby cards, a radial light field centered at
   * the released card illuminates the exact facing outline edges and corners.
   */
  const applyOutlineGlowToOverlay = (
    id: string,
    sample: DropGlowRippleSample,
    isDroppedCard: boolean,
    target: RectBox,
    droppedCx: number,
    droppedCy: number
  ) => {
    const overlay = glowOverlayRefs.current[id];
    const ring = glowRingRefs.current[id];
    if (!overlay || !ring) return;
    if (sample.intensity <= 0.01) {
      overlay.style.opacity = '0';
      overlay.style.boxShadow = 'none';
      overlay.style.filter = 'none';
      overlay.dataset.glowIntensity = '0';
      ring.style.background = 'none';
      return;
    }

    const a = sample.intensity;
    overlay.style.opacity = '1';
    overlay.dataset.glowIntensity = a.toFixed(3);

    if (isDroppedCard) {
      const bright = (a * 0.98).toFixed(3);
      const mid = (a * 0.86).toFixed(3);
      ring.style.background = `linear-gradient(135deg, rgba(233, 213, 255, ${bright}) 0%, rgba(167, 139, 250, ${mid}) 50%, rgba(233, 213, 255, ${bright}) 100%)`;
      overlay.style.filter = `drop-shadow(0 0 ${(11 * a).toFixed(1)}px rgba(167, 139, 250, ${(0.85 * a).toFixed(3)}))`;
      overlay.style.boxShadow = `0 0 ${(22 * a).toFixed(1)}px ${(1.5 * a).toFixed(1)}px rgba(139, 92, 246, ${(0.42 * a).toFixed(3)})`;
    } else {
      // Position light source at the released note's center relative to this card's outline
      const relX = droppedCx - target.left;
      const relY = droppedCy - target.top;
      const nearX = Math.max(0, Math.min(target.width, relX));
      const nearY = Math.max(0, Math.min(target.height, relY));
      const distToEdge = Math.hypot(relX - nearX, relY - nearY);
      const spread = Math.max(220, Math.min(target.width, target.height) * 1.65);

      const stop0 = Math.max(0, distToEdge - 10).toFixed(1);
      const stop1 = (distToEdge + spread * 0.42).toFixed(1);
      const stop2 = (distToEdge + spread * 0.78).toFixed(1);
      const stop3 = (distToEdge + spread).toFixed(1);

      ring.style.background = `radial-gradient(circle at ${relX.toFixed(1)}px ${relY.toFixed(1)}px, rgba(233, 213, 255, ${(a * 0.98).toFixed(3)}) ${stop0}px, rgba(167, 139, 250, ${(a * 0.82).toFixed(3)}) ${stop1}px, rgba(139, 92, 246, ${(a * 0.28).toFixed(3)}) ${stop2}px, rgba(139, 92, 246, 0) ${stop3}px)`;
      overlay.style.filter = `drop-shadow(0 0 ${(9 * a).toFixed(1)}px rgba(167, 139, 250, ${(0.75 * a).toFixed(3)}))`;
      overlay.style.boxShadow = 'none';
    }
  };

  // Compute slot layout for a given order & view mode using cached card heights
  const computeLayout = (
    orderIds: string[],
    width: number,
    gridMode: boolean
  ): {
    itemWidth: number;
    totalHeight: number;
    slots: RectBox[];
    byId: Record<string, RectBox>;
  } => {
    const gap = width >= 640 ? 14 : 10;
    const itemWidth = gridMode ? Math.max(1, (width - gap) / 2) : width;
    const slots: RectBox[] = [];
    const byId: Record<string, RectBox> = {};

    if (gridMode) {
      const colHeights = [0, 0];
      for (let s = 0; s < orderIds.length; s++) {
        const id = orderIds[s];
        const h = heightsRef.current[id] ?? 148;
        const lane = colHeights[0] <= colHeights[1] ? 0 : 1;
        const sx = lane === 0 ? 0 : width - itemWidth;
        const sy = s === 0 || s === 1 ? 0 : colHeights[lane];
        const box: RectBox = {
          left: sx,
          top: sy,
          right: sx + itemWidth,
          bottom: sy + h,
          width: itemWidth,
          height: h,
          cx: sx + itemWidth * 0.5,
          cy: sy + h * 0.5,
        };
        slots[s] = box;
        byId[id] = box;
        colHeights[lane] = sy + h + gap;
      }
      const totalHeight = Math.max(
        0,
        Math.max(colHeights[0], colHeights[1]) - (orderIds.length > 0 ? gap : 0)
      );
      return { itemWidth, totalHeight, slots, byId };
    } else {
      let runningY = 0;
      for (let s = 0; s < orderIds.length; s++) {
        const id = orderIds[s];
        const h = heightsRef.current[id] ?? 148;
        const box: RectBox = {
          left: 0,
          top: runningY,
          right: itemWidth,
          bottom: runningY + h,
          width: itemWidth,
          height: h,
          cx: itemWidth * 0.5,
          cy: runningY + h * 0.5,
        };
        slots[s] = box;
        byId[id] = box;
        runningY += h + gap;
      }
      const totalHeight = Math.max(0, runningY - (orderIds.length > 0 ? gap : 0));
      return { itemWidth, totalHeight, slots, byId };
    }
  };

  // Capture current card heights from DOM (called only on discrete layout/content changes)
  const measureCardHeights = () => {
    let changed = false;
    const ids = resolvedIdsRef.current;
    for (let i = 0; i < ids.length; i++) {
      const id = ids[i];
      const el = itemRefs.current[id];
      if (el) {
        const h = el.offsetHeight;
        if (h > 0 && Math.abs((heightsRef.current[id] ?? 0) - h) > 0.5) {
          heightsRef.current[id] = h;
          changed = true;
        }
      }
    }
    return changed;
  };

  // Apply static settled DOM positions directly (zero React re-renders)
  const applySettledPositions = (
    orderIds: string[],
    byId: Record<string, RectBox>,
    totalHeight: number
  ) => {
    if (containerRef.current) {
      containerRef.current.style.height = `${Math.round(totalHeight)}px`;
    }
    lastTotalHeightRef.current = totalHeight;
    for (let i = 0; i < orderIds.length; i++) {
      const id = orderIds[i];
      const box = byId[id];
      const el = itemRefs.current[id];
      if (!box || !el) continue;
      animPosRef.current[id] = { x: box.left, y: box.top };
      cardBoundsRef.current[id] = box;
      el.style.transformOrigin = '';
      el.style.transform = `translate3d(${box.left.toFixed(2)}px, ${box.top.toFixed(2)}px, 0)`;
      el.style.zIndex = '1';
      el.style.opacity = '1';
      el.style.filter = 'none';
      el.style.willChange = 'auto';
    }
  };

  // Observe container width & child height changes (debounced via rAF, paused during animations)
  useLayoutEffect(() => {
    const el = containerRef.current;
    if (!el) return;

    const updateWidth = () => {
      if (el.clientWidth > 0) {
        setContainerWidth((prev) => (prev !== el.clientWidth ? el.clientWidth : prev));
      }
    };
    updateWidth();

    const ro = new ResizeObserver(() => {
      if (isMorphAnimatingRef.current || draggedIdRef.current || droppingIdRef.current) return;
      if (resizeRafRef.current !== null) return;
      resizeRafRef.current = requestAnimationFrame(() => {
        resizeRafRef.current = null;
        if (!containerRef.current) return;
        const wChanged = containerRef.current.clientWidth !== containerWidth;
        const hChanged = measureCardHeights();
        if (wChanged && containerRef.current.clientWidth > 0) {
          setContainerWidth(containerRef.current.clientWidth);
        } else if (hChanged) {
          setMeasureVersion((v) => v + 1);
        }
      });
    });

    ro.observe(el);
    resolvedIds.forEach((id) => {
      const childEl = itemRefs.current[id];
      if (childEl) ro.observe(childEl);
    });

    return () => {
      ro.disconnect();
      if (resizeRafRef.current !== null) {
        cancelAnimationFrame(resizeRafRef.current);
        resizeRafRef.current = null;
      }
    };
  }, [items.length, containerWidth, resolvedIds]);

  // True GPU FLIP morph when toggling Grid <-> Column (smooth scaleX/scaleY + translate3d, zero width pop!)
  useLayoutEffect(() => {
    if (containerWidth <= 0) return;

    const didModeChange = lastGridViewRef.current !== isGridView;
    const prevRects: Record<string, FlipRect> = {};
    const prevTotalHeight = lastTotalHeightRef.current;

    if (didModeChange) {
      resolvedIds.forEach((id) => {
        const bounds = cardBoundsRef.current[id];
        if (bounds && bounds.width > 0 && bounds.height > 0) {
          prevRects[id] = {
            left: bounds.left,
            top: bounds.top,
            width: bounds.width,
            height: bounds.height,
          };
        } else {
          const pos = animPosRef.current[id];
          const oldSlot = targetSlotsByIdRef.current[id];
          if (pos && oldSlot) {
            prevRects[id] = {
              left: pos.x,
              top: pos.y,
              width: oldSlot.width,
              height: oldSlot.height,
            };
          }
        }
      });
      morphStartRectsRef.current = prevRects;
      lastGridViewRef.current = isGridView;
    }

    // Measure heights once at the new target width before painting
    measureCardHeights();
    const order = liveOrderRef.current;
    const layout = computeLayout(order, containerWidth, isGridView);
    slotBoundsRef.current = layout.slots;
    targetSlotsByIdRef.current = layout.byId;

    if (draggedIdRef.current || droppingIdRef.current) {
      return;
    }

    if (didModeChange && Object.keys(prevRects).length > 0) {
      if (morphRafRef.current !== null) {
        cancelAnimationFrame(morphRafRef.current);
      }
      clearAllGlowOverlays();
      isMorphAnimatingRef.current = true;

      const startHeight = prevTotalHeight > 0 ? prevTotalHeight : layout.totalHeight;
      const targetHeight = layout.totalHeight;
      const durationMs = 340;
      const startTime = performance.now();

      const applyMorphFrame = (p: number) => {
        if (containerRef.current) {
          const h = startHeight + (targetHeight - startHeight) * p;
          containerRef.current.style.height = `${Math.round(h)}px`;
        }
        for (let i = 0; i < order.length; i++) {
          const id = order[i];
          const target = layout.byId[id];
          const el = itemRefs.current[id];
          if (!target || !el) continue;
          const startRect = prevRects[id] ?? {
            left: target.left,
            top: target.top,
            width: target.width,
            height: target.height,
          };
          const flip = computeFlipMorphTransform(startRect, target, p);
          animPosRef.current[id] = { x: flip.x, y: flip.y };
          cardBoundsRef.current[id] = {
            ...target,
            left: flip.x,
            top: flip.y,
            width: target.width * flip.scaleX,
            height: target.height * flip.scaleY,
            right: flip.x + target.width * flip.scaleX,
            bottom: flip.y + target.height * flip.scaleY,
            cx: flip.x + target.width * flip.scaleX * 0.5,
            cy: flip.y + target.height * flip.scaleY * 0.5,
          };
          el.style.willChange = 'transform';
          el.style.transformOrigin = '0 0';
          el.style.transform = `translate3d(${flip.x.toFixed(2)}px, ${flip.y.toFixed(2)}px, 0) scale(${flip.scaleX.toFixed(4)}, ${flip.scaleY.toFixed(4)})`;
          el.style.opacity = '1';
          el.style.zIndex = String(order.length - i);
        }
      };

      // Place cards at exact visual start geometry BEFORE browser paints the resized cards
      applyMorphFrame(0);

      const step = (now: number) => {
        const rawT = Math.min(1, Math.max(0, (now - startTime) / durationMs));
        const eased = easeOutCubic(rawT);
        applyMorphFrame(eased);

        if (rawT < 1) {
          morphRafRef.current = requestAnimationFrame(step);
        } else {
          morphRafRef.current = null;
          isMorphAnimatingRef.current = false;
          applySettledPositions(order, layout.byId, layout.totalHeight);
        }
      };

      morphRafRef.current = requestAnimationFrame(step);
      return () => {
        if (morphRafRef.current !== null) {
          cancelAnimationFrame(morphRafRef.current);
          morphRafRef.current = null;
        }
        isMorphAnimatingRef.current = false;
      };
    }

    applySettledPositions(order, layout.byId, layout.totalHeight);
  }, [isGridView, containerWidth, resolvedIds, measureVersion]);

  // Cleanup any in-flight rAFs on unmount
  useEffect(() => {
    return () => {
      if (morphRafRef.current !== null) cancelAnimationFrame(morphRafRef.current);
      if (dragRafRef.current !== null) cancelAnimationFrame(dragRafRef.current);
      if (dropRafRef.current !== null) cancelAnimationFrame(dropRafRef.current);
      if (resizeRafRef.current !== null) cancelAnimationFrame(resizeRafRef.current);
      if (holdTimerRef.current !== null) window.clearTimeout(holdTimerRef.current);
    };
  }, []);

  // Pure DOM rAF loop while actively dragging (0 React re-renders per frame!)
  const startDragAnimationLoop = () => {
    if (dragRafRef.current !== null) {
      cancelAnimationFrame(dragRafRef.current);
    }
    const loopStart = performance.now();

    const tick = (now: number) => {
      const activeId = draggedIdRef.current;
      if (!activeId) {
        dragRafRef.current = null;
        return;
      }

      const phase = ((now - loopStart) / 1000) * 16.5;
      const elev = Math.min(1, (now - dragStateRef.current.elevationStartTime) / 140);
      const order = liveOrderRef.current;
      const targetsById = targetSlotsByIdRef.current;
      const activeTarget = targetsById[activeId];
      const itemW = activeTarget?.width ?? 160;
      const activeH = heightsRef.current[activeId] ?? 148;

      const dragX = dragStateRef.current.startX + dragStateRef.current.deltaX;
      const dragY = dragStateRef.current.startY + dragStateRef.current.deltaY;
      const dragCx = dragX + itemW * 0.5;
      const dragCy = dragY + activeH * 0.5;
      const isDraggedPinned = lockedIdSetRef.current.has(activeId);

      for (let i = 0; i < order.length; i++) {
        const id = order[i];
        const el = itemRefs.current[id];
        const target = targetsById[id];
        if (!el || !target) continue;

        if (id === activeId) {
          animPosRef.current[id] = { x: dragX, y: dragY };
          cardBoundsRef.current[id] = {
            ...target,
            left: dragX,
            top: dragY,
            right: dragX + target.width,
            bottom: dragY + target.height,
            cx: dragCx,
            cy: dragCy,
          };
          const scale = 1 + 0.045 * elev;
          const rot = Math.sin(phase) * 1.15 * elev;
          lastDragRotRef.current = rot;
          el.style.willChange = 'transform';
          el.style.transformOrigin = '';
          el.style.zIndex = '30';
          el.style.transform = `translate3d(${dragX.toFixed(2)}px, ${dragY.toFixed(2)}px, 0) scale(${scale.toFixed(4)}) rotate(${rot.toFixed(2)}deg)`;
          el.style.filter = 'drop-shadow(0 16px 28px rgba(89, 37, 220, 0.26))';
        } else {
          const prev = animPosRef.current[id] ?? { x: target.left, y: target.top };
          const dx = target.left - prev.x;
          const dy = target.top - prev.y;
          const dist = Math.hypot(dx, dy);
          const drawX = dist > 0.6 ? prev.x + dx * 0.3 : target.left;
          const drawY = dist > 0.6 ? prev.y + dy * 0.3 : target.top;

          animPosRef.current[id] = { x: drawX, y: drawY };
          cardBoundsRef.current[id] = {
            ...target,
            left: drawX,
            top: drawY,
            right: drawX + target.width,
            bottom: drawY + target.height,
            cx: drawX + target.width * 0.5,
            cy: drawY + target.height * 0.5,
          };

          const isSameGroup = lockedIdSetRef.current.has(id) === isDraggedPinned;
          const distToDragged = Math.hypot(
            dragCx - (drawX + target.width * 0.5),
            dragCy - (drawY + target.height * 0.5)
          );
          const hoverProximity = isSameGroup
            ? Math.max(0, Math.min(1, 1 - distToDragged / (itemW * 1.15)))
            : 0;
          const glideFactor = Math.min(1, dist / 40);
          const wobbleIntensity = Math.max(hoverProximity * 0.85, glideFactor * 1.0);
          const rot = Math.sin(phase * 1.25 + i * 1.7) * wobbleIntensity;
          const scale = 1 - hoverProximity * 0.015;

          el.style.willChange = 'transform';
          el.style.transformOrigin = '';
          el.style.zIndex = glideFactor > 0.05 ? '5' : '1';
          el.style.filter = 'none';
          el.style.transform = `translate3d(${drawX.toFixed(2)}px, ${drawY.toFixed(2)}px, 0) scale(${scale.toFixed(4)}) rotate(${rot.toFixed(2)}deg)`;
        }
      }

      dragRafRef.current = requestAnimationFrame(tick);
    };

    dragRafRef.current = requestAnimationFrame(tick);
  };

  /**
   * Unified silky drop settle (280ms cubic ease-out) + luminous 4-corner glow on the
   * dropped card that radiates outward across nearby notes (740ms) with facing-corner
   * illumination and a subtle sympathetic wave nudge.
   */
  const runDropAndGlowAnimation = (
    activeId: string,
    targetOrder: string[],
    releaseRot: number,
    startPositions: Record<string, { x: number; y: number }>,
    withCornerGlow: boolean
  ) => {
    const layout = computeLayout(targetOrder, containerWidth, isGridView);
    slotBoundsRef.current = layout.slots;
    targetSlotsByIdRef.current = layout.byId;

    const droppedTarget = layout.byId[activeId];
    const droppedCx = droppedTarget ? droppedTarget.cx : 0;
    const droppedCy = droppedTarget ? droppedTarget.cy : 0;
    const maxGlowRadius = Math.max(360, containerWidth * 1.15);

    droppingIdRef.current = activeId;
    const startHeight = lastTotalHeightRef.current || layout.totalHeight;
    const targetHeight = layout.totalHeight;
    const startTime = performance.now();
    const settleDurationMs = withCornerGlow ? 280 : 165;
    const totalDurationMs = withCornerGlow ? 740 : 165;

    if (dropRafRef.current !== null) {
      cancelAnimationFrame(dropRafRef.current);
      dropRafRef.current = null;
    }

    const step = (now: number) => {
      const elapsed = now - startTime;
      const settleRaw = Math.min(1, Math.max(0, elapsed / settleDurationMs));
      const settleP = easeOutCubic(settleRaw);
      const settleRem = 1 - settleP;

      if (containerRef.current) {
        const h = startHeight + (targetHeight - startHeight) * settleP;
        containerRef.current.style.height = `${Math.round(h)}px`;
      }

      for (let i = 0; i < targetOrder.length; i++) {
        const id = targetOrder[i];
        const el = itemRefs.current[id];
        const target = layout.byId[id];
        if (!el || !target) continue;

        const s = startPositions[id] ?? { x: target.left, y: target.top };
        const baseX = s.x + (target.left - s.x) * settleP;
        const baseY = s.y + (target.top - s.y) * settleP;

        const isDropped = id === activeId;
        const dxFromDropped = target.cx - droppedCx;
        const dyFromDropped = target.cy - droppedCy;

        const glowSample = withCornerGlow
          ? computeDropGlowRipple(
              elapsed,
              totalDurationMs,
              isDropped,
              dxFromDropped,
              dyFromDropped,
              maxGlowRadius
            )
          : {
              intensity: 0,
              waveOffsetPx: 0,
              scaleBoost: 0,
              cornerWeights: { topLeft: 0, topRight: 0, bottomLeft: 0, bottomRight: 0 },
            };

        applyOutlineGlowToOverlay(id, glowSample, isDropped, target, droppedCx, droppedCy);

        if (isDropped) {
          // Smooth cushioned touchdown from elevated drag scale to 1.0
          const cushion = Math.sin(settleP * Math.PI) * 0.008;
          const scale = 1 + 0.045 * settleRem - cushion;
          const rot = releaseRot * settleRem;
          animPosRef.current[id] = { x: baseX, y: baseY };
          cardBoundsRef.current[id] = {
            ...target,
            left: baseX,
            top: baseY,
            right: baseX + target.width,
            bottom: baseY + target.height,
            cx: baseX + target.width * 0.5,
            cy: baseY + target.height * 0.5,
          };
          el.style.willChange = 'transform';
          el.style.transformOrigin = '';
          el.style.zIndex = settleRaw < 0.95 ? '20' : '8';
          el.style.transform = `translate3d(${baseX.toFixed(2)}px, ${baseY.toFixed(2)}px, 0) scale(${scale.toFixed(4)}) rotate(${rot.toFixed(2)}deg)`;
          const shadowStrength = Math.max(settleRem * 0.24, glowSample.intensity * 0.28);
          el.style.filter =
            shadowStrength > 0.02
              ? `drop-shadow(0 ${(10 * settleRem + 4 * glowSample.intensity).toFixed(1)}px ${(20 * Math.max(settleRem, glowSample.intensity)).toFixed(1)}px rgba(124, 58, 237, ${shadowStrength.toFixed(3)}))`
              : 'none';
        } else {
          // Nearby cards gently respond to the outward drop wave
          const dist = Math.max(1, Math.hypot(dxFromDropped, dyFromDropped));
          const pushX = (dxFromDropped / dist) * glowSample.waveOffsetPx;
          const pushY = (dyFromDropped / dist) * glowSample.waveOffsetPx;
          const drawX = baseX + pushX;
          const drawY = baseY + pushY;
          const scale = 1 + glowSample.scaleBoost;

          animPosRef.current[id] = { x: baseX, y: baseY };
          cardBoundsRef.current[id] = {
            ...target,
            left: baseX,
            top: baseY,
            right: baseX + target.width,
            bottom: baseY + target.height,
            cx: baseX + target.width * 0.5,
            cy: baseY + target.height * 0.5,
          };
          el.style.willChange = 'transform';
          el.style.transformOrigin = '';
          el.style.zIndex = glowSample.intensity > 0.05 ? '3' : '1';
          el.style.filter = 'none';
          el.style.transform = `translate3d(${drawX.toFixed(2)}px, ${drawY.toFixed(2)}px, 0) scale(${scale.toFixed(4)})`;
        }
      }

      if (elapsed < totalDurationMs) {
        dropRafRef.current = requestAnimationFrame(step);
      } else {
        dropRafRef.current = null;
        droppingIdRef.current = null;
        clearAllGlowOverlays();
        applySettledPositions(targetOrder, layout.byId, layout.totalHeight);
      }
    };

    dropRafRef.current = requestAnimationFrame(step);
  };

  const cancelPendingHold = () => {
    if (holdTimerRef.current !== null) {
      window.clearTimeout(holdTimerRef.current);
      holdTimerRef.current = null;
    }
    pointerDownInfoRef.current = null;
  };

  const finishActiveDrag = (cancelled: boolean) => {
    cancelPendingHold();
    const activeId = draggedIdRef.current;
    if (!activeId) return;

    if (dragRafRef.current !== null) {
      cancelAnimationFrame(dragRafRef.current);
      dragRafRef.current = null;
    }

    // Suppress any trailing click synthesized by the browser right after pointerup
    suppressNextClickUntilRef.current = performance.now() + 180;

    const finalX = dragStateRef.current.startX + dragStateRef.current.deltaX;
    const finalY = dragStateRef.current.startY + dragStateRef.current.deltaY;
    const releaseRot = lastDragRotRef.current;
    const didMove = dragStateRef.current.moved;

    const finalOrder = [...liveOrderRef.current];
    const initialOrder = initialOrderRef.current;
    const orderChanged =
      !cancelled &&
      didMove &&
      finalOrder.length === initialOrder.length &&
      finalOrder.some((id, idx) => id !== initialOrder[idx]);

    draggedIdRef.current = null;
    lastDragRotRef.current = 0;
    setIsDragActive(false);

    const captureStartPositions = (order: string[]) => {
      const startPositions: Record<string, { x: number; y: number }> = {};
      order.forEach((id) => {
        if (id === activeId) {
          startPositions[id] = { x: finalX, y: finalY };
        } else {
          const cur = animPosRef.current[id] ?? {
            x: targetSlotsByIdRef.current[id]?.left ?? 0,
            y: targetSlotsByIdRef.current[id]?.top ?? 0,
          };
          startPositions[id] = { x: cur.x, y: cur.y };
        }
      });
      return startPositions;
    };

    if (orderChanged) {
      // Smooth glide into new locked slot + radiant corner glow rippling to nearby notes (zero jump-back!)
      const startPositions = captureStartPositions(finalOrder);
      liveOrderRef.current = finalOrder;
      runDropAndGlowAnimation(activeId, finalOrder, releaseRot, startPositions, true);
      callbacksRef.current.onReorderNotes?.(finalOrder);
    } else if (didMove) {
      // Smooth glide back to original slot + radiant corner glow rippling to nearby notes
      const restoreOrder = [...initialOrder];
      const startPositions = captureStartPositions(restoreOrder);
      liveOrderRef.current = restoreOrder;
      runDropAndGlowAnimation(activeId, restoreOrder, releaseRot, startPositions, true);
    } else {
      // Held and released without dragging: keep selected, ease elevation down, and ripple outline glow
      const stayOrder = [...liveOrderRef.current];
      const startPositions = captureStartPositions(stayOrder);
      runDropAndGlowAnimation(activeId, stayOrder, releaseRot, startPositions, !cancelled);
      if (!cancelled) {
        callbacksRef.current.onReleaseHoldWithoutDrag?.(activeId);
      }
    }

    dragStateRef.current.moved = false;
    dragStateRef.current.deltaX = 0;
    dragStateRef.current.deltaY = 0;
  };

  // Fallback to responsive CSS grid before first DOM measurement
  if (containerWidth <= 0) {
    return (
      <div
        ref={containerRef}
        className={`w-full grid gap-2.5 sm:gap-3.5 items-start ${
          isGridView ? 'grid-cols-2' : 'grid-cols-1'
        }`}
      >
        {items.map((child, idx) => {
          const id = resolvedIds[idx];
          return (
            <div
              key={id}
              ref={(el) => {
                itemRefs.current[id] = el;
              }}
              className="w-full"
            >
              {child}
            </div>
          );
        })}
      </div>
    );
  }

  const gap = containerWidth >= 640 ? 14 : 10;
  const targetItemWidth = isGridView ? Math.max(1, (containerWidth - gap) / 2) : containerWidth;

  return (
    <div
      ref={containerRef}
      data-testid="morphing-notes-grid"
      data-grid-mode={isGridView ? 'grid' : 'column'}
      className="relative w-full"
      style={{
        height: `${Math.round(lastTotalHeightRef.current || 148)}px`,
        touchAction: isDragActive ? 'none' : 'pan-y',
      }}
      onClickCapture={(e) => {
        if (performance.now() < suppressNextClickUntilRef.current) {
          e.stopPropagation();
          e.preventDefault();
          if (!draggedIdRef.current) {
            suppressNextClickUntilRef.current = 0;
          }
        }
      }}
      onPointerMove={(e) => {
        if (pointerDownInfoRef.current && !draggedIdRef.current) {
          const dx = e.clientX - pointerDownInfoRef.current.startClientX;
          const dy = e.clientY - pointerDownInfoRef.current.startClientY;
          if (Math.hypot(dx, dy) > 18) {
            cancelPendingHold();
          }
          return;
        }

        const activeId = draggedIdRef.current;
        if (!activeId) return;
        e.preventDefault();

        const downInfo = pointerDownInfoRef.current;
        if (!downInfo) return;

        const deltaX = e.clientX - downInfo.startClientX;
        const deltaY = e.clientY - downInfo.startClientY;
        dragStateRef.current.deltaX = deltaX;
        dragStateRef.current.deltaY = deltaY;

        if (!dragStateRef.current.moved && Math.hypot(deltaX, deltaY) > 14) {
          dragStateRef.current.moved = true;
        }

        const cardH = heightsRef.current[activeId] ?? 148;
        const centerX = dragStateRef.current.startX + deltaX + targetItemWidth * 0.5;
        const centerY = dragStateRef.current.startY + deltaY + cardH * 0.5;

        const currentOrder = [...liveOrderRef.current];
        const currentSlotIdx = currentOrder.indexOf(activeId);
        if (currentSlotIdx >= 0) {
          const isDraggedPinned = lockedIdSetRef.current.has(activeId);
          let bestSameGroupSlotIdx = -1;
          let bestSameGroupDist = Number.MAX_VALUE;
          let hoveredOppositeSlotIdx = -1;
          const slots = slotBoundsRef.current;

          for (let s = 0; s < slots.length; s++) {
            const occupantId = currentOrder[s];
            const isSlotPinned = occupantId ? lockedIdSetRef.current.has(occupantId) : false;
            const rect = slots[s];
            if (!rect) continue;
            const padX = rect.width * 0.08;
            const padY = rect.height * 0.08;
            if (
              centerX >= rect.left - padX &&
              centerX <= rect.right + padX &&
              centerY >= rect.top - padY &&
              centerY <= rect.bottom + padY
            ) {
              if (isSlotPinned === isDraggedPinned) {
                if (s !== currentSlotIdx) {
                  const dist = Math.hypot(centerX - rect.cx, centerY - rect.cy);
                  if (dist < bestSameGroupDist) {
                    bestSameGroupDist = dist;
                    bestSameGroupSlotIdx = s;
                  }
                }
              } else {
                hoveredOppositeSlotIdx = s;
              }
            }
          }

          let maxPinnedBottom = -Number.MAX_VALUE;
          let minUnpinnedTop = Number.MAX_VALUE;
          for (let s = 0; s < slots.length; s++) {
            const occId = currentOrder[s];
            const rect = slots[s];
            if (!occId || !rect) continue;
            if (lockedIdSetRef.current.has(occId)) {
              if (rect.bottom > maxPinnedBottom) maxPinnedBottom = rect.bottom;
            } else {
              if (rect.top < minUnpinnedTop) minUnpinnedTop = rect.top;
            }
          }

          const crossedIntoOppositeGroup =
            hoveredOppositeSlotIdx >= 0 ||
            (isDraggedPinned && minUnpinnedTop < Number.MAX_VALUE && centerY > minUnpinnedTop) ||
            (!isDraggedPinned && maxPinnedBottom > -Number.MAX_VALUE && centerY < maxPinnedBottom);

          if (crossedIntoOppositeGroup) {
            const initialOrder = initialOrderRef.current;
            const differs =
              initialOrder.length === currentOrder.length &&
              currentOrder.some((id, idx) => id !== initialOrder[idx]);
            if (differs) {
              liveOrderRef.current = [...initialOrder];
              const nextLayout = computeLayout(liveOrderRef.current, containerWidth, isGridView);
              slotBoundsRef.current = nextLayout.slots;
              targetSlotsByIdRef.current = nextLayout.byId;
            }
          } else if (bestSameGroupSlotIdx >= 0 && bestSameGroupSlotIdx !== currentSlotIdx) {
            currentOrder.splice(currentSlotIdx, 1);
            currentOrder.splice(bestSameGroupSlotIdx, 0, activeId);
            liveOrderRef.current = currentOrder;
            const nextLayout = computeLayout(currentOrder, containerWidth, isGridView);
            slotBoundsRef.current = nextLayout.slots;
            targetSlotsByIdRef.current = nextLayout.byId;
            if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
              try {
                navigator.vibrate(12);
              } catch {
                // ignore
              }
            }
          }
        }
      }}
      onPointerUp={() => {
        if (draggedIdRef.current) {
          finishActiveDrag(false);
        } else {
          cancelPendingHold();
        }
      }}
      onPointerCancel={() => {
        if (draggedIdRef.current) {
          finishActiveDrag(true);
        } else {
          cancelPendingHold();
        }
      }}
    >
      {items.map((child, origIdx) => {
        const id = resolvedIds[origIdx];
        const initialBox = targetSlotsByIdRef.current[id];
        const initialX = animPosRef.current[id]?.x ?? initialBox?.left ?? 0;
        const initialY = animPosRef.current[id]?.y ?? initialBox?.top ?? 0;

        return (
          <div
            key={id}
            data-note-card-id={id}
            ref={(el) => {
              itemRefs.current[id] = el;
            }}
            onPointerDown={(e) => {
              if (!callbacksRef.current.onHoldNote && !callbacksRef.current.onReorderNotes) return;
              if (e.button !== 0 && e.pointerType === 'mouse') return;
              const targetEl = e.target as HTMLElement | null;
              if (targetEl && targetEl.closest('button, a, input')) return;

              cancelPendingHold();
              const downClientX = e.clientX;
              const downClientY = e.clientY;
              const pointerId = e.pointerId;
              const hostEl = e.currentTarget;

              pointerDownInfoRef.current = {
                pointerId,
                id,
                startClientX: downClientX,
                startClientY: downClientY,
              };

              holdTimerRef.current = window.setTimeout(() => {
                holdTimerRef.current = null;
                suppressNextClickUntilRef.current = performance.now() + 600;

                if (dropRafRef.current !== null) {
                  cancelAnimationFrame(dropRafRef.current);
                  dropRafRef.current = null;
                }
                droppingIdRef.current = null;
                clearAllGlowOverlays();

                if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
                  try {
                    navigator.vibrate(40);
                  } catch {
                    // ignore
                  }
                }
                callbacksRef.current.onHoldNote?.(id);

                measureCardHeights();
                const currentOrder = [...resolvedIdsRef.current];
                initialOrderRef.current = currentOrder;
                liveOrderRef.current = currentOrder;
                const layout = computeLayout(currentOrder, containerWidth, isGridView);
                slotBoundsRef.current = layout.slots;
                targetSlotsByIdRef.current = layout.byId;

                const currentBox = cardBoundsRef.current[id] ?? layout.byId[id];
                dragStateRef.current = {
                  startX: currentBox?.left ?? 0,
                  startY: currentBox?.top ?? 0,
                  deltaX: 0,
                  deltaY: 0,
                  moved: false,
                  elevationStartTime: performance.now(),
                };
                draggedIdRef.current = id;
                lastDragRotRef.current = 0;
                setIsDragActive(true);
                startDragAnimationLoop();

                try {
                  if (containerRef.current) {
                    containerRef.current.setPointerCapture(pointerId);
                  } else {
                    hostEl.setPointerCapture(pointerId);
                  }
                } catch {
                  // ignore
                }
              }, 320);
            }}
            onClickCapture={(e) => {
              if (performance.now() < suppressNextClickUntilRef.current) {
                e.stopPropagation();
                e.preventDefault();
                if (!draggedIdRef.current) {
                  suppressNextClickUntilRef.current = 0;
                }
              }
            }}
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              width: `${targetItemWidth}px`,
              transform: `translate3d(${initialX.toFixed(2)}px, ${initialY.toFixed(2)}px, 0)`,
            }}
          >
            {child}
            <div
              ref={(el) => {
                glowOverlayRefs.current[id] = el;
              }}
              data-drop-glow-overlay={id}
              data-glow-intensity="0"
              aria-hidden="true"
              className="pointer-events-none absolute -inset-[1px] rounded-xl sm:rounded-2xl z-20"
              style={{ opacity: 0 }}
            >
              <div
                ref={(el) => {
                  glowRingRefs.current[id] = el;
                }}
                className="absolute inset-0 rounded-[inherit]"
                style={{
                  padding: '2px',
                  WebkitMask: 'linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0)',
                  WebkitMaskComposite: 'xor',
                  mask: 'linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0)',
                  maskComposite: 'exclude',
                }}
              />
            </div>
          </div>
        );
      })}
    </div>
  );
};

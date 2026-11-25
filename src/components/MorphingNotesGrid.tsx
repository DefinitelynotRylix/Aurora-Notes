import React, { useEffect, useLayoutEffect, useRef, useState } from 'react';

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

const easeFastOutSlowIn = (t: number): number =>
  t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;

/**
 * Smoothly morphs child note cards between a 2-column Masonry Grid (`isGridView = true`)
 * and a Full-Width Single Column layout (`isGridView = false`), with interactive
 * Hold-to-Elevate, subtle wobbly physics, neighbor hover interaction, and Drag-to-Reorder
 * in both Masonry and Single Column view modes.
 *
 * Performance architecture:
 * - Grid <-> Column morph renders ONCE at target width and animates transforms purely in
 *   requestAnimationFrame DOM mutations (zero per-frame React re-renders, zero width reflows).
 * - Drag wobble, neighbor hover reaction, slot swap glides, and drop-back settle mutate
 *   GPU transforms directly on DOM refs (zero React re-renders during drag or drop).
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
  const morphStartPosRef = useRef<Record<string, { x: number; y: number }>>({});
  const targetSlotsByIdRef = useRef<Record<string, RectBox>>({});
  const slotBoundsRef = useRef<RectBox[]>([]);
  const cardBoundsRef = useRef<Record<string, RectBox>>({});
  const lastGridViewRef = useRef<boolean>(isGridView);
  const lastTotalHeightRef = useRef<number>(0);

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

  const suppressNextClickRef = useRef<boolean>(false);

  // Sync liveOrderRef when upstream item order changes (outside active drag)
  if (!draggedIdRef.current) {
    liveOrderRef.current = resolvedIds;
  }

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

  // Pure GPU FLIP morph when toggling Grid <-> Column (1 React render, 0 per-frame setState)
  useLayoutEffect(() => {
    if (containerWidth <= 0) return;

    const didModeChange = lastGridViewRef.current !== isGridView;
    const prevPositions: Record<string, { x: number; y: number }> = {};
    if (didModeChange) {
      resolvedIds.forEach((id) => {
        const pos = animPosRef.current[id];
        if (pos) prevPositions[id] = { x: pos.x, y: pos.y };
      });
      morphStartPosRef.current = prevPositions;
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

    if (didModeChange && Object.keys(prevPositions).length > 0) {
      if (morphRafRef.current !== null) {
        cancelAnimationFrame(morphRafRef.current);
      }
      isMorphAnimatingRef.current = true;
      const startHeight = Math.max(lastTotalHeightRef.current, layout.totalHeight);
      if (containerRef.current) {
        containerRef.current.style.height = `${Math.round(startHeight)}px`;
      }

      const durationMs = 240;
      const startTime = performance.now();

      const applyMorphFrame = (p: number) => {
        const scale = 0.94 + 0.06 * p;
        const opacity = 0.9 + 0.1 * p;
        for (let i = 0; i < order.length; i++) {
          const id = order[i];
          const target = layout.byId[id];
          const el = itemRefs.current[id];
          if (!target || !el) continue;
          const start = prevPositions[id] ?? { x: target.left, y: target.top };
          const x = start.x + (target.left - start.x) * p;
          const y = start.y + (target.top - start.y) * p;
          animPosRef.current[id] = { x, y };
          cardBoundsRef.current[id] = {
            ...target,
            left: x,
            top: y,
            right: x + target.width,
            bottom: y + target.height,
            cx: x + target.width * 0.5,
            cy: y + target.height * 0.5,
          };
          el.style.willChange = 'transform, opacity';
          el.style.transform = `translate3d(${x.toFixed(2)}px, ${y.toFixed(2)}px, 0) scale(${scale.toFixed(4)})`;
          el.style.opacity = opacity.toFixed(3);
          el.style.zIndex = String(order.length - i);
        }
      };

      // Immediately place cards at start coordinates BEFORE browser paints the resized cards
      applyMorphFrame(0);

      const step = (now: number) => {
        const rawT = Math.min(1, Math.max(0, (now - startTime) / durationMs));
        const eased = easeFastOutSlowIn(rawT);
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
          el.style.willChange = 'transform';
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
          el.style.zIndex = glideFactor > 0.05 ? '5' : '1';
          el.style.filter = 'none';
          el.style.transform = `translate3d(${drawX.toFixed(2)}px, ${drawY.toFixed(2)}px, 0) scale(${scale.toFixed(4)}) rotate(${rot.toFixed(2)}deg)`;
        }
      }

      dragRafRef.current = requestAnimationFrame(tick);
    };

    dragRafRef.current = requestAnimationFrame(tick);
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

    const finalX = dragStateRef.current.startX + dragStateRef.current.deltaX;
    const finalY = dragStateRef.current.startY + dragStateRef.current.deltaY;
    const didMove = dragStateRef.current.moved;

    const finalOrder = [...liveOrderRef.current];
    const initialOrder = initialOrderRef.current;
    const orderChanged =
      !cancelled &&
      didMove &&
      finalOrder.length === initialOrder.length &&
      finalOrder.some((id, idx) => id !== initialOrder[idx]);

    draggedIdRef.current = null;
    setIsDragActive(false);

    if (orderChanged) {
      // Snap all cards directly into their new locked slot positions with zero jump-back
      const layout = computeLayout(finalOrder, containerWidth, isGridView);
      slotBoundsRef.current = layout.slots;
      targetSlotsByIdRef.current = layout.byId;
      droppingIdRef.current = null;
      applySettledPositions(finalOrder, layout.byId, layout.totalHeight);
      callbacksRef.current.onReorderNotes?.(finalOrder);
    } else if (didMove) {
      // Smooth pure-DOM drop-back into original slot (zero React re-renders, zero lag spike!)
      const restoreOrder = [...initialOrder];
      liveOrderRef.current = restoreOrder;
      const layout = computeLayout(restoreOrder, containerWidth, isGridView);
      slotBoundsRef.current = layout.slots;
      targetSlotsByIdRef.current = layout.byId;

      const startPositions: Record<string, { x: number; y: number }> = {};
      restoreOrder.forEach((id) => {
        if (id === activeId) {
          startPositions[id] = { x: finalX, y: finalY };
        } else {
          const cur = animPosRef.current[id] ?? {
            x: layout.byId[id]?.left ?? 0,
            y: layout.byId[id]?.top ?? 0,
          };
          startPositions[id] = { x: cur.x, y: cur.y };
        }
      });

      droppingIdRef.current = activeId;
      const startTime = performance.now();
      const durationMs = 175;

      const settleStep = (now: number) => {
        const rawT = Math.min(1, Math.max(0, (now - startTime) / durationMs));
        const eased = easeFastOutSlowIn(rawT);
        const rem = 1 - eased;

        for (let i = 0; i < restoreOrder.length; i++) {
          const id = restoreOrder[i];
          const el = itemRefs.current[id];
          const target = layout.byId[id];
          if (!el || !target) continue;
          const s = startPositions[id] ?? { x: target.left, y: target.top };
          const x = s.x + (target.left - s.x) * eased;
          const y = s.y + (target.top - s.y) * eased;
          animPosRef.current[id] = { x, y };
          if (id === activeId) {
            const scale = 1 + 0.04 * rem;
            el.style.zIndex = '15';
            el.style.transform = `translate3d(${x.toFixed(2)}px, ${y.toFixed(2)}px, 0) scale(${scale.toFixed(4)})`;
            el.style.filter =
              rem > 0.05
                ? `drop-shadow(0 ${(12 * rem).toFixed(1)}px ${(20 * rem).toFixed(1)}px rgba(89, 37, 220, ${(0.22 * rem).toFixed(3)}))`
                : 'none';
          } else {
            el.style.zIndex = '1';
            el.style.filter = 'none';
            el.style.transform = `translate3d(${x.toFixed(2)}px, ${y.toFixed(2)}px, 0)`;
          }
        }

        if (rawT < 1) {
          dropRafRef.current = requestAnimationFrame(settleStep);
        } else {
          dropRafRef.current = null;
          droppingIdRef.current = null;
          applySettledPositions(restoreOrder, layout.byId, layout.totalHeight);
        }
      };

      if (dropRafRef.current !== null) cancelAnimationFrame(dropRafRef.current);
      dropRafRef.current = requestAnimationFrame(settleStep);
    } else {
      // Released in place without dragging: settle immediately with zero lag
      droppingIdRef.current = null;
      const layout = computeLayout(liveOrderRef.current, containerWidth, isGridView);
      applySettledPositions(liveOrderRef.current, layout.byId, layout.totalHeight);
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
      onPointerMove={(e) => {
        if (pointerDownInfoRef.current && !draggedIdRef.current) {
          const dx = Math.abs(e.clientX - pointerDownInfoRef.current.startClientX);
          const dy = Math.abs(e.clientY - pointerDownInfoRef.current.startClientY);
          if (dx > 10 || dy > 10) {
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

        if (!dragStateRef.current.moved && Math.hypot(deltaX, deltaY) > 8) {
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
                suppressNextClickRef.current = true;

                if (dropRafRef.current !== null) {
                  cancelAnimationFrame(dropRafRef.current);
                  dropRafRef.current = null;
                }
                droppingIdRef.current = null;

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
              }, 380);
            }}
            onClickCapture={(e) => {
              if (suppressNextClickRef.current) {
                e.stopPropagation();
                e.preventDefault();
                suppressNextClickRef.current = false;
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
          </div>
        );
      })}
    </div>
  );
};

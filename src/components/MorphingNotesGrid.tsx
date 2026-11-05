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

/**
 * Smoothly morphs child note cards between a 2-column Masonry Grid (`isGridView = true`)
 * and a Full-Width Single Column layout (`isGridView = false`), with interactive
 * Hold-to-Elevate, subtle wobbly physics, neighbor hover interaction, and Drag-to-Reorder
 * in both Masonry and Single Column view modes.
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
  const itemRefs = useRef<(HTMLDivElement | null)[]>([]);
  const [containerWidth, setContainerWidth] = useState<number>(0);
  const [progress, setProgress] = useState<number>(() => (isGridView ? 0 : 1));
  const progressRef = useRef<number>(isGridView ? 0 : 1);
  const rafRef = useRef<number | null>(null);
  const [, setMeasureTick] = useState(0);

  const items = React.Children.toArray(children);
  const resolvedIds = React.useMemo(() => {
    if (itemIds && itemIds.length === items.length) return itemIds;
    return items.map((child, idx) => String((child as React.ReactElement)?.key ?? idx));
  }, [itemIds, items]);

  const lockedIdSet = React.useMemo(
    () => new Set<string>(lockedItemIds ?? []),
    [lockedItemIds]
  );
  const heldLockedIdRef = useRef<string | null>(null);

  // Live slot permutation order during drag-to-reorder
  const [liveOrderIds, setLiveOrderIds] = useState<string[]>(resolvedIds);
  const liveOrderRef = useRef<string[]>(resolvedIds);

  const [draggedId, setDraggedId] = useState<string | null>(null);
  const draggedIdRef = useRef<string | null>(null);
  const [droppingId, setDroppingId] = useState<string | null>(null);
  const droppingIdRef = useRef<string | null>(null);
  const [wobblePhase, setWobblePhase] = useState<number>(0);

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
  }>({
    startX: 0,
    startY: 0,
    deltaX: 0,
    deltaY: 0,
    moved: false,
  });

  const animPosRef = useRef<Record<string, { x: number; y: number }>>({});
  const slotBoundsRef = useRef<RectBox[]>([]);
  const cardBoundsRef = useRef<Record<string, RectBox>>({});
  const suppressNextClickRef = useRef<boolean>(false);

  // Sync liveOrderIds when not actively dragging
  useEffect(() => {
    if (!draggedIdRef.current) {
      liveOrderRef.current = resolvedIds;
      setLiveOrderIds(resolvedIds);
    }
  }, [resolvedIds]);

  // Observe container width & child height changes
  useLayoutEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const updateWidth = () => {
      if (el.clientWidth > 0) {
        setContainerWidth(el.clientWidth);
      }
    };
    updateWidth();

    const ro = new ResizeObserver(() => {
      updateWidth();
      setMeasureTick((t) => t + 1);
    });
    ro.observe(el);
    itemRefs.current.forEach((childEl) => {
      if (childEl) ro.observe(childEl);
    });

    return () => ro.disconnect();
  }, [items.length]);

  // Smoothly animate progress between 0 (Masonry Grid) and 1 (Single Column)
  useEffect(() => {
    const target = isGridView ? 0 : 1;
    const startVal = progressRef.current;
    if (Math.abs(target - startVal) < 0.001) {
      progressRef.current = target;
      setProgress(target);
      return;
    }

    const durationMs = 320;
    const startTime = performance.now();

    const easeFastOutSlowIn = (t: number): number => {
      return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    };

    const step = (now: number) => {
      const elapsed = now - startTime;
      const rawT = Math.min(1, Math.max(0, elapsed / durationMs));
      const eased = easeFastOutSlowIn(rawT);
      const nextP = startVal + (target - startVal) * eased;
      progressRef.current = nextP;
      setProgress(nextP);

      if (rawT < 1) {
        rafRef.current = requestAnimationFrame(step);
      } else {
        progressRef.current = target;
        setProgress(target);
      }
    };

    if (rafRef.current !== null) {
      cancelAnimationFrame(rafRef.current);
    }
    rafRef.current = requestAnimationFrame(step);

    return () => {
      if (rafRef.current !== null) {
        cancelAnimationFrame(rafRef.current);
      }
    };
  }, [isGridView]);

  // 60fps wobble + smooth slot glide animation loop while dragging or settling
  useEffect(() => {
    if (!draggedId && !droppingId) return;
    let frameId: number | null = null;
    const start = performance.now();

    const tick = (now: number) => {
      setWobblePhase(((now - start) / 1000) * 16.5);
      frameId = requestAnimationFrame(tick);
    };
    frameId = requestAnimationFrame(tick);
    return () => {
      if (frameId !== null) cancelAnimationFrame(frameId);
    };
  }, [draggedId, droppingId]);

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

    const finalX = dragStateRef.current.startX + dragStateRef.current.deltaX;
    const finalY = dragStateRef.current.startY + dragStateRef.current.deltaY;
    animPosRef.current[activeId] = { x: finalX, y: finalY };

    const didMove = dragStateRef.current.moved;
    const finalOrder = [...liveOrderRef.current];

    draggedIdRef.current = null;
    setDraggedId(null);
    droppingIdRef.current = activeId;
    setDroppingId(activeId);

    window.setTimeout(() => {
      if (droppingIdRef.current === activeId) {
        droppingIdRef.current = null;
        setDroppingId(null);
      }
    }, 240);

    if (!cancelled && didMove && onReorderNotes) {
      const orderChanged =
        finalOrder.length === resolvedIds.length &&
        finalOrder.some((id, idx) => id !== resolvedIds[idx]);
      if (orderChanged) {
        onReorderNotes(finalOrder);
      }
    } else if (!cancelled && !didMove && onReleaseHoldWithoutDrag) {
      onReleaseHoldWithoutDrag(activeId);
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
        {items.map((child, idx) => (
          <div
            key={(child as React.ReactElement)?.key ?? idx}
            ref={(el) => {
              itemRefs.current[idx] = el;
            }}
            className="w-full"
          >
            {child}
          </div>
        ))}
      </div>
    );
  }

  const p = Math.min(1, Math.max(0, progress));
  const gap = containerWidth >= 640 ? 14 : 10;
  const cols = 2;
  const gridItemWidth = Math.max(1, (containerWidth - gap * (cols - 1)) / cols);
  const colItemWidth = containerWidth;
  const currentItemWidth = gridItemWidth + (colItemWidth - gridItemWidth) * p;

  if (itemRefs.current.length > items.length) {
    itemRefs.current.length = items.length;
  }

  const idToOrigIdx: Record<string, number> = {};
  resolvedIds.forEach((id, idx) => {
    idToOrigIdx[id] = idx;
  });

  const effectiveOrderIds =
    liveOrderIds.length === resolvedIds.length &&
    liveOrderIds.every((id) => idToOrigIdx[id] !== undefined)
      ? liveOrderIds
      : resolvedIds;

  const itemHeightsByOrigIdx = items.map((_, idx) => {
    const el = itemRefs.current[idx];
    return el ? el.offsetHeight : 148;
  });

  // 1. Compute 2-column Masonry Grid coordinates per slot in effectiveOrderIds
  const colHeights = [0, 0];
  const slotGridX: number[] = [];
  const slotGridY: number[] = [];
  for (let s = 0; s < effectiveOrderIds.length; s++) {
    const origIdx = idToOrigIdx[effectiveOrderIds[s]] ?? s;
    const h = itemHeightsByOrigIdx[origIdx];
    const lane = colHeights[0] <= colHeights[1] ? 0 : 1;
    slotGridX[s] = lane === 0 ? 0 : containerWidth - gridItemWidth;
    slotGridY[s] = colHeights[lane];
    colHeights[lane] += h + gap;
  }
  const gridTotalHeight = Math.max(
    0,
    Math.max(colHeights[0], colHeights[1]) - (items.length > 0 ? gap : 0)
  );

  // 2. Compute Single-Column vertical stack coordinates per slot in effectiveOrderIds
  let runningY = 0;
  const slotColX: number[] = [];
  const slotColY: number[] = [];
  for (let s = 0; s < effectiveOrderIds.length; s++) {
    const origIdx = idToOrigIdx[effectiveOrderIds[s]] ?? s;
    const h = itemHeightsByOrigIdx[origIdx];
    slotColX[s] = 0;
    slotColY[s] = runningY;
    runningY += h + gap;
  }
  const colTotalHeight = Math.max(0, runningY - (items.length > 0 ? gap : 0));

  const totalHeight = Math.round(gridTotalHeight + (colTotalHeight - gridTotalHeight) * p);

  // 3. Compute interpolated target slot boxes
  const nextSlotBounds: RectBox[] = [];
  const idToSlotIdx: Record<string, number> = {};

  for (let s = 0; s < effectiveOrderIds.length; s++) {
    const id = effectiveOrderIds[s];
    idToSlotIdx[id] = s;
    const origIdx = idToOrigIdx[id] ?? s;
    const h = itemHeightsByOrigIdx[origIdx];
    const sx = slotGridX[s] + (slotColX[s] - slotGridX[s]) * p;
    const rawSy = slotGridY[s] + (slotColY[s] - slotGridY[s]) * p;
    const sy = s === 0 ? 0 : s === 1 && p <= 0.001 ? 0 : rawSy;
    nextSlotBounds[s] = {
      left: sx,
      top: sy,
      right: sx + currentItemWidth,
      bottom: sy + h,
      width: currentItemWidth,
      height: h,
      cx: sx + currentItemWidth * 0.5,
      cy: sy + h * 0.5,
    };
  }
  slotBoundsRef.current = nextSlotBounds;

  // Compute dragged card center for neighbor hover interaction
  let dragCenterX = -10000;
  let dragCenterY = -10000;
  if (draggedId) {
    const dOrigIdx = idToOrigIdx[draggedId] ?? 0;
    const dH = itemHeightsByOrigIdx[dOrigIdx] ?? 148;
    dragCenterX = dragStateRef.current.startX + dragStateRef.current.deltaX + currentItemWidth * 0.5;
    dragCenterY = dragStateRef.current.startY + dragStateRef.current.deltaY + dH * 0.5;
  }

  return (
    <div
      ref={containerRef}
      className="relative w-full"
      style={{
        height: `${totalHeight}px`,
        touchAction: draggedId ? 'none' : 'pan-y',
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

        const origIdx = idToOrigIdx[activeId] ?? 0;
        const cardH = itemHeightsByOrigIdx[origIdx] ?? 148;
        const centerX = dragStateRef.current.startX + deltaX + currentItemWidth * 0.5;
        const centerY = dragStateRef.current.startY + deltaY + cardH * 0.5;

        const currentOrder = [...liveOrderRef.current];
        const currentSlotIdx = currentOrder.indexOf(activeId);
        if (currentSlotIdx >= 0) {
          let bestSlotIdx = -1;
          let bestDist = Number.MAX_VALUE;
          const slots = slotBoundsRef.current;

          for (let s = 0; s < slots.length; s++) {
            if (s === currentSlotIdx) continue;
            // Never allow swapping into a pinned (locked) note's slot
            const occupantId = currentOrder[s];
            if (occupantId && lockedIdSet.has(occupantId)) continue;

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
              const dist = Math.hypot(centerX - rect.cx, centerY - rect.cy);
              if (dist < bestDist) {
                bestDist = dist;
                bestSlotIdx = s;
              }
            }
          }

          if (bestSlotIdx >= 0 && bestSlotIdx !== currentSlotIdx) {
            currentOrder.splice(currentSlotIdx, 1);
            currentOrder.splice(bestSlotIdx, 0, activeId);
            liveOrderRef.current = currentOrder;
            setLiveOrderIds(currentOrder);
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
        if (heldLockedIdRef.current) {
          const lockedId = heldLockedIdRef.current;
          heldLockedIdRef.current = null;
          cancelPendingHold();
          if (onReleaseHoldWithoutDrag) {
            onReleaseHoldWithoutDrag(lockedId);
          }
          return;
        }
        if (draggedIdRef.current) {
          finishActiveDrag(false);
        } else {
          cancelPendingHold();
        }
      }}
      onPointerCancel={() => {
        heldLockedIdRef.current = null;
        if (draggedIdRef.current) {
          finishActiveDrag(true);
        } else {
          cancelPendingHold();
        }
      }}
    >
      {items.map((child, origIdx) => {
        const id = resolvedIds[origIdx];
        const slotIdx = idToSlotIdx[id] ?? origIdx;
        const targetBox = nextSlotBounds[slotIdx] ?? {
          left: 0,
          top: 0,
          right: currentItemWidth,
          bottom: itemHeightsByOrigIdx[origIdx],
          width: currentItemWidth,
          height: itemHeightsByOrigIdx[origIdx],
          cx: currentItemWidth * 0.5,
          cy: itemHeightsByOrigIdx[origIdx] * 0.5,
        };

        const isThisDragged = draggedId === id;
        const isThisDropping = droppingId === id;

        let drawX = targetBox.left;
        let drawY = targetBox.top;

        if (isThisDragged) {
          drawX = dragStateRef.current.startX + dragStateRef.current.deltaX;
          drawY = dragStateRef.current.startY + dragStateRef.current.deltaY;
          animPosRef.current[id] = { x: drawX, y: drawY };
        } else if (draggedId || isThisDropping) {
          const prev = animPosRef.current[id] ?? { x: targetBox.left, y: targetBox.top };
          const dx = targetBox.left - prev.x;
          const dy = targetBox.top - prev.y;
          if (Math.hypot(dx, dy) > 0.6) {
            drawX = prev.x + dx * 0.28;
            drawY = prev.y + dy * 0.28;
          } else {
            drawX = targetBox.left;
            drawY = targetBox.top;
          }
          animPosRef.current[id] = { x: drawX, y: drawY };
        } else {
          animPosRef.current[id] = { x: targetBox.left, y: targetBox.top };
        }

        cardBoundsRef.current[id] = {
          left: drawX,
          top: drawY,
          right: drawX + currentItemWidth,
          bottom: drawY + itemHeightsByOrigIdx[origIdx],
          width: currentItemWidth,
          height: itemHeightsByOrigIdx[origIdx],
          cx: drawX + currentItemWidth * 0.5,
          cy: drawY + itemHeightsByOrigIdx[origIdx] * 0.5,
        };

        let scale = 1;
        let rotateDeg = 0;
        let zIndex = 1;

        if (isThisDragged) {
          scale = 1.045;
          rotateDeg = Math.sin(wobblePhase) * 1.15;
          zIndex = 30;
        } else if (isThisDropping) {
          const remDist = Math.hypot(targetBox.left - drawX, targetBox.top - drawY);
          const settleFactor = Math.min(1, remDist / 42);
          scale = 1 + 0.03 * settleFactor;
          rotateDeg = Math.sin(wobblePhase * 1.2) * 0.7 * settleFactor;
          zIndex = 15;
        } else if (draggedId && !lockedIdSet.has(id)) {
          const cardCx = drawX + currentItemWidth * 0.5;
          const cardCy = drawY + itemHeightsByOrigIdx[origIdx] * 0.5;
          const distToDragged = Math.hypot(dragCenterX - cardCx, dragCenterY - cardCy);
          const hoverProximity = Math.max(
            0,
            Math.min(1, 1 - distToDragged / (currentItemWidth * 1.15))
          );
          const glideDist = Math.hypot(targetBox.left - drawX, targetBox.top - drawY);
          const glideFactor = Math.min(1, glideDist / 40);
          const wobbleIntensity = Math.max(hoverProximity * 0.85, glideFactor * 1.0);
          rotateDeg = Math.sin(wobblePhase * 1.25 + origIdx * 1.7) * wobbleIntensity;
          scale = 1 - hoverProximity * 0.015;
          zIndex = glideFactor > 0.05 ? 5 : 1;
        }

        return (
          <div
            key={(child as React.ReactElement)?.key ?? origIdx}
            ref={(el) => {
              itemRefs.current[origIdx] = el;
            }}
            onPointerDown={(e) => {
              if (!onHoldNote && !onReorderNotes) return;
              if (e.button !== 0 && e.pointerType === 'mouse') return;
              const targetEl = e.target as HTMLElement | null;
              if (targetEl && targetEl.closest('button, a, input')) return;

              cancelPendingHold();
              heldLockedIdRef.current = null;
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

                if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
                  try {
                    navigator.vibrate(40);
                  } catch {
                    // ignore
                  }
                }
                if (onHoldNote) {
                  onHoldNote(id);
                }

                // Pinned notes cannot be reordered when pin is active
                if (lockedIdSet.has(id)) {
                  heldLockedIdRef.current = id;
                  return;
                }

                const currentBox = cardBoundsRef.current[id] ?? targetBox;
                dragStateRef.current = {
                  startX: currentBox.left,
                  startY: currentBox.top,
                  deltaX: 0,
                  deltaY: 0,
                  moved: false,
                };
                liveOrderRef.current = [...resolvedIds];
                setLiveOrderIds([...resolvedIds]);
                draggedIdRef.current = id;
                setDraggedId(id);

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
              width: `${currentItemWidth}px`,
              zIndex,
              transform: `translate3d(${drawX.toFixed(2)}px, ${drawY.toFixed(2)}px, 0) scale(${scale.toFixed(4)}) rotate(${rotateDeg.toFixed(2)}deg)`,
              filter: isThisDragged ? 'drop-shadow(0 16px 28px rgba(89, 37, 220, 0.26))' : 'none',
              willChange:
                draggedId || isThisDropping || (p > 0.001 && p < 0.999)
                  ? 'transform, width'
                  : 'auto',
            }}
          >
            {child}
          </div>
        );
      })}
    </div>
  );
};

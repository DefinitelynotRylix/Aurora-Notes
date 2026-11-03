import React, { useEffect, useLayoutEffect, useRef, useState } from 'react';

interface MorphingNotesGridProps {
  isGridView: boolean;
  children: React.ReactNode;
}

/**
 * Smoothly morphs child note cards between a 2-column Masonry Grid (`isGridView = true`)
 * and a Full-Width Single Column layout (`isGridView = false`) by continuously interpolating
 * each card's real pixel width and `(x, y)` coordinates over 320ms without scaleX font distortion
 * or 1-frame layout snaps.
 */
export const MorphingNotesGrid: React.FC<MorphingNotesGridProps> = ({
  isGridView,
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

    // FastOutSlowIn cubic-bezier(0.4, 0.0, 0.2, 1.0) approximation
    const easeFastOutSlowIn = (t: number): number => {
      return t < 0.5
        ? 4 * t * t * t
        : 1 - Math.pow(-2 * t + 2, 3) / 2;
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

  const itemHeights = items.map((_, idx) => {
    const el = itemRefs.current[idx];
    return el ? el.offsetHeight : 148;
  });

  // 1. Compute 2-column Masonry Grid coordinates
  const colHeights = [0, 0];
  const gridX: number[] = [];
  const gridY: number[] = [];
  for (let i = 0; i < items.length; i++) {
    const lane = colHeights[0] <= colHeights[1] ? 0 : 1;
    gridX[i] = lane === 0 ? 0 : containerWidth - gridItemWidth;
    gridY[i] = colHeights[lane];
    colHeights[lane] += itemHeights[i] + gap;
  }
  const gridTotalHeight = Math.max(0, Math.max(colHeights[0], colHeights[1]) - (items.length > 0 ? gap : 0));

  // 2. Compute Single-Column vertical stack coordinates
  let runningY = 0;
  const colX: number[] = [];
  const colY: number[] = [];
  for (let i = 0; i < items.length; i++) {
    colX[i] = 0;
    colY[i] = runningY;
    runningY += itemHeights[i] + gap;
  }
  const colTotalHeight = Math.max(0, runningY - (items.length > 0 ? gap : 0));

  const totalHeight = Math.round(gridTotalHeight + (colTotalHeight - gridTotalHeight) * p);

  return (
    <div
      ref={containerRef}
      className="relative w-full"
      style={{ height: `${totalHeight}px` }}
    >
      {items.map((child, i) => {
        const x = gridX[i] + (colX[i] - gridX[i]) * p;
        const y = gridY[i] + (colY[i] - gridY[i]) * p;
        return (
          <div
            key={(child as React.ReactElement)?.key ?? i}
            ref={(el) => {
              itemRefs.current[i] = el;
            }}
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              width: `${currentItemWidth}px`,
              transform: `translate3d(${x.toFixed(2)}px, ${y.toFixed(2)}px, 0)`,
              willChange: p > 0.001 && p < 0.999 ? 'transform, width' : 'auto',
            }}
          >
            {child}
          </div>
        );
      })}
    </div>
  );
};

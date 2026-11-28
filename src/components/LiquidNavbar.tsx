import React, { useEffect, useRef, useState } from 'react';
import { ActiveTab } from '../types';
import {
  FileText,
  Archive,
  ArchiveRestore,
  Trash2,
  Settings,
  Share2,
  Tag,
  Edit3,
  X,
} from 'lucide-react';

interface LiquidNavbarProps {
  activeTab: ActiveTab;
  pendingTab?: ActiveTab;
  isNavTransitionPending?: boolean;
  onChangeTab: (tab: ActiveTab) => void;
  archivedCount: number;
  trashCount?: number;
  isHapticsEnabled?: boolean;
  selectedCount?: number;
  isArchiveTab?: boolean;
  onShareSelected?: () => void;
  onTagSelected?: () => void;
  onArchiveOrRestoreSelected?: () => void;
  onEditSelected?: () => void;
  onDeleteSelected?: () => void;
  onClearSelection?: () => void;
}

interface NavItem {
  id: ActiveTab;
  label: string;
  icon: React.ElementType;
  badge?: number;
}

function triggerWebHaptic(enabled: boolean) {
  if (
    enabled &&
    typeof navigator !== 'undefined' &&
    typeof navigator.vibrate === 'function'
  ) {
    navigator.vibrate(30);
  }
}

const easeSmoothOvershoot = (t: number, overshoot: number = 1.65): number => {
  const clamped = Math.min(1, Math.max(0, t));
  const inv = clamped - 1;
  return 1 + (overshoot + 1) * Math.pow(inv, 3) + overshoot * Math.pow(inv, 2);
};

export const LiquidNavbar: React.FC<LiquidNavbarProps> = ({
  activeTab,
  pendingTab,
  isNavTransitionPending = false,
  onChangeTab,
  archivedCount,
  trashCount = 0,
  isHapticsEnabled = true,
  selectedCount = 0,
  isArchiveTab = false,
  onShareSelected,
  onTagSelected,
  onArchiveOrRestoreSelected,
  onEditSelected,
  onDeleteSelected,
  onClearSelection,
}) => {
  const isSelectionMode = selectedCount > 0;
  const effectiveTab = isNavTransitionPending && pendingTab ? pendingTab : activeTab;

  const items: NavItem[] = [
    { id: 'notes', label: 'Notes', icon: FileText },
    { id: 'archive', label: 'Archive', icon: Archive, badge: archivedCount },
    { id: 'trash', label: 'Trash', icon: Trash2, badge: trashCount },
    { id: 'settings', label: 'Settings', icon: Settings },
  ];

  const targetIndex = Math.max(
    0,
    items.findIndex((item) => item.id === effectiveTab)
  );

  const trackRef = useRef<HTMLDivElement>(null);
  const pillRef = useRef<HTMLDivElement>(null);
  const trackWidthRef = useRef<number>(320);
  const [isHoldingPill, setIsHoldingPill] = useState(false);
  const [dragTabPos, setDragTabPos] = useState(targetIndex);
  const dragTabPosRef = useRef<number>(targetIndex);
  dragTabPosRef.current = dragTabPos;

  const animRef = useRef({
    currentLeft: targetIndex,
    currentRight: targetIndex + 1,
    startLeft: targetIndex,
    startRight: targetIndex + 1,
    targetLeft: targetIndex,
    targetRight: targetIndex + 1,
    startTime: 0,
    rafId: 0,
  });

  const gestureRef = useRef<{
    pointerId: number | null;
    startX: number;
    currentX: number;
    tappedIndex: number;
    holdModeActive: boolean;
    holdTimer: number | null;
  }>({
    pointerId: null,
    startX: 0,
    currentX: 0,
    tappedIndex: targetIndex,
    holdModeActive: false,
    holdTimer: null,
  });

  // Directly updates GPU-composited transform on the pill element (0 React re-renders, 0 layout/paint)
  const applyPillTransform = (leftSlot: number, rightSlot: number) => {
    const el = pillRef.current;
    if (!el) return;
    const tabCount = Math.max(1, items.length);
    const width = Math.max(1, trackWidthRef.current);
    const slotWidth = width / tabCount;
    const clampedLeft = Math.max(0, Math.min(tabCount - 1, leftSlot));
    const clampedRight = Math.max(clampedLeft + 1, Math.min(tabCount, rightSlot));
    const leftPx = clampedLeft * slotWidth;
    const scaleX = Math.max(1, clampedRight - clampedLeft);
    el.style.transform = `translate3d(${leftPx.toFixed(2)}px, 0, 0) scaleX(${scaleX.toFixed(4)})`;
  };

  useEffect(() => {
    const updateMeasure = () => {
      if (trackRef.current) {
        const rect = trackRef.current.getBoundingClientRect();
        if (rect.width > 0) {
          trackWidthRef.current = rect.width;
          applyPillTransform(animRef.current.currentLeft, animRef.current.currentRight);
        }
      }
    };
    updateMeasure();
    window.addEventListener('resize', updateMeasure);
    return () => window.removeEventListener('resize', updateMeasure);
  }, []);

  // Follow finger smoothly when holding and sliding across buttons (pure rAF DOM transform, zero setState)
  useEffect(() => {
    if (!isHoldingPill) return;
    cancelAnimationFrame(animRef.current.rafId);

    let raf = 0;
    const step = () => {
      const state = animRef.current;
      const targetLeft = dragTabPosRef.current;
      const targetRight = targetLeft + 1;
      const nextLeft = state.currentLeft + (targetLeft - state.currentLeft) * 0.42;
      const nextRight = state.currentRight + (targetRight - state.currentRight) * 0.42;
      state.currentLeft = nextLeft;
      state.currentRight = nextRight;
      applyPillTransform(nextLeft, nextRight);
      raf = requestAnimationFrame(step);
    };
    raf = requestAnimationFrame(step);
    return () => cancelAnimationFrame(raf);
  }, [isHoldingPill]);

  // Single solid GPU-composited pill travels smoothly at 60/120fps with interruptible retargeting
  useEffect(() => {
    if (isHoldingPill) return;
    const state = animRef.current;
    if (
      state.targetLeft === targetIndex &&
      Math.abs(state.currentLeft - targetIndex) < 0.004 &&
      Math.abs(state.currentRight - (targetIndex + 1)) < 0.004
    ) {
      applyPillTransform(targetIndex, targetIndex + 1);
      return;
    }

    cancelAnimationFrame(state.rafId);
    state.startLeft = state.currentLeft;
    state.startRight = state.currentRight;
    state.targetLeft = targetIndex;
    state.targetRight = targetIndex + 1;
    state.startTime = performance.now();

    const DURATION = 260;

    const tick = (now: number) => {
      const elapsed = Math.max(0, now - state.startTime);
      const progress = Math.min(1, elapsed / DURATION);

      const movingRight = state.targetLeft >= state.startLeft;

      const leadProgress = easeSmoothOvershoot(progress, 1.45);
      const trailProgress =
        progress < 0.05
          ? 0
          : easeSmoothOvershoot((progress - 0.05) / 0.95, 1.05);

      let nextLeft = state.currentLeft;
      let nextRight = state.currentRight;

      if (movingRight) {
        nextRight = state.startRight + (state.targetRight - state.startRight) * leadProgress;
        nextLeft = state.startLeft + (state.targetLeft - state.startLeft) * trailProgress;
        if (nextRight - nextLeft > 1.24) {
          nextLeft = nextRight - 1.24;
        }
      } else {
        nextLeft = state.startLeft + (state.targetLeft - state.startLeft) * leadProgress;
        nextRight = state.startRight + (state.targetRight - state.startRight) * trailProgress;
        if (nextRight - nextLeft > 1.24) {
          nextRight = nextLeft + 1.24;
        }
      }

      if (progress >= 1) {
        nextLeft = state.targetLeft;
        nextRight = state.targetRight;
      }

      state.currentLeft = nextLeft;
      state.currentRight = nextRight;
      applyPillTransform(nextLeft, nextRight);

      if (progress < 1) {
        state.rafId = requestAnimationFrame(tick);
      }
    };

    state.rafId = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(state.rafId);
  }, [targetIndex, isHoldingPill]);

  const computeDragPosFromClientX = (clientX: number): number => {
    if (!trackRef.current) return targetIndex;
    const rect = trackRef.current.getBoundingClientRect();
    const relX = clientX - rect.left;
    const tabWidth = Math.max(1, rect.width / items.length);
    return Math.min(items.length - 1, Math.max(0, relX / tabWidth - 0.5));
  };

  const handlePointerDown = (e: React.PointerEvent<HTMLDivElement>) => {
    if (!trackRef.current || isSelectionMode) return;
    e.currentTarget.setPointerCapture(e.pointerId);
    const rect = trackRef.current.getBoundingClientRect();
    const relX = e.clientX - rect.left;
    const tabWidth = Math.max(1, rect.width / items.length);
    const tappedIndex = Math.min(
      items.length - 1,
      Math.max(0, Math.floor(relX / tabWidth))
    );

    if (tappedIndex !== targetIndex) {
      triggerWebHaptic(isHapticsEnabled);
      onChangeTab(items[tappedIndex].id);
    }

    if (gestureRef.current.holdTimer !== null) {
      window.clearTimeout(gestureRef.current.holdTimer);
    }

    const holdTimer = window.setTimeout(() => {
      if (gestureRef.current.pointerId === e.pointerId) {
        gestureRef.current.holdModeActive = true;
        setIsHoldingPill(true);
        setDragTabPos(computeDragPosFromClientX(gestureRef.current.currentX));
      }
    }, 150);

    gestureRef.current = {
      pointerId: e.pointerId,
      startX: e.clientX,
      currentX: e.clientX,
      tappedIndex,
      holdModeActive: false,
      holdTimer,
    };
  };

  const handlePointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (gestureRef.current.pointerId !== e.pointerId) return;
    gestureRef.current.currentX = e.clientX;
    const dx = Math.abs(e.clientX - gestureRef.current.startX);

    if (!gestureRef.current.holdModeActive && dx > 6) {
      if (gestureRef.current.holdTimer !== null) {
        window.clearTimeout(gestureRef.current.holdTimer);
        gestureRef.current.holdTimer = null;
      }
      gestureRef.current.holdModeActive = true;
      setIsHoldingPill(true);
    }

    if (gestureRef.current.holdModeActive) {
      setDragTabPos(computeDragPosFromClientX(e.clientX));
    }
  };

  const finishGesture = (e: React.PointerEvent<HTMLDivElement>) => {
    if (gestureRef.current.pointerId !== e.pointerId) return;
    if (gestureRef.current.holdTimer !== null) {
      window.clearTimeout(gestureRef.current.holdTimer);
      gestureRef.current.holdTimer = null;
    }

    const wasHolding = gestureRef.current.holdModeActive;
    const tappedIdx = gestureRef.current.tappedIndex;
    const finalDragPos = computeDragPosFromClientX(e.clientX);
    gestureRef.current.pointerId = null;
    gestureRef.current.holdModeActive = false;
    setIsHoldingPill(false);

    if (wasHolding) {
      const landedIndex = Math.min(
        items.length - 1,
        Math.max(0, Math.round(finalDragPos))
      );
      if (landedIndex !== tappedIdx) {
        triggerWebHaptic(isHapticsEnabled);
        onChangeTab(items[landedIndex].id);
      }
    }
  };

  const visualActiveIndex = isHoldingPill
    ? Math.min(items.length - 1, Math.max(0, Math.round(dragTabPos)))
    : targetIndex;

  const lastActiveCountRef = useRef<number>(1);
  if (selectedCount > 0) {
    lastActiveCountRef.current = selectedCount;
  }
  const effectiveSelectedCount = selectedCount > 0 ? selectedCount : lastActiveCountRef.current;
  const showEditButton = !isArchiveTab && effectiveSelectedCount === 1;

  return (
    <div
      data-selection-active={isSelectionMode ? 'true' : 'false'}
      className="fixed bottom-5 left-1/2 -translate-x-1/2 z-40 w-[92%] max-w-sm sm:max-w-md px-2 pointer-events-none"
    >
      <div className="relative h-[56px] w-full">
        {/* Original 4-Tab Navigation Bar (hides under the screen when holding/selecting a note) */}
        <nav
          aria-label="Bottom Navigation"
          aria-hidden={isSelectionMode}
          className={`absolute inset-0 transition-transform duration-300 ease-[cubic-bezier(0.22,1,0.36,1)] ${
            isSelectionMode
              ? 'translate-y-28 opacity-0 pointer-events-none'
              : 'translate-y-0 opacity-100 pointer-events-auto'
          }`}
        >
          <div className="glass-floating rounded-full p-1.5 flex items-center justify-between shadow-[0_14px_32px_-4px_rgba(89,37,220,0.26)] dark:shadow-[0_14px_32px_-4px_rgba(0,0,0,0.72)] border border-white/80 dark:border-white/15 bg-white/95 dark:bg-[#140E21]/95 backdrop-blur-xl overflow-hidden">
            <div
              ref={trackRef}
              onPointerDown={handlePointerDown}
              onPointerMove={handlePointerMove}
              onPointerUp={finishGesture}
              onPointerCancel={finishGesture}
              className="relative grid grid-cols-4 w-full rounded-full overflow-hidden touch-none select-none"
            >
              {/* GPU-Composited Active Pill (transform-only, zero SVG filter blur, zero per-frame React re-renders) */}
              <div
                ref={pillRef}
                aria-hidden="true"
                data-testid="navbar-active-pill"
                style={{
                  width: `${100 / Math.max(1, items.length)}%`,
                  transformOrigin: 'left center',
                  willChange: 'transform',
                }}
                className="pointer-events-none absolute top-0 bottom-0 left-0 z-0 rounded-full bg-gradient-to-r from-[#6929F5] via-[#7B35E8] to-[#B86BFF] border border-white/30 shadow-[0_2px_12px_rgba(105,41,245,0.45)]"
              />

              {items.map((item, idx) => {
                const isSelected = visualActiveIndex === idx;
                const IconComponent = item.icon;

                return (
                  <button
                    key={item.id}
                    type="button"
                    aria-label={item.label}
                    aria-current={isSelected ? 'page' : undefined}
                    onClick={(e) => {
                      e.preventDefault();
                      if (idx !== targetIndex) {
                        triggerWebHaptic(isHapticsEnabled);
                        onChangeTab(item.id);
                      }
                    }}
                    className={`relative py-2.5 rounded-full flex items-center justify-center gap-1 text-xs font-semibold transition-all duration-150 select-none focus:outline-none cursor-pointer w-full z-10 ${
                      isSelected
                        ? 'text-white'
                        : 'text-[#2B194D] dark:text-[#D8CFE8] hover:text-[#5925DC] dark:hover:text-[#C296FD]'
                    }`}
                  >
                    <span className="relative z-10 flex items-center justify-center gap-1">
                      <IconComponent
                        className={`w-4 h-4 transition-transform duration-150 ${
                          isSelected ? 'scale-105' : 'group-hover:scale-105'
                        }`}
                      />
                      <span className="text-[11px] font-semibold whitespace-nowrap">{item.label}</span>
                      {typeof item.badge === 'number' && item.badge > 0 && (
                        <span
                          className={`ml-0.5 px-1.5 py-0.2 rounded-full text-[9px] font-bold ${
                            isSelected
                              ? 'bg-white/30 text-white'
                              : 'bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD]'
                          }`}
                        >
                          {item.badge}
                        </span>
                      )}
                    </span>
                  </button>
                );
              })}
            </div>
          </div>
        </nav>

        {/* Selection Action Navbar (pops up from under the screen to replace original navbar when holding a note) */}
        <nav
          aria-label="Note Selection Actions"
          aria-hidden={!isSelectionMode}
          data-testid="selection-action-navbar"
          className={`absolute inset-0 flex justify-center transition-all duration-300 ease-[cubic-bezier(0.22,1,0.36,1)] ${
            isSelectionMode
              ? 'translate-y-0 opacity-100 pointer-events-auto'
              : 'translate-y-28 opacity-0 pointer-events-none'
          }`}
        >
          <div
            className={`glass-floating w-full rounded-full p-1.5 flex items-center justify-between gap-1 shadow-[0_16px_36px_-4px_rgba(89,37,220,0.32)] dark:shadow-[0_16px_36px_-4px_rgba(0,0,0,0.8)] border border-[#5925DC]/45 dark:border-[#9D5AF5]/45 bg-white/95 dark:bg-[#140E21]/95 backdrop-blur-2xl overflow-hidden transition-[max-width] duration-300 ease-[cubic-bezier(0.22,1,0.36,1)] ${
              showEditButton ? 'max-w-[396px]' : 'max-w-[316px]'
            }`}
          >
            {/* Clear / Exit Hold Selection Mode Button */}
            <button
              type="button"
              aria-label="Clear selection"
              data-testid="selection-navbar-clear-btn"
              onClick={() => {
                triggerWebHaptic(isHapticsEnabled);
                onClearSelection?.();
              }}
              className="px-2.5 py-2 rounded-full bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] text-[11px] font-bold flex items-center gap-1 shrink-0 hover:bg-[#EADBFC] dark:hover:bg-[#3A2854] active:scale-95 transition-all cursor-pointer"
            >
              <X className="w-3.5 h-3.5 stroke-[2.5]" />
              <span>{effectiveSelectedCount}</span>
            </button>

            <div className="flex items-center justify-around flex-1 min-w-0">
              {/* Share Button (when 1 note is selected) or Tag Together Button (when multiple notes are selected) */}
              {effectiveSelectedCount === 1 ? (
                <button
                  type="button"
                  aria-label="Share selected notes"
                  data-testid="selection-navbar-share-btn"
                  onClick={() => {
                    triggerWebHaptic(isHapticsEnabled);
                    onShareSelected?.();
                  }}
                  className="flex-1 py-2.5 px-1.5 rounded-full flex items-center justify-center gap-1 text-[11px] font-bold text-[#2B194D] dark:text-[#D8CFE8] hover:bg-[#F4EBFF]/70 dark:hover:bg-[#2B1E3E]/70 hover:text-[#5925DC] dark:hover:text-[#C296FD] active:scale-95 transition-all cursor-pointer"
                >
                  <Share2 className="w-3.5 h-3.5 shrink-0" />
                  <span className="truncate">Share</span>
                </button>
              ) : (
                <button
                  type="button"
                  aria-label="Tag selected notes together"
                  data-testid="selection-navbar-tag-btn"
                  onClick={() => {
                    triggerWebHaptic(isHapticsEnabled);
                    onTagSelected?.();
                  }}
                  className="flex-1 py-2.5 px-1.5 rounded-full flex items-center justify-center gap-1 text-[11px] font-bold text-[#2B194D] dark:text-[#D8CFE8] hover:bg-[#F4EBFF]/70 dark:hover:bg-[#2B1E3E]/70 hover:text-[#5925DC] dark:hover:text-[#C296FD] active:scale-95 transition-all cursor-pointer"
                >
                  <Tag className="w-3.5 h-3.5 shrink-0" />
                  <span className="truncate">Tag</span>
                </button>
              )}

              {/* Archive / Restore Button */}
              <button
                type="button"
                aria-label={isArchiveTab ? 'Restore selected notes' : 'Archive selected notes'}
                data-testid="selection-navbar-archive-btn"
                onClick={() => {
                  triggerWebHaptic(isHapticsEnabled);
                  onArchiveOrRestoreSelected?.();
                }}
                className="flex-1 py-2.5 px-1.5 rounded-full flex items-center justify-center gap-1 text-[11px] font-bold text-[#2B194D] dark:text-[#D8CFE8] hover:bg-[#F4EBFF]/70 dark:hover:bg-[#2B1E3E]/70 hover:text-[#5925DC] dark:hover:text-[#C296FD] active:scale-95 transition-all cursor-pointer"
              >
                {isArchiveTab ? (
                  <>
                    <ArchiveRestore className="w-3.5 h-3.5 shrink-0" />
                    <span className="truncate">Restore</span>
                  </>
                ) : (
                  <>
                    <Archive className="w-3.5 h-3.5 shrink-0" />
                    <span className="truncate">Archive</span>
                  </>
                )}
              </button>

              {/* Edit Button (Visible only when 1 note is selected; disappears when multiple notes are selected, and expands the bar when returning to 1 note) */}
              {showEditButton && (
                <button
                  type="button"
                  aria-label="Edit Note Details"
                  data-testid="edit-note-details-btn"
                  onClick={() => {
                    triggerWebHaptic(isHapticsEnabled);
                    onEditSelected?.();
                  }}
                  className="flex-1 py-2.5 px-1.5 rounded-full flex items-center justify-center gap-1 text-[11px] font-bold text-[#2B194D] dark:text-[#D8CFE8] hover:bg-[#F4EBFF]/70 dark:hover:bg-[#2B1E3E]/70 hover:text-[#5925DC] dark:hover:text-[#C296FD] active:scale-95 transition-all cursor-pointer animate-fade-in"
                >
                  <Edit3 className="w-3.5 h-3.5 shrink-0" />
                  <span className="truncate">Edit</span>
                </button>
              )}

              {/* Delete Button */}
              <button
                type="button"
                aria-label="Delete selected notes"
                data-testid="selection-navbar-delete-btn"
                onClick={() => {
                  triggerWebHaptic(isHapticsEnabled);
                  onDeleteSelected?.();
                }}
                className="flex-1 py-2.5 px-1.5 rounded-full flex items-center justify-center gap-1 text-[11px] font-bold text-rose-600 dark:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-950/40 active:scale-95 transition-all cursor-pointer"
              >
                <Trash2 className="w-3.5 h-3.5 shrink-0" />
                <span className="truncate">Delete</span>
              </button>
            </div>
          </div>
        </nav>
      </div>
    </div>
  );
};

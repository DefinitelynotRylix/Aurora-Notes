import React, { useEffect, useRef, useState } from 'react';
import { ActiveTab } from '../types';
import {
  FileText,
  Archive,
  ArchiveRestore,
  Trash2,
  Settings,
  Share2,
  Edit3,
  X,
} from 'lucide-react';

interface LiquidNavbarProps {
  activeTab: ActiveTab;
  onChangeTab: (tab: ActiveTab) => void;
  archivedCount: number;
  trashCount?: number;
  isHapticsEnabled?: boolean;
  selectedCount?: number;
  isArchiveTab?: boolean;
  onShareSelected?: () => void;
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
  onChangeTab,
  archivedCount,
  trashCount = 0,
  isHapticsEnabled = true,
  selectedCount = 0,
  isArchiveTab = false,
  onShareSelected,
  onArchiveOrRestoreSelected,
  onEditSelected,
  onDeleteSelected,
  onClearSelection,
}) => {
  const isSelectionMode = selectedCount > 0;

  const items: NavItem[] = [
    { id: 'notes', label: 'Notes', icon: FileText },
    { id: 'archive', label: 'Archive', icon: Archive, badge: archivedCount },
    { id: 'trash', label: 'Trash', icon: Trash2, badge: trashCount },
    { id: 'settings', label: 'Settings', icon: Settings },
  ];

  const targetIndex = Math.max(
    0,
    items.findIndex((item) => item.id === activeTab)
  );

  const trackRef = useRef<HTMLDivElement>(null);
  const [trackSize, setTrackSize] = useState({ width: 320, height: 44 });
  const [isHoldingPill, setIsHoldingPill] = useState(false);
  const [dragTabPos, setDragTabPos] = useState(targetIndex);

  const [animBounds, setAnimBounds] = useState({
    leftSlot: targetIndex,
    rightSlot: targetIndex + 1,
  });

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

  useEffect(() => {
    const updateMeasure = () => {
      if (trackRef.current) {
        const rect = trackRef.current.getBoundingClientRect();
        if (rect.width > 0 && rect.height > 0) {
          setTrackSize({ width: rect.width, height: rect.height });
        }
      }
    };
    updateMeasure();
    window.addEventListener('resize', updateMeasure);
    return () => window.removeEventListener('resize', updateMeasure);
  }, []);

  // Follow finger smoothly when holding and sliding across buttons
  useEffect(() => {
    if (!isHoldingPill) return;
    cancelAnimationFrame(animRef.current.rafId);

    let raf = 0;
    const step = () => {
      const state = animRef.current;
      const targetLeft = dragTabPos;
      const targetRight = dragTabPos + 1;
      const nextLeft = state.currentLeft + (targetLeft - state.currentLeft) * 0.4;
      const nextRight = state.currentRight + (targetRight - state.currentRight) * 0.4;
      state.currentLeft = nextLeft;
      state.currentRight = nextRight;
      setAnimBounds({
        leftSlot: nextLeft,
        rightSlot: nextRight,
      });
      if (Math.abs(targetLeft - nextLeft) > 0.002 || Math.abs(targetRight - nextRight) > 0.002) {
        raf = requestAnimationFrame(step);
      }
    };
    raf = requestAnimationFrame(step);
    return () => cancelAnimationFrame(raf);
  }, [isHoldingPill, dragTabPos]);

  // Single solid pill travels smoothly with zero cooldown and interruptible mid-flight retargeting
  useEffect(() => {
    if (isHoldingPill) return;
    const state = animRef.current;
    if (
      state.targetLeft === targetIndex &&
      Math.abs(state.currentLeft - targetIndex) < 0.005 &&
      Math.abs(state.currentRight - (targetIndex + 1)) < 0.005
    ) {
      return;
    }

    cancelAnimationFrame(state.rafId);
    state.startLeft = state.currentLeft;
    state.startRight = state.currentRight;
    state.targetLeft = targetIndex;
    state.targetRight = targetIndex + 1;
    state.startTime = performance.now();

    const DURATION = 320;

    const tick = (now: number) => {
      const elapsed = Math.max(0, now - state.startTime);
      const progress = Math.min(1, elapsed / DURATION);

      const movingRight = state.targetLeft >= state.startLeft;

      const leadProgress = easeSmoothOvershoot(progress, 1.75);
      const trailProgress = progress < 0.04
        ? 0
        : easeSmoothOvershoot((progress - 0.04) / 0.96, 1.25);

      let nextLeft = state.currentLeft;
      let nextRight = state.currentRight;

      if (movingRight) {
        nextRight = state.startRight + (state.targetRight - state.startRight) * leadProgress;
        nextLeft = state.startLeft + (state.targetLeft - state.startLeft) * trailProgress;
        if (nextRight - nextLeft > 1.3) {
          nextLeft = nextRight - 1.3;
        }
      } else {
        nextLeft = state.startLeft + (state.targetLeft - state.startLeft) * leadProgress;
        nextRight = state.startRight + (state.targetRight - state.startRight) * trailProgress;
        if (nextRight - nextLeft > 1.3) {
          nextRight = nextLeft + 1.3;
        }
      }

      if (progress >= 1) {
        nextLeft = state.targetLeft;
        nextRight = state.targetRight;
      }

      state.currentLeft = nextLeft;
      state.currentRight = nextRight;
      setAnimBounds({
        leftSlot: nextLeft,
        rightSlot: nextRight,
      });

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

    // Switch immediately on pointer down with zero wait response
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

  const slotWidth = trackSize.width / Math.max(1, items.length);
  const h = trackSize.height;
  const leftPx = Math.max(0, Math.min(trackSize.width - h, animBounds.leftSlot * slotWidth));
  const rightPx = Math.max(leftPx + h, Math.min(trackSize.width, animBounds.rightSlot * slotWidth));
  const pillWidth = Math.max(h, rightPx - leftPx);
  const pillRadius = h / 2;

  const visualActiveIndex = isHoldingPill
    ? Math.min(items.length - 1, Math.max(0, Math.round(dragTabPos)))
    : targetIndex;

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
          className={`absolute inset-0 transition-all duration-300 ease-[cubic-bezier(0.22,1,0.36,1)] ${
            isSelectionMode
              ? 'translate-y-28 opacity-0 pointer-events-none'
              : 'translate-y-0 opacity-100 pointer-events-auto'
          }`}
        >
          <div className="glass-floating rounded-full p-1.5 flex items-center justify-between shadow-[0_16px_36px_-4px_rgba(89,37,220,0.28)] dark:shadow-[0_16px_36px_-4px_rgba(0,0,0,0.75)] border border-white/80 dark:border-white/15 bg-white/95 dark:bg-[#140E21]/95 backdrop-blur-2xl overflow-hidden">
            <div
              ref={trackRef}
              onPointerDown={handlePointerDown}
              onPointerMove={handlePointerMove}
              onPointerUp={finishGesture}
              onPointerCancel={finishGesture}
              className="relative grid grid-cols-4 w-full rounded-full overflow-hidden touch-none select-none"
            >
              <svg
                width={trackSize.width}
                height={trackSize.height}
                viewBox={`0 0 ${trackSize.width} ${trackSize.height}`}
                className="absolute inset-0 pointer-events-none z-0 overflow-hidden"
                aria-hidden="true"
              >
                <defs>
                  <linearGradient id="aurora-pill-grad" x1="0%" y1="0%" x2="100%" y2="0%">
                    <stop offset="0%" stopColor="#6929F5" />
                    <stop offset="50%" stopColor="#7B35E8" />
                    <stop offset="100%" stopColor="#B86BFF" />
                  </linearGradient>
                  <filter id="aurora-pill-glow" x="-15%" y="-25%" width="130%" height="150%">
                    <feGaussianBlur stdDeviation="5" result="blur" />
                    <feMerge>
                      <feMergeNode in="blur" />
                      <feMergeNode in="SourceGraphic" />
                    </feMerge>
                  </filter>
                </defs>
                <rect
                  x={leftPx}
                  y={0}
                  width={pillWidth}
                  height={h}
                  rx={pillRadius}
                  ry={pillRadius}
                  fill="url(#aurora-pill-grad)"
                  stroke="rgba(255,255,255,0.34)"
                  strokeWidth="1"
                  filter="url(#aurora-pill-glow)"
                />
              </svg>

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
          className={`absolute inset-0 transition-all duration-300 ease-[cubic-bezier(0.22,1,0.36,1)] ${
            isSelectionMode
              ? 'translate-y-0 opacity-100 pointer-events-auto'
              : 'translate-y-28 opacity-0 pointer-events-none'
          }`}
        >
          <div className="glass-floating rounded-full p-1.5 flex items-center justify-between gap-1 shadow-[0_16px_36px_-4px_rgba(89,37,220,0.32)] dark:shadow-[0_16px_36px_-4px_rgba(0,0,0,0.8)] border border-[#5925DC]/45 dark:border-[#9D5AF5]/45 bg-white/95 dark:bg-[#140E21]/95 backdrop-blur-2xl overflow-hidden">
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
              <span>{selectedCount}</span>
            </button>

            <div className="flex items-center justify-around flex-1 min-w-0">
              {/* Share Button */}
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

              {/* Edit Button (Opens Edit Note Details) */}
              {!isArchiveTab && (
                <button
                  type="button"
                  aria-label="Edit Note Details"
                  data-testid="edit-note-details-btn"
                  onClick={() => {
                    triggerWebHaptic(isHapticsEnabled);
                    onEditSelected?.();
                  }}
                  className="flex-1 py-2.5 px-1.5 rounded-full flex items-center justify-center gap-1 text-[11px] font-bold text-[#2B194D] dark:text-[#D8CFE8] hover:bg-[#F4EBFF]/70 dark:hover:bg-[#2B1E3E]/70 hover:text-[#5925DC] dark:hover:text-[#C296FD] active:scale-95 transition-all cursor-pointer"
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

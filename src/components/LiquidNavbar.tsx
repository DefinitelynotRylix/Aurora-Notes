import React, { useEffect, useRef, useState } from 'react';
import { ActiveTab } from '../types';
import { FileText, Archive, Trash2, Settings } from 'lucide-react';

interface LiquidNavbarProps {
  activeTab: ActiveTab;
  onChangeTab: (tab: ActiveTab) => void;
  archivedCount: number;
  trashCount?: number;
  isHapticsEnabled?: boolean;
}

interface NavItem {
  id: ActiveTab;
  label: string;
  icon: React.ElementType;
  badge?: number;
}

const easeFluidOvershoot = (t: number, overshoot: number = 1.32): number => {
  const clamped = Math.min(1, Math.max(0, t));
  const inv = clamped - 1;
  return 1 + (overshoot + 1) * Math.pow(inv, 3) + overshoot * Math.pow(inv, 2);
};

function triggerWebHaptic(enabled: boolean) {
  if (
    enabled &&
    typeof navigator !== 'undefined' &&
    typeof navigator.vibrate === 'function'
  ) {
    navigator.vibrate(30);
  }
}

function buildMetaballDropPath(
  dropPos: number,
  bodyPos: number,
  tabCount: number,
  width: number,
  height: number
): string {
  const slotWidth = width / Math.max(1, tabCount);
  const h = height;
  const centerY = h / 2;
  const maxTabIndex = Math.max(0, tabCount - 1);
  const clampedDrop = Math.min(maxTabIndex, Math.max(0, dropPos));
  const clampedBody = Math.min(maxTabIndex, Math.max(0, bodyPos));
  const sep = Math.abs(clampedDrop - clampedBody);
  const dropShrink = Math.min(1, Math.max(0, sep / (sep + 0.28)));

  if (sep < 0.005) {
    const left = Math.max(0, Math.min(width - slotWidth, clampedDrop * slotWidth));
    const right = Math.max(left + h, Math.min(width, left + slotWidth));
    const r = h / 2;
    const lc = left + r;
    const rc = Math.max(lc, right - r);
    return `M ${lc} 0 L ${rc} 0 A ${r} ${r} 0 0 1 ${rc} ${h} L ${lc} ${h} A ${r} ${r} 0 0 1 ${lc} 0 Z`;
  }

  const isMovingRight = clampedDrop >= clampedBody;
  const bodyCenterX = (clampedBody + 0.5) * slotWidth;
  const bodyH = Math.max(h * 0.88, h * (1 - 0.03 * dropShrink));
  const bodyW = Math.max(bodyH, slotWidth * (1 - 0.08 * dropShrink));

  const dropCenterX = (clampedDrop + 0.5) * slotWidth;
  const dropH = Math.max(h * 0.80, h * (1 - 0.14 * dropShrink));
  const dropW = Math.max(dropH, slotWidth * (1 - 0.24 * dropShrink));

  const leftCX = isMovingRight ? bodyCenterX : dropCenterX;
  const leftW = isMovingRight ? bodyW : dropW;
  const leftH = isMovingRight ? bodyH : dropH;
  const rightCX = isMovingRight ? dropCenterX : bodyCenterX;
  const rightW = isMovingRight ? dropW : bodyW;
  const rightH = isMovingRight ? dropH : bodyH;

  // Strictly clamp inside [0, width] so the pill can never clip outside the navbar
  const leftOuter = Math.max(0, Math.min(Math.max(0, width - leftH), leftCX - leftW / 2));
  const rightOuter = Math.max(
    Math.min(width, leftOuter + rightH),
    Math.min(width, rightCX + rightW / 2)
  );

  const leftTop = centerY - leftH / 2;
  const leftBottom = centerY + leftH / 2;
  const rightTop = centerY - rightH / 2;
  const rightBottom = centerY + rightH / 2;

  const leftCapCenterX = leftOuter + leftH / 2;
  const rightCapCenterX = rightOuter - rightH / 2;

  if (rightCapCenterX <= leftCapCenterX + 2) {
    const r = h / 2;
    const lc = leftOuter + r;
    const rc = Math.max(lc, rightOuter - r);
    return `M ${lc} 0 L ${rc} 0 A ${r} ${r} 0 0 1 ${rc} ${h} L ${lc} ${h} A ${r} ${r} 0 0 1 ${lc} 0 Z`;
  }

  const neckBias = isMovingRight ? 0.56 : 0.44;
  const neckX = leftCapCenterX + (rightCapCenterX - leftCapCenterX) * neckBias;
  const minLobeHalfH = Math.min(leftH, rightH) * 0.5;
  const neckHalfH = Math.max(h * 0.34, Math.min(minLobeHalfH, minLobeHalfH * (1 - 0.26 * dropShrink)));

  const neckTopY = centerY - neckHalfH;
  const neckBottomY = centerY + neckHalfH;

  const leftSpan = Math.max(1, neckX - leftCapCenterX);
  const rightSpan = Math.max(1, rightCapCenterX - neckX);
  const lr = leftH / 2;
  const rr = rightH / 2;

  return [
    `M ${leftCapCenterX} ${leftTop}`,
    `C ${leftCapCenterX + leftSpan * 0.54} ${leftTop}, ${neckX - leftSpan * 0.36} ${neckTopY}, ${neckX} ${neckTopY}`,
    `C ${neckX + rightSpan * 0.36} ${neckTopY}, ${rightCapCenterX - rightSpan * 0.54} ${rightTop}, ${rightCapCenterX} ${rightTop}`,
    `A ${rr} ${rr} 0 0 1 ${rightCapCenterX} ${rightBottom}`,
    `C ${rightCapCenterX - rightSpan * 0.54} ${rightBottom}, ${neckX + rightSpan * 0.36} ${neckBottomY}, ${neckX} ${neckBottomY}`,
    `C ${neckX - leftSpan * 0.36} ${neckBottomY}, ${leftCapCenterX + leftSpan * 0.54} ${leftBottom}, ${leftCapCenterX} ${leftBottom}`,
    `A ${lr} ${lr} 0 0 1 ${leftCapCenterX} ${leftTop}`,
    'Z',
  ].join(' ');
}

export const LiquidNavbar: React.FC<LiquidNavbarProps> = ({
  activeTab,
  onChangeTab,
  archivedCount,
  trashCount = 0,
  isHapticsEnabled = true,
}) => {
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

  const [animState, setAnimState] = useState({
    dropPos: targetIndex,
    bodyPos: targetIndex,
  });

  const animRef = useRef({
    startDrop: targetIndex,
    startBody: targetIndex,
    currentDrop: targetIndex,
    currentBody: targetIndex,
    target: targetIndex,
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

  // Sync while holding & sliding the purple pill
  useEffect(() => {
    if (!isHoldingPill) return;
    cancelAnimationFrame(animRef.current.rafId);

    let raf = 0;
    const step = () => {
      const state = animRef.current;
      const nextDrop = state.currentDrop + (dragTabPos - state.currentDrop) * 0.36;
      const nextBody = state.currentBody + (dragTabPos - state.currentBody) * 0.18;
      state.currentDrop = nextDrop;
      state.currentBody = nextBody;
      setAnimState({
        dropPos: nextDrop,
        bodyPos: nextBody,
      });
      if (
        Math.abs(dragTabPos - nextDrop) > 0.002 ||
        Math.abs(dragTabPos - nextBody) > 0.002
      ) {
        raf = requestAnimationFrame(step);
      }
    };
    raf = requestAnimationFrame(step);
    return () => cancelAnimationFrame(raf);
  }, [isHoldingPill, dragTabPos]);

  // Animate fluid drop going slightly past the target button and settling back into place smoothly
  useEffect(() => {
    if (isHoldingPill) return;
    const state = animRef.current;
    if (
      state.target === targetIndex &&
      Math.abs(state.currentDrop - targetIndex) < 0.005 &&
      Math.abs(state.currentBody - targetIndex) < 0.005
    ) {
      return;
    }

    cancelAnimationFrame(state.rafId);
    state.startDrop = state.currentDrop;
    state.startBody = state.currentBody;
    state.target = targetIndex;
    state.startTime = performance.now();

    const DROP_DURATION = 420;
    const BODY_DURATION = 660;

    const tick = (now: number) => {
      const elapsed = Math.max(0, now - state.startTime);
      const dropProgress = easeFluidOvershoot(elapsed / DROP_DURATION, 1.32);
      const bodyProgress = easeFluidOvershoot(elapsed / BODY_DURATION, 1.12);

      const nextDrop = Math.min(
        items.length - 1,
        Math.max(0, state.startDrop + (state.target - state.startDrop) * dropProgress)
      );
      const nextBody = Math.min(
        items.length - 1,
        Math.max(0, state.startBody + (state.target - state.startBody) * bodyProgress)
      );

      state.currentDrop = nextDrop;
      state.currentBody = nextBody;
      setAnimState({
        dropPos: nextDrop,
        bodyPos: nextBody,
      });

      if (elapsed < BODY_DURATION) {
        state.rafId = requestAnimationFrame(tick);
      } else {
        state.currentDrop = state.target;
        state.currentBody = state.target;
        setAnimState({
          dropPos: state.target,
          bodyPos: state.target,
        });
      }
    };

    state.rafId = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(state.rafId);
  }, [targetIndex, isHoldingPill, items.length]);

  const computeDragPosFromClientX = (clientX: number): number => {
    if (!trackRef.current) return targetIndex;
    const rect = trackRef.current.getBoundingClientRect();
    const relX = clientX - rect.left;
    const tabWidth = Math.max(1, rect.width / items.length);
    return Math.min(items.length - 1, Math.max(0, relX / tabWidth - 0.5));
  };

  const handlePointerDown = (e: React.PointerEvent<HTMLDivElement>) => {
    if (!trackRef.current) return;
    e.currentTarget.setPointerCapture(e.pointerId);
    const rect = trackRef.current.getBoundingClientRect();
    const relX = e.clientX - rect.left;
    const tabWidth = Math.max(1, rect.width / items.length);
    const tappedIndex = Math.min(
      items.length - 1,
      Math.max(0, Math.floor(relX / tabWidth))
    );

    // Haptic feedback at push
    triggerWebHaptic(isHapticsEnabled);

    if (gestureRef.current.holdTimer !== null) {
      window.clearTimeout(gestureRef.current.holdTimer);
    }

    const holdTimer = window.setTimeout(() => {
      if (gestureRef.current.pointerId === e.pointerId) {
        gestureRef.current.holdModeActive = true;
        setIsHoldingPill(true);
        setDragTabPos(computeDragPosFromClientX(gestureRef.current.currentX));
      }
    }, 165);

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
      // Slide the purple pill through the menus with NO haptic feedback
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

    if (wasHolding) {
      const landedIndex = Math.min(
        items.length - 1,
        Math.max(0, Math.round(finalDragPos))
      );
      setIsHoldingPill(false);
      // Haptic feedback upon letting go of the hold phase
      triggerWebHaptic(isHapticsEnabled);
      onChangeTab(items[landedIndex].id);
    } else {
      setIsHoldingPill(false);
      onChangeTab(items[tappedIdx].id);
    }
  };

  const pillPath = buildMetaballDropPath(
    animState.dropPos,
    animState.bodyPos,
    items.length,
    trackSize.width,
    trackSize.height
  );

  const visualActiveIndex = isHoldingPill
    ? Math.min(items.length - 1, Math.max(0, Math.round(dragTabPos)))
    : targetIndex;

  return (
    <nav
      aria-label="Bottom Navigation"
      className="fixed bottom-5 left-1/2 -translate-x-1/2 z-40 w-[92%] max-w-sm sm:max-w-md px-2 pointer-events-auto"
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
            <path
              d={pillPath}
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
                className={`relative py-2.5 rounded-full flex items-center justify-center gap-1.5 text-xs font-semibold transition-all duration-200 select-none focus:outline-none cursor-pointer w-full z-10 ${
                  isSelected
                    ? 'text-white'
                    : 'text-[#484455] dark:text-[#A39BAC] hover:text-[#5925DC] dark:hover:text-[#C296FD]'
                }`}
              >
                <span className="relative z-10 flex items-center justify-center gap-1">
                  <IconComponent
                    className={`w-4 h-4 transition-transform duration-200 ${
                      isSelected ? 'scale-105' : 'group-hover:scale-105'
                    }`}
                  />
                  <span className="hidden sm:inline text-[11px] font-medium">{item.label}</span>
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
  );
};

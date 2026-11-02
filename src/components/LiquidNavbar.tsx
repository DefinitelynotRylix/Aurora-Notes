import React, { useEffect, useRef, useState } from 'react';
import { ActiveTab } from '../types';
import { FileText, Pin, Trash2, Settings } from 'lucide-react';

interface LiquidNavbarProps {
  activeTab: ActiveTab;
  onChangeTab: (tab: ActiveTab) => void;
  pinnedCount: number;
  trashCount?: number;
}

interface NavItem {
  id: ActiveTab;
  label: string;
  icon: React.ElementType;
  badge?: number;
}

// Smooth fluid cubic-out easing
const easeFluidGlide = (t: number): number => {
  const clamped = Math.min(1, Math.max(0, t));
  return 1 - Math.pow(1 - clamped, 3.2);
};

export const LiquidNavbar: React.FC<LiquidNavbarProps> = ({
  activeTab,
  onChangeTab,
  pinnedCount,
  trashCount = 0,
}) => {
  const items: NavItem[] = [
    { id: 'notes', label: 'Notes', icon: FileText },
    { id: 'pinned', label: 'Pinned', icon: Pin, badge: pinnedCount },
    { id: 'trash', label: 'Trash', icon: Trash2, badge: trashCount },
    { id: 'settings', label: 'Settings', icon: Settings },
  ];

  const targetIndex = Math.max(
    0,
    items.findIndex((item) => item.id === activeTab)
  );

  const trackRef = useRef<HTMLDivElement>(null);
  const [trackSize, setTrackSize] = useState({ width: 320, height: 44 });
  const [animState, setAnimState] = useState({
    leadPos: targetIndex,
    trailPos: targetIndex,
  });

  const animRef = useRef({
    startLead: targetIndex,
    startTrail: targetIndex,
    currentLead: targetIndex,
    currentTrail: targetIndex,
    target: targetIndex,
    startTime: 0,
    rafId: 0,
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

  useEffect(() => {
    const state = animRef.current;
    if (state.target === targetIndex) return;

    cancelAnimationFrame(state.rafId);
    state.startLead = state.currentLead;
    state.startTrail = state.currentTrail;
    state.target = targetIndex;
    state.startTime = performance.now();

    // Fluid durations so the leading edge glides out first (stretching the pill)
    // and the trailing edge smoothly catches up to restore the original pill size
    const LEAD_DURATION = 420;
    const TRAIL_DURATION = 720;

    const tick = (now: number) => {
      const elapsed = Math.max(0, now - state.startTime);
      const leadProgress = easeFluidGlide(elapsed / LEAD_DURATION);
      const trailProgress = easeFluidGlide(elapsed / TRAIL_DURATION);

      const nextLead = Math.min(
        items.length - 1,
        Math.max(0, state.startLead + (state.target - state.startLead) * leadProgress)
      );
      const nextTrail = Math.min(
        items.length - 1,
        Math.max(0, state.startTrail + (state.target - state.startTrail) * trailProgress)
      );

      state.currentLead = nextLead;
      state.currentTrail = nextTrail;
      setAnimState({ leadPos: nextLead, trailPos: nextTrail });

      if (elapsed < TRAIL_DURATION) {
        state.rafId = requestAnimationFrame(tick);
      } else {
        state.currentLead = state.target;
        state.currentTrail = state.target;
        setAnimState({ leadPos: state.target, trailPos: state.target });
      }
    };

    state.rafId = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(state.rafId);
  }, [targetIndex, items.length]);

  const tabCount = items.length;
  const slotWidth = trackSize.width / tabCount;
  const pillHeight = trackSize.height;
  const capRadius = pillHeight / 2;

  const leftEdgeRaw = Math.min(animState.leadPos, animState.trailPos) * slotWidth;
  const rightEdgeRaw = (Math.max(animState.leadPos, animState.trailPos) + 1) * slotWidth;

  const leftX = Math.max(0, Math.min(trackSize.width - pillHeight, leftEdgeRaw));
  const rightX = Math.max(leftX + pillHeight, Math.min(trackSize.width, rightEdgeRaw));

  const stretchDelta = Math.abs(animState.leadPos - animState.trailPos);
  const stretchFactor = stretchDelta / (stretchDelta + 0.55);
  const waistPinch = pillHeight * 0.28 * stretchFactor;

  const leftCapCenterX = leftX + capRadius;
  const rightCapCenterX = Math.max(leftCapCenterX, rightX - capRadius);
  const midX = (leftCapCenterX + rightCapCenterX) / 2;

  const pillPath = [
    `M ${leftCapCenterX} 0`,
    `Q ${midX} ${waistPinch} ${rightCapCenterX} 0`,
    `A ${capRadius} ${capRadius} 0 0 1 ${rightCapCenterX} ${pillHeight}`,
    `Q ${midX} ${pillHeight - waistPinch} ${leftCapCenterX} ${pillHeight}`,
    `A ${capRadius} ${capRadius} 0 0 1 ${leftCapCenterX} 0`,
    'Z',
  ].join(' ');

  return (
    <nav
      aria-label="Bottom Navigation"
      className="fixed bottom-5 left-1/2 -translate-x-1/2 z-40 w-[92%] max-w-sm sm:max-w-md px-2 pointer-events-auto"
    >
      <div className="glass-floating rounded-full p-1.5 flex items-center justify-between shadow-[0_16px_36px_-4px_rgba(89,37,220,0.28)] dark:shadow-[0_16px_36px_-4px_rgba(0,0,0,0.75)] border border-white/80 dark:border-white/15 bg-white/95 dark:bg-[#140E21]/95 backdrop-blur-2xl overflow-hidden">
        <div
          ref={trackRef}
          className="relative grid grid-cols-4 w-full rounded-full overflow-hidden"
        >
          {/* Fluid Stretching & Thin-Waist SVG Pill Indicator */}
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
                <stop offset="50%" stopColor="#8E42FF" />
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

          {items.map((item) => {
            const isSelected = activeTab === item.id;
            const IconComponent = item.icon;

            return (
              <button
                key={item.id}
                onClick={() => {
                  if (typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function') {
                    navigator.vibrate(12);
                  }
                  onChangeTab(item.id);
                }}
                aria-label={item.label}
                aria-current={isSelected ? 'page' : undefined}
                className={`relative py-2.5 rounded-full flex items-center justify-center gap-1.5 text-xs font-semibold transition-colors duration-300 select-none focus:outline-none cursor-pointer w-full z-10 ${
                  isSelected
                    ? 'text-white'
                    : 'text-[#484455] dark:text-[#A39BAC] hover:text-[#5925DC] dark:hover:text-[#C296FD]'
                }`}
              >
                <span className="relative z-10 flex items-center justify-center gap-1">
                  <IconComponent
                    className={`w-4 h-4 transition-transform duration-300 ${
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

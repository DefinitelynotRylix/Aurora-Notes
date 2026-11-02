import React from 'react';
import { motion } from 'motion/react';
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

  return (
    <nav
      aria-label="Bottom Navigation"
      className="fixed bottom-5 left-1/2 -translate-x-1/2 z-40 max-w-md w-auto px-2 pointer-events-auto"
    >
      <div className="glass-floating rounded-full p-1.5 sm:p-2 flex items-center gap-1 shadow-[0_16px_36px_-4px_rgba(89,37,220,0.2)] dark:shadow-[0_16px_36px_-4px_rgba(0,0,0,0.6)] border border-white/80 dark:border-white/10 bg-white/90 dark:bg-[#1B1524]/90 backdrop-blur-2xl">
        {items.map((item) => {
          const isSelected = activeTab === item.id;
          const IconComponent = item.icon;

          return (
            <button
              key={item.id}
              onClick={() => onChangeTab(item.id)}
              aria-label={item.label}
              aria-current={isSelected ? 'page' : undefined}
              className={`relative px-3 sm:px-4 py-2 sm:py-2.5 rounded-full flex items-center gap-1.5 text-xs font-semibold transition-colors duration-200 select-none focus:outline-none cursor-pointer ${
                isSelected
                  ? 'text-white'
                  : 'text-[#484455] dark:text-[#A39BAC] hover:text-[#5925DC] dark:hover:text-[#C296FD]'
              }`}
            >
              {/* Liquid Sliding Purple Gradient Circle Indicator */}
              {isSelected && (
                <motion.div
                  layoutId="nav-liquid-circle"
                  className="absolute inset-0 rounded-full bg-gradient-to-r from-[#5925DC] to-[#9B51E0] dark:from-[#9D5AF5] dark:to-[#C296FD] shadow-[0_4px_16px_rgba(89,37,220,0.35)] dark:shadow-[0_4px_16px_rgba(157,90,245,0.4)] z-0"
                  transition={{
                    layout: {
                      type: 'spring',
                      stiffness: 400,
                      damping: 28,
                    },
                    scaleX: {
                      type: 'tween',
                      duration: 0.32,
                      ease: 'easeInOut',
                    },
                    scaleY: {
                      type: 'tween',
                      duration: 0.32,
                      ease: 'easeInOut',
                    },
                  }}
                  animate={{
                    scaleX: [1, 1.25, 0.95, 1],
                    scaleY: [1, 0.7, 1.05, 1],
                  }}
                />
              )}

              {/* Icon & Label */}
              <span className="relative z-10 flex items-center gap-1.5">
                <IconComponent
                  className={`w-4 h-4 transition-transform ${
                    isSelected ? 'scale-105' : 'group-hover:scale-105'
                  }`}
                />
                <span className="hidden sm:inline">{item.label}</span>
                {typeof item.badge === 'number' && item.badge > 0 && (
                  <span
                    className={`ml-0.5 px-1.5 py-0.2 rounded-full text-[10px] font-bold ${
                      isSelected
                        ? 'bg-white/25 text-white'
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
    </nav>
  );
};

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
      className="fixed bottom-5 left-1/2 -translate-x-1/2 z-40 w-[92%] max-w-sm sm:max-w-md px-2 pointer-events-auto"
    >
      <div className="glass-floating rounded-full p-1.5 flex items-center justify-between shadow-[0_16px_36px_-4px_rgba(89,37,220,0.25)] dark:shadow-[0_16px_36px_-4px_rgba(0,0,0,0.7)] border border-white/80 dark:border-white/10 bg-white/95 dark:bg-[#1B1524]/95 backdrop-blur-2xl">
        <div className="grid grid-cols-4 w-full gap-1">
          {items.map((item) => {
            const isSelected = activeTab === item.id;
            const IconComponent = item.icon;

            return (
              <button
                key={item.id}
                onClick={() => onChangeTab(item.id)}
                aria-label={item.label}
                aria-current={isSelected ? 'page' : undefined}
                className={`relative py-2.5 rounded-full flex items-center justify-center gap-1.5 text-xs font-semibold transition-colors duration-200 select-none focus:outline-none cursor-pointer w-full ${
                  isSelected
                    ? 'text-white'
                    : 'text-[#484455] dark:text-[#A39BAC] hover:text-[#5925DC] dark:hover:text-[#C296FD]'
                }`}
              >
                {/* Glowing Purple Active Pill Indicator */}
                {isSelected && (
                  <motion.div
                    layoutId="nav-liquid-circle"
                    className="absolute inset-0 rounded-full bg-gradient-to-r from-[#5925DC] via-[#7B35E8] to-[#9B51E0] dark:from-[#7839EE] dark:via-[#9D5AF5] dark:to-[#C296FD] shadow-[0_0_18px_rgba(157,90,245,0.65)] border border-white/30 z-0"
                    transition={{
                      type: 'spring',
                      stiffness: 450,
                      damping: 30,
                    }}
                  />
                )}

                {/* Icon & Label */}
                <span className="relative z-10 flex items-center justify-center gap-1">
                  <IconComponent
                    className={`w-4 h-4 transition-transform ${
                      isSelected ? 'scale-110' : 'group-hover:scale-105'
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

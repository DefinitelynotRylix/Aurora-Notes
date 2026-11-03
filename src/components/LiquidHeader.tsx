import React from 'react';
import { Search, LayoutGrid, List } from 'lucide-react';

interface LiquidHeaderProps {
  searchQuery: string;
  onSearchChange: (query: string) => void;
  isGridView: boolean;
  onToggleGridView: () => void;
  noteCount: number;
  onCreateNote?: () => void;
}

export const LiquidHeader: React.FC<LiquidHeaderProps> = ({
  searchQuery,
  onSearchChange,
  isGridView,
  onToggleGridView,
  noteCount,
}) => {
  return (
    <header className="sticky top-0 z-30 w-full glass-panel border-b border-[#EADBFC]/70 dark:border-[#322742]/70 px-4 sm:px-6 py-3 transition-all">
      <div className="max-w-6xl mx-auto flex flex-col md:flex-row md:items-center justify-between gap-3">
        {/* Brand & Requested Text */}
        <div className="flex items-center justify-between">
          <div>
            <div className="flex items-center gap-2">
              <h1 className="font-bold text-lg sm:text-xl tracking-tight text-[#1E1A24] dark:text-[#F7EEFC]">
                Aurora Notes
              </h1>
            </div>
            <p className="text-[11px] sm:text-xs text-[#797487] dark:text-[#A39BAC] font-medium tracking-tight">
              Local Only Privacy Focused Note Taking App.
            </p>
          </div>

          {/* Mobile Actions: View Toggle */}
          <div className="flex items-center gap-1.5 md:hidden">
            <button
              onClick={onToggleGridView}
              aria-label={isGridView ? 'Switch to single column view' : 'Switch to grid view'}
              className="p-2 rounded-xl bg-white/80 dark:bg-[#1B1524] border border-[#EADBFC] dark:border-[#322742] text-[#484455] dark:text-[#A39BAC] active:scale-95 transition-all"
            >
              {isGridView ? <List className="w-4 h-4" /> : <LayoutGrid className="w-4 h-4" />}
            </button>
          </div>
        </div>

        {/* Search Bar & Desktop View Toggle */}
        <div className="flex items-center gap-2 flex-1 md:max-w-md md:justify-end">
          <div className="relative flex-1">
            <Search className="w-4 h-4 text-[#797487] dark:text-[#A39BAC] absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" />
            <input
              type="text"
              aria-label="Search notes"
              placeholder={`Search ${noteCount} notes...`}
              value={searchQuery}
              onChange={(e) => onSearchChange(e.target.value)}
              className="w-full pl-9 pr-8 py-1.5 sm:py-2 text-xs sm:text-sm bg-white/90 dark:bg-[#1B1524] border border-[#EADBFC] dark:border-[#322742] rounded-xl text-[#1E1A24] dark:text-[#F7EEFC] placeholder-[#797487] dark:placeholder-[#A39BAC] focus:outline-none focus:ring-2 focus:ring-[#5925DC]/20 dark:focus:ring-[#9D5AF5]/30 focus:border-[#5925DC] dark:focus:border-[#9D5AF5] transition-all shadow-xs"
            />
            {searchQuery && (
              <button
                onClick={() => onSearchChange('')}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-xs text-[#797487] hover:text-[#1E1A24] dark:hover:text-[#F7EEFC] px-1"
                aria-label="Clear search query"
              >
                ✕
              </button>
            )}
          </div>

          {/* Desktop View Switcher */}
          <div className="hidden md:flex items-center gap-1.5">
            <button
              onClick={onToggleGridView}
              aria-label={isGridView ? 'Switch to single column view' : 'Switch to grid view'}
              className="p-2 rounded-xl bg-white dark:bg-[#1B1524] border border-[#EADBFC] dark:border-[#322742] text-[#484455] dark:text-[#A39BAC] hover:text-[#5925DC] dark:hover:text-[#C296FD] transition-all shadow-xs"
              title={isGridView ? 'Single Column View' : 'Masonry Grid View'}
            >
              {isGridView ? <List className="w-4 h-4" /> : <LayoutGrid className="w-4 h-4" />}
            </button>
          </div>
        </div>
      </div>
    </header>
  );
};

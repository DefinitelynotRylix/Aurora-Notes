import React, { useState, useRef } from 'react';
import { Plus, X } from 'lucide-react';

interface CategoryFilterBarProps {
  currentCategory: string;
  onSelectCategory: (category: string) => void;
  customCategories: string[];
  onAddCategory: (category: string) => void;
  onRemoveCategory: (category: string) => void;
  counts: Record<string, number>;
}

export const CategoryFilterBar: React.FC<CategoryFilterBarProps> = ({
  currentCategory,
  onSelectCategory,
  customCategories,
  onAddCategory,
  onRemoveCategory,
  counts,
}) => {
  const [isAdding, setIsAdding] = useState(false);
  const [newTagName, setNewTagName] = useState('');
  const [pendingDeleteTag, setPendingDeleteTag] = useState<string | null>(null);
  const longPressTimerRef = useRef<number | null>(null);

  const handleAddSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const clean = newTagName.trim().replace(/^#/, '').trim();
    if (clean && clean.toLowerCase() !== 'all' && !customCategories.includes(clean)) {
      onAddCategory(clean);
      onSelectCategory(clean);
      setNewTagName('');
      setIsAdding(false);
    }
  };

  const startLongPress = (tag: string) => {
    if (longPressTimerRef.current) {
      window.clearTimeout(longPressTimerRef.current);
    }
    longPressTimerRef.current = window.setTimeout(() => {
      setPendingDeleteTag(tag);
    }, 450);
  };

  const clearLongPress = () => {
    if (longPressTimerRef.current) {
      window.clearTimeout(longPressTimerRef.current);
      longPressTimerRef.current = null;
    }
  };

  const isAllActive = currentCategory === 'All';

  return (
    <div className="w-full overflow-x-auto no-scrollbar pt-1 pb-1 px-1">
      {pendingDeleteTag && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-fadeIn"
          onClick={() => setPendingDeleteTag(null)}
        >
          <div
            className="w-full max-w-xs rounded-2xl bg-white dark:bg-[#1B1524] border border-[#EADBFC] dark:border-[#322742] p-5 shadow-2xl space-y-3"
            onClick={(e) => e.stopPropagation()}
          >
            <h4 className="font-bold text-sm text-[#1E1A24] dark:text-[#F7EEFC]">
              Delete Tag #{pendingDeleteTag}?
            </h4>
            <p className="text-xs text-[#797487] dark:text-[#A39BAC] leading-relaxed">
              Deleting this tag will remove the tag and return any notes under #{pendingDeleteTag} back into All without deleting the notes.
            </p>
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setPendingDeleteTag(null)}
                className="px-3 py-1.5 rounded-xl text-xs font-semibold text-[#797487] hover:bg-[#FAF8FB] dark:hover:bg-[#120E17] cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => {
                  const tag = pendingDeleteTag;
                  setPendingDeleteTag(null);
                  if (tag) {
                    onRemoveCategory(tag);
                    if (currentCategory === tag) {
                      onSelectCategory('All');
                    }
                  }
                }}
                className="px-3.5 py-1.5 rounded-xl text-xs font-semibold bg-rose-600 text-white hover:bg-rose-700 cursor-pointer"
              >
                Delete Tag
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="flex items-center gap-2 min-w-max">
        {/* 1. "All" Button anchored on the left */}
        <button
          type="button"
          onClick={() => onSelectCategory('All')}
          aria-pressed={isAllActive}
          className={`px-3.5 py-1.5 rounded-full text-xs font-semibold tracking-wide transition-all flex items-center gap-1.5 cursor-pointer ${
            isAllActive
              ? 'bg-[#5925DC] dark:bg-[#9D5AF5] text-white shadow-[0_4px_14px_rgba(89,37,220,0.28)]'
              : 'bg-white/95 dark:bg-[#1B1524] text-[#484455] dark:text-[#A39BAC] border border-[#EADBFC] dark:border-[#322742] hover:border-[#D6BBFB] hover:text-[#5925DC] dark:hover:text-[#C296FD]'
          }`}
        >
          <span>All</span>
          <span
            className={`px-1.5 py-0.2 rounded-full text-[10px] font-bold ${
              isAllActive
                ? 'bg-white/25 text-white'
                : 'bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD]'
            }`}
          >
            {counts.All ?? 0}
          </span>
        </button>

        {/* 2. "+ Create Tag" Button anchored right next to "All" */}
        {isAdding ? (
          <form onSubmit={handleAddSubmit} className="flex items-center gap-1">
            <input
              type="text"
              autoFocus
              placeholder="Tag name..."
              value={newTagName}
              onChange={(e) => setNewTagName(e.target.value)}
              className="px-2.5 py-1 text-xs rounded-full bg-white dark:bg-[#1B1524] border border-[#5925DC] dark:border-[#9D5AF5] text-[#1E1A24] dark:text-[#F7EEFC] focus:outline-none w-28"
            />
            <button
              type="submit"
              className="px-2.5 py-1 rounded-full bg-[#5925DC] dark:bg-[#9D5AF5] text-white text-[10px] font-semibold cursor-pointer"
            >
              Create
            </button>
            <button
              type="button"
              onClick={() => setIsAdding(false)}
              className="p-1 text-[#797487] hover:text-[#1E1A24] dark:hover:text-[#F7EEFC] cursor-pointer"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          </form>
        ) : (
          <button
            type="button"
            onClick={() => setIsAdding(true)}
            className="px-3 py-1.5 rounded-full text-xs font-semibold text-[#5925DC] dark:text-[#C296FD] bg-[#F4EBFF] dark:bg-[#2B1E3E] border border-[#D6BBFB] dark:border-[#7D49D4] hover:bg-[#ebd8ff] transition-all flex items-center gap-1 cursor-pointer"
            title="Create a new tag"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Create Tag</span>
          </button>
        )}

        {/* 3. User-Created Tags appear to the right of All and Create Tag */}
        {customCategories.map((tag) => {
          const isActive = currentCategory === tag;
          const count = counts[tag] ?? 0;

          return (
            <div key={tag} className="relative flex items-center select-none">
              <button
                type="button"
                onClick={() => onSelectCategory(isActive ? 'All' : tag)}
                onContextMenu={(e) => {
                  e.preventDefault();
                  setPendingDeleteTag(tag);
                }}
                onTouchStart={() => startLongPress(tag)}
                onTouchEnd={clearLongPress}
                onTouchMove={clearLongPress}
                onMouseDown={() => startLongPress(tag)}
                onMouseUp={clearLongPress}
                onMouseLeave={clearLongPress}
                aria-pressed={isActive}
                title="Tap to filter, long-press to delete tag"
                className={`px-3.5 py-1.5 rounded-full text-xs font-semibold tracking-wide transition-all flex items-center gap-1.5 cursor-pointer ${
                  isActive
                    ? 'bg-[#5925DC] dark:bg-[#9D5AF5] text-white shadow-[0_4px_14px_rgba(89,37,220,0.28)]'
                    : 'bg-white/95 dark:bg-[#1B1524] text-[#484455] dark:text-[#A39BAC] border border-[#EADBFC] dark:border-[#322742] hover:border-[#D6BBFB] hover:text-[#5925DC] dark:hover:text-[#C296FD]'
                }`}
              >
                <span>#{tag}</span>
                <span
                  className={`px-1.5 py-0.2 rounded-full text-[10px] font-bold ${
                    isActive
                      ? 'bg-white/25 text-white'
                      : 'bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD]'
                  }`}
                >
                  {count}
                </span>
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
};

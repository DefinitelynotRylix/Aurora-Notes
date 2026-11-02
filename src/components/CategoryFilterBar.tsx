import React, { useState } from 'react';
import { Plus, X, Pin } from 'lucide-react';

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
  const [newPinName, setNewPinName] = useState('');

  // Fixed core pins as requested: ONLY "All" and "Pinned"
  const defaultPins = ['All', 'Pinned'];

  const allPins = [...defaultPins, ...customCategories];

  const handleAddSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const clean = newPinName.trim();
    if (clean && !allPins.includes(clean)) {
      onAddCategory(clean);
      onSelectCategory(clean);
      setNewPinName('');
      setIsAdding(false);
    }
  };

  return (
    <div className="w-full overflow-x-auto no-scrollbar py-2 px-1">
      <div className="flex items-center gap-1.5 sm:gap-2 min-w-max">
        {allPins.map((category) => {
          const isActive = currentCategory === category;
          const count = counts[category] ?? 0;
          const isCustom = !defaultPins.includes(category);

          return (
            <div key={category} className="relative group/chip flex items-center">
              <button
                onClick={() => onSelectCategory(category)}
                aria-pressed={isActive}
                className={`px-3 sm:px-3.5 py-1 sm:py-1.5 rounded-full text-xs font-semibold tracking-wide transition-all flex items-center gap-1.5 ${
                  isActive
                    ? 'bg-[#5925DC] dark:bg-[#9D5AF5] text-white shadow-[0_4px_14px_rgba(89,37,220,0.28)] scale-[1.02]'
                    : 'bg-white/95 dark:bg-[#1B1524] text-[#484455] dark:text-[#A39BAC] border border-[#EADBFC] dark:border-[#322742] hover:border-[#D6BBFB] hover:text-[#5925DC] dark:hover:text-[#C296FD]'
                }`}
              >
                {category === 'Pinned' && <Pin className="w-3 h-3 fill-current opacity-80" />}
                <span>{category}</span>
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

              {/* Delete button on custom pins */}
              {isCustom && (
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    onRemoveCategory(category);
                    if (currentCategory === category) {
                      onSelectCategory('All');
                    }
                  }}
                  aria-label={`Remove custom category pin ${category}`}
                  className="ml-0.5 p-0.5 text-[#797487] hover:text-rose-600 dark:hover:text-rose-400 opacity-60 hover:opacity-100 transition-opacity"
                  title="Remove pin"
                >
                  <X className="w-3 h-3" />
                </button>
              )}
            </div>
          );
        })}

        {/* Add Custom Pin Button or Inline Input */}
        {isAdding ? (
          <form onSubmit={handleAddSubmit} className="flex items-center gap-1">
            <input
              type="text"
              autoFocus
              placeholder="Pin name..."
              value={newPinName}
              onChange={(e) => setNewPinName(e.target.value)}
              className="px-2.5 py-1 text-xs rounded-full bg-white dark:bg-[#1B1524] border border-[#5925DC] dark:border-[#9D5AF5] text-[#1E1A24] dark:text-[#F7EEFC] focus:outline-none w-28"
            />
            <button
              type="submit"
              className="px-2 py-1 rounded-full bg-[#5925DC] dark:bg-[#9D5AF5] text-white text-[10px] font-semibold"
            >
              Add
            </button>
            <button
              type="button"
              onClick={() => setIsAdding(false)}
              className="p-1 text-[#797487] hover:text-[#1E1A24] dark:hover:text-[#F7EEFC]"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          </form>
        ) : (
          <button
            type="button"
            onClick={() => setIsAdding(true)}
            className="px-2.5 py-1 rounded-full text-xs font-semibold text-[#5925DC] dark:text-[#C296FD] bg-[#F4EBFF] dark:bg-[#2B1E3E] border border-dashed border-[#D6BBFB] dark:border-[#7D49D4] hover:bg-[#ebd8ff] transition-all flex items-center gap-1"
            title="Create your own category pin"
          >
            <Plus className="w-3 h-3" />
            <span>New Pin</span>
          </button>
        )}
      </div>
    </div>
  );
};

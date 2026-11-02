import React, { useState } from 'react';
import { Note } from '../types';
import {
  Pin,
  Lock,
  Check,
  MoreVertical,
  Trash2,
  Binary
} from 'lucide-react';

interface NoteCardProps {
  note: Note;
  onSelect: (note: Note) => void;
  onTogglePin: (id: string, e: React.MouseEvent) => void;
  onDelete: (id: string, e: React.MouseEvent) => void;
  onToggleCheckItem: (noteId: string, itemId: string, e: React.MouseEvent) => void;
  onInspectCipher: (note: Note, e: React.MouseEvent) => void;
  isGridView: boolean;
  animateMovement: boolean;
}

export const NoteCard: React.FC<NoteCardProps> = ({
  note,
  onSelect,
  onTogglePin,
  onDelete,
  onToggleCheckItem,
  onInspectCipher,
  animateMovement,
}) => {
  const [showMenu, setShowMenu] = useState(false);

  const formattedDate = new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
  }).format(note.updatedAt);

  const plainTextPreview = React.useMemo(() => {
    if (!note.content) return '';
    try {
      const doc = new DOMParser().parseFromString(note.content, 'text/html');
      return doc.body.textContent || '';
    } catch {
      return note.content.replace(/<[^>]*>/g, '');
    }
  }, [note.content]);
  return (
    <div
      onClick={() => onSelect(note)}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          onSelect(note);
        }
      }}
      className={`glass-card rounded-xl sm:rounded-2xl p-3 sm:p-3.5 cursor-pointer relative group flex flex-col justify-between select-none focus:outline-none focus:ring-2 focus:ring-[#5925DC]/30 active:scale-[0.98] ${
        animateMovement ? 'transition-all duration-300 ease-out' : ''
      }`}
    >
      <div>
        {/* Top Header: Title, Pin, Lock & Actions */}
        <div className="flex items-start justify-between gap-1.5 sm:gap-2 mb-1.5 sm:mb-2">
          <h2 className="font-semibold text-xs sm:text-[15px] leading-snug text-[#1E1A24] dark:text-[#F7EEFC] line-clamp-2">
            {note.title || 'Untitled Note'}
          </h2>

          <div className="flex items-center gap-0.5 shrink-0" onClick={(e) => e.stopPropagation()}>
            {/* Pin Button */}
            <button
              onClick={(e) => onTogglePin(note.id, e)}
              aria-label={note.isPinned ? `Unpin ${note.title || 'note'}` : `Pin ${note.title || 'note'}`}
              className={`p-1 rounded-lg transition-colors ${
                note.isPinned
                  ? 'text-[#5925DC] dark:text-[#C296FD] bg-[#F4EBFF] dark:bg-[#2B1E3E]'
                  : 'text-[#797487] dark:text-[#A39BAC] opacity-40 hover:opacity-100 hover:bg-[#F4EBFF] dark:hover:bg-[#2B1E3E] hover:text-[#5925DC]'
              }`}
              title={note.isPinned ? 'Unpin' : 'Pin to top'}
            >
              <Pin className={`w-3.5 h-3.5 ${note.isPinned ? 'fill-current' : ''}`} />
            </button>

            {/* More Menu */}
            <div className="relative">
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  setShowMenu((prev) => !prev);
                }}
                aria-label={`Options for ${note.title || 'note'}`}
                className="p-1 rounded-lg text-[#797487] dark:text-[#A39BAC] hover:text-[#1E1A24] dark:hover:text-[#F7EEFC] hover:bg-[#FAF8FB] dark:hover:bg-[#120E17] transition-all"
              >
                <MoreVertical className="w-3.5 h-3.5" />
              </button>

              {showMenu && (
                <div
                  className="absolute right-0 top-7 z-20 w-40 bg-white/95 dark:bg-[#1B1524]/95 backdrop-blur-md rounded-xl border border-[#EADBFC] dark:border-[#322742] shadow-[0_12px_28px_rgba(89,37,220,0.14)] p-1 flex flex-col text-xs font-medium text-[#1E1A24] dark:text-[#F7EEFC]"
                  onClick={(e) => e.stopPropagation()}
                >
                  <button
                    onClick={(e) => {
                      setShowMenu(false);
                      onInspectCipher(note, e);
                    }}
                    className="w-full text-left px-2 py-1.5 rounded-lg hover:bg-[#F4EBFF] dark:hover:bg-[#2B1E3E] hover:text-[#5925DC] dark:hover:text-[#C296FD] flex items-center gap-1.5"
                  >
                    <Binary className="w-3.5 h-3.5 text-[#5925DC] dark:text-[#C296FD]" />
                    <span>View AES Cipher</span>
                  </button>
                  <button
                    onClick={(e) => {
                      setShowMenu(false);
                      onTogglePin(note.id, e);
                    }}
                    className="w-full text-left px-2 py-1.5 rounded-lg hover:bg-[#F4EBFF] dark:hover:bg-[#2B1E3E] hover:text-[#5925DC] dark:hover:text-[#C296FD] flex items-center gap-1.5"
                  >
                    <Pin className="w-3.5 h-3.5" />
                    <span>{note.isPinned ? 'Unpin' : 'Pin to top'}</span>
                  </button>
                  <div className="my-1 border-t border-[#EADBFC]/60 dark:border-[#322742]" />
                  <button
                    onClick={(e) => {
                      setShowMenu(false);
                      onDelete(note.id, e);
                    }}
                    className="w-full text-left px-2 py-1.5 rounded-lg hover:bg-rose-50 dark:hover:bg-rose-950/40 text-rose-600 dark:text-rose-400 flex items-center gap-1.5"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                    <span>Delete note</span>
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Note Body Preview (Truncated compactly) */}
        {plainTextPreview && (
          <p className="text-[11px] sm:text-xs leading-[17px] text-[#484455] dark:text-[#A39BAC] line-clamp-2 sm:line-clamp-3 mb-2 font-normal whitespace-pre-wrap">
            {plainTextPreview}
          </p>
        )}

        {/* Inline Checklists Preview (Interactive directly on card) */}
        {note.checklist && note.checklist.length > 0 && (
          <div className="space-y-1 mb-2.5" onClick={(e) => e.stopPropagation()}>
            {note.checklist.slice(0, 2).map((item) => (
              <div
                key={item.id}
                onClick={(e) => onToggleCheckItem(note.id, item.id, e)}
                role="checkbox"
                aria-checked={item.completed}
                tabIndex={0}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    onToggleCheckItem(note.id, item.id, e as unknown as React.MouseEvent);
                  }
                }}
                className="flex items-center gap-2 text-[11px] sm:text-xs text-[#1E1A24] dark:text-[#F7EEFC] cursor-pointer hover:bg-[#FAF8FB] dark:hover:bg-[#120E17] p-0.5 rounded transition-all group/item focus:outline-none"
              >
                <div
                  className={`w-3.5 h-3.5 rounded-[5px] border flex items-center justify-center transition-all ${
                    item.completed
                      ? 'bg-[#5925DC] dark:bg-[#9D5AF5] border-transparent text-white shadow-xs'
                      : 'border-[#CAC3D8] dark:border-[#4A3B60] bg-white dark:bg-[#1B1524]'
                  }`}
                >
                  {item.completed && <Check className="w-2.5 h-2.5 stroke-[2.5]" />}
                </div>
                <span
                  className={`truncate flex-1 transition-all ${
                    item.completed
                      ? 'line-through text-[#797487] dark:text-[#7A7185] opacity-65'
                      : 'text-[#1E1A24] dark:text-[#F7EEFC]'
                  }`}
                >
                  {item.text}
                </span>
              </div>
            ))}
            {note.checklist.length > 2 && (
              <span className="text-[10px] text-[#797487] dark:text-[#A39BAC] font-medium block pl-5">
                +{note.checklist.length - 2} more
              </span>
            )}
          </div>
        )}

        {/* Tags */}
        {note.tags && note.tags.length > 0 && (
          <div className="flex flex-wrap gap-1 mb-2">
            {note.tags.slice(0, 2).map((tag) => (
              <span
                key={tag}
                className="text-[9px] sm:text-[10px] font-medium px-1.5 py-0.2 rounded bg-[#FAF8FB] dark:bg-[#120E17] text-[#484455] dark:text-[#A39BAC] border border-[#EADBFC]/60 dark:border-[#322742]"
              >
                #{tag}
              </span>
            ))}
          </div>
        )}
      </div>

      {/* Bottom Metadata Row */}
      <div className="flex items-center justify-between pt-2 border-t border-[#EADBFC]/60 dark:border-[#322742]/60 mt-auto text-[10px] sm:text-[11px]">
        <div className="flex items-center gap-1.5 truncate">
          {/* Category Pill */}
          <span className="px-2 py-0.2 rounded-full bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] font-semibold text-[10px] tracking-wide border border-[#EADBFC]/70 dark:border-[#322742] truncate max-w-[80px] sm:max-w-[120px]">
            {note.category || 'General'}
          </span>

        </div>

        <div className="flex items-center gap-1 text-[#797487] dark:text-[#A39BAC] text-[10px] font-medium shrink-0">
          <Lock className="w-2.5 h-2.5 text-[#5925DC] dark:text-[#C296FD]" />
          <span>{formattedDate}</span>
        </div>
      </div>
    </div>
  );
};

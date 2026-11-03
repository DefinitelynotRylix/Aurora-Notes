import React, { useState, useRef } from 'react';
import { Note } from '../types';
import {
  Pin,
  Check,
  MoreVertical,
  Trash2,
  Share2,
  CheckCircle2,
  Circle,
  Archive,
  ArchiveRestore,
  Lock
} from 'lucide-react';
import { parseTextWithLinks, openUrlInDefaultBrowser } from '../utils/linkUtils';

interface NoteCardProps {
  note: Note;
  onSelect: (note: Note) => void;
  onTogglePin: (id: string, e: React.MouseEvent) => void;
  onArchive?: (id: string, e?: React.MouseEvent) => void;
  onUnarchive?: (id: string, e?: React.MouseEvent) => void;
  onDelete: (id: string, e?: React.MouseEvent | React.TouchEvent | null) => void;
  onToggleCheckItem: (noteId: string, itemId: string, e: React.MouseEvent) => void;
  onShare?: (note: Note) => void;
  onDeleteTag?: (tag: string) => void;
  isSelectionMode?: boolean;
  isSelected?: boolean;
  onToggleSelection?: (noteId: string) => void;
  isGridView: boolean;
  animateMovement: boolean;
}

export const NoteCard: React.FC<NoteCardProps> = ({
  note,
  onSelect,
  onTogglePin,
  onArchive,
  onUnarchive,
  onDelete,
  onToggleCheckItem,
  onShare,
  onDeleteTag,
  isSelectionMode = false,
  isSelected = false,
  onToggleSelection,
  animateMovement,
}) => {
  const [showMenu, setShowMenu] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [pendingDeleteTag, setPendingDeleteTag] = useState<string | null>(null);
  const [isHolding, setIsHolding] = useState(false);
  const isArchived = Boolean(note.isArchived);

  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const startPosRef = useRef<{ x: number; y: number } | null>(null);
  const isLongPressTriggeredRef = useRef(false);

  const cancelHold = () => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
    setIsHolding(false);
  };

  const startHold = (clientX: number, clientY: number) => {
    isLongPressTriggeredRef.current = false;
    startPosRef.current = { x: clientX, y: clientY };
    setIsHolding(true);

    if (timerRef.current) clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => {
      isLongPressTriggeredRef.current = true;
      setIsHolding(false);
      if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
        try {
          navigator.vibrate(50);
        } catch {
          // ignore
        }
      }
      if (onToggleSelection) {
        onToggleSelection(note.id);
      } else {
        setShowDeleteConfirm(true);
      }
    }, 420);
  };

  const handlePointerDown = (e: React.PointerEvent) => {
    if (e.button !== 0 && e.pointerType === 'mouse') return;
    startHold(e.clientX, e.clientY);
  };

  const handlePointerMove = (e: React.PointerEvent) => {
    if (!startPosRef.current) return;
    const dx = Math.abs(e.clientX - startPosRef.current.x);
    const dy = Math.abs(e.clientY - startPosRef.current.y);
    if (dx > 10 || dy > 10) {
      cancelHold();
    }
  };

  const handlePointerUp = () => {
    cancelHold();
  };

  const handlePointerCancel = () => {
    cancelHold();
  };

  const handleClick = (e: React.MouseEvent) => {
    if (isLongPressTriggeredRef.current) {
      e.preventDefault();
      e.stopPropagation();
      isLongPressTriggeredRef.current = false;
      return;
    }
    if (isSelectionMode && onToggleSelection) {
      onToggleSelection(note.id);
      return;
    }
    onSelect(note);
  };

  const handleContextMenu = (e: React.MouseEvent) => {
    e.preventDefault();
    cancelHold();
    if (onToggleSelection) {
      onToggleSelection(note.id);
    } else {
      setShowDeleteConfirm(true);
    }
  };

  const formattedDate = new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
  }).format(note.updatedAt);

  const plainTextPreview = React.useMemo(() => {
    if (!note.content) return '';
    try {
      const doc = new DOMParser().parseFromString(note.content, 'text/html');
      doc.body.querySelectorAll('.aurora-checklist-line').forEach((el) => el.remove());
      doc.body.querySelectorAll('br').forEach((br) => br.replaceWith('\n'));
      doc.body.querySelectorAll('div, p, li, h1, h2, h3').forEach((block) => {
        block.append('\n');
      });
      return (doc.body.textContent || '')
        .replace(/\u200B/g, '')
        .replace(/\n{2,}/g, '\n')
        .trim();
    } catch {
      return note.content.replace(/<[^>]*>/g, '');
    }
  }, [note.content]);

  return (
    <>
      <div
        onClick={handleClick}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerCancel}
        onContextMenu={handleContextMenu}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            if (isSelectionMode && onToggleSelection) {
              onToggleSelection(note.id);
            } else {
              onSelect(note);
            }
          } else if (e.key === 'Delete' || e.key === 'Backspace') {
            e.preventDefault();
            setShowDeleteConfirm(true);
          }
        }}
        className={`w-full glass-card rounded-xl sm:rounded-2xl p-3 sm:p-3.5 cursor-pointer relative group flex flex-col justify-between select-none focus:outline-none focus:ring-2 focus:ring-[#5925DC]/30 active:scale-[0.98] ${
          isSelected
            ? 'ring-2 ring-[#5925DC] dark:ring-[#9D5AF5] bg-[#F4EBFF]/60 dark:bg-[#2B1E3E]/60 shadow-md'
            : ''
        } ${
          isHolding ? 'scale-[0.97] ring-2 ring-[#5925DC]/50 shadow-md transition-transform duration-150' : ''
        } ${
          animateMovement ? 'transition-all duration-300 ease-out' : ''
        }`}
      >
        <div>
          {isArchived && (
            <div className="mb-1.5 inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] text-[9px] font-bold tracking-wide border border-[#EADBFC] dark:border-[#322742]">
              <Lock className="w-2.5 h-2.5" />
              <span>Read-Only • Archived</span>
            </div>
          )}

          {/* Top Header: Selection Indicator, Title, Actions */}
          <div className="flex items-start justify-between gap-1.5 sm:gap-2 mb-1.5 sm:mb-2">
            <div className="flex items-center gap-1.5 min-w-0 flex-1">
              {isSelectionMode && (
                <span className="text-[#5925DC] dark:text-[#C296FD] shrink-0">
                  {isSelected ? (
                    <CheckCircle2 className="w-4 h-4 fill-[#5925DC]/15" />
                  ) : (
                    <Circle className="w-4 h-4 opacity-50" />
                  )}
                </span>
              )}
              <h2 className="font-semibold text-xs sm:text-[15px] leading-snug text-[#1E1A24] dark:text-[#F7EEFC] line-clamp-2">
                {note.title || 'Untitled Note'}
              </h2>
            </div>

            {!isSelectionMode && (
              <div className="flex items-center gap-0.5 shrink-0" onClick={(e) => e.stopPropagation()}>
                {isArchived ? (
                  <>
                    {/* Restore (Unarchive) Button */}
                    {onUnarchive && (
                      <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          onUnarchive(note.id, e);
                        }}
                        aria-label={`Restore ${note.title || 'note'} from Archive`}
                        className="p-1 rounded-lg bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] hover:opacity-90 transition-colors cursor-pointer"
                        title="Restore note"
                      >
                        <ArchiveRestore className="w-3.5 h-3.5" />
                      </button>
                    )}

                    {/* Delete Button */}
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        setShowDeleteConfirm(true);
                      }}
                      aria-label={`Delete ${note.title || 'note'}`}
                      className="p-1 rounded-lg bg-rose-50 dark:bg-rose-950/40 text-rose-600 dark:text-rose-400 hover:bg-rose-100 dark:hover:bg-rose-900/50 transition-colors cursor-pointer"
                      title="Delete note"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </>
                ) : (
                  <>
                    {/* Share Button */}
                    {onShare && (
                      <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          onShare(note);
                        }}
                        aria-label={`Share ${note.title || 'note'}`}
                        className="p-1 rounded-lg text-[#5925DC] dark:text-[#C296FD] opacity-75 hover:opacity-100 hover:bg-[#F4EBFF] dark:hover:bg-[#2B1E3E] transition-colors cursor-pointer"
                        title="Share note"
                      >
                        <Share2 className="w-3.5 h-3.5" />
                      </button>
                    )}

                    {/* Pin Button */}
                    <button
                      type="button"
                      onClick={(e) => onTogglePin(note.id, e)}
                      aria-label={note.isPinned ? `Unpin ${note.title || 'note'}` : `Pin ${note.title || 'note'}`}
                      className={`p-1 rounded-lg transition-colors cursor-pointer ${
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
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          setShowMenu((prev) => !prev);
                        }}
                        aria-label={`Options for ${note.title || 'note'}`}
                        className="p-1 rounded-lg text-[#797487] dark:text-[#A39BAC] hover:text-[#1E1A24] dark:hover:text-[#F7EEFC] hover:bg-[#FAF8FB] dark:hover:bg-[#120E17] transition-all cursor-pointer"
                      >
                        <MoreVertical className="w-3.5 h-3.5" />
                      </button>

                      {showMenu && (
                        <div
                          className="absolute right-0 top-7 z-20 w-44 bg-white/95 dark:bg-[#1B1524]/95 backdrop-blur-md rounded-xl border border-[#EADBFC] dark:border-[#322742] shadow-[0_12px_28px_rgba(89,37,220,0.14)] p-1 flex flex-col text-xs font-medium text-[#1E1A24] dark:text-[#F7EEFC]"
                          onClick={(e) => e.stopPropagation()}
                        >
                          {onShare && (
                            <button
                              type="button"
                              onClick={() => {
                                setShowMenu(false);
                                onShare(note);
                              }}
                              className="w-full text-left px-2 py-1.5 rounded-lg hover:bg-[#F4EBFF] dark:hover:bg-[#2B1E3E] hover:text-[#5925DC] dark:hover:text-[#C296FD] flex items-center gap-1.5 cursor-pointer"
                            >
                              <Share2 className="w-3.5 h-3.5" />
                              <span>Share note</span>
                            </button>
                          )}
                          {onArchive && (
                            <button
                              type="button"
                              onClick={(e) => {
                                setShowMenu(false);
                                onArchive(note.id, e);
                              }}
                              className="w-full text-left px-2 py-1.5 rounded-lg hover:bg-[#F4EBFF] dark:hover:bg-[#2B1E3E] hover:text-[#5925DC] dark:hover:text-[#C296FD] flex items-center gap-1.5 cursor-pointer"
                            >
                              <Archive className="w-3.5 h-3.5" />
                              <span>Archive note</span>
                            </button>
                          )}
                          {onToggleSelection && (
                            <button
                              type="button"
                              onClick={() => {
                                setShowMenu(false);
                                onToggleSelection(note.id);
                              }}
                              className="w-full text-left px-2 py-1.5 rounded-lg hover:bg-[#F4EBFF] dark:hover:bg-[#2B1E3E] hover:text-[#5925DC] dark:hover:text-[#C296FD] flex items-center gap-1.5 cursor-pointer"
                            >
                              <CheckCircle2 className="w-3.5 h-3.5" />
                              <span>Select multiple</span>
                            </button>
                          )}
                          <button
                            type="button"
                            onClick={(e) => {
                              setShowMenu(false);
                              onTogglePin(note.id, e);
                            }}
                            className="w-full text-left px-2 py-1.5 rounded-lg hover:bg-[#F4EBFF] dark:hover:bg-[#2B1E3E] hover:text-[#5925DC] dark:hover:text-[#C296FD] flex items-center gap-1.5 cursor-pointer"
                          >
                            <Pin className="w-3.5 h-3.5" />
                            <span>{note.isPinned ? 'Unpin' : 'Pin to top'}</span>
                          </button>
                          <div className="my-1 border-t border-[#EADBFC]/60 dark:border-[#322742]" />
                          <button
                            type="button"
                            onClick={(e) => {
                              setShowMenu(false);
                              onDelete(note.id, e);
                            }}
                            className="w-full text-left px-2 py-1.5 rounded-lg hover:bg-rose-50 dark:hover:bg-rose-950/40 text-rose-600 dark:text-rose-400 flex items-center gap-1.5 cursor-pointer"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                            <span>Delete note</span>
                          </button>
                        </div>
                      )}
                    </div>
                  </>
                )}
              </div>
            )}
          </div>

          {/* Note Body Preview with Clickable Purple Links */}
          {plainTextPreview && (
            <p className="text-[11px] sm:text-xs leading-[17px] text-[#484455] dark:text-[#A39BAC] line-clamp-2 sm:line-clamp-3 mb-2 font-normal whitespace-pre-wrap">
              {parseTextWithLinks(plainTextPreview).map((seg, idx) =>
                seg.type === 'link' && seg.url ? (
                  <a
                    key={idx}
                    href={seg.url}
                    target="_blank"
                    rel="noopener noreferrer"
                    onClick={(e) => {
                      e.stopPropagation();
                      if (isSelectionMode && onToggleSelection) {
                        e.preventDefault();
                        onToggleSelection(note.id);
                      } else {
                        e.preventDefault();
                        openUrlInDefaultBrowser(seg.url!);
                      }
                    }}
                    className="text-[#8B3DFF] dark:text-[#C296FD] underline decoration-[#8B3DFF] dark:decoration-[#C296FD] font-semibold cursor-pointer hover:opacity-85 break-all"
                  >
                    {seg.text}
                  </a>
                ) : (
                  <React.Fragment key={idx}>{seg.text}</React.Fragment>
                )
              )}
            </p>
          )}

          {/* Inline Checklists Preview */}
          {note.checklist && note.checklist.length > 0 && (
            <div className="space-y-1 mb-2.5" onClick={(e) => !isArchived && e.stopPropagation()}>
              {note.checklist.slice(0, 2).map((item) => (
                <div
                  key={item.id}
                  onClick={(e) => {
                    if (isSelectionMode && onToggleSelection) {
                      e.stopPropagation();
                      onToggleSelection(note.id);
                    } else if (!isArchived) {
                      onToggleCheckItem(note.id, item.id, e);
                    }
                  }}
                  role="checkbox"
                  aria-checked={item.completed}
                  aria-disabled={isArchived}
                  tabIndex={isArchived ? -1 : 0}
                  onKeyDown={(e) => {
                    if (!isArchived && (e.key === 'Enter' || e.key === ' ')) {
                      e.preventDefault();
                      onToggleCheckItem(note.id, item.id, e as unknown as React.MouseEvent);
                    }
                  }}
                  className={`flex items-center gap-2 text-[11px] sm:text-xs text-[#1E1A24] dark:text-[#F7EEFC] p-0.5 rounded transition-all group/item focus:outline-none ${
                    isArchived
                      ? 'cursor-default opacity-80'
                      : 'cursor-pointer hover:bg-[#FAF8FB] dark:hover:bg-[#120E17]'
                  }`}
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
                    {parseTextWithLinks(item.text).map((seg, idx) =>
                      seg.type === 'link' && seg.url ? (
                        <a
                          key={idx}
                          href={seg.url}
                          target="_blank"
                          rel="noopener noreferrer"
                          onClick={(e) => {
                            e.stopPropagation();
                            e.preventDefault();
                            openUrlInDefaultBrowser(seg.url!);
                          }}
                          className="text-[#8B3DFF] dark:text-[#C296FD] underline decoration-[#8B3DFF] dark:decoration-[#C296FD] font-semibold cursor-pointer"
                        >
                          {seg.text}
                        </a>
                      ) : (
                        <React.Fragment key={idx}>{seg.text}</React.Fragment>
                      )
                    )}
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

          {/* Tags with long press deletion (disabled when archived) */}
          {note.tags && note.tags.length > 0 && (
            <div className="flex flex-wrap gap-1 mb-2" onClick={(e) => !isArchived && e.stopPropagation()}>
              {note.tags.slice(0, 3).map((tag) => (
                <span
                  key={tag}
                  onContextMenu={(e) => {
                    if (isArchived) return;
                    e.preventDefault();
                    e.stopPropagation();
                    setPendingDeleteTag(tag);
                  }}
                  title={isArchived ? 'Archived note (read-only)' : 'Right-click / long press to delete tag'}
                  className={`text-[9px] sm:text-[10px] font-medium px-1.5 py-0.2 rounded bg-[#FAF8FB] dark:bg-[#120E17] text-[#484455] dark:text-[#A39BAC] border border-[#EADBFC]/60 dark:border-[#322742] ${
                    isArchived ? 'cursor-default' : 'cursor-pointer hover:border-rose-400'
                  }`}
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
            <span className="px-2 py-0.2 rounded-full bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] font-semibold text-[10px] tracking-wide border border-[#EADBFC]/70 dark:border-[#322742] truncate max-w-[80px] sm:max-w-[120px]">
              {note.category || 'All'}
            </span>
          </div>

          <div className="flex items-center gap-1 text-[#797487] dark:text-[#A39BAC] text-[10px] font-medium shrink-0">
            <span>{formattedDate}</span>
          </div>
        </div>
      </div>

      {/* Delete Tag Confirmation Modal */}
      {pendingDeleteTag && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-fade-in"
          onClick={(e) => {
            e.stopPropagation();
            setPendingDeleteTag(null);
          }}
        >
          <div
            className="w-full max-w-sm rounded-2xl bg-white dark:bg-[#1B1524] border border-[#EADBFC] dark:border-[#322742] shadow-2xl p-5 space-y-4"
            onClick={(e) => e.stopPropagation()}
          >
            <h3 className="font-bold text-sm text-[#1E1A24] dark:text-[#F7EEFC]">
              Delete Tag #{pendingDeleteTag}?
            </h3>
            <p className="text-xs text-[#484455] dark:text-[#A39BAC] leading-relaxed">
              Deleting #{pendingDeleteTag} removes the tag and returns any notes under it back to All without deleting the notes.
            </p>
            <div className="flex items-center justify-end gap-2">
              <button
                type="button"
                onClick={() => setPendingDeleteTag(null)}
                className="px-3.5 py-2 rounded-xl text-xs font-semibold text-[#484455] dark:text-[#A39BAC] border border-[#EADBFC] dark:border-[#322742] cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => {
                  const tag = pendingDeleteTag;
                  setPendingDeleteTag(null);
                  if (tag && onDeleteTag) {
                    onDeleteTag(tag);
                  }
                }}
                className="px-4 py-2 rounded-xl text-xs font-semibold text-white bg-rose-600 hover:bg-rose-700 cursor-pointer"
              >
                Delete Tag
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Single Note Delete Confirmation Dialog */}
      {showDeleteConfirm && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-fade-in"
          onClick={(e) => {
            e.stopPropagation();
            setShowDeleteConfirm(false);
          }}
        >
          <div
            className="w-full max-w-sm rounded-2xl bg-white dark:bg-[#1B1524] border border-[#EADBFC] dark:border-[#322742] shadow-[0_20px_50px_rgba(0,0,0,0.35)] p-5 space-y-4"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-rose-50 dark:bg-rose-950/40 text-rose-600 dark:text-rose-400 flex items-center justify-center shrink-0">
                <Trash2 className="w-5 h-5" />
              </div>
              <div className="min-w-0">
                <h3 className="font-bold text-sm text-[#1E1A24] dark:text-[#F7EEFC]">
                  Delete Note?
                </h3>
                <p className="text-xs text-[#797487] dark:text-[#A39BAC] truncate">
                  {note.title ? `"${note.title}"` : 'Untitled Note'}
                </p>
              </div>
            </div>

            <p className="text-xs text-[#484455] dark:text-[#A39BAC] leading-relaxed">
              Move this note to Trash? You can restore it later or empty the trash permanently.
            </p>

            <div className="flex items-center justify-end gap-2 pt-1">
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  setShowDeleteConfirm(false);
                }}
                className="px-3.5 py-2 rounded-xl text-xs font-semibold text-[#484455] dark:text-[#A39BAC] hover:bg-[#FAF8FB] dark:hover:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] transition-colors cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  setShowDeleteConfirm(false);
                  onDelete(note.id, e);
                }}
                className="px-4 py-2 rounded-xl text-xs font-semibold text-white bg-rose-600 hover:bg-rose-700 active:scale-95 transition-all shadow-sm flex items-center gap-1.5 cursor-pointer"
              >
                <Trash2 className="w-3.5 h-3.5" />
                <span>Move to Trash</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

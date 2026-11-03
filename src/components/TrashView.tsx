import React, { useState, useMemo } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { Note } from '../types';
import {
  Trash2,
  RotateCcw,
  AlertTriangle,
  FileText,
  Search,
  CheckCircle2,
  Calendar,
  Lock,
  X
} from 'lucide-react';
import { logger } from '../utils/logger';

interface TrashViewProps {
  trashedNotes: Note[];
  searchQuery?: string;
  onSearchChange?: (query: string) => void;
  isGridView?: boolean;
  onRestoreNote: (id: string) => void;
  onPermanentDelete: (id: string) => void;
  onEmptyTrash: () => void;
}

const getPlainTextPreview = (htmlOrText: string) => {
  if (!htmlOrText) return '';
  try {
    const doc = new DOMParser().parseFromString(htmlOrText, 'text/html');
    return doc.body.textContent || '';
  } catch {
    return htmlOrText.replace(/<[^>]*>/g, '');
  }
};

export const TrashView: React.FC<TrashViewProps> = ({
  trashedNotes,
  searchQuery: externalSearchQuery,
  onSearchChange: externalOnSearchChange,
  isGridView = true,
  onRestoreNote,
  onPermanentDelete,
  onEmptyTrash,
}) => {
  const [internalSearchQuery, setInternalSearchQuery] = useState('');
  const [confirmEmptyOpen, setConfirmEmptyOpen] = useState(false);
  const [itemToDeleteConfirm, setItemToDeleteConfirm] = useState<string | null>(null);

  const query = externalSearchQuery !== undefined ? externalSearchQuery : internalSearchQuery;
  const setQuery = (q: string) => {
    if (externalOnSearchChange) {
      externalOnSearchChange(q);
    } else {
      setInternalSearchQuery(q);
    }
  };

  const filteredNotes = useMemo(() => {
    if (!query.trim()) return trashedNotes;
    const q = query.toLowerCase().trim();
    return trashedNotes.filter((note) => {
      const matchTitle = (note.title || '').toLowerCase().includes(q);
      const matchContent = (note.content || '').toLowerCase().includes(q);
      const matchCategory = (note.category || '').toLowerCase().includes(q);
      const matchTags = note.tags?.some((t) => t.toLowerCase().includes(q));
      const matchChecklist = note.checklist?.some((c) => c.text.toLowerCase().includes(q));
      return matchTitle || matchContent || matchCategory || matchTags || matchChecklist;
    });
  }, [trashedNotes, query]);

  return (
    <div className="space-y-6 pb-28">
      {/* Top Banner / Header */}
      <div className="glass-panel p-5 sm:p-6 rounded-3xl border border-white/60 dark:border-white/10 shadow-sm">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="w-11 h-11 rounded-2xl bg-rose-50 dark:bg-rose-950/40 text-rose-600 dark:text-rose-400 flex items-center justify-center border border-rose-200 dark:border-rose-800 shrink-0">
              <Trash2 className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-xl font-bold text-[#1E1926] dark:text-[#F3EFF8]">
                Trash
              </h2>
              <p className="text-xs text-[#797487] dark:text-[#A39BAC]">
                {trashedNotes.length === 1
                  ? '1 item in trash'
                  : `${trashedNotes.length} items in trash`}
                {' • '}
                Stored strictly in local device memory
              </p>
            </div>
          </div>

          {trashedNotes.length > 0 && (
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                setConfirmEmptyOpen(true);
              }}
              className="inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-2xl bg-rose-50 hover:bg-rose-100 dark:bg-rose-950/40 dark:hover:bg-rose-900/50 text-rose-700 dark:text-rose-300 font-semibold text-xs border border-rose-200 dark:border-rose-800 transition-all cursor-pointer shadow-sm active:scale-95 shrink-0"
            >
              <Trash2 className="w-4 h-4" />
              <span>Empty Trash (Delete All)</span>
            </button>
          )}
        </div>

        {/* Search within trash whenever trashed notes exist */}
        {trashedNotes.length > 0 && (
          <div className="mt-4 pt-4 border-t border-[#EADBFC]/60 dark:border-[#322742]/60">
            <div className="relative">
              <Search className="w-4 h-4 text-[#797487] dark:text-[#A39BAC] absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
              <input
                type="text"
                placeholder="Search trashed notes by title, content, category, or checklist..."
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                className="w-full pl-10 pr-9 py-2 text-xs sm:text-sm rounded-xl bg-white/80 dark:bg-[#1E1728]/80 border border-[#EADBFC] dark:border-[#322742] focus:outline-none focus:ring-2 focus:ring-[#9B51E0] text-[#1E1926] dark:text-[#F3EFF8] placeholder-[#797487] dark:placeholder-[#A39BAC] transition-all"
              />
              {query && (
                <button
                  type="button"
                  onClick={() => setQuery('')}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-xs font-bold text-[#797487] hover:text-[#1E1926] dark:hover:text-white p-1 rounded-md transition-colors"
                  aria-label="Clear trash search query"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              )}
            </div>
            {query.trim() && (
              <div className="mt-2 text-[11px] text-[#797487] dark:text-[#A39BAC] font-medium flex items-center justify-between">
                <span>
                  Found <strong>{filteredNotes.length}</strong> matching {filteredNotes.length === 1 ? 'note' : 'notes'} for "{query}"
                </span>
                <button
                  type="button"
                  onClick={() => setQuery('')}
                  className="text-[#5925DC] dark:text-[#C296FD] hover:underline"
                >
                  Clear search
                </button>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Confirmation Modal for Empty Trash */}
      <AnimatePresence>
        {confirmEmptyOpen && (
          <motion.div
            key="confirm-empty-trash-backdrop"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.15 }}
            className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm"
            onClick={(e) => {
              if (e.target === e.currentTarget) {
                e.stopPropagation();
                setConfirmEmptyOpen(false);
              }
            }}
          >
            <motion.div
              key="confirm-empty-trash-card"
              initial={{ scale: 0.92, opacity: 0, y: 12 }}
              animate={{ scale: 1, opacity: 1, y: 0 }}
              exit={{ scale: 0.92, opacity: 0, y: 12 }}
              transition={{ duration: 0.18, ease: [0.16, 1, 0.3, 1] }}
              onClick={(e) => e.stopPropagation()}
              className="glass-panel p-6 rounded-3xl max-w-sm w-full border border-rose-200 dark:border-rose-900/60 shadow-2xl bg-white dark:bg-[#1B1524] text-center relative z-10"
            >
              <div className="w-12 h-12 rounded-2xl bg-rose-100 dark:bg-rose-950/60 text-rose-600 dark:text-rose-400 mx-auto mb-4 flex items-center justify-center border border-rose-300 dark:border-rose-800">
                <AlertTriangle className="w-6 h-6" />
              </div>
              <h3 className="text-base font-bold text-[#1E1926] dark:text-[#F3EFF8] mb-1">
                Permanently empty trash?
              </h3>
              <p className="text-xs text-[#797487] dark:text-[#A39BAC] mb-5 leading-relaxed">
                This will irreversibly delete all {trashedNotes.length} notes from your local device storage. This action cannot be undone.
              </p>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    setConfirmEmptyOpen(false);
                  }}
                  className="flex-1 py-2.5 rounded-xl border border-gray-200 dark:border-gray-700 text-xs font-semibold text-[#484455] dark:text-[#A39BAC] hover:bg-gray-100 dark:hover:bg-gray-800 transition cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    const count = trashedNotes.length;
                    onEmptyTrash();
                    setConfirmEmptyOpen(false);
                    logger.security('STORAGE', `User permanently emptied trash (${count} notes purged)`);
                  }}
                  className="flex-1 py-2.5 rounded-xl bg-rose-600 hover:bg-rose-700 text-white text-xs font-semibold shadow-md transition cursor-pointer"
                >
                  Yes, Empty All
                </button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Confirmation Modal for Single Permanent Delete */}
      <AnimatePresence>
        {itemToDeleteConfirm && (
          <motion.div
            key="confirm-single-delete-backdrop"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.15 }}
            className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm"
            onClick={(e) => {
              if (e.target === e.currentTarget) {
                e.stopPropagation();
                setItemToDeleteConfirm(null);
              }
            }}
          >
            <motion.div
              key="confirm-single-delete-card"
              initial={{ scale: 0.92, opacity: 0, y: 12 }}
              animate={{ scale: 1, opacity: 1, y: 0 }}
              exit={{ scale: 0.92, opacity: 0, y: 0 }}
              transition={{ duration: 0.18, ease: [0.16, 1, 0.3, 1] }}
              onClick={(e) => e.stopPropagation()}
              className="glass-panel p-6 rounded-3xl max-w-sm w-full border border-rose-200 dark:border-rose-900/60 shadow-2xl bg-white dark:bg-[#1B1524] text-center relative z-10"
            >
              <div className="w-12 h-12 rounded-2xl bg-rose-100 dark:bg-rose-950/60 text-rose-600 dark:text-rose-400 mx-auto mb-4 flex items-center justify-center border border-rose-300 dark:border-rose-800">
                <Trash2 className="w-6 h-6" />
              </div>
              <h3 className="text-base font-bold text-[#1E1926] dark:text-[#F3EFF8] mb-1">
                Permanently delete note?
              </h3>
              <p className="text-xs text-[#797487] dark:text-[#A39BAC] mb-5 leading-relaxed">
                This note will be permanently purged from local storage.
              </p>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    setItemToDeleteConfirm(null);
                  }}
                  className="flex-1 py-2.5 rounded-xl border border-gray-200 dark:border-gray-700 text-xs font-semibold text-[#484455] dark:text-[#A39BAC] hover:bg-gray-100 dark:hover:bg-gray-800 transition cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    const id = itemToDeleteConfirm;
                    onPermanentDelete(id);
                    setItemToDeleteConfirm(null);
                    logger.security('STORAGE', `Permanently deleted note ID: ${id}`);
                  }}
                  className="flex-1 py-2.5 rounded-xl bg-rose-600 hover:bg-rose-700 text-white text-xs font-semibold shadow-md transition cursor-pointer"
                >
                  Delete
                </button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Trashed Items List */}
      {filteredNotes.length === 0 ? (
        <div className="glass-panel p-12 rounded-3xl text-center border border-white/60 dark:border-white/10">
          <div className="w-16 h-16 rounded-full bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] mx-auto mb-4 flex items-center justify-center">
            <Trash2 className="w-8 h-8 opacity-70" />
          </div>
          <h3 className="text-base font-bold text-[#1E1926] dark:text-[#F3EFF8] mb-1">
            {trashedNotes.length === 0 ? 'Trash is empty' : 'No matching notes found'}
          </h3>
          <p className="text-xs text-[#797487] dark:text-[#A39BAC] max-w-sm mx-auto">
            {trashedNotes.length === 0
              ? 'When you delete a note from your vault, it moves here first so you can restore it or permanently wipe it.'
              : 'Try clearing your search query to see all items in the trash.'}
          </p>
        </div>
      ) : (
        <motion.div
          layout
          transition={{ layout: { type: 'spring', stiffness: 280, damping: 28 } }}
          className={`grid gap-2.5 sm:gap-4 ${
            isGridView
              ? 'grid-cols-2 sm:grid-cols-3 lg:grid-cols-4'
              : 'grid-cols-1 max-w-2xl mx-auto'
          }`}
        >
          {filteredNotes.map((note) => {
            const formattedDate = new Intl.DateTimeFormat('en-US', {
              month: 'short',
              day: 'numeric',
              hour: '2-digit',
              minute: '2-digit',
            }).format(note.deletedAt || note.updatedAt);

            return (
              <motion.div
                key={note.id}
                layout
                transition={{ layout: { type: 'spring', stiffness: 280, damping: 28 } }}
                className="glass-card rounded-2xl sm:rounded-3xl p-4 sm:p-5 flex flex-col justify-between border border-[#EADBFC]/80 dark:border-[#322742]/80 bg-white/70 dark:bg-[#1A1423]/70 hover:shadow-md transition-shadow relative group"
              >
                <div>
                  <div className="flex items-start justify-between gap-2 mb-2">
                    <h4 className="font-bold text-sm sm:text-base text-[#1E1926] dark:text-[#F3EFF8] line-clamp-1">
                      {note.title || 'Untitled Note'}
                    </h4>
                    <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-rose-50 dark:bg-rose-950/40 text-rose-600 dark:text-rose-400 border border-rose-200 dark:border-rose-800 shrink-0">
                      In Trash
                    </span>
                  </div>

                  {note.content && (
                    <p className="text-xs text-[#484455] dark:text-[#A39BAC] line-clamp-3 mb-3 leading-relaxed">
                      {getPlainTextPreview(note.content)}
                    </p>
                  )}

                  {/* Checklist preview if present */}
                  {note.checklist && note.checklist.length > 0 && (
                    <div className="mb-3 space-y-1">
                      {note.checklist.slice(0, 2).map((item) => (
                        <div
                          key={item.id}
                          className="flex items-center gap-1.5 text-[11px] text-[#797487] dark:text-[#A39BAC]"
                        >
                          <CheckCircle2
                            className={`w-3 h-3 ${
                              item.completed ? 'text-[#5925DC] dark:text-[#C296FD]' : 'opacity-40'
                            }`}
                          />
                          <span className={item.completed ? 'line-through opacity-70' : ''}>
                            {item.text}
                          </span>
                        </div>
                      ))}
                      {note.checklist.length > 2 && (
                        <span className="text-[10px] text-[#797487] dark:text-[#A39BAC]">
                          +{note.checklist.length - 2} more checklist items
                        </span>
                      )}
                    </div>
                  )}

                  {/* Tags */}
                  {note.tags && note.tags.length > 0 && (
                    <div className="flex flex-wrap gap-1 mb-3">
                      {note.tags.map((t) => (
                        <span
                          key={t}
                          className="text-[9px] font-medium px-1.5 py-0.5 rounded bg-[#FAF8FB] dark:bg-[#120E17] text-[#484455] dark:text-[#A39BAC] border border-[#EADBFC]/60 dark:border-[#322742]"
                        >
                          #{t}
                        </span>
                      ))}
                    </div>
                  )}
                </div>

                {/* Bottom Row: Actions & Meta */}
                <div className="pt-3 border-t border-[#EADBFC]/60 dark:border-[#322742]/60 mt-2 flex items-center justify-between gap-2">
                  <div className="flex items-center gap-1 text-[10px] text-[#797487] dark:text-[#A39BAC]">
                    <Calendar className="w-3 h-3" />
                    <span>Deleted {formattedDate}</span>
                  </div>

                  <div className="flex items-center gap-1.5">
                    {/* Restore Button */}
                    <button
                      onClick={() => onRestoreNote(note.id)}
                      className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-xl bg-[#F4EBFF] hover:bg-[#EADBFC] dark:bg-[#2B1E3E] dark:hover:bg-[#3B2956] text-[#5925DC] dark:text-[#C296FD] font-semibold text-[11px] border border-[#EADBFC] dark:border-[#4B3766] transition cursor-pointer active:scale-95"
                      title="Restore note to vault"
                    >
                      <RotateCcw className="w-3 h-3" />
                      <span>Restore</span>
                    </button>

                    {/* Permanent Delete Button */}
                    <button
                      onClick={() => setItemToDeleteConfirm(note.id)}
                      className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-xl bg-rose-50 hover:bg-rose-100 dark:bg-rose-950/40 dark:hover:bg-rose-900/50 text-rose-600 dark:text-rose-400 font-semibold text-[11px] border border-rose-200 dark:border-rose-800 transition cursor-pointer active:scale-95"
                      title="Delete permanently"
                    >
                      <Trash2 className="w-3 h-3" />
                      <span>Delete</span>
                    </button>
                  </div>
                </div>
              </motion.div>
            );
          })}
        </motion.div>
      )}
    </div>
  );
};

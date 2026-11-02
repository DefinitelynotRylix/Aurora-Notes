import React, { useState, useEffect, useMemo } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { Note, ThemeOption, ActiveTab } from './types';
import { LiquidHeader } from './components/LiquidHeader';
import { CategoryFilterBar } from './components/CategoryFilterBar';
import { NoteCard } from './components/NoteCard';
import { LiquidNavbar } from './components/LiquidNavbar';
import { TrashView } from './components/TrashView';
import { NoteEditorModal } from './components/NoteEditorModal';
import { SettingsPage } from './components/SettingsPage';
import { WelcomeTourModal } from './components/WelcomeTourModal';
import { isNativeLockEnabled, verifyWithAndroidNativeLock } from './utils/nativeAuth';
import { logger } from './utils/logger';
import { FileText, Plus, Fingerprint, Smartphone } from 'lucide-react';

const STORAGE_NOTES_KEY = 'aurora_local_notes_v1';
const STORAGE_CATEGORIES_KEY = 'aurora_custom_categories_v1';
const STORAGE_THEME_KEY = 'aurora_theme_v1';
const STORAGE_WELCOME_KEY = 'aurora_notes_welcome_seen_v1';

export default function App() {
  // Theme state
  const [theme, setTheme] = useState<ThemeOption>(() => {
    try {
      const stored = localStorage.getItem(STORAGE_THEME_KEY) as ThemeOption;
      if (stored && ['light', 'dark', 'system'].includes(stored)) {
        return stored;
      }
    } catch {
      // fallback
    }
    return 'system';
  });

  // Android Native Lock State
  const [isNativeLocked, setIsNativeLocked] = useState(() => isNativeLockEnabled());
  const [nativeAuthError, setNativeAuthError] = useState<string | null>(null);

  // Active navigation tab
  const [activeTab, setActiveTab] = useState<ActiveTab>('notes');

  // Custom user pins (categories)
  const [customCategories, setCustomCategories] = useState<string[]>(() => {
    try {
      const stored = localStorage.getItem(STORAGE_CATEGORIES_KEY);
      if (stored) return JSON.parse(stored);
    } catch {
      // fallback
    }
    return ['Work', 'Personal', 'Ideas'];
  });

  // Notes state - starts completely fresh and empty
  const [notes, setNotes] = useState<Note[]>(() => {
    try {
      const stored = localStorage.getItem(STORAGE_NOTES_KEY);
      if (stored) {
        return JSON.parse(stored);
      }
    } catch (e) {
      console.warn('Could not read notes from localStorage', e);
    }
    return [];
  });

  // Active vs Trashed note partitions
  const activeNotes = useMemo(() => notes.filter((n) => !n.isDeleted), [notes]);
  const trashedNotes = useMemo(() => notes.filter((n) => n.isDeleted), [notes]);

  // UI States
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string>('All');
  const [isGridView, setIsGridView] = useState(true);
  const [activeNote, setActiveNote] = useState<Note | null>(null);

  // Track the most recently pinned note id to animate only its move to top
  const [lastPinnedId, setLastPinnedId] = useState<string | null>(null);

  // Welcome tour modal on first launch
  const [isWelcomeOpen, setIsWelcomeOpen] = useState(() => {
    try {
      return localStorage.getItem(STORAGE_WELCOME_KEY) !== 'true';
    } catch {
      return false;
    }
  });

  const handleCloseWelcome = () => {
    setIsWelcomeOpen(false);
    try {
      localStorage.setItem(STORAGE_WELCOME_KEY, 'true');
    } catch (e) {
      console.warn('Failed to save welcome state', e);
    }
  };

  // Apply theme to document element
  useEffect(() => {
    const root = document.documentElement;
    const applyTheme = () => {
      if (theme === 'dark') {
        root.classList.add('dark');
      } else if (theme === 'light') {
        root.classList.remove('dark');
      } else {
        const isDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
        if (isDark) {
          root.classList.add('dark');
        } else {
          root.classList.remove('dark');
        }
      }
    };

    applyTheme();

    if (theme === 'system') {
      const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
      const listener = () => applyTheme();
      mediaQuery.addEventListener('change', listener);
      return () => mediaQuery.removeEventListener('change', listener);
    }
  }, [theme]);

  // Initialize global error listeners
  useEffect(() => {
    logger.setupGlobalErrorListeners();
  }, []);

  // Attempt Android native unlock automatically if enabled
  useEffect(() => {
    if (isNativeLocked) {
      handleAndroidNativeUnlock();
    }
  }, []);

  const handleAndroidNativeUnlock = async () => {
    setNativeAuthError(null);
    const result = await verifyWithAndroidNativeLock();
    if (result.success) {
      setIsNativeLocked(false);
      logger.security('AUTH', 'Android native authentication successful');
    } else {
      setNativeAuthError(result.error || 'Android native authentication required.');
      logger.warn('AUTH', 'Android native authentication rejected or canceled', result.error);
    }
  };

  const handleSelectTheme = (newTheme: ThemeOption) => {
    setTheme(newTheme);
    try {
      localStorage.setItem(STORAGE_THEME_KEY, newTheme);
    } catch {
      // ignore
    }
  };



  // Save notes locally
  const persistNotes = (updated: Note[]) => {
    setNotes(updated);
    try {
      localStorage.setItem(STORAGE_NOTES_KEY, JSON.stringify(updated));
    } catch (e) {
      console.error('Failed to save notes locally', e);
      logger.error('STORAGE', 'Failed to save notes to localStorage', String(e));
    }
  };

  // Custom Category Pins Management
  const handleAddCategory = (newCat: string) => {
    if (!customCategories.includes(newCat)) {
      const updated = [...customCategories, newCat];
      setCustomCategories(updated);
      try {
        localStorage.setItem(STORAGE_CATEGORIES_KEY, JSON.stringify(updated));
      } catch {
        // ignore
      }
    }
  };

  const handleRemoveCategory = (catToRemove: string) => {
    const updated = customCategories.filter((c) => c !== catToRemove);
    setCustomCategories(updated);
    try {
      localStorage.setItem(STORAGE_CATEGORIES_KEY, JSON.stringify(updated));
    } catch {
      // ignore
    }
  };

  // Note CRUD operations
  const handleCreateNote = () => {
    const newNote: Note = {
      id: 'note-' + Date.now() + '-' + Math.random().toString(36).substring(2, 6),
      title: '',
      content: '',
      checklist: [],
      tags: [],
      category: selectedCategory !== 'All' && selectedCategory !== 'Pinned' ? selectedCategory : 'General',
      isPinned: activeTab === 'pinned',
      createdAt: Date.now(),
      updatedAt: Date.now(),
    };

    setActiveNote(newNote);
  };

  const handleSaveActiveNote = (savedNote: Note) => {
    const exists = notes.some((n) => n.id === savedNote.id);
    let updatedNotes: Note[];
    if (exists) {
      updatedNotes = notes.map((n) => (n.id === savedNote.id ? savedNote : n));
    } else {
      updatedNotes = [savedNote, ...notes];
    }
    persistNotes(updatedNotes);
  };

  // Soft Delete: moves note to Trash
  const handleDeleteNote = (id: string, e?: React.MouseEvent | React.TouchEvent | null) => {
    if (e && 'stopPropagation' in e && typeof e.stopPropagation === 'function') {
      e.stopPropagation();
    }
    const updated = notes.map((n) =>
      n.id === id ? { ...n, isDeleted: true, deletedAt: Date.now() } : n
    );
    persistNotes(updated);
  };

  // Restore Note from Trash
  const handleRestoreNote = (id: string) => {
    const updated = notes.map((n) =>
      n.id === id ? { ...n, isDeleted: false, deletedAt: undefined } : n
    );
    persistNotes(updated);
  };

  // Permanent Delete for single note in Trash
  const handlePermanentDelete = (id: string) => {
    const updated = notes.filter((n) => n.id !== id);
    persistNotes(updated);
  };

  // Empty all trashed notes
  const handleEmptyTrash = () => {
    const updated = notes.filter((n) => !n.isDeleted);
    persistNotes(updated);
  };

  /**
   * Fixed Pinning Logic:
   * "If a note isn't on top and the user pins it only then move it to the first column and every other pinned or not pinned note under it but always keep the pinned notes on top.
   * If the pinned notes are next to each other don't give them an animation."
   */
  const handleTogglePin = (id: string, e: React.MouseEvent) => {
    e.stopPropagation();

    const targetIndex = notes.findIndex((n) => n.id === id);
    if (targetIndex === -1) return;

    const target = notes[targetIndex];
    const willBePinned = !target.isPinned;
    const remaining = notes.filter((n) => n.id !== id);

    let updated: Note[];

    if (willBePinned) {
      setLastPinnedId(id);
      // Place at index 0 (top of the pinned notes), followed by other pinned notes, followed by unpinned
      const otherPinned = remaining.filter((n) => n.isPinned);
      const unpinned = remaining.filter((n) => !n.isPinned);
      updated = [{ ...target, isPinned: true }, ...otherPinned, ...unpinned];
    } else {
      setLastPinnedId(null);
      // Unpinning: place at top of unpinned list
      const pinned = remaining.filter((n) => n.isPinned);
      const unpinned = remaining.filter((n) => !n.isPinned);
      updated = [...pinned, { ...target, isPinned: false }, ...unpinned];
    }

    persistNotes(updated);
  };

  const handleToggleCheckItem = (noteId: string, itemId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    const updated = notes.map((n) => {
      if (n.id === noteId && n.checklist) {
        const updatedChecklist = n.checklist.map((item) =>
          item.id === itemId ? { ...item, completed: !item.completed } : item
        );
        return { ...n, checklist: updatedChecklist, updatedAt: Date.now() };
      }
      return n;
    });
    persistNotes(updated);
  };

  // Filter & Search Logic across active notes only
  const filteredNotes = useMemo(() => {
    let result = [...activeNotes];

    // Navigation tab filter
    if (activeTab === 'pinned') {
      result = result.filter((n) => n.isPinned);
    }

    // Search query
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      result = result.filter(
        (n) =>
          n.title.toLowerCase().includes(q) ||
          n.content.toLowerCase().includes(q) ||
          n.tags?.some((t) => t.toLowerCase().includes(q)) ||
          n.checklist?.some((c) => c.text.toLowerCase().includes(q))
      );
    }

    // Category filter: "All", "Pinned", or custom categories
    if (selectedCategory === 'Pinned') {
      result = result.filter((n) => n.isPinned);
    } else if (selectedCategory !== 'All') {
      result = result.filter((n) => n.category.toLowerCase() === selectedCategory.toLowerCase());
    }

    // Ensure pinned notes are ALWAYS strictly at the top, preserving user's order
    const pinned = result.filter((n) => n.isPinned);
    const unpinned = result.filter((n) => !n.isPinned);
    return [...pinned, ...unpinned];
  }, [activeNotes, searchQuery, selectedCategory, activeTab]);

  // Counts for pills
  const categoryCounts = useMemo(() => {
    const counts: Record<string, number> = {
      All: activeNotes.length,
      Pinned: activeNotes.filter((n) => n.isPinned).length,
    };
    customCategories.forEach((cat) => {
      counts[cat] = activeNotes.filter((n) => n.category.toLowerCase() === cat.toLowerCase()).length;
    });
    return counts;
  }, [activeNotes, customCategories]);

  // If locked via Android native screen lock
  if (isNativeLocked) {
    return (
      <div className="min-h-screen bg-[#FAF8FB] dark:bg-[#120E17] text-[#1E1A24] dark:text-[#F7EEFC] flex items-center justify-center p-4">
        <div className="glass-floating max-w-sm w-full p-8 rounded-3xl text-center space-y-4 border border-[#EADBFC] dark:border-[#322742] bg-white/90 dark:bg-[#1B1524]/90 shadow-2xl">
          <div className="w-16 h-16 rounded-2xl bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] flex items-center justify-center mx-auto shadow-md">
            <Fingerprint className="w-9 h-9" />
          </div>
          <div>
            <h2 className="text-xl font-bold text-[#1E1A24] dark:text-[#F7EEFC]">
              Iris Locked
            </h2>
            <p className="text-xs text-[#797487] dark:text-[#A39BAC] mt-1">
              Verify with Android native fingerprint, face, or device PIN
            </p>
          </div>

          {nativeAuthError && (
            <div className="p-2.5 rounded-xl bg-rose-50 dark:bg-rose-950/40 text-rose-700 dark:text-rose-300 text-xs font-medium">
              {nativeAuthError}
            </div>
          )}

          <button
            type="button"
            onClick={handleAndroidNativeUnlock}
            className="w-full py-3 rounded-2xl bg-gradient-to-r from-[#5925DC] to-[#9B51E0] dark:from-[#9D5AF5] dark:to-[#C296FD] text-white font-semibold text-sm shadow-md flex items-center justify-center gap-2 active:scale-95 transition-all cursor-pointer"
          >
            <Smartphone className="w-4 h-4" />
            <span>Unlock with Android Lock</span>
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#FAF8FB] dark:bg-[#120E17] text-[#1E1A24] dark:text-[#F7EEFC] flex flex-col selection:bg-[#ccbeff] selection:text-[#1e0060] pb-24 transition-colors duration-300">
      {/* Liquid Glass Header */}
      <LiquidHeader
        searchQuery={searchQuery}
        onSearchChange={setSearchQuery}
        isGridView={isGridView}
        onToggleGridView={() => setIsGridView(!isGridView)}
        noteCount={activeNotes.length}
        onCreateNote={handleCreateNote}
      />

      {/* Main Workspace: Notes / Trash / Settings */}
      <main className="max-w-6xl w-full mx-auto px-3 sm:px-6 pt-4 flex-1">
        {activeTab === 'settings' ? (
          <SettingsPage
            currentTheme={theme}
            onSelectTheme={handleSelectTheme}
            customCategories={customCategories}
            onAddCategory={handleAddCategory}
            onRemoveCategory={handleRemoveCategory}
            notes={notes}
            onOpenWelcomeTour={() => setIsWelcomeOpen(true)}
            onWipeData={() => {
              localStorage.removeItem(STORAGE_NOTES_KEY);
              setNotes([]);
              logger.security('STORAGE', 'User wiped local storage notes');
            }}
          />
        ) : activeTab === 'trash' ? (
          <TrashView
            trashedNotes={trashedNotes}
            searchQuery={searchQuery}
            onSearchChange={setSearchQuery}
            isGridView={isGridView}
            onRestoreNote={handleRestoreNote}
            onPermanentDelete={handlePermanentDelete}
            onEmptyTrash={handleEmptyTrash}
          />
        ) : (
          <>
            {/* Category Filter Bar */}
            <div className="mb-4">
              <CategoryFilterBar
                currentCategory={selectedCategory}
                onSelectCategory={setSelectedCategory}
                customCategories={customCategories}
                onAddCategory={handleAddCategory}
                onRemoveCategory={handleRemoveCategory}
                counts={categoryCounts}
              />
            </div>

            {/* Notes Grid or Empty State */}
            {filteredNotes.length === 0 ? (
              <div className="py-16 text-center flex flex-col items-center justify-center animate-glide-in">
                <div className="w-14 h-14 rounded-2xl bg-[#F4EBFF] dark:bg-[#2B1E3E] border border-[#EADBFC] dark:border-[#322742] flex items-center justify-center text-[#5925DC] dark:text-[#C296FD] mb-3 shadow-sm">
                  <FileText className="w-7 h-7 stroke-[1.5]" />
                </div>
                <h3 className="font-bold text-base text-[#1E1A24] dark:text-[#F7EEFC] mb-1">
                  {searchQuery ? 'No matching notes found' : 'No notes has been created.'}
                </h3>
                <p className="text-xs text-[#797487] dark:text-[#A39BAC] max-w-xs mb-5">
                  {searchQuery
                    ? 'Try a different keyword or reset the filter.'
                    : 'Tap the circular button in the bottom right corner to write a note.'}
                </p>
                <button
                  onClick={handleCreateNote}
                  className="px-4 py-2 rounded-xl bg-gradient-to-r from-[#5925DC] to-[#9B51E0] dark:from-[#9D5AF5] dark:to-[#C296FD] text-white text-xs font-semibold shadow-xs flex items-center gap-1.5 transition-all hover:scale-[1.02] active:scale-95 cursor-pointer"
                >
                  <Plus className="w-4 h-4" />
                  <span>Create Note</span>
                </button>
              </div>
            ) : (
              <div
                className={`grid gap-2.5 sm:gap-4 ${
                  isGridView
                    ? 'grid-cols-2 sm:grid-cols-3 lg:grid-cols-4'
                    : 'grid-cols-1 max-w-2xl mx-auto'
                }`}
              >
                {filteredNotes.map((note) => {
                  // If note was just pinned, animate its entrance to the top column.
                  // If notes are adjacent pinned notes, don't trigger jarring movement animations.
                  const animateMovement = note.id === lastPinnedId;

                  const handleDeleteTagAndDetach = (tagToDelete: string) => {
                    const updatedNotes = notes.map((n) => {
                      if (n.tags?.includes(tagToDelete)) {
                        return {
                          ...n,
                          tags: n.tags.filter((t) => t !== tagToDelete),
                        };
                      }
                      return n;
                    });
                    persistNotes(updatedNotes);
                    logger.security('TAGS', `Deleted tag "${tagToDelete}" from local notes`);
                  };

                  return (
                    <NoteCard
                      key={note.id}
                      note={note}
                      isGridView={isGridView}
                      animateMovement={animateMovement}
                      onSelect={(selected) => setActiveNote(selected)}
                      onTogglePin={handleTogglePin}
                      onDelete={handleDeleteNote}
                      onToggleCheckItem={handleToggleCheckItem}
                      onDeleteTag={handleDeleteTagAndDetach}
                    />
                  );
                })}
              </div>
            )}
          </>
        )}
      </main>

      {/* Floating Circular UI Element in the Down Right Corner - safely elevated above bottom bar on phones */}
      <motion.button
        type="button"
        onClick={handleCreateNote}
        whileHover={{ scale: 1.1 }}
        whileTap={{ scale: 0.92 }}
        aria-label="Create note"
        className="fixed bottom-24 right-4 sm:bottom-8 sm:right-8 z-40 w-14 h-14 rounded-full bg-gradient-to-tr from-[#5925DC] via-[#7B35E8] to-[#9B51E0] dark:from-[#7839EE] dark:via-[#9D5AF5] dark:to-[#C296FD] text-white shadow-[0_8px_24px_rgba(89,37,220,0.45)] dark:shadow-[0_8px_24px_rgba(157,90,245,0.45)] border border-white/30 flex items-center justify-center cursor-pointer hover:shadow-[0_12px_32px_rgba(89,37,220,0.6)] transition-all group"
      >
        <Plus className="w-7 h-7 stroke-[2.5] transition-transform duration-300 group-hover:rotate-90" />
      </motion.button>

      {/* Liquid Navigation Bar */}
      <LiquidNavbar
        activeTab={activeTab}
        onChangeTab={setActiveTab}
        pinnedCount={activeNotes.filter((n) => n.isPinned).length}
        trashCount={trashedNotes.length}
      />

      {/* Note Editor Modal with smooth glide-in */}
      <AnimatePresence>
        {activeNote && (
          <NoteEditorModal
            note={activeNote}
            customCategories={customCategories}
            onSave={handleSaveActiveNote}
            onClose={() => setActiveNote(null)}
          />
        )}
      </AnimatePresence>

      {/* Welcome Tour Modal on First Launch */}
      <WelcomeTourModal
        isOpen={isWelcomeOpen}
        onClose={handleCloseWelcome}
      />
    </div>
  );
}

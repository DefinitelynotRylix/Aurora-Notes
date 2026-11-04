import React, { useState, useEffect, useMemo, useRef } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { Note, ThemeOption, ActiveTab } from './types';
import { LiquidHeader } from './components/LiquidHeader';
import { CategoryFilterBar } from './components/CategoryFilterBar';
import { NoteCard } from './components/NoteCard';
import { MorphingNotesGrid } from './components/MorphingNotesGrid';
import { LiquidNavbar } from './components/LiquidNavbar';
import { TrashView } from './components/TrashView';
import { NoteEditorModal } from './components/NoteEditorModal';
import { SettingsPage } from './components/SettingsPage';
import { WelcomeTourModal } from './components/WelcomeTourModal';
import { WhatsNewModal, CURRENT_WHATS_NEW_VERSION } from './components/WhatsNewModal';
import { ShareNoteModal } from './components/ShareNoteModal';
import { isNativeLockEnabled, verifyWithAndroidNativeLock } from './utils/nativeAuth';
import { logger } from './utils/logger';
import {
  PRIVATE_EXTERNAL_FILE_PREFIX,
  ENC_STORAGE_PREFIX,
  encryptStoragePayload,
  decryptStoragePayload,
  cleanupOrphanedPrivateStorageEntries,
} from './utils/editorRules';
import { FileText, Plus, Fingerprint, Smartphone, Share2, Trash2, X, CheckSquare, Archive, ArchiveRestore, Edit3, Tag, Pin, Check } from 'lucide-react';

const STORAGE_NOTES_KEY = 'aurora_local_notes_v1';
const STORAGE_CATEGORIES_KEY = 'aurora_custom_categories_v1';
const STORAGE_THEME_KEY = 'aurora_theme_v1';
const STORAGE_WELCOME_KEY = 'aurora_notes_welcome_seen_v1';
const STORAGE_WHATS_NEW_KEY = 'aurora_whats_new_seen_version_v1';
const STORAGE_HAPTICS_KEY = 'aurora_haptics_enabled_v1';
const STORAGE_DB_ENCRYPTED_KEY = 'aurora_room_db_encrypted_v1';
const DEVICE_PRIVATE_EXTERNAL_DIR =
  '/storage/emulated/0/Android/data/com.aurora.notes/files/aurora_private_notes';

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

  // In-app Haptic Feedback State (enabled by default)
  const [isHapticsEnabled, setIsHapticsEnabled] = useState<boolean>(() => {
    try {
      const saved = localStorage.getItem(STORAGE_HAPTICS_KEY);
      return saved === null ? true : saved === 'true';
    } catch {
      return true;
    }
  });

  const handleToggleHaptics = (enabled: boolean) => {
    setIsHapticsEnabled(enabled);
    try {
      localStorage.setItem(STORAGE_HAPTICS_KEY, String(enabled));
    } catch {
      // ignore
    }
  };

  // Active navigation tab & directional slide tracking
  const [activeTab, setActiveTab] = useState<ActiveTab>('notes');
  const [slideDirection, setSlideDirection] = useState<number>(1);

  const TAB_ORDER: Record<ActiveTab, number> = {
    notes: 0,
    archive: 1,
    trash: 2,
    settings: 3,
  };

  const lastTabSwitchRef = useRef<number>(0);
  const handleSelectNavTab = (nextTab: ActiveTab) => {
    if (nextTab === activeTab) return;
    const now = performance.now();
    if (now - lastTabSwitchRef.current < 480) return;
    lastTabSwitchRef.current = now;
    setSlideDirection(TAB_ORDER[nextTab] >= TAB_ORDER[activeTab] ? 1 : -1);
    setSelectedNoteIds([]);
    setActiveTab(nextTab);
  };

  // Custom user tags - starts completely empty in the base app
  const [customCategories, setCustomCategories] = useState<string[]>(() => {
    try {
      const stored = localStorage.getItem('aurora_user_tags_v2');
      if (stored) return JSON.parse(stored);
    } catch {
      // fallback
    }
    return [];
  });

  // Mandatory Always-On Room Database & Private External Storage Encryption
  const isDbEncryptionEnabled = true;
  const [lastCleanupSummary, setLastCleanupSummary] = useState<string | null>(null);

  // Notes state - transparently decrypts Room DB storage
  const [notes, setNotes] = useState<Note[]>(() => {
    try {
      localStorage.setItem(STORAGE_DB_ENCRYPTED_KEY, 'true');
      const stored = localStorage.getItem(STORAGE_NOTES_KEY);
      if (stored) {
        const plainJson = stored.startsWith(ENC_STORAGE_PREFIX)
          ? decryptStoragePayload(stored)
          : stored;
        return JSON.parse(plainJson);
      }
    } catch (e) {
      console.warn('Could not read notes from localStorage', e);
    }
    return [];
  });

  // Active, Archived, and Trashed note partitions
  const activeNotes = useMemo(() => notes.filter((n) => !n.isDeleted && !n.isArchived), [notes]);
  const archivedNotes = useMemo(() => notes.filter((n) => !n.isDeleted && n.isArchived), [notes]);
  const trashedNotes = useMemo(() => notes.filter((n) => n.isDeleted), [notes]);

  // UI States
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string>('All');
  const [isGridView, setIsGridView] = useState(true);
  const [activeNote, setActiveNote] = useState<Note | null>(null);
  const [selectedNoteIds, setSelectedNoteIds] = useState<string[]>([]);
  const [notesToShare, setNotesToShare] = useState<Note[]>([]);
  const [showBatchDeleteConfirm, setShowBatchDeleteConfirm] = useState(false);

  // Edit Note Details Modal state (opened when long-pressing notes in the main menu)
  const [editingDetailsNote, setEditingDetailsNote] = useState<Note | null>(null);
  const [detailsTitle, setDetailsTitle] = useState('');
  const [detailsCategory, setDetailsCategory] = useState('General');
  const [detailsTags, setDetailsTags] = useState<string[]>([]);
  const [detailsNewTagInput, setDetailsNewTagInput] = useState('');
  const [detailsIsPinned, setDetailsIsPinned] = useState(false);

  const openEditNoteDetailsModal = (targetNote: Note) => {
    setEditingDetailsNote(targetNote);
    setDetailsTitle(targetNote.title || '');
    setDetailsCategory(targetNote.category || targetNote.tags?.[0] || 'General');
    setDetailsTags(targetNote.tags || []);
    setDetailsNewTagInput('');
    setDetailsIsPinned(Boolean(targetNote.isPinned));
  };

  const handleAddDetailsTag = (e?: React.KeyboardEvent | React.MouseEvent) => {
    if (e) e.preventDefault();
    const clean = detailsNewTagInput.trim().replace(/^#/, '').trim();
    if (clean && !detailsTags.some((t) => t.toLowerCase() === clean.toLowerCase())) {
      setDetailsTags((prev) => [...prev, clean]);
      handleAddCategory(clean);
      setDetailsNewTagInput('');
    }
  };

  const handleRemoveDetailsTag = (tagToRemove: string) => {
    setDetailsTags((prev) => prev.filter((t) => t !== tagToRemove));
  };

  const handleSaveEditedNoteDetails = () => {
    if (!editingDetailsNote) return;
    const cleanTitle = detailsTitle.trim() || 'Untitled Note';
    const updatedNote: Note = {
      ...editingDetailsNote,
      title: cleanTitle,
      category: detailsCategory === 'General' && detailsTags.length > 0 ? detailsTags[0] : detailsCategory,
      tags: detailsTags,
      isPinned: detailsIsPinned,
      updatedAt: Date.now(),
    };
    detailsTags.forEach((t) => handleAddCategory(t));
    handleSaveActiveNote(updatedNote);
    setEditingDetailsNote(null);
    setSelectedNoteIds([]);
  };

  // Ref & origin coordinates for the circular + Create Note button reveal animation
  const fabButtonRef = useRef<HTMLButtonElement | null>(null);
  const [editorRevealOrigin, setEditorRevealOrigin] = useState<{
    x: number;
    y: number;
    radius: number;
  } | null>(null);

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

  // What's New introduction modal shown ONCE after the first restart of a major update
  const [isWhatsNewOpen, setIsWhatsNewOpen] = useState(() => {
    try {
      const seenVersion = localStorage.getItem(STORAGE_WHATS_NEW_KEY);
      if (seenVersion !== CURRENT_WHATS_NEW_VERSION) {
        localStorage.setItem(STORAGE_WHATS_NEW_KEY, CURRENT_WHATS_NEW_VERSION);
        return true;
      }
      return false;
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

  const handleCloseWhatsNew = () => {
    setIsWhatsNewOpen(false);
    try {
      localStorage.setItem(STORAGE_WHATS_NEW_KEY, CURRENT_WHATS_NEW_VERSION);
    } catch (e) {
      console.warn('Failed to save whats new state', e);
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



  // Sync individual note files to private external storage & automatically clean up orphaned/temp files
  const syncNotesToPrivateExternalStorage = (currentNotes: Note[], encryptFiles: boolean) => {
    try {
      const nonDeleted = currentNotes.filter((n) => !n.isDeleted);
      const validIds = nonDeleted.map((n) => n.id);

      nonDeleted.forEach((n) => {
        const safeId = n.id.replace(/[^a-zA-Z0-9_-]/g, '_');
        const key = `${PRIVATE_EXTERNAL_FILE_PREFIX}${safeId}.json`;
        const rawJson = JSON.stringify(n);
        const diskPayload = encryptFiles ? encryptStoragePayload(rawJson) : rawJson;
        localStorage.setItem(key, diskPayload);
      });

      const snapshot: Record<string, string> = {};
      for (let i = 0; i < localStorage.length; i++) {
        const k = localStorage.key(i);
        if (k) {
          snapshot[k] = localStorage.getItem(k) || '';
        }
      }
      const report = cleanupOrphanedPrivateStorageEntries(snapshot, validIds);
      report.cleanedKeys.forEach((k) => localStorage.removeItem(k));
      const totalRemoved = report.orphanedRemoved + report.tempRemoved;
      if (totalRemoved > 0) {
        setLastCleanupSummary(
          `Auto-cleaned ${report.orphanedRemoved} orphaned file(s) & ${report.tempRemoved} temp file(s) (${report.freedBytes} B freed)`
        );
      }
      return report;
    } catch {
      return {
        cleanedKeys: [],
        orphanedRemoved: 0,
        tempRemoved: 0,
        remainingFileCount: 0,
        freedBytes: 0,
      };
    }
  };

  // Save notes locally (Always-Encrypted Room DB + Private External Storage mirror + Automatic Orphan Cleanup)
  const persistNotes = (updated: Note[]) => {
    setNotes(updated);
    try {
      const serialized = JSON.stringify(updated);
      const payload = encryptStoragePayload(serialized);
      localStorage.setItem(STORAGE_NOTES_KEY, payload);
      syncNotesToPrivateExternalStorage(updated, true);
    } catch (e) {
      console.error('Failed to save notes locally', e);
      logger.error('STORAGE', 'Failed to save notes to localStorage', String(e));
    }
  };

  const handleCleanUpOrphanedFiles = () => {
    syncNotesToPrivateExternalStorage(notes, true);
  };

  // Run automatic private storage encryption sync & orphan/temp cleanup on startup, visibility resume, and periodic interval
  useEffect(() => {
    try {
      const serialized = JSON.stringify(notes);
      localStorage.setItem(STORAGE_NOTES_KEY, encryptStoragePayload(serialized));
    } catch {
      // ignore
    }
    syncNotesToPrivateExternalStorage(notes, true);

    const onVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        syncNotesToPrivateExternalStorage(notes, true);
      }
    };
    document.addEventListener('visibilitychange', onVisibilityChange);
    const intervalId = window.setInterval(() => {
      syncNotesToPrivateExternalStorage(notes, true);
    }, 3 * 60 * 1000);

    return () => {
      document.removeEventListener('visibilitychange', onVisibilityChange);
      window.clearInterval(intervalId);
    };
  }, [notes]);

  const storageStats = useMemo(() => {
    const nonDeleted = notes.filter((n) => !n.isDeleted);
    let totalBytes = 0;
    try {
      nonDeleted.forEach((n) => {
        const safeId = n.id.replace(/[^a-zA-Z0-9_-]/g, '_');
        const key = `${PRIVATE_EXTERNAL_FILE_PREFIX}${safeId}.json`;
        const val = localStorage.getItem(key) || JSON.stringify(n);
        totalBytes += val.length;
      });
    } catch {
      // ignore
    }
    return {
      directoryPath: DEVICE_PRIVATE_EXTERNAL_DIR,
      syncedFileCount: nonDeleted.length,
      orphanedFileCount: 0,
      totalBytes,
    };
  }, [notes, isDbEncryptionEnabled]);

  // User Tags Management
  const handleAddCategory = (newCat: string) => {
    const clean = newCat.trim().replace(/^#/, '').trim();
    if (clean && clean.toLowerCase() !== 'all' && !customCategories.includes(clean)) {
      const updated = [...customCategories, clean];
      setCustomCategories(updated);
      try {
        localStorage.setItem('aurora_user_tags_v2', JSON.stringify(updated));
      } catch {
        // ignore
      }
    }
  };

  const handleRemoveCategory = (catToRemove: string) => {
    const updated = customCategories.filter((c) => c !== catToRemove);
    setCustomCategories(updated);
    try {
      localStorage.setItem('aurora_user_tags_v2', JSON.stringify(updated));
    } catch {
      // ignore
    }
    if (selectedCategory.toLowerCase() === catToRemove.toLowerCase()) {
      setSelectedCategory('All');
    }
    // Return any notes under the deleted tag back to All
    const updatedNotes = notes.map((n) => {
      const hadTag = n.tags?.some((t) => t.toLowerCase() === catToRemove.toLowerCase());
      const hadCat = n.category?.toLowerCase() === catToRemove.toLowerCase();
      if (hadTag || hadCat) {
        const remainingTags = (n.tags || []).filter(
          (t) => t.toLowerCase() !== catToRemove.toLowerCase()
        );
        return {
          ...n,
          tags: remainingTags,
          category: hadCat ? remainingTags[0] || '' : n.category,
        };
      }
      return n;
    });
    persistNotes(updatedNotes);
  };

  const captureFabOrigin = (e?: React.MouseEvent) => {
    if (fabButtonRef.current) {
      const rect = fabButtonRef.current.getBoundingClientRect();
      if (rect.width > 0 && rect.height > 0) {
        setEditorRevealOrigin({
          x: Math.round(rect.left + rect.width / 2),
          y: Math.round(rect.top + rect.height / 2),
          radius: Math.round(rect.width / 2),
        });
        return;
      }
    }
    if (e && e.currentTarget) {
      const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
      if (rect.width > 0 && rect.height > 0) {
        setEditorRevealOrigin({
          x: Math.round(rect.left + rect.width / 2),
          y: Math.round(rect.top + rect.height / 2),
          radius: Math.round(Math.min(rect.width, rect.height) / 2),
        });
        return;
      }
    }
    setEditorRevealOrigin({
      x: typeof window !== 'undefined' ? window.innerWidth - 44 : 320,
      y: typeof window !== 'undefined' ? window.innerHeight - 120 : 640,
      radius: 28,
    });
  };

  // Note CRUD operations
  const handleCreateNote = (e?: React.MouseEvent) => {
    captureFabOrigin(e);
    const initialTag = selectedCategory !== 'All' ? selectedCategory : '';
    const newNote: Note = {
      id: 'note-' + Date.now() + '-' + Math.random().toString(36).substring(2, 6),
      title: '',
      content: '',
      checklist: [],
      tags: initialTag ? [initialTag] : [],
      category: initialTag,
      isPinned: false,
      isArchived: false,
      createdAt: Date.now(),
      updatedAt: Date.now(),
    };

    setActiveNote(newNote);
  };

  const handleSaveActiveNote = (savedNote: Note) => {
    if (savedNote.isArchived) return;
    const exists = notes.some((n) => n.id === savedNote.id);
    let updatedNotes: Note[];
    if (exists) {
      updatedNotes = notes.map((n) => (n.id === savedNote.id ? savedNote : n));
    } else {
      updatedNotes = [savedNote, ...notes];
    }
    persistNotes(updatedNotes);
  };

  // Archive single note (makes it read-only and moves to Archive menu)
  const handleArchiveNote = (id: string, e?: React.MouseEvent) => {
    if (e && typeof e.stopPropagation === 'function') {
      e.stopPropagation();
    }
    const updated = notes.map((n) =>
      n.id === id ? { ...n, isArchived: true, isPinned: false } : n
    );
    persistNotes(updated);
    setSelectedNoteIds((prev) => prev.filter((item) => item !== id));
    logger.security('ARCHIVE', `Archived note (ID: ${id})`);
  };

  // Batch Archive selected notes
  const handleArchiveNotesBatch = (ids: string[]) => {
    const idSet = new Set(ids);
    const updated = notes.map((n) =>
      idSet.has(n.id) ? { ...n, isArchived: true, isPinned: false } : n
    );
    persistNotes(updated);
    setSelectedNoteIds([]);
    logger.security('ARCHIVE', `Batch archived ${ids.length} selected notes`);
  };

  // Restore (Unarchive) single note back to Notes menu
  const handleUnarchiveNote = (id: string, e?: React.MouseEvent) => {
    if (e && typeof e.stopPropagation === 'function') {
      e.stopPropagation();
    }
    const updated = notes.map((n) =>
      n.id === id ? { ...n, isArchived: false } : n
    );
    persistNotes(updated);
    setSelectedNoteIds((prev) => prev.filter((item) => item !== id));
    logger.security('ARCHIVE', `Restored note from Archive (ID: ${id})`);
  };

  // Batch Restore (Unarchive) selected notes back to Notes menu
  const handleUnarchiveNotesBatch = (ids: string[]) => {
    const idSet = new Set(ids);
    const updated = notes.map((n) =>
      idSet.has(n.id) ? { ...n, isArchived: false } : n
    );
    persistNotes(updated);
    setSelectedNoteIds([]);
    logger.security('ARCHIVE', `Batch restored ${ids.length} archived notes`);
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
    setSelectedNoteIds((prev) => prev.filter((item) => item !== id));
  };

  // Batch Soft Delete: moves multiple selected notes to Trash
  const handleDeleteNotesBatch = (ids: string[]) => {
    const idSet = new Set(ids);
    const now = Date.now();
    const updated = notes.map((n) =>
      idSet.has(n.id) ? { ...n, isDeleted: true, deletedAt: now } : n
    );
    persistNotes(updated);
    setSelectedNoteIds([]);
    logger.security('TRASH', `Batch moved ${ids.length} selected notes to Trash`);
  };

  const handleToggleNoteSelection = (noteId: string) => {
    setSelectedNoteIds((prev) =>
      prev.includes(noteId) ? prev.filter((id) => id !== noteId) : [...prev, noteId]
    );
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
        let toggledIndex = -1;
        let nextCompleted = false;
        const updatedChecklist = n.checklist.map((item, idx) => {
          if (item.id === itemId) {
            toggledIndex = idx;
            nextCompleted = !item.completed;
            return { ...item, completed: nextCompleted };
          }
          return item;
        });

        let updatedContent = n.content;
        if (toggledIndex >= 0 && n.content) {
          try {
            const doc = new DOMParser().parseFromString(n.content, 'text/html');
            const lines = doc.body.querySelectorAll('.aurora-checklist-line');
            const targetLine = lines[toggledIndex];
            if (targetLine) {
              const cb = targetLine.querySelector('input[type="checkbox"]') as HTMLInputElement | null;
              const txt = targetLine.querySelector('.aurora-checklist-text') as HTMLElement | null;
              if (cb) {
                if (nextCompleted) {
                  cb.setAttribute('checked', 'true');
                } else {
                  cb.removeAttribute('checked');
                }
              }
              if (txt) {
                if (nextCompleted) {
                  txt.classList.add('line-through', 'opacity-60', 'text-[#797487]', 'dark:text-[#7A7185]');
                } else {
                  txt.classList.remove('line-through', 'opacity-60', 'text-[#797487]', 'dark:text-[#7A7185]');
                }
              }
              updatedContent = doc.body.innerHTML;
            }
          } catch {
            // ignore DOMParser errors
          }
        }

        return {
          ...n,
          content: updatedContent,
          checklist: updatedChecklist,
          updatedAt: Date.now(),
        };
      }
      return n;
    });
    persistNotes(updated);
  };

  // Filter & Search Logic across active notes (independent of activeTab to prevent any transition flash)
  const allFilteredNotes = useMemo(() => {
    let result = [...activeNotes];

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

    // Tag filter: "All" or user-created tag
    if (selectedCategory !== 'All') {
      const target = selectedCategory.toLowerCase();
      result = result.filter(
        (n) =>
          n.tags?.some((t) => t.toLowerCase() === target) ||
          n.category?.toLowerCase() === target
      );
    }

    // Ensure pinned notes are ALWAYS strictly at the top, preserving user's order
    const pinned = result.filter((n) => n.isPinned);
    const unpinned = result.filter((n) => !n.isPinned);
    return [...pinned, ...unpinned];
  }, [activeNotes, searchQuery, selectedCategory]);

  const archivedFilteredNotes = useMemo(() => {
    let result = [...archivedNotes];

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

    if (selectedCategory !== 'All') {
      const target = selectedCategory.toLowerCase();
      result = result.filter(
        (n) =>
          n.tags?.some((t) => t.toLowerCase() === target) ||
          n.category?.toLowerCase() === target
      );
    }

    return result;
  }, [archivedNotes, searchQuery, selectedCategory]);

  const currentTabNotes = activeTab === 'archive' ? archivedFilteredNotes : allFilteredNotes;

  // Automatically prune any stale IDs from selectedNoteIds when notes change
  useEffect(() => {
    const validIds = new Set(currentTabNotes.map((n) => n.id));
    setSelectedNoteIds((prev) => {
      const filtered = prev.filter((id) => validIds.has(id));
      return filtered.length === prev.length ? prev : filtered;
    });
  }, [currentTabNotes]);

  // Counts for pills
  const categoryCounts = useMemo(() => {
    const counts: Record<string, number> = {
      All: activeNotes.length,
    };
    customCategories.forEach((cat) => {
      const target = cat.toLowerCase();
      counts[cat] = activeNotes.filter(
        (n) =>
          n.tags?.some((t) => t.toLowerCase() === target) ||
          n.category?.toLowerCase() === target
      ).length;
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
      />

      {/* Main Workspace: Notes / Pinned / Trash / Settings with Directional Page Slide */}
      <main className="max-w-6xl w-full mx-auto px-3 sm:px-6 pt-4 flex-1 overflow-x-hidden">
        <AnimatePresence mode="wait" custom={slideDirection} initial={false}>
          <motion.div
            key={activeTab}
            custom={slideDirection}
            variants={{
              enter: (dir: number) => ({
                x: dir > 0 ? 48 : -48,
                opacity: 0,
              }),
              center: {
                x: 0,
                opacity: 1,
              },
              exit: (dir: number) => ({
                x: dir > 0 ? -48 : 48,
                opacity: 0,
              }),
            }}
            initial="enter"
            animate="center"
            exit="exit"
            transition={{ duration: 0.22, ease: [0.22, 1, 0.36, 1] }}
          >
            {activeTab === 'settings' ? (
              <SettingsPage
                currentTheme={theme}
                onSelectTheme={handleSelectTheme}
                isHapticsEnabled={isHapticsEnabled}
                onToggleHaptics={handleToggleHaptics}
                isDbEncryptionEnabled={true}
                storageStats={storageStats}
                lastCleanupSummary={lastCleanupSummary}
                onCleanUpOrphanedFiles={handleCleanUpOrphanedFiles}
                customCategories={customCategories}
                onAddCategory={handleAddCategory}
                onRemoveCategory={handleRemoveCategory}
                notes={notes}
                onOpenWelcomeTour={() => setIsWelcomeOpen(true)}
                onOpenWhatsNew={() => setIsWhatsNewOpen(true)}
                onWipeData={() => {
                  localStorage.removeItem(STORAGE_NOTES_KEY);
                  localStorage.removeItem('aurora_user_tags_v2');
                  syncNotesToPrivateExternalStorage([], isDbEncryptionEnabled);
                  setNotes([]);
                  setCustomCategories([]);
                  setSelectedCategory('All');
                  setSelectedNoteIds([]);
                  setLastCleanupSummary('Wiped all notes and cleaned private external storage');
                  logger.security('STORAGE', 'User wiped local storage notes and custom categories after biometric verification');
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
                {/* Multi-Selection Action Bar (when user holds down on one or multiple notes) */}
                {selectedNoteIds.length > 0 && (
                  <div className="mb-3 p-3 rounded-2xl bg-[#F4EBFF] dark:bg-[#2B1E3E] border-2 border-[#5925DC] dark:border-[#9D5AF5] flex items-center justify-between gap-2 shadow-sm animate-fade-in">
                    <div className="flex items-center gap-2">
                      <button
                        type="button"
                        onClick={() => setSelectedNoteIds([])}
                        className="w-8 h-8 rounded-xl bg-white/80 dark:bg-[#1B1524] text-[#5925DC] dark:text-[#C296FD] flex items-center justify-center cursor-pointer"
                        title="Clear selection"
                      >
                        <X className="w-4 h-4" />
                      </button>
                      <span className="text-xs sm:text-sm font-bold text-[#5925DC] dark:text-[#C296FD]">
                        {selectedNoteIds.length} selected
                      </span>
                    </div>

                    <div className="flex items-center gap-2 flex-wrap">
                      <button
                        type="button"
                        onClick={() => {
                          if (selectedNoteIds.length === currentTabNotes.length) {
                            setSelectedNoteIds([]);
                          } else {
                            setSelectedNoteIds(currentTabNotes.map((n) => n.id));
                          }
                        }}
                        className="px-3 py-1.5 rounded-xl bg-white dark:bg-[#1B1524] text-[#5925DC] dark:text-[#C296FD] text-xs font-semibold border border-[#EADBFC] dark:border-[#322742] flex items-center gap-1 cursor-pointer"
                      >
                        <CheckSquare className="w-3.5 h-3.5" />
                        <span>
                          {selectedNoteIds.length === currentTabNotes.length ? 'None' : 'All'}
                        </span>
                      </button>

                      {activeTab === 'archive' ? (
                        <button
                          type="button"
                          onClick={() => handleUnarchiveNotesBatch(selectedNoteIds)}
                          className="px-3 py-1.5 rounded-xl bg-[#5925DC] dark:bg-[#9D5AF5] text-white text-xs font-bold flex items-center gap-1 shadow-xs cursor-pointer"
                        >
                          <ArchiveRestore className="w-3.5 h-3.5" />
                          <span>Restore ({selectedNoteIds.length})</span>
                        </button>
                      ) : (
                        <>
                          <button
                            type="button"
                            data-testid="edit-note-details-btn"
                            onClick={() => {
                              const target = currentTabNotes.find((n) => selectedNoteIds.includes(n.id));
                              if (target) {
                                openEditNoteDetailsModal(target);
                              }
                            }}
                            className="px-3 py-1.5 rounded-xl bg-white dark:bg-[#1B1524] text-[#5925DC] dark:text-[#C296FD] text-xs font-bold border border-[#EADBFC] dark:border-[#322742] flex items-center gap-1 shadow-xs cursor-pointer"
                          >
                            <Edit3 className="w-3.5 h-3.5" />
                            <span>Edit Note Details</span>
                          </button>

                          <button
                            type="button"
                            onClick={() => handleArchiveNotesBatch(selectedNoteIds)}
                            className="px-3 py-1.5 rounded-xl bg-[#5925DC] dark:bg-[#9D5AF5] text-white text-xs font-bold flex items-center gap-1 shadow-xs cursor-pointer"
                          >
                            <Archive className="w-3.5 h-3.5" />
                            <span>Archive ({selectedNoteIds.length})</span>
                          </button>

                          <button
                            type="button"
                            onClick={() => {
                              const selected = currentTabNotes.filter((n) => selectedNoteIds.includes(n.id));
                              if (selected.length > 0) {
                                setNotesToShare(selected);
                              }
                            }}
                            className="px-3 py-1.5 rounded-xl bg-white dark:bg-[#1B1524] text-[#5925DC] dark:text-[#C296FD] text-xs font-bold border border-[#EADBFC] dark:border-[#322742] flex items-center gap-1 shadow-xs cursor-pointer"
                          >
                            <Share2 className="w-3.5 h-3.5" />
                            <span>Share</span>
                          </button>
                        </>
                      )}

                      <button
                        type="button"
                        onClick={() => setShowBatchDeleteConfirm(true)}
                        className="px-3 py-1.5 rounded-xl bg-rose-600 text-white text-xs font-bold flex items-center gap-1 shadow-xs cursor-pointer"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                        <span>Delete ({selectedNoteIds.length})</span>
                      </button>
                    </div>
                  </div>
                )}

                {/* Category Filter Bar */}
                <div className="mb-2.5">
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
                {currentTabNotes.length === 0 ? (
                  <div className="py-16 text-center flex flex-col items-center justify-center animate-glide-in">
                    <div className="w-14 h-14 rounded-2xl bg-[#F4EBFF] dark:bg-[#2B1E3E] border border-[#EADBFC] dark:border-[#322742] flex items-center justify-center text-[#5925DC] dark:text-[#C296FD] mb-3 shadow-sm">
                      {activeTab === 'archive' ? (
                        <Archive className="w-7 h-7 stroke-[1.5]" />
                      ) : (
                        <FileText className="w-7 h-7 stroke-[1.5]" />
                      )}
                    </div>
                    <h3 className="font-bold text-base text-[#1E1A24] dark:text-[#F7EEFC] mb-1">
                      {searchQuery
                        ? 'No matching notes found'
                        : activeTab === 'archive'
                        ? 'No archived notes'
                        : 'No notes has been created.'}
                    </h3>
                    <p className="text-xs text-[#797487] dark:text-[#A39BAC] max-w-xs mb-5">
                      {searchQuery
                        ? 'Try a different keyword or reset the filter.'
                        : activeTab === 'archive'
                        ? 'Hold any note in the Notes menu to archive one or multiple notes as read-only.'
                        : 'Tap the circular button in the bottom right corner to write a note.'}
                    </p>
                    {activeTab === 'notes' && (
                      <button
                        onClick={handleCreateNote}
                        className="px-4 py-2 rounded-xl bg-gradient-to-r from-[#5925DC] to-[#9B51E0] dark:from-[#9D5AF5] dark:to-[#C296FD] text-white text-xs font-semibold shadow-xs flex items-center gap-1.5 transition-all hover:scale-[1.02] active:scale-95 cursor-pointer"
                      >
                        <Plus className="w-4 h-4" />
                        <span>Create Note</span>
                      </button>
                    )}
                  </div>
                ) : (
                  <MorphingNotesGrid isGridView={isGridView}>
                    {currentTabNotes.map((note) => {
                      const animateMovement = note.id === lastPinnedId;

                      const handleDeleteTagAndDetach = (tagToDelete: string) => {
                        handleRemoveCategory(tagToDelete);
                      };

                      return (
                        <div key={note.id} className="w-full">
                          <NoteCard
                            note={note}
                            isGridView={isGridView}
                            animateMovement={animateMovement}
                            isSelectionMode={selectedNoteIds.length > 0}
                            isSelected={selectedNoteIds.includes(note.id)}
                            onToggleSelection={handleToggleNoteSelection}
                            onSelect={(selected) => {
                              captureFabOrigin();
                              setActiveNote(selected);
                            }}
                            onTogglePin={handleTogglePin}
                            onArchive={handleArchiveNote}
                            onUnarchive={handleUnarchiveNote}
                            onDelete={handleDeleteNote}
                            onShare={(noteToShare) => setNotesToShare([noteToShare])}
                            onEditDetails={openEditNoteDetailsModal}
                            onToggleCheckItem={handleToggleCheckItem}
                            onDeleteTag={handleDeleteTagAndDetach}
                          />
                        </div>
                      );
                    })}
                  </MorphingNotesGrid>
                )}
              </>
            )}
          </motion.div>
        </AnimatePresence>
      </main>

      {/* Batch Delete Confirmation Modal */}
      {showBatchDeleteConfirm && selectedNoteIds.length > 0 && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-fade-in"
          onClick={() => setShowBatchDeleteConfirm(false)}
        >
          <div
            className="w-full max-w-sm rounded-2xl bg-white dark:bg-[#1B1524] border border-[#EADBFC] dark:border-[#322742] shadow-2xl p-5 space-y-4"
            onClick={(e) => e.stopPropagation()}
          >
            <h3 className="font-bold text-sm text-[#1E1A24] dark:text-[#F7EEFC]">
              Delete {selectedNoteIds.length} Selected {selectedNoteIds.length === 1 ? 'Note' : 'Notes'}?
            </h3>
            <p className="text-xs text-[#484455] dark:text-[#A39BAC] leading-relaxed">
              Move {selectedNoteIds.length} selected {selectedNoteIds.length === 1 ? 'note' : 'notes'} to Trash? You can restore them anytime from the Trash tab.
            </p>
            <div className="flex items-center justify-end gap-2">
              <button
                type="button"
                onClick={() => setShowBatchDeleteConfirm(false)}
                className="px-3.5 py-2 rounded-xl text-xs font-semibold text-[#484455] dark:text-[#A39BAC] border border-[#EADBFC] dark:border-[#322742] cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => {
                  setShowBatchDeleteConfirm(false);
                  handleDeleteNotesBatch(selectedNoteIds);
                }}
                className="px-4 py-2 rounded-xl text-xs font-bold text-white bg-rose-600 hover:bg-rose-700 cursor-pointer"
              >
                Delete ({selectedNoteIds.length})
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Edit Note Details Modal (Opened via Long-Press on Notes in the Main Menu) */}
      {editingDetailsNote && (
        <div
          data-testid="edit-note-details-modal"
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-fade-in"
          onClick={() => setEditingDetailsNote(null)}
        >
          <div
            className="w-full max-w-md rounded-2xl bg-white dark:bg-[#1B1524] border border-[#EADBFC] dark:border-[#322742] shadow-2xl p-5 space-y-4"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between pb-2 border-b border-[#EADBFC] dark:border-[#322742]">
              <h3 className="font-bold text-base text-[#1E1A24] dark:text-[#F7EEFC]">
                Edit Note Details
              </h3>
              <button
                type="button"
                aria-label="Close Edit Note Details"
                onClick={() => setEditingDetailsNote(null)}
                className="p-1 rounded-lg text-[#797487] hover:text-[#1E1A24] dark:text-[#A39BAC] dark:hover:text-white cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Note Title */}
            <div>
              <label className="block text-xs font-semibold text-[#797487] dark:text-[#A39BAC] mb-1">
                TITLE
              </label>
              <input
                type="text"
                aria-label="Edit note details title"
                value={detailsTitle}
                onChange={(e) => setDetailsTitle(e.target.value)}
                placeholder="Note Title..."
                className="w-full text-sm px-3 py-2 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] text-[#1E1A24] dark:text-[#F7EEFC] focus:outline-none focus:border-[#5925DC]"
              />
            </div>

            {/* Category Select */}
            <div>
              <label className="block text-xs font-semibold text-[#797487] dark:text-[#A39BAC] mb-1">
                CATEGORY
              </label>
              <select
                value={detailsCategory}
                onChange={(e) => setDetailsCategory(e.target.value)}
                className="w-full text-sm px-3 py-2 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] text-[#1E1A24] dark:text-[#F7EEFC] focus:outline-none focus:border-[#5925DC]"
              >
                {Array.from(
                  new Set([
                    'General',
                    'Personal',
                    'Work',
                    'Ideas',
                    'Checklist',
                    'Journal',
                    ...customCategories,
                    ...detailsTags,
                  ])
                ).map((cat) => (
                  <option key={cat} value={cat}>
                    {cat}
                  </option>
                ))}
              </select>
            </div>

            {/* Tags Feature */}
            <div>
              <label className="block text-xs font-semibold text-[#797487] dark:text-[#A39BAC] mb-1">
                TAGS
              </label>
              <div className="flex items-center gap-1.5 flex-wrap mb-2">
                {detailsTags.map((tag) => (
                  <span
                    key={tag}
                    className="inline-flex items-center gap-1 text-xs font-medium px-2.5 py-1 rounded-lg bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] border border-[#EADBFC] dark:border-[#322742]"
                  >
                    <Tag className="w-3 h-3" />
                    <span>#{tag}</span>
                    <button
                      type="button"
                      onClick={() => handleRemoveDetailsTag(tag)}
                      className="hover:text-rose-600 dark:hover:text-rose-400 ml-0.5 cursor-pointer"
                    >
                      ✕
                    </button>
                  </span>
                ))}
              </div>

              <div className="flex gap-2">
                <input
                  type="text"
                  placeholder="Add tag name..."
                  value={detailsNewTagInput}
                  onChange={(e) => setDetailsNewTagInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault();
                      handleAddDetailsTag(e);
                    }
                  }}
                  className="flex-1 text-xs px-3 py-2 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] text-[#1E1A24] dark:text-[#F7EEFC] focus:outline-none"
                />
                <button
                  type="button"
                  onClick={handleAddDetailsTag}
                  className="px-3 py-2 rounded-xl bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] text-xs font-semibold flex items-center gap-1 border border-[#EADBFC] dark:border-[#322742] cursor-pointer"
                >
                  <Plus className="w-3.5 h-3.5" />
                  <span>Add</span>
                </button>
              </div>
            </div>

            {/* Pin Toggle */}
            <div className="flex items-center justify-between pt-1">
              <span className="text-xs font-semibold text-[#1E1A24] dark:text-[#F7EEFC]">
                Pin note to top
              </span>
              <button
                type="button"
                onClick={() => setDetailsIsPinned(!detailsIsPinned)}
                className={`p-2 rounded-xl border transition-all cursor-pointer ${
                  detailsIsPinned
                    ? 'bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] border-[#D6BBFB]'
                    : 'bg-[#FAF8FB] dark:bg-[#120E17] text-[#797487] border-[#EADBFC] dark:border-[#322742]'
                }`}
              >
                <Pin className={`w-4 h-4 ${detailsIsPinned ? 'fill-current' : ''}`} />
              </button>
            </div>

            {/* Submit Save */}
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setEditingDetailsNote(null)}
                className="px-4 py-2 rounded-xl text-xs font-semibold text-[#797487] hover:bg-[#FAF8FB] dark:hover:bg-[#120E17] cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
                data-testid="save-note-details-submit-btn"
                onClick={handleSaveEditedNoteDetails}
                className="px-5 py-2 rounded-xl text-xs font-semibold text-white bg-gradient-to-r from-[#5925DC] to-[#9B51E0] shadow-sm flex items-center gap-1.5 active:scale-95 cursor-pointer"
              >
                <Check className="w-4 h-4 stroke-[2.5]" />
                <span>Save Note</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Share & Export Modal */}
      {notesToShare.length > 0 && (
        <ShareNoteModal
          notes={notesToShare}
          onClose={() => setNotesToShare([])}
        />
      )}

      {/* Floating Circular UI Element in the Down Right Corner (only on active Notes tab) */}
      {activeTab === 'notes' && (
        <motion.button
          ref={fabButtonRef}
          type="button"
          onClick={(e) => handleCreateNote(e)}
          whileHover={{ scale: 1.1 }}
          whileTap={{ scale: 0.92 }}
          aria-label="Create note"
          className="fixed bottom-24 right-4 sm:bottom-8 sm:right-8 z-40 w-14 h-14 rounded-full bg-gradient-to-tr from-[#5925DC] via-[#7B35E8] to-[#9B51E0] dark:from-[#7839EE] dark:via-[#9D5AF5] dark:to-[#C296FD] text-white shadow-[0_8px_24px_rgba(89,37,220,0.45)] dark:shadow-[0_8px_24px_rgba(157,90,245,0.45)] border border-white/30 flex items-center justify-center cursor-pointer hover:shadow-[0_12px_32px_rgba(89,37,220,0.6)] transition-all group"
        >
          <Plus className="w-7 h-7 stroke-[2.5] transition-transform duration-300 group-hover:rotate-90" />
        </motion.button>
      )}

      {/* Liquid Navigation Bar */}
      <LiquidNavbar
        activeTab={activeTab}
        onChangeTab={handleSelectNavTab}
        archivedCount={archivedNotes.length}
        trashCount={trashedNotes.length}
        isHapticsEnabled={isHapticsEnabled}
      />

      {/* Note Editor Modal with Circular Reveal & Predictive Back */}
      <AnimatePresence>
        {activeNote && (
          <NoteEditorModal
            key={activeNote.id}
            note={activeNote}
            customCategories={customCategories}
            revealOrigin={editorRevealOrigin}
            onSave={handleSaveActiveNote}
            onUnarchive={handleUnarchiveNote}
            onDelete={(id) => handleDeleteNote(id, null)}
            onClose={() => setActiveNote(null)}
          />
        )}
      </AnimatePresence>

      {/* Welcome Tour Modal on First Launch */}
      <WelcomeTourModal
        isOpen={isWelcomeOpen}
        onClose={handleCloseWelcome}
      />

      {/* What's New Introduction Modal (Shown once after update restart) */}
      <WhatsNewModal
        isOpen={!isWelcomeOpen && isWhatsNewOpen}
        onClose={handleCloseWhatsNew}
      />
    </div>
  );
}

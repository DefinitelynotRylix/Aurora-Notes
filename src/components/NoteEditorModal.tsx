import React, { useState, useRef, useEffect } from 'react';
import { Note, ChecklistItem } from '../types';
import {
  Pin,
  Check,
  CheckSquare,
  Tag,
  Folder,
  Bold,
  Italic,
  List,
  Heading1,
  Heading2,
  ArrowLeft,
  X,
  Plus,
  Share2
} from 'lucide-react';
import { logger } from '../utils/logger';
import { ShareNoteModal } from './ShareNoteModal';

interface NoteEditorModalProps {
  note: Note;
  customCategories: string[];
  onSave: (updatedNote: Note) => void;
  onClose: () => void;
}

export const NoteEditorModal: React.FC<NoteEditorModalProps> = ({
  note,
  customCategories,
  onSave,
  onClose,
}) => {
  const [title, setTitle] = useState(note.title);
  const [category, setCategory] = useState(note.category || 'General');
  const [isPinned, setIsPinned] = useState(note.isPinned);
  const [tags, setTags] = useState<string[]>(note.tags || []);
  const [newTagInput, setNewTagInput] = useState('');
  const [showDoneModal, setShowDoneModal] = useState(false);
  const [showShareModal, setShowShareModal] = useState(false);

  // Active Size Mode ('small' | 'medium' | 'large')
  const [textSizeMode, setTextSizeMode] = useState<'small' | 'medium' | 'large'>('small');

  // Ref for Rich ContentEditable Editor & Scroll Container
  const editorRef = useRef<HTMLDivElement | null>(null);
  const scrollContainerRef = useRef<HTMLDivElement | null>(null);
  const hasSavedRef = useRef(false);

  /**
   * Ensures the active caret stays well above the bottom formatting bar / keyboard
   * by scrolling the outer container down before the cursor reaches the bottom edge.
   */
  const ensureCaretVisible = () => {
    const container = scrollContainerRef.current;
    if (!container) return;
    const selection = window.getSelection();
    if (!selection || selection.rangeCount === 0) return;

    const range = selection.getRangeAt(0);
    let rect = range.getBoundingClientRect();

    if (!rect || (rect.top === 0 && rect.bottom === 0)) {
      const node = range.startContainer;
      const el = (
        node.nodeType === Node.ELEMENT_NODE ? node : node.parentElement
      ) as HTMLElement | null;
      if (el) {
        rect = el.getBoundingClientRect();
      }
    }

    if (!rect || (rect.top === 0 && rect.bottom === 0)) return;

    const containerRect = container.getBoundingClientRect();
    const bottomClearance = 104;
    const topClearance = 36;

    if (rect.bottom > containerRect.bottom - bottomClearance) {
      const delta = rect.bottom - (containerRect.bottom - bottomClearance);
      container.scrollTop += delta;
    } else if (rect.top < containerRect.top + topClearance) {
      const delta = containerRect.top + topClearance - rect.top;
      container.scrollTop -= delta;
    }
  };

  const allCategoryOptions = Array.from(new Set(['General', ...customCategories, note.category].filter(Boolean)));

  // Populate editor initial content on mount & sync checkboxes
  useEffect(() => {
    if (editorRef.current) {
      editorRef.current.innerHTML = note.content || '';

      // Sync checkbox checked states and strikethrough styling
      const checkboxes = editorRef.current.querySelectorAll('input[type="checkbox"]');
      checkboxes.forEach((cb) => {
        const checkbox = cb as HTMLInputElement;
        checkbox.setAttribute('contenteditable', 'false');
        const textDiv = checkbox.nextElementSibling as HTMLElement;
        if (checkbox.hasAttribute('checked') || checkbox.checked) {
          checkbox.checked = true;
          if (textDiv) {
            textDiv.style.textDecoration = 'line-through';
            textDiv.style.opacity = '0.6';
          }
        }
      });
    }
  }, [note.id]);

  /**
   * Constructs latest note object from state & editor
   */
  const getConstructedNote = (): Note => {
    const rawHtml = editorRef.current ? editorRef.current.innerHTML : note.content;

    // Extract inline checklist items for note metadata
    const extractedChecklist: ChecklistItem[] = [];
    if (editorRef.current) {
      const checkboxes = editorRef.current.querySelectorAll('input[type="checkbox"]');
      checkboxes.forEach((cb, index) => {
        const checkbox = cb as HTMLInputElement;
        const line = checkbox.parentElement;
        if (line) {
          const clone = line.cloneNode(true) as HTMLElement;
          clone.querySelectorAll('input[type="checkbox"]').forEach((c) => c.remove());
          const text = clone.textContent?.trim() || '';
          if (text) {
            extractedChecklist.push({
              id: `chk-${index}-${Date.now()}`,
              text,
              completed: checkbox.checked,
            });
          }
        }
      });
    }

    return {
      ...note,
      title: title.trim() || 'Untitled Note',
      content: rawHtml,
      category,
      isPinned,
      checklist: extractedChecklist.length > 0 ? extractedChecklist : note.checklist || [],
      tags,
      updatedAt: Date.now(),
    };
  };

  /**
   * Auto-Save & Close Handler (Discards completely empty notes)
   */
  const handleAutoSaveAndClose = () => {
    if (hasSavedRef.current) return;
    hasSavedRef.current = true;

    const rawContent = editorRef.current ? editorRef.current.innerHTML : note.content;
    const cleanText = (rawContent || '')
      .replace(/<[^>]*>/g, '')
      .replace(/&nbsp;/g, ' ')
      .replace(/\u200B/g, '')
      .trim();
    const cleanTitle = title.trim();

    // Discard note if user entered nothing
    if (!cleanTitle && !cleanText) {
      logger.warn('NOTE', `Discarded blank note (ID: ${note.id})`);
      onClose();
      return;
    }

    const updated = getConstructedNote();
    onSave(updated);
    logger.security('NOTE', `Saved local note: "${updated.title}" (ID: ${updated.id})`);
    onClose();
  };

  // Register Android native back button & gesture popstate listener
  useEffect(() => {
    try {
      window.history.pushState({ auroraModalOpen: true }, '');
    } catch {
      // ignore
    }

    const handlePopState = () => {
      handleAutoSaveAndClose();
    };

    window.addEventListener('popstate', handlePopState);

    return () => {
      window.removeEventListener('popstate', handlePopState);
      if (!hasSavedRef.current) {
        handleAutoSaveAndClose();
      }
    };
  }, []);

  /**
   * Formatting Functions
   */
  const handleBoldClick = () => {
    document.execCommand('bold', false);
  };

  const handleItalicClick = () => {
    document.execCommand('italic', false);
  };

  const handleOutlineClick = () => {
    document.execCommand('insertUnorderedList', false);
  };

  const handleSelectSize = (clickedMode: 'small' | 'medium' | 'large') => {
    // Tapping Medium or Large again toggles it off and returns to normal small size
    const nextMode: 'small' | 'medium' | 'large' =
      clickedMode !== 'small' && textSizeMode === clickedMode ? 'small' : clickedMode;

    setTextSizeMode(nextMode);

    if (!editorRef.current) return;
    editorRef.current.focus();

    const selection = window.getSelection();
    if (!selection || selection.rangeCount === 0) return;

    const sizePxMap = {
      small: '0.925rem',
      medium: '1.25rem',
      large: '1.65rem',
    };
    const targetSize = sizePxMap[nextMode];

    const range = selection.getRangeAt(0);
    const fontSpan = document.createElement('span');
    fontSpan.style.fontSize = targetSize;
    fontSpan.style.lineHeight = '1.35';
    const zwsp = document.createTextNode('\u200B');
    fontSpan.appendChild(zwsp);

    range.collapse(false);
    range.insertNode(fontSpan);

    const newRange = document.createRange();
    newRange.setStart(zwsp, 1);
    newRange.setEnd(zwsp, 1);
    selection.removeAllRanges();
    selection.addRange(newRange);
  };

  const createChecklistLine = (initialContent?: Node | string): HTMLDivElement => {
    const line = document.createElement('div');
    line.className = 'aurora-checklist-line flex items-start gap-2.5 my-1.5 group select-none';
    line.setAttribute('contenteditable', 'false');
    line.setAttribute('data-checklist', 'true');

    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.className = 'w-4 h-4 rounded accent-[#5925DC] dark:accent-[#9D5AF5] cursor-pointer mt-1 shrink-0 border-[#EADBFC] dark:border-[#322742]';
    checkbox.setAttribute('contenteditable', 'false');

    const textDiv = document.createElement('div');
    textDiv.className = 'aurora-checklist-text flex-1 focus:outline-none min-w-[20px] text-sm sm:text-base leading-relaxed text-[#1E1A24] dark:text-[#F7EEFC] select-text';
    textDiv.setAttribute('contenteditable', 'true');

    if (initialContent) {
      if (typeof initialContent === 'string') {
        textDiv.innerHTML = initialContent;
      } else {
        textDiv.appendChild(initialContent);
      }
    } else {
      textDiv.innerHTML = '<br>';
    }

    line.appendChild(checkbox);
    line.appendChild(textDiv);

    return line;
  };

  const handleChecklistClick = () => {
    if (!editorRef.current) return;
    editorRef.current.focus();

    const selection = window.getSelection();
    if (!selection || selection.rangeCount === 0) return;

    const range = selection.getRangeAt(0);

    const getTopLevelLine = (node: Node | null): HTMLElement | null => {
      let current = node as HTMLElement | null;
      while (current && current.parentElement && current.parentElement !== editorRef.current) {
        if (current.parentElement.classList?.contains('aurora-checklist-line')) {
          return current.parentElement;
        }
        current = current.parentElement;
      }
      if (current && current.parentElement === editorRef.current) {
        return current;
      }
      return null;
    };

    let startLine = getTopLevelLine(range.startContainer);
    let endLine = getTopLevelLine(range.endContainer);

    if (!startLine) {
      const plainDiv = document.createElement('div');
      plainDiv.className = 'my-1 focus:outline-none min-h-[1.5em]';
      plainDiv.setAttribute('contenteditable', 'true');
      if (!range.collapsed) {
        plainDiv.appendChild(range.extractContents());
      } else {
        plainDiv.innerHTML = '<br>';
      }
      range.insertNode(plainDiv);
      startLine = plainDiv;
      endLine = plainDiv;
    }

    const targetLines: HTMLElement[] = [];
    if (startLine && endLine) {
      let current: HTMLElement | null = startLine;
      while (current) {
        targetLines.push(current);
        if (current === endLine || !current.nextElementSibling) break;
        current = current.nextElementSibling as HTMLElement;
      }
    } else if (startLine) {
      targetLines.push(startLine);
    }

    if (targetLines.length === 0) return;

    const isCurrentlyChecklist = targetLines[0].classList?.contains('aurora-checklist-line') || !!targetLines[0].querySelector('input[type="checkbox"]');

    targetLines.forEach((line) => {
      if (isCurrentlyChecklist) {
        const textDiv = line.querySelector('.aurora-checklist-text') || line;
        const plainLine = document.createElement('div');
        plainLine.className = 'my-1 focus:outline-none min-h-[1.5em]';
        plainLine.setAttribute('contenteditable', 'true');

        const clone = textDiv.cloneNode(true) as HTMLElement;
        clone.querySelectorAll('input[type="checkbox"]').forEach((cb) => cb.remove());
        if (clone.childNodes.length > 0) {
          while (clone.firstChild) {
            plainLine.appendChild(clone.firstChild);
          }
        } else {
          plainLine.innerHTML = '<br>';
        }

        line.parentNode?.replaceChild(plainLine, line);

        const newRange = document.createRange();
        newRange.selectNodeContents(plainLine);
        newRange.collapse(false);
        selection.removeAllRanges();
        selection.addRange(newRange);
      } else {
        if (!line.classList?.contains('aurora-checklist-line')) {
          const contentFragment = document.createDocumentFragment();
          while (line.firstChild) {
            contentFragment.appendChild(line.firstChild);
          }
          if (contentFragment.childNodes.length === 0) {
            contentFragment.appendChild(document.createElement('br'));
          }

          const checkLine = createChecklistLine(contentFragment);
          line.parentNode?.replaceChild(checkLine, line);

          const textDiv = checkLine.querySelector('.aurora-checklist-text') as HTMLElement;
          if (textDiv) {
            const newRange = document.createRange();
            newRange.selectNodeContents(textDiv);
            newRange.collapse(false);
            selection.removeAllRanges();
            selection.addRange(newRange);
          }
        }
      }
    });
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'Enter') {
      const selection = window.getSelection();
      if (!selection || selection.rangeCount === 0) return;

      const range = selection.getRangeAt(0);
      let current = range.startContainer as HTMLElement | null;

      while (current && current !== editorRef.current) {
        if (current.classList?.contains('aurora-checklist-text')) {
          e.preventDefault();

          const parentLine = current.parentElement as HTMLElement;

          const plainLine = document.createElement('div');
          plainLine.className = 'my-1 min-h-[1.5em] focus:outline-none';
          plainLine.setAttribute('contenteditable', 'true');
          plainLine.innerHTML = '<br>';

          if (parentLine.nextSibling) {
            parentLine.parentNode?.insertBefore(plainLine, parentLine.nextSibling);
          } else {
            parentLine.parentNode?.appendChild(plainLine);
          }

          const newRange = document.createRange();
          newRange.setStart(plainLine, 0);
          newRange.collapse(true);
          selection.removeAllRanges();
          selection.addRange(newRange);
          requestAnimationFrame(ensureCaretVisible);
          return;
        }
        current = current.parentElement;
      }
    }
    requestAnimationFrame(ensureCaretVisible);
  };

  const handleAddTag = (e: React.KeyboardEvent | React.MouseEvent) => {
    const clean = newTagInput.trim().replace(/^#/, '');
    if (clean && !tags.includes(clean)) {
      setTags([...tags, clean]);
      setNewTagInput('');
    }
  };

  const handleRemoveTag = (tagToRemove: string) => {
    setTags(tags.filter((t) => t !== tagToRemove));
  };

  return (
    <div className="fixed inset-0 z-50 flex flex-col w-full h-full bg-white dark:bg-[#1B1524] overflow-hidden animate-fadeIn">
      {/* 
        CLEAN FULL-SCREEN HEADER (PERFECT ALIGNMENT WITH SAFE AREA)
      */}
      <div className="px-4 pt-3.5 pb-3 border-b border-[#EADBFC] dark:border-[#322742] flex items-center justify-between gap-3 shrink-0 bg-white/95 dark:bg-[#1B1524]/95 shadow-xs min-h-[56px]">
        <div className="flex items-center gap-2">
          {/* Back Button */}
          <button
            type="button"
            onClick={handleAutoSaveAndClose}
            aria-label="Save and return"
            className="w-9 h-9 rounded-xl bg-white dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] text-[#1E1A24] dark:text-[#F7EEFC] hover:text-[#5925DC] dark:hover:text-[#C296FD] flex items-center justify-center transition-all cursor-pointer active:scale-95 shrink-0"
            title="Auto-Save & Close"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>

          {/* Current Category Pill */}
          <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#F4EBFF] dark:bg-[#2B1E3E] text-xs font-semibold text-[#5925DC] dark:text-[#C296FD] border border-[#EADBFC]/80 dark:border-[#322742]">
            <Folder className="w-3.5 h-3.5" />
            <span className="truncate max-w-[100px] sm:max-w-[150px]">{category}</span>
          </div>
        </div>

        {/* Right side: Share & Done Buttons */}
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setShowShareModal(true)}
            aria-label="Share or export note"
            className="w-9 h-9 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] text-[#5925DC] dark:text-[#C296FD] hover:bg-[#F4EBFF] dark:hover:bg-[#2B1E3E] flex items-center justify-center transition-all active:scale-95 cursor-pointer"
            title="Share or save note (.md / .txt)"
          >
            <Share2 className="w-4 h-4" />
          </button>

          <button
            type="button"
            onClick={() => setShowDoneModal(true)}
            className="px-4 py-2 rounded-xl bg-gradient-to-r from-[#5925DC] to-[#9B51E0] dark:from-[#9D5AF5] dark:to-[#C296FD] text-white text-xs font-semibold shadow-sm flex items-center gap-1.5 transition-all active:scale-95 cursor-pointer"
          >
            <Check className="w-4 h-4 stroke-[2.5]" />
            <span>Done</span>
          </button>
        </div>
      </div>

      {showShareModal && (
        <ShareNoteModal
          notes={[
            {
              ...note,
              title: title.trim() || 'Untitled Note',
              content: editorRef.current?.innerHTML || note.content,
              category,
              tags,
              isPinned,
              updatedAt: Date.now(),
            },
          ]}
          onClose={() => setShowShareModal(false)}
        />
      )}

      {/* 
        MAXIMIZED FULL-PAGE WRITING CANVAS
      */}
      <div
        ref={scrollContainerRef}
        className="p-4 sm:p-8 pb-40 overflow-y-auto space-y-4 flex-1 max-w-3xl mx-auto w-full scroll-smooth"
      >
        {/* Note Title Input */}
        <input
          type="text"
          aria-label="Note title"
          placeholder="Title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          className="w-full text-2xl sm:text-3xl font-bold text-[#1E1A24] dark:text-[#F7EEFC] placeholder-[#797487]/50 dark:placeholder-[#A39BAC]/50 border-none focus:outline-none focus:ring-0 bg-transparent tracking-tight py-1"
        />

        {/* 
          WYSIWYG RICH CONTENT-EDITABLE CANVAS
        */}
        <div
          ref={editorRef}
          contentEditable={true}
          onKeyDown={handleKeyDown}
          onKeyUp={() => requestAnimationFrame(ensureCaretVisible)}
          onInput={() => requestAnimationFrame(ensureCaretVisible)}
          onClick={() => requestAnimationFrame(ensureCaretVisible)}
          aria-label="Note content editor"
          data-placeholder="Start typing your note here..."
          className="w-full min-h-[360px] pb-36 text-sm sm:text-base leading-relaxed text-[#1E1A24] dark:text-[#F7EEFC] focus:outline-none bg-transparent font-normal space-y-2 [&_h1]:text-2xl [&_h1]:font-bold [&_h1]:my-2 [&_h2]:text-xl [&_h2]:font-semibold [&_h2]:my-1.5 [&_ul]:list-disc [&_ul]:pl-6 [&_ul]:space-y-1 [&_b]:font-bold [&_strong]:font-bold [&_i]:italic [&_em]:italic empty:before:content-[attr(data-placeholder)] empty:before:text-[#797487]/50 empty:before:dark:text-[#A39BAC]/50"
        />
      </div>

      {/* 
        STICKY FLOATING FORMATTING TOOLBAR (Directly above soft keyboard)
      */}
      <div className="sticky bottom-0 z-30 p-2 sm:p-3 bg-white/95 dark:bg-[#1B1524]/95 backdrop-blur-md border-t border-[#EADBFC] dark:border-[#322742] flex items-center justify-between gap-1 overflow-x-auto custom-scrollbar shadow-lg shrink-0 max-w-3xl mx-auto w-full">
        <div className="flex items-center gap-1.5">
          {/* Bold */}
          <button
            type="button"
            onMouseDown={(e) => e.preventDefault()}
            onClick={handleBoldClick}
            aria-label="Make text thick and bold"
            className="w-8.5 h-9 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] hover:bg-[#F4EBFF] dark:hover:bg-[#2B1E3E] text-[#1E1A24] dark:text-[#F7EEFC] hover:text-[#5925DC] dark:hover:text-[#C296FD] border border-[#EADBFC] dark:border-[#322742] flex items-center justify-center transition-all cursor-pointer active:scale-95 shrink-0"
            title="Thick / Bold"
          >
            <Bold className="w-3.5 h-3.5 stroke-[2.5]" />
          </button>

          {/* Italic */}
          <button
            type="button"
            onMouseDown={(e) => e.preventDefault()}
            onClick={handleItalicClick}
            aria-label="Make text italic"
            className="w-8.5 h-9 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] hover:bg-[#F4EBFF] dark:hover:bg-[#2B1E3E] text-[#1E1A24] dark:text-[#F7EEFC] hover:text-[#5925DC] dark:hover:text-[#C296FD] border border-[#EADBFC] dark:border-[#322742] flex items-center justify-center transition-all cursor-pointer active:scale-95 shrink-0"
            title="Italic"
          >
            <Italic className="w-3.5 h-3.5 stroke-[2.2]" />
          </button>

          {/* Text Size Toggle Buttons (Default is Small; tapping active Medium or Large turns it off back to Small) */}
          <div className="flex items-center p-0.5 rounded-2xl bg-[#FAF8FB] dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742]">
            <button
              type="button"
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => handleSelectSize('medium')}
              aria-label="Toggle Medium text size"
              className={`h-9 px-3 rounded-xl text-xs transition-all cursor-pointer flex items-center gap-1 ${
                textSizeMode === 'medium'
                  ? 'bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] shadow-xs font-bold border border-[#D6BBFB] dark:border-[#7D49D4]'
                  : 'text-[#484455] dark:text-[#A39BAC] font-medium hover:text-[#1E1A24] dark:hover:text-white'
              }`}
            >
              <Heading2 className="w-3.5 h-3.5" />
              <span>Medium</span>
            </button>

            <button
              type="button"
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => handleSelectSize('large')}
              aria-label="Toggle Large text size"
              className={`h-9 px-3 rounded-xl text-xs transition-all cursor-pointer flex items-center gap-1 ${
                textSizeMode === 'large'
                  ? 'bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] shadow-xs font-bold border border-[#D6BBFB] dark:border-[#7D49D4]'
                  : 'text-[#484455] dark:text-[#A39BAC] font-medium hover:text-[#1E1A24] dark:hover:text-white'
              }`}
            >
              <Heading1 className="w-3.5 h-3.5" />
              <span>Large</span>
            </button>
          </div>

          {/* Bullet List */}
          <button
            type="button"
            onMouseDown={(e) => e.preventDefault()}
            onClick={handleOutlineClick}
            aria-label="Add bullet list item"
            className="w-10 h-10 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] hover:bg-[#F4EBFF] dark:hover:bg-[#2B1E3E] text-[#1E1A24] dark:text-[#F7EEFC] hover:text-[#5925DC] dark:hover:text-[#C296FD] border border-[#EADBFC] dark:border-[#322742] flex items-center justify-center transition-all cursor-pointer active:scale-95"
            title="Bullet List"
          >
            <List className="w-4 h-4" />
          </button>

          {/* Checklist */}
          <button
            type="button"
            onMouseDown={(e) => e.preventDefault()}
            onClick={handleChecklistClick}
            aria-label="Insert inline checklist item"
            className="w-10 h-10 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] hover:bg-[#F4EBFF] dark:hover:bg-[#2B1E3E] text-[#1E1A24] dark:text-[#F7EEFC] hover:text-[#5925DC] dark:hover:text-[#C296FD] border border-[#EADBFC] dark:border-[#322742] flex items-center justify-center transition-all cursor-pointer active:scale-95"
            title="Insert Checklist Item"
          >
            <CheckSquare className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* 
        NOTE DETAILS / SAVE MODAL (WHEN USER TAPS "DONE")
      */}
      {showDoneModal && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-fadeIn"
          onClick={() => setShowDoneModal(false)}
        >
          <div
            className="w-full max-w-md rounded-2xl bg-white dark:bg-[#1B1524] border border-[#EADBFC] dark:border-[#322742] shadow-2xl p-5 space-y-4"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between pb-2 border-b border-[#EADBFC] dark:border-[#322742]">
              <h3 className="font-bold text-base text-[#1E1A24] dark:text-[#F7EEFC]">
                Save Note Details
              </h3>
              <button
                type="button"
                onClick={() => setShowDoneModal(false)}
                className="p-1 rounded-lg text-[#797487] hover:text-[#1E1A24] dark:text-[#A39BAC] dark:hover:text-white"
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
                value={title}
                onChange={(e) => setTitle(e.target.value)}
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
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                className="w-full text-sm px-3 py-2 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] text-[#1E1A24] dark:text-[#F7EEFC] focus:outline-none focus:border-[#5925DC]"
              >
                {allCategoryOptions.map((cat) => (
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
                {tags.map((tag) => (
                  <span
                    key={tag}
                    className="inline-flex items-center gap-1 text-xs font-medium px-2.5 py-1 rounded-lg bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] border border-[#EADBFC] dark:border-[#322742]"
                  >
                    <Tag className="w-3 h-3" />
                    <span>#{tag}</span>
                    <button
                      type="button"
                      onClick={() => handleRemoveTag(tag)}
                      className="hover:text-rose-600 dark:hover:text-rose-400 ml-0.5"
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
                  value={newTagInput}
                  onChange={(e) => setNewTagInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault();
                      handleAddTag(e);
                    }
                  }}
                  className="flex-1 text-xs px-3 py-2 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] text-[#1E1A24] dark:text-[#F7EEFC] focus:outline-none"
                />
                <button
                  type="button"
                  onClick={handleAddTag}
                  className="px-3 py-2 rounded-xl bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] text-xs font-semibold flex items-center gap-1 border border-[#EADBFC] dark:border-[#322742]"
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
                onClick={() => setIsPinned(!isPinned)}
                className={`p-2 rounded-xl border transition-all ${
                  isPinned
                    ? 'bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] border-[#D6BBFB]'
                    : 'bg-[#FAF8FB] dark:bg-[#120E17] text-[#797487] border-[#EADBFC] dark:border-[#322742]'
                }`}
              >
                <Pin className={`w-4 h-4 ${isPinned ? 'fill-current' : ''}`} />
              </button>
            </div>

            {/* Submit Save */}
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setShowDoneModal(false)}
                className="px-4 py-2 rounded-xl text-xs font-semibold text-[#797487] hover:bg-[#FAF8FB] dark:hover:bg-[#120E17]"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => {
                  setShowDoneModal(false);
                  handleAutoSaveAndClose();
                }}
                className="px-5 py-2 rounded-xl text-xs font-semibold text-white bg-gradient-to-r from-[#5925DC] to-[#9B51E0] shadow-sm flex items-center gap-1.5 active:scale-95"
              >
                <Check className="w-4 h-4 stroke-[2.5]" />
                <span>Save Note</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

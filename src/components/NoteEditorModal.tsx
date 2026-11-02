import React, { useState, useRef, useEffect } from 'react';
import { Note, ChecklistItem } from '../types';
import {
  Pin,
  Check,
  CheckSquare,
  Tag,
  Folder,
  Binary,
  Bold,
  Italic,
  List,
  Heading1,
  Heading2,
  ArrowLeft
} from 'lucide-react';
import { logger } from '../utils/logger';

interface NoteEditorModalProps {
  note: Note;
  customCategories: string[];
  onSave: (updatedNote: Note) => void;
  onClose: () => void;
  onInspectCipher: (note: Note) => void;
}

export const NoteEditorModal: React.FC<NoteEditorModalProps> = ({
  note,
  customCategories,
  onSave,
  onClose,
  onInspectCipher,
}) => {
  const [title, setTitle] = useState(note.title);
  const [category, setCategory] = useState(note.category || 'General');
  const [isPinned, setIsPinned] = useState(note.isPinned);
  const [tags, setTags] = useState<string[]>(note.tags || []);
  const [newTagInput, setNewTagInput] = useState('');

  // Active Size Mode ('small' | 'medium' | 'large')
  const [textSizeMode, setTextSizeMode] = useState<'small' | 'medium' | 'large'>('small');

  // Ref for Rich ContentEditable Editor
  const editorRef = useRef<HTMLDivElement | null>(null);
  const hasSavedRef = useRef(false);

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
   * Auto-Save & Close Handler
   * Triggers automatically on back button, gestural swipe, done click, or unmount
   */
  const handleAutoSaveAndClose = () => {
    if (hasSavedRef.current) return;
    hasSavedRef.current = true;

    const updated = getConstructedNote();
    onSave(updated);
    logger.security('NOTE', `Auto-saved local note: "${updated.title}" (ID: ${updated.id})`);
    onClose();
  };

  // Register Android native back button & gesture popstate listener
  useEffect(() => {
    // Push a dummy history state so Android back button / swipe back triggers popstate
    try {
      window.history.pushState({ irisModalOpen: true }, '');
    } catch {
      // ignore
    }

    const handlePopState = () => {
      handleAutoSaveAndClose();
    };

    window.addEventListener('popstate', handlePopState);

    return () => {
      window.removeEventListener('popstate', handlePopState);
      // Auto-save on component unmount
      if (!hasSavedRef.current) {
        handleAutoSaveAndClose();
      }
    };
  }, []);

  /**
   * WYSIWYG Formatting ExecCommands
   * Produces direct visual formatted text without raw markdown tags
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

  /**
   * Inline Text Size Change:
   * Changes ONLY selected text or new typed characters at cursor position without affecting text behind selection
   */
  const handleSelectSize = (targetMode: 'small' | 'medium' | 'large') => {
    setTextSizeMode(targetMode);

    const selection = window.getSelection();
    if (!selection || selection.rangeCount === 0) return;

    const sizePxMap = {
      small: '0.925rem',  // ~14.8px
      medium: '1.25rem',  // ~20px
      large: '1.65rem',   // ~26.4px
    };
    const targetSize = sizePxMap[targetMode];

    if (!selection.isCollapsed) {
      // Text is selected -> wrap ONLY selected text in a span with style.fontSize
      const range = selection.getRangeAt(0);
      const fontSpan = document.createElement('span');
      fontSpan.style.fontSize = targetSize;
      fontSpan.style.lineHeight = '1.35';

      try {
        const content = range.extractContents();
        fontSpan.appendChild(content);
        range.insertNode(fontSpan);

        selection.removeAllRanges();
        const newRange = document.createRange();
        newRange.selectNodeContents(fontSpan);
        selection.addRange(newRange);
      } catch {
        const execSizeMap = { small: '3', medium: '5', large: '6' };
        document.execCommand('fontSize', false, execSizeMap[targetMode]);
      }
    } else {
      // No text selected -> insert zero-width space span so newly typed characters inherit selected size
      const range = selection.getRangeAt(0);
      const fontSpan = document.createElement('span');
      fontSpan.style.fontSize = targetSize;
      fontSpan.style.lineHeight = '1.35';
      const zwsp = document.createTextNode('\u200B'); // zero-width space
      fontSpan.appendChild(zwsp);

      range.insertNode(fontSpan);

      // Position cursor inside fontSpan right after ZWSP
      const newRange = document.createRange();
      newRange.setStart(fontSpan, 1);
      newRange.setEnd(fontSpan, 1);
      selection.removeAllRanges();
      selection.addRange(newRange);
    }
  };

  /**
   * Creates a protected -1 position checklist line structure.
   * The outer container is contenteditable="false", keeping the checkbox permanently
   * at position -1 so characters can NEVER be typed behind or before the checkbox.
   */
  const createChecklistLine = (initialContent?: Node | string): HTMLDivElement => {
    const line = document.createElement('div');
    line.className = 'iris-checklist-line flex items-start gap-2.5 my-1.5 group select-none';
    line.setAttribute('contenteditable', 'false');
    line.setAttribute('data-checklist', 'true');

    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.className = 'w-4 h-4 rounded accent-[#5925DC] dark:accent-[#9D5AF5] cursor-pointer mt-1 shrink-0 border-[#EADBFC] dark:border-[#322742]';
    checkbox.setAttribute('contenteditable', 'false');

    const textDiv = document.createElement('div');
    textDiv.className = 'iris-checklist-text flex-1 focus:outline-none min-w-[20px] text-sm sm:text-base leading-relaxed text-[#1E1A24] dark:text-[#F7EEFC] select-text';
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

  /**
   * Redirects cursor focus into .iris-checklist-text if user clicks on line margins or before checkbox
   */
  const enforceChecklistCursorGuard = () => {
    const selection = window.getSelection();
    if (!selection || selection.rangeCount === 0) return;

    const anchor = selection.anchorNode as HTMLElement | null;

    if (anchor) {
      let current: HTMLElement | null = anchor.nodeType === Node.ELEMENT_NODE ? anchor : anchor.parentElement;
      while (current && current !== editorRef.current) {
        if (current.classList?.contains('iris-checklist-line')) {
          const textDiv = current.querySelector('.iris-checklist-text') as HTMLElement | null;
          if (textDiv && !textDiv.contains(selection.anchorNode)) {
            const newRange = document.createRange();
            newRange.setStart(textDiv, 0);
            newRange.collapse(true);
            selection.removeAllRanges();
            selection.addRange(newRange);
          }
          break;
        }
        current = current.parentElement;
      }
    }
  };

  /**
   * Top-Level Line Checklist Toggle:
   * Push 1: Converts top-level line to single checklist line with 1 checkbox at -1 position.
   * Push 2: Removes checklist line and converts back to standard text line, purging checkbox.
   * Regardless of which text inside that line is selected!
   */
  const handleChecklistClick = () => {
    if (!editorRef.current) return;
    editorRef.current.focus();

    const selection = window.getSelection();
    if (!selection || selection.rangeCount === 0) return;

    const range = selection.getRangeAt(0);

    // Helper to find top-level line container directly under editorRef
    const getTopLevelLine = (node: Node | null): HTMLElement | null => {
      let current = node as HTMLElement | null;
      while (current && current.parentElement && current.parentElement !== editorRef.current) {
        if (current.parentElement.classList?.contains('iris-checklist-line')) {
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

    const isCurrentlyChecklist = targetLines[0].classList?.contains('iris-checklist-line') || !!targetLines[0].querySelector('input[type="checkbox"]');

    targetLines.forEach((line) => {
      if (isCurrentlyChecklist) {
        // REMOVE CHECKBOX: Convert line back to plain editable div
        const textDiv = line.querySelector('.iris-checklist-text') || line;
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
        // ADD CHECKBOX: Convert line to single .iris-checklist-line
        if (!line.classList?.contains('iris-checklist-line')) {
          const contentFragment = document.createDocumentFragment();
          while (line.firstChild) {
            contentFragment.appendChild(line.firstChild);
          }
          if (contentFragment.childNodes.length === 0) {
            contentFragment.appendChild(document.createElement('br'));
          }

          const checkLine = createChecklistLine(contentFragment);
          line.parentNode?.replaceChild(checkLine, line);

          const textDiv = checkLine.querySelector('.iris-checklist-text') as HTMLElement;
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

  /**
   * Keyboard Enter handling inside checklist items:
   * Pressing Enter on a checklist line keeps the current line's checkbox intact,
   * and creates a new standard plain text line below it (disabled by default).
   * Checkboxes must always be manually triggered per line by clicking the Checklist button.
   */
  const handleKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'Enter') {
      const selection = window.getSelection();
      if (!selection || selection.rangeCount === 0) return;

      const range = selection.getRangeAt(0);
      let current = range.startContainer as HTMLElement | null;

      while (current && current !== editorRef.current) {
        if (current.classList?.contains('iris-checklist-text')) {
          e.preventDefault();

          const parentLine = current.parentElement as HTMLElement;

          // Create a new standard plain text line (disabled checkbox by default)
          const plainLine = document.createElement('div');
          plainLine.className = 'my-1 min-h-[1.5em] focus:outline-none';
          plainLine.setAttribute('contenteditable', 'true');
          plainLine.innerHTML = '<br>';

          if (parentLine.nextSibling) {
            parentLine.parentNode?.insertBefore(plainLine, parentLine.nextSibling);
          } else {
            parentLine.parentNode?.appendChild(plainLine);
          }

          // Move cursor focus into the new standard line
          const newRange = document.createRange();
          newRange.setStart(plainLine, 0);
          newRange.collapse(true);
          selection.removeAllRanges();
          selection.addRange(newRange);
          return;
        }
        current = current.parentElement;
      }
    }
  };

  const handleEditorClick = (e: React.MouseEvent<HTMLDivElement>) => {
    const target = e.target as HTMLElement;
    if (target && target.tagName === 'INPUT' && (target as HTMLInputElement).type === 'checkbox') {
      const checkbox = target as HTMLInputElement;
      const textDiv = checkbox.nextElementSibling as HTMLElement;
      if (textDiv) {
        if (checkbox.checked) {
          checkbox.setAttribute('checked', 'true');
          textDiv.style.textDecoration = 'line-through';
          textDiv.style.opacity = '0.6';
        } else {
          checkbox.removeAttribute('checked');
          textDiv.style.textDecoration = 'none';
          textDiv.style.opacity = '1';
        }
      }
    }
  };

  const handleAddTag = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault();
      const clean = newTagInput.trim().replace(/^#/, '');
      if (clean && !tags.includes(clean)) {
        setTags([...tags, clean]);
      }
      setNewTagInput('');
    }
  };

  const handleRemoveTag = (tagToRemove: string) => {
    setTags(tags.filter((t) => t !== tagToRemove));
  };

  return (
    <div className="fixed inset-0 z-50 flex flex-col w-full h-full bg-white dark:bg-[#1B1524] overflow-hidden animate-fadeIn">
      {/* 
        FULL-SCREEN MAXIMIZED WINDOW HEADER
      */}
      <div className="glass-panel px-4 sm:px-6 py-3 border-b border-[#EADBFC] dark:border-[#322742] flex items-center justify-between gap-2 shrink-0 bg-white/95 dark:bg-[#1B1524]/95">
        <div className="flex items-center gap-2 sm:gap-3 flex-wrap">
          {/* Back Arrow Button */}
          <button
            type="button"
            onClick={handleAutoSaveAndClose}
            aria-label="Save and return"
            className="min-h-[40px] min-w-[40px] p-2 rounded-xl bg-white dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] text-[#1E1A24] dark:text-[#F7EEFC] hover:text-[#5925DC] dark:hover:text-[#C296FD] flex items-center justify-center transition-all cursor-pointer active:scale-95"
            title="Auto-Save & Close"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>

          {/* Category Selector */}
          <div className="flex items-center gap-1 px-2.5 py-1.5 rounded-xl bg-white dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] text-xs font-semibold text-[#1E1A24] dark:text-[#F7EEFC] min-h-[40px]">
            <Folder className="w-3.5 h-3.5 text-[#5925DC] dark:text-[#C296FD]" />
            <select
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              className="bg-transparent focus:outline-none cursor-pointer pr-1 text-xs"
            >
              {allCategoryOptions.map((cat) => (
                <option key={cat} value={cat} className="dark:bg-[#1B1524]">
                  {cat}
                </option>
              ))}
            </select>
          </div>

          {/* Pin Toggle */}
          <button
            type="button"
            onClick={() => setIsPinned(!isPinned)}
            aria-label={isPinned ? 'Unpin note' : 'Pin note'}
            className={`min-h-[40px] min-w-[40px] flex items-center justify-center p-2 rounded-xl border transition-all cursor-pointer ${
              isPinned
                ? 'bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] border-[#D6BBFB] dark:border-[#7D49D4]'
                : 'bg-white dark:bg-[#120E17] text-[#797487] dark:text-[#A39BAC] border-[#EADBFC] dark:border-[#322742]'
            }`}
            title={isPinned ? 'Pinned to top' : 'Pin note'}
          >
            <Pin className={`w-4 h-4 ${isPinned ? 'fill-current' : ''}`} />
          </button>
        </div>

        <div className="flex items-center gap-2">
          {/* Inspect Cipher */}
          <button
            type="button"
            onClick={() => onInspectCipher(getConstructedNote())}
            aria-label="Inspect AES Cipher"
            className="min-h-[40px] px-3 py-2 rounded-xl bg-white dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] text-[#5925DC] dark:text-[#C296FD] text-xs font-semibold flex items-center gap-1.5 shadow-xs cursor-pointer"
            title="Inspect AES-256-GCM cipher payload"
          >
            <Binary className="w-4 h-4" />
            <span className="hidden sm:inline">Cipher</span>
          </button>

          {/* Save & Done */}
          <button
            type="button"
            onClick={handleAutoSaveAndClose}
            className="min-h-[40px] px-4 py-2 rounded-xl bg-gradient-to-r from-[#5925DC] to-[#9B51E0] dark:from-[#9D5AF5] dark:to-[#C296FD] text-white text-xs font-semibold shadow-xs flex items-center gap-1.5 transition-all active:scale-95 cursor-pointer"
          >
            <Check className="w-4 h-4 stroke-[2.5]" />
            <span>Done</span>
          </button>
        </div>
      </div>

      {/* 
        MAXIMIZED FULL-PAGE WRITING CANVAS
      */}
      <div className="p-4 sm:p-8 overflow-y-auto space-y-5 flex-1 max-w-4xl mx-auto w-full">
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
          Shows rendered bold, italic, inline sizes & lists directly
        */}
        <div
          ref={editorRef}
          contentEditable={true}
          onClick={(e) => {
            handleEditorClick(e);
            enforceChecklistCursorGuard();
          }}
          onKeyUp={enforceChecklistCursorGuard}
          onKeyDown={handleKeyDown}
          aria-label="Note content editor"
          data-placeholder="Start typing your note here..."
          className="w-full min-h-[360px] text-sm sm:text-base leading-relaxed text-[#1E1A24] dark:text-[#F7EEFC] focus:outline-none bg-transparent font-normal space-y-2 [&_h1]:text-2xl [&_h1]:font-bold [&_h1]:my-2 [&_h2]:text-xl [&_h2]:font-semibold [&_h2]:my-1.5 [&_ul]:list-disc [&_ul]:pl-6 [&_ul]:space-y-1 [&_b]:font-bold [&_strong]:font-bold [&_i]:italic [&_em]:italic empty:before:content-[attr(data-placeholder)] empty:before:text-[#797487]/50 empty:before:dark:text-[#A39BAC]/50"
        />

        {/* Tags Section */}
        <div className="pt-2">
          <div className="flex items-center gap-1.5 flex-wrap mb-2">
            {tags.map((tag) => (
              <span
                key={tag}
                className="inline-flex items-center gap-1 text-xs font-medium px-2.5 py-1 rounded-lg bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] border border-[#EADBFC] dark:border-[#322742]"
              >
                <Tag className="w-3 h-3" />
                <span>{tag}</span>
                <button
                  type="button"
                  onClick={() => handleRemoveTag(tag)}
                  className="hover:text-rose-600 dark:hover:text-rose-400 ml-0.5"
                  aria-label={`Remove tag ${tag}`}
                >
                  ✕
                </button>
              </span>
            ))}
          </div>

          <input
            type="text"
            placeholder="Add tag and press Enter..."
            value={newTagInput}
            onChange={(e) => setNewTagInput(e.target.value)}
            onKeyDown={handleAddTag}
            className="text-xs sm:text-sm px-3.5 py-2 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] text-[#1E1A24] dark:text-[#F7EEFC] focus:outline-none focus:border-[#5925DC] dark:focus:border-[#9D5AF5] w-56 min-h-[42px]"
          />
        </div>
      </div>

      {/* 
        STICKY MOBILE KEYBOARD FORMATTING TOOLBAR
      */}
      <div className="sticky bottom-0 z-30 p-2 sm:p-3 bg-white/95 dark:bg-[#1B1524]/95 backdrop-blur-md border-t border-[#EADBFC] dark:border-[#322742] flex items-center justify-between gap-1 overflow-x-auto custom-scrollbar shadow-lg shrink-0 max-w-4xl mx-auto w-full">
        <div className="flex items-center gap-1.5">
          {/* Thick / Bold */}
          <button
            type="button"
            onMouseDown={(e) => e.preventDefault()}
            onClick={handleBoldClick}
            aria-label="Make text thick and bold"
            className="min-h-[44px] min-w-[44px] p-2 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] hover:bg-[#F4EBFF] dark:hover:bg-[#2B1E3E] text-[#1E1A24] dark:text-[#F7EEFC] hover:text-[#5925DC] dark:hover:text-[#C296FD] border border-[#EADBFC] dark:border-[#322742] flex items-center justify-center transition-all cursor-pointer active:scale-95"
            title="Thick / Bold"
          >
            <Bold className="w-4 h-4 stroke-[2.5]" />
          </button>

          {/* Italic */}
          <button
            type="button"
            onMouseDown={(e) => e.preventDefault()}
            onClick={handleItalicClick}
            aria-label="Make text italic"
            className="min-h-[44px] min-w-[44px] p-2 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] hover:bg-[#F4EBFF] dark:hover:bg-[#2B1E3E] text-[#1E1A24] dark:text-[#F7EEFC] hover:text-[#5925DC] dark:hover:text-[#C296FD] border border-[#EADBFC] dark:border-[#322742] flex items-center justify-center transition-all cursor-pointer active:scale-95"
            title="Italic"
          >
            <Italic className="w-4 h-4 stroke-[2.2]" />
          </button>

          {/* 3 Distinct Text Size Buttons (Mutually Exclusive) */}
          <div className="flex items-center p-0.5 rounded-2xl bg-[#FAF8FB] dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742]">
            {/* Small / Normal Button */}
            <button
              type="button"
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => handleSelectSize('small')}
              aria-label="Set text size to Small"
              className={`min-h-[40px] px-2.5 rounded-xl text-xs transition-all cursor-pointer ${
                textSizeMode === 'small'
                  ? 'bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] shadow-xs font-bold border border-[#D6BBFB] dark:border-[#7D49D4]'
                  : 'text-[#484455] dark:text-[#A39BAC] font-medium hover:text-[#1E1A24] dark:hover:text-white'
              }`}
              title="Small / Normal Text Size"
            >
              Small
            </button>

            {/* Medium / Subtitle Button */}
            <button
              type="button"
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => handleSelectSize('medium')}
              aria-label="Set text size to Medium"
              className={`min-h-[40px] px-2.5 rounded-xl text-xs transition-all cursor-pointer flex items-center gap-1 ${
                textSizeMode === 'medium'
                  ? 'bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] shadow-xs font-bold border border-[#D6BBFB] dark:border-[#7D49D4]'
                  : 'text-[#484455] dark:text-[#A39BAC] font-medium hover:text-[#1E1A24] dark:hover:text-white'
              }`}
              title="Medium / Subtitle Size"
            >
              <Heading2 className="w-3.5 h-3.5" />
              <span>Medium</span>
            </button>

            {/* Large / Title Button */}
            <button
              type="button"
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => handleSelectSize('large')}
              aria-label="Set text size to Large"
              className={`min-h-[40px] px-2.5 rounded-xl text-xs transition-all cursor-pointer flex items-center gap-1 ${
                textSizeMode === 'large'
                  ? 'bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] shadow-xs font-bold border border-[#D6BBFB] dark:border-[#7D49D4]'
                  : 'text-[#484455] dark:text-[#A39BAC] font-medium hover:text-[#1E1A24] dark:hover:text-white'
              }`}
              title="Large / Title Size"
            >
              <Heading1 className="w-3.5 h-3.5" />
              <span>Large</span>
            </button>
          </div>

          {/* Outline / Bullet List */}
          <button
            type="button"
            onMouseDown={(e) => e.preventDefault()}
            onClick={handleOutlineClick}
            aria-label="Add outline bullet list item"
            className="min-h-[44px] min-w-[44px] p-2 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] hover:bg-[#F4EBFF] dark:hover:bg-[#2B1E3E] text-[#1E1A24] dark:text-[#F7EEFC] hover:text-[#5925DC] dark:hover:text-[#C296FD] border border-[#EADBFC] dark:border-[#322742] flex items-center justify-center transition-all cursor-pointer active:scale-95"
            title="Outline Bullet List"
          >
            <List className="w-4 h-4" />
          </button>

          {/* Checklist */}
          <button
            type="button"
            onMouseDown={(e) => e.preventDefault()}
            onClick={handleChecklistClick}
            aria-label="Insert inline checklist item"
            className="min-h-[44px] min-w-[44px] p-2 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] hover:bg-[#F4EBFF] dark:hover:bg-[#2B1E3E] text-[#1E1A24] dark:text-[#F7EEFC] hover:text-[#5925DC] dark:hover:text-[#C296FD] border border-[#EADBFC] dark:border-[#322742] flex items-center justify-center transition-all cursor-pointer active:scale-95"
            title="Insert Inline Checklist Item"
          >
            <CheckSquare className="w-4 h-4" />
          </button>
        </div>

        <div className="text-[10px] font-semibold text-[#797487] dark:text-[#A39BAC] hidden sm:block pr-1">
          Formatting Bar
        </div>
      </div>
    </div>
  );
};

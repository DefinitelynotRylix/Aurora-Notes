import React, { useState, useRef, useEffect, useCallback } from 'react';
import { motion } from 'motion/react';
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
  Share2,
  ArchiveRestore,
  Trash2,
  Lock
} from 'lucide-react';
import { logger } from '../utils/logger';
import {
  convertPlainTextToLinkedHtml,
  linkifyDomContainer,
  openUrlInDefaultBrowser,
} from '../utils/linkUtils';
import { ShareNoteModal } from './ShareNoteModal';

interface NoteEditorModalProps {
  note: Note;
  customCategories: string[];
  revealOrigin?: { x: number; y: number; radius: number } | null;
  onSave: (updatedNote: Note) => void;
  onUnarchive?: (id: string) => void;
  onDelete?: (id: string) => void;
  onClose: () => void;
}

export const NoteEditorModal: React.FC<NoteEditorModalProps> = ({
  note,
  customCategories,
  revealOrigin,
  onSave,
  onUnarchive,
  onDelete,
  onClose,
}) => {
  const isReadOnly = Boolean(note.isArchived);
  const [title, setTitle] = useState(note.title);
  const [category, setCategory] = useState(note.category || 'General');
  const [isPinned, setIsPinned] = useState(note.isPinned);
  const [tags, setTags] = useState<string[]>(note.tags || []);
  const [newTagInput, setNewTagInput] = useState('');
  const [showDoneModal, setShowDoneModal] = useState(false);
  const [showShareModal, setShowShareModal] = useState(false);

  // Active Size Mode ('small' | 'medium' | 'large')
  const [textSizeMode, setTextSizeMode] = useState<'small' | 'medium' | 'large'>('small');

  // Tracks whether the current cursor line has an active checkbox in its -1 slot
  const [isCurrentLineChecklist, setIsCurrentLineChecklist] = useState(false);

  // Predictive Back Gesture State (0..1 progress & swipe edge)
  const [predictiveBackProgress, setPredictiveBackProgress] = useState(0);
  const [predictiveSwipeEdge, setPredictiveSwipeEdge] = useState<'left' | 'right'>('left');
  const edgeSwipeStartRef = useRef<{ x: number; y: number; edge: 'left' | 'right' } | null>(null);

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

  const bindCheckboxBehavior = useCallback(
    (checkbox: HTMLInputElement, textDiv?: HTMLElement | null) => {
      checkbox.setAttribute('contenteditable', 'false');
      checkbox.disabled = isReadOnly;
      const targetTextDiv =
        textDiv || (checkbox.nextElementSibling as HTMLElement | null);
      if (targetTextDiv && isReadOnly) {
        targetTextDiv.setAttribute('contenteditable', 'false');
      }
      const applyVisualState = (checked: boolean) => {
        if (checked) {
          checkbox.setAttribute('checked', 'checked');
        } else {
          checkbox.removeAttribute('checked');
        }
        if (targetTextDiv) {
          targetTextDiv.style.textDecoration = checked ? 'line-through' : 'none';
          targetTextDiv.style.opacity = checked ? '0.6' : '1';
        }
      };

      const initialChecked = checkbox.hasAttribute('checked') || checkbox.checked;
      checkbox.checked = initialChecked;
      applyVisualState(initialChecked);

      checkbox.onchange = () => {
        if (isReadOnly) return;
        applyVisualState(checkbox.checked);
      };
    },
    [isReadOnly]
  );

  // Populate editor initial content on mount & sync checkboxes and clickable purple links
  useEffect(() => {
    if (editorRef.current) {
      editorRef.current.innerHTML = note.content || '';
      linkifyDomContainer(editorRef.current);

      const checkboxes = editorRef.current.querySelectorAll('input[type="checkbox"]');
      checkboxes.forEach((cb) => {
        bindCheckboxBehavior(cb as HTMLInputElement);
      });
    }
  }, [note.id, isReadOnly, bindCheckboxBehavior]);

  /**
   * Constructs latest note object from state & editor
   */
  const getConstructedNote = (): Note => {
    if (editorRef.current) {
      linkifyDomContainer(editorRef.current);
    }
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

    if (isReadOnly) {
      onClose();
      return;
    }

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

  const createChecklistLine = (
    initialContent?: Node | string,
    checked: boolean = false
  ): HTMLDivElement => {
    const line = document.createElement('div');
    line.className =
      'aurora-checklist-line flex items-start gap-2.5 my-1.5 group select-none';
    line.setAttribute('contenteditable', 'false');
    line.setAttribute('data-checklist', 'true');

    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.checked = checked;
    if (checked) {
      checkbox.setAttribute('checked', 'checked');
    }
    checkbox.className =
      'w-4 h-4 rounded accent-[#5925DC] dark:accent-[#9D5AF5] cursor-pointer mt-1 shrink-0 border-[#EADBFC] dark:border-[#322742]';
    checkbox.setAttribute('contenteditable', 'false');

    const textDiv = document.createElement('div');
    textDiv.className =
      'aurora-checklist-text flex-1 focus:outline-none min-w-[20px] text-sm sm:text-base leading-relaxed text-[#1E1A24] dark:text-[#F7EEFC] select-text';
    textDiv.setAttribute('contenteditable', 'true');

    if (initialContent) {
      if (typeof initialContent === 'string') {
        textDiv.innerHTML = initialContent || '<br>';
      } else {
        textDiv.appendChild(initialContent);
        if (!textDiv.innerHTML.trim()) {
          textDiv.innerHTML = '<br>';
        }
      }
    } else {
      textDiv.innerHTML = '<br>';
    }

    line.appendChild(checkbox);
    line.appendChild(textDiv);
    bindCheckboxBehavior(checkbox, textDiv);

    return line;
  };

  /**
   * Normalizes top-level bare text nodes or <br>-separated lines inside editorRef
   * into block-level <div> elements so every line can independently have a checkbox
   * enabled or disabled in its -1 prefix slot.
   */
  const normalizeEditorLines = () => {
    const root = editorRef.current;
    if (!root) return;

    const children = Array.from(root.childNodes);
    const hasBareInlineNodes = children.some(
      (node) =>
        node.nodeType === Node.TEXT_NODE ||
        (node.nodeType === Node.ELEMENT_NODE &&
          !['DIV', 'P', 'UL', 'OL', 'H1', 'H2', 'H3'].includes(
            (node as HTMLElement).tagName
          ))
    );

    if (!hasBareInlineNodes) return;

    let currentBlock: HTMLDivElement | null = null;
    children.forEach((node) => {
      if (
        node.nodeType === Node.ELEMENT_NODE &&
        ['DIV', 'P', 'UL', 'OL', 'H1', 'H2', 'H3'].includes(
          (node as HTMLElement).tagName
        )
      ) {
        currentBlock = null;
        return;
      }

      if (
        node.nodeType === Node.ELEMENT_NODE &&
        (node as HTMLElement).tagName === 'BR'
      ) {
        if (!currentBlock) {
          const emptyLine = document.createElement('div');
          emptyLine.className = 'my-1 focus:outline-none min-h-[1.5em]';
          emptyLine.setAttribute('contenteditable', 'true');
          emptyLine.innerHTML = '<br>';
          root.replaceChild(emptyLine, node);
        } else {
          if (!currentBlock.innerHTML.trim()) {
            currentBlock.innerHTML = '<br>';
          }
          root.removeChild(node);
          currentBlock = null;
        }
        return;
      }

      if (
        node.nodeType === Node.TEXT_NODE &&
        !(node.textContent || '').trim() &&
        children.length > 1 &&
        !currentBlock
      ) {
        return;
      }

      if (!currentBlock) {
        currentBlock = document.createElement('div');
        currentBlock.className = 'my-1 focus:outline-none min-h-[1.5em]';
        currentBlock.setAttribute('contenteditable', 'true');
        root.insertBefore(currentBlock, node);
      }
      currentBlock.appendChild(node);
    });
  };

  /**
   * Resolves the top-level line element inside editorRef containing `node`,
   * and enforces the -1 prefix slot rule so the caret never lands before the checkbox.
   */
  const resolveTopLevelLine = useCallback(
    (node: Node | null): HTMLElement | null => {
      const root = editorRef.current;
      if (!root || !node) return null;
      if (node === root) {
        return (root.lastElementChild as HTMLElement) || null;
      }
      let curr: Node | null = node;
      while (curr && curr.parentNode && curr.parentNode !== root) {
        if (
          curr.parentNode instanceof HTMLElement &&
          curr.parentNode.classList.contains('aurora-checklist-line')
        ) {
          return curr.parentNode;
        }
        curr = curr.parentNode;
      }
      if (curr && curr.parentNode === root && curr instanceof HTMLElement) {
        return curr;
      }
      return null;
    },
    []
  );

  /**
   * Updates `isCurrentLineChecklist` state and clamps the caret out of the -1 checkbox slot.
   */
  const syncCursorLineStateAndEnforceMinusOneRule = useCallback(() => {
    const root = editorRef.current;
    if (!root) return;
    const selection = window.getSelection();
    if (!selection || selection.rangeCount === 0) return;

    const range = selection.getRangeAt(0);
    if (!root.contains(range.startContainer)) return;

    const line = resolveTopLevelLine(range.startContainer);
    const hasCheckbox = Boolean(
      line &&
        (line.classList.contains('aurora-checklist-line') ||
          line.querySelector('input[type="checkbox"]'))
    );
    setIsCurrentLineChecklist(hasCheckbox);

    // Enforce the -1 rule: if the caret landed directly on the checklist container or checkbox
    // (before .aurora-checklist-text), move it to position 0 inside .aurora-checklist-text.
    if (line && hasCheckbox && range.collapsed) {
      const textDiv = line.querySelector('.aurora-checklist-text') as HTMLElement | null;
      if (textDiv && !textDiv.contains(range.startContainer)) {
        const clampedRange = document.createRange();
        clampedRange.setStart(textDiv, 0);
        clampedRange.collapse(true);
        selection.removeAllRanges();
        selection.addRange(clampedRange);
      }
    }
  }, [resolveTopLevelLine]);

  /**
   * Converts a checklist line back into a normal editable line (disabling its checkbox).
   */
  const disableCheckboxOnLine = (
    line: HTMLElement,
    placeCaretAtStart: boolean = false
  ): HTMLDivElement => {
    const textDiv = (line.querySelector('.aurora-checklist-text') || line) as HTMLElement;
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

    const selection = window.getSelection();
    if (selection) {
      const newRange = document.createRange();
      newRange.selectNodeContents(plainLine);
      newRange.collapse(placeCaretAtStart);
      selection.removeAllRanges();
      selection.addRange(newRange);
    }
    setIsCurrentLineChecklist(false);
    return plainLine;
  };

  /**
   * Toggles the checkbox on the specific line(s) where the cursor is located,
   * following the -1 prefix slot rule (at most one checkbox per line; pressing again disables it).
   */
  const handleChecklistClick = () => {
    const root = editorRef.current;
    if (!root || isReadOnly) return;
    root.focus();

    normalizeEditorLines();

    const selection = window.getSelection();
    if (!selection) return;

    let range: Range;
    if (selection.rangeCount === 0 || !root.contains(selection.getRangeAt(0).startContainer)) {
      range = document.createRange();
      range.selectNodeContents(root);
      range.collapse(false);
      selection.removeAllRanges();
      selection.addRange(range);
    } else {
      range = selection.getRangeAt(0);
    }

    let startLine = resolveTopLevelLine(range.startContainer);
    let endLine = resolveTopLevelLine(range.endContainer);

    if (!startLine) {
      const plainDiv = document.createElement('div');
      plainDiv.className = 'my-1 focus:outline-none min-h-[1.5em]';
      plainDiv.setAttribute('contenteditable', 'true');
      if (!range.collapsed) {
        plainDiv.appendChild(range.extractContents());
      } else if (root.textContent && root.childNodes.length > 0) {
        while (root.firstChild) {
          plainDiv.appendChild(root.firstChild);
        }
      } else {
        plainDiv.innerHTML = '<br>';
      }
      root.appendChild(plainDiv);
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

    const isCurrentlyChecklist =
      targetLines[0].classList?.contains('aurora-checklist-line') ||
      Boolean(targetLines[0].querySelector('input[type="checkbox"]'));

    targetLines.forEach((line) => {
      if (isCurrentlyChecklist) {
        // Pressing the checkbox button again on this specific line disables the checkbox
        disableCheckboxOnLine(line, false);
      } else {
        // Enable checkbox in the -1 prefix slot of this specific line
        if (!line.classList?.contains('aurora-checklist-line')) {
          const contentFragment = document.createDocumentFragment();
          if (line.nodeType === Node.TEXT_NODE) {
            contentFragment.appendChild(line.cloneNode(true));
          } else {
            while (line.firstChild) {
              if (
                line.firstChild instanceof HTMLElement &&
                line.firstChild.getAttribute('type') === 'checkbox'
              ) {
                line.removeChild(line.firstChild);
              } else {
                contentFragment.appendChild(line.firstChild);
              }
            }
          }
          if (contentFragment.childNodes.length === 0) {
            contentFragment.appendChild(document.createElement('br'));
          }

          const checkLine = createChecklistLine(contentFragment, false);
          line.parentNode?.replaceChild(checkLine, line);

          const textDiv = checkLine.querySelector(
            '.aurora-checklist-text'
          ) as HTMLElement | null;
          if (textDiv) {
            const newRange = document.createRange();
            newRange.selectNodeContents(textDiv);
            newRange.collapse(false);
            selection.removeAllRanges();
            selection.addRange(newRange);
          }
          setIsCurrentLineChecklist(true);
        }
      }
    });
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    const selection = window.getSelection();
    if (!selection || selection.rangeCount === 0) return;

    const range = selection.getRangeAt(0);
    let current = range.startContainer as HTMLElement | null;

    if (e.key === 'Backspace' && range.collapsed) {
      while (current && current !== editorRef.current) {
        if (current.classList?.contains('aurora-checklist-text')) {
          // Check if cursor is at position 0 (right after the -1 checkbox slot)
          const preRange = range.cloneRange();
          preRange.selectNodeContents(current);
          preRange.setEnd(range.startContainer, range.startOffset);
          const textBeforeCursor = (preRange.toString() || '').replace(/\u200B/g, '');
          if (textBeforeCursor.length === 0) {
            e.preventDefault();
            const parentLine = current.parentElement as HTMLElement | null;
            if (parentLine && parentLine.classList.contains('aurora-checklist-line')) {
              disableCheckboxOnLine(parentLine, true);
            }
            return;
          }
          break;
        }
        current = current.parentElement;
      }
    }

    current = range.startContainer as HTMLElement | null;
    if (e.key === 'Enter') {
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
          setIsCurrentLineChecklist(false);
          requestAnimationFrame(ensureCaretVisible);
          return;
        }
        current = current.parentElement;
      }
    }
    requestAnimationFrame(() => {
      syncCursorLineStateAndEnforceMinusOneRule();
      ensureCaretVisible();
    });
  };

  const handleEditorPaste = (e: React.ClipboardEvent<HTMLDivElement>) => {
    if (isReadOnly) {
      e.preventDefault();
      return;
    }
    const plainText = e.clipboardData?.getData('text/plain') || '';
    if (plainText && /((?:https?:\/\/|www\.)[^\s<>"']+)/i.test(plainText)) {
      e.preventDefault();
      const linkedHtml = convertPlainTextToLinkedHtml(plainText);
      document.execCommand('insertHTML', false, `${linkedHtml}&nbsp;`);
      requestAnimationFrame(ensureCaretVisible);
    } else {
      setTimeout(() => {
        if (editorRef.current) {
          linkifyDomContainer(editorRef.current);
        }
      }, 0);
    }
  };

  const handleEditorClick = (e: React.MouseEvent<HTMLDivElement>) => {
    const target = e.target as HTMLElement | null;
    if (target) {
      if (target instanceof HTMLInputElement && target.type === 'checkbox') {
        bindCheckboxBehavior(target);
        return;
      }
      const anchor = target.closest('a') as HTMLAnchorElement | null;
      if (anchor) {
        const href = anchor.getAttribute('href') || anchor.textContent || '';
        if (href) {
          e.preventDefault();
          e.stopPropagation();
          openUrlInDefaultBrowser(href);
          return;
        }
      }
    }
    if (!isReadOnly) {
      requestAnimationFrame(() => {
        syncCursorLineStateAndEnforceMinusOneRule();
        ensureCaretVisible();
      });
    }
  };

  // Interactive Predictive Back Edge-Swipe Handlers
  const handleEdgePointerDown = (e: React.PointerEvent<HTMLDivElement>) => {
    if (showDoneModal || showShareModal) return;
    const edgeThreshold = 28;
    const vw = typeof window !== 'undefined' ? window.innerWidth : 400;
    if (e.clientX <= edgeThreshold) {
      edgeSwipeStartRef.current = { x: e.clientX, y: e.clientY, edge: 'left' };
      setPredictiveSwipeEdge('left');
    } else if (e.clientX >= vw - edgeThreshold) {
      edgeSwipeStartRef.current = { x: e.clientX, y: e.clientY, edge: 'right' };
      setPredictiveSwipeEdge('right');
    } else {
      edgeSwipeStartRef.current = null;
    }
  };

  const handleEdgePointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
    const start = edgeSwipeStartRef.current;
    if (!start) return;
    const dx =
      start.edge === 'left' ? e.clientX - start.x : start.x - e.clientX;
    const dy = Math.abs(e.clientY - start.y);
    if (dx < 0 || (dy > dx * 1.4 && predictiveBackProgress === 0)) {
      edgeSwipeStartRef.current = null;
      setPredictiveBackProgress(0);
      return;
    }
    const maxSwipe = 220;
    const progress = Math.max(0, Math.min(1, dx / maxSwipe));
    setPredictiveBackProgress(progress);
  };

  const handleEdgePointerUp = () => {
    if (!edgeSwipeStartRef.current) return;
    edgeSwipeStartRef.current = null;
    if (predictiveBackProgress >= 0.35) {
      setPredictiveBackProgress(0);
      handleAutoSaveAndClose();
    } else {
      setPredictiveBackProgress(0);
    }
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

  const originX =
    revealOrigin?.x ??
    (typeof window !== 'undefined' ? window.innerWidth - 44 : 320);
  const originY =
    revealOrigin?.y ??
    (typeof window !== 'undefined' ? window.innerHeight - 120 : 640);
  const originRadius = revealOrigin?.radius ?? 28;
  const maxRevealRadius =
    typeof window !== 'undefined'
      ? Math.ceil(
          Math.hypot(
            Math.max(originX, window.innerWidth - originX),
            Math.max(originY, window.innerHeight - originY)
          ) + 48
        )
      : 1600;

  const backScale = 1 - predictiveBackProgress * 0.12;
  const backTranslateX =
    predictiveBackProgress > 0
      ? (predictiveSwipeEdge === 'left' ? 1 : -1) * predictiveBackProgress * 36
      : 0;
  const backBorderRadius = predictiveBackProgress > 0 ? predictiveBackProgress * 28 : 0;

  return (
    <motion.div
      initial={{
        clipPath: `circle(${originRadius}px at ${originX}px ${originY}px)`,
      }}
      animate={{
        clipPath: `circle(${maxRevealRadius}px at ${originX}px ${originY}px)`,
      }}
      exit={{
        clipPath: `circle(${originRadius}px at ${originX}px ${originY}px)`,
        opacity: 0,
      }}
      transition={{
        duration: 0.42,
        ease: [0.22, 1, 0.36, 1],
      }}
      onPointerDown={handleEdgePointerDown}
      onPointerMove={handleEdgePointerMove}
      onPointerUp={handleEdgePointerUp}
      onPointerCancel={handleEdgePointerUp}
      style={
        predictiveBackProgress > 0
          ? {
              transform: `translateX(${backTranslateX}px) scale(${backScale})`,
              borderRadius: `${backBorderRadius}px`,
            }
          : undefined
      }
      className="fixed inset-0 z-50 flex flex-col w-full h-full bg-white dark:bg-[#1B1524] overflow-hidden shadow-2xl"
    >
      {/* Smooth Purple Circle Morph Overlay from the Create Note Circle Button */}
      <motion.div
        initial={{ opacity: 0.88 }}
        animate={{ opacity: 0 }}
        exit={{ opacity: 0.85 }}
        transition={{ duration: 0.24, ease: 'easeOut' }}
        className="pointer-events-none absolute inset-0 z-50 bg-gradient-to-tr from-[#5925DC] via-[#7B35E8] to-[#9B51E0]"
      />
      {/* 
        CLEAN FULL-SCREEN HEADER (PERFECT ALIGNMENT WITH SAFE AREA)
      */}
      <div className="px-4 pt-3.5 pb-3 border-b border-[#EADBFC] dark:border-[#322742] flex items-center justify-between gap-3 shrink-0 bg-white/95 dark:bg-[#1B1524]/95 shadow-xs min-h-[56px]">
        <div className="flex items-center gap-2">
          {/* Back Button */}
          <button
            type="button"
            onClick={handleAutoSaveAndClose}
            aria-label={isReadOnly ? 'Return to Archive' : 'Save and return'}
            className="w-9 h-9 rounded-xl bg-white dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] text-[#1E1A24] dark:text-[#F7EEFC] hover:text-[#5925DC] dark:hover:text-[#C296FD] flex items-center justify-center transition-all cursor-pointer active:scale-95 shrink-0"
            title={isReadOnly ? 'Close' : 'Auto-Save & Close'}
          >
            <ArrowLeft className="w-5 h-5" />
          </button>

          {/* Current Category Pill or Read-Only Badge */}
          {isReadOnly ? (
            <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#F4EBFF] dark:bg-[#2B1E3E] text-xs font-bold text-[#5925DC] dark:text-[#C296FD] border border-[#EADBFC]/80 dark:border-[#322742]">
              <Lock className="w-3.5 h-3.5" />
              <span>Archived • Read-Only</span>
            </div>
          ) : (
            <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#F4EBFF] dark:bg-[#2B1E3E] text-xs font-semibold text-[#5925DC] dark:text-[#C296FD] border border-[#EADBFC]/80 dark:border-[#322742]">
              <Folder className="w-3.5 h-3.5" />
              <span className="truncate max-w-[100px] sm:max-w-[150px]">{category}</span>
            </div>
          )}
        </div>

        {/* Right side: Restore + Delete when archived, or Share + Done when active */}
        <div className="flex items-center gap-2">
          {isReadOnly ? (
            <>
              {onUnarchive && (
                <button
                  type="button"
                  onClick={() => {
                    hasSavedRef.current = true;
                    onUnarchive(note.id);
                    onClose();
                  }}
                  className="px-3.5 py-2 rounded-xl bg-[#5925DC] dark:bg-[#9D5AF5] text-white text-xs font-bold shadow-xs flex items-center gap-1.5 transition-all active:scale-95 cursor-pointer"
                >
                  <ArchiveRestore className="w-3.5 h-3.5" />
                  <span>Restore</span>
                </button>
              )}
              {onDelete && (
                <button
                  type="button"
                  onClick={() => {
                    hasSavedRef.current = true;
                    onDelete(note.id);
                    onClose();
                  }}
                  className="px-3.5 py-2 rounded-xl bg-rose-600 text-white text-xs font-bold shadow-xs flex items-center gap-1.5 transition-all active:scale-95 cursor-pointer"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                  <span>Delete</span>
                </button>
              )}
            </>
          ) : (
            <>
              <button
                type="button"
                onClick={() => setShowShareModal(true)}
                aria-label="Share or export note"
                className="w-9 h-9 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] text-[#5925DC] dark:text-[#C296FD] hover:bg-[#F4EBFF] dark:hover:bg-[#2B1E3E] flex items-center justify-center transition-all active:scale-95 cursor-pointer"
                title="Share note"
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
            </>
          )}
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
        {isReadOnly && (
          <div className="p-3 rounded-xl bg-[#F4EBFF]/70 dark:bg-[#2B1E3E]/60 border border-[#EADBFC] dark:border-[#322742] text-xs font-medium text-[#5925DC] dark:text-[#C296FD]">
            This archived note is read-only. Tap <strong>Restore</strong> above to edit it again, or <strong>Delete</strong> to move it to Trash.
          </div>
        )}

        {/* Note Title Input */}
        <input
          type="text"
          readOnly={isReadOnly}
          aria-label="Note title"
          placeholder="Title"
          value={title}
          onChange={(e) => !isReadOnly && setTitle(e.target.value)}
          className="w-full text-2xl sm:text-3xl font-bold text-[#1E1A24] dark:text-[#F7EEFC] placeholder-[#797487]/50 dark:placeholder-[#A39BAC]/50 border-none focus:outline-none focus:ring-0 bg-transparent tracking-tight py-1"
        />

        {/* 
          WYSIWYG RICH CONTENT-EDITABLE CANVAS
        */}
        <div
          ref={editorRef}
          contentEditable={!isReadOnly}
          onPaste={handleEditorPaste}
          onBlur={() => {
            if (editorRef.current) {
              linkifyDomContainer(editorRef.current);
            }
          }}
          onKeyDown={isReadOnly ? undefined : handleKeyDown}
          onKeyUp={
            isReadOnly
              ? undefined
              : (e) => {
                  if ((e.key === ' ' || e.key === 'Enter') && editorRef.current) {
                    linkifyDomContainer(editorRef.current);
                  }
                  requestAnimationFrame(ensureCaretVisible);
                }
          }
          onInput={isReadOnly ? undefined : () => requestAnimationFrame(ensureCaretVisible)}
          onClick={handleEditorClick}
          aria-label="Note content editor"
          data-placeholder="Start typing your note here..."
          className="w-full min-h-[360px] pb-36 text-sm sm:text-base leading-relaxed text-[#1E1A24] dark:text-[#F7EEFC] focus:outline-none bg-transparent font-normal space-y-2 [&_a]:text-[#8B3DFF] dark:[&_a]:text-[#C296FD] [&_a]:underline [&_a]:font-semibold [&_a]:cursor-pointer [&_h1]:text-2xl [&_h1]:font-bold [&_h1]:my-2 [&_h2]:text-xl [&_h2]:font-semibold [&_h2]:my-1.5 [&_ul]:list-disc [&_ul]:pl-6 [&_ul]:space-y-1 [&_b]:font-bold [&_strong]:font-bold [&_i]:italic [&_em]:italic empty:before:content-[attr(data-placeholder)] empty:before:text-[#797487]/50 empty:before:dark:text-[#A39BAC]/50"
        />
      </div>

      {/* 
        STICKY FLOATING FORMATTING TOOLBAR (Only shown when note is editable)
      */}
      {!isReadOnly && (
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

          {/* Checklist (Per-line -1 rule toggle: press to enable on current line, press again on that line to disable) */}
          <button
            type="button"
            onMouseDown={(e) => e.preventDefault()}
            onClick={handleChecklistClick}
            aria-label="Toggle inline checklist on current line"
            aria-pressed={isCurrentLineChecklist}
            className={`w-10 h-10 rounded-xl border flex items-center justify-center transition-all cursor-pointer active:scale-95 ${
              isCurrentLineChecklist
                ? 'bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] border-[#5925DC] dark:border-[#9D5AF5] shadow-xs'
                : 'bg-[#FAF8FB] dark:bg-[#120E17] hover:bg-[#F4EBFF] dark:hover:bg-[#2B1E3E] text-[#1E1A24] dark:text-[#F7EEFC] hover:text-[#5925DC] dark:hover:text-[#C296FD] border-[#EADBFC] dark:border-[#322742]'
            }`}
            title={
              isCurrentLineChecklist
                ? 'Disable Checkbox on Current Line'
                : 'Enable Checkbox on Current Line'
            }
          >
            <CheckSquare className="w-4 h-4" />
          </button>
        </div>
      </div>
      )}

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
    </motion.div>
  );
};

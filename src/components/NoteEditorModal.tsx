import React, { useState, useRef, useEffect, useCallback } from 'react';
import { motion } from 'motion/react';
import { Note, ChecklistItem, NoteImage } from '../types';
import {
  Pin,
  Check,
  CheckSquare,
  Tag,
  Folder,
  Bold,
  Italic,
  List,
  ArrowLeft,
  X,
  Plus,
  Share2,
  ArchiveRestore,
  Trash2,
  Lock,
  Clock,
  Image as ImageIcon,
  Maximize2,
  Minimize2
} from 'lucide-react';
import { logger } from '../utils/logger';
import {
  convertPlainTextToLinkedHtml,
  linkifyDomContainer,
  openUrlInDefaultBrowser,
} from '../utils/linkUtils';
import { computeWordAndLetterCounts } from '../utils/editorRules';
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

  // Explicit active/inactive toggle states for Checkbox and Bullet List
  const [isCurrentLineChecklist, setIsCurrentLineChecklist] = useState(false);
  const [isBulletModeActive, setIsBulletModeActive] = useState(false);

  // Selected image popover state (small window next to clicked image)
  const [activeImagePopover, setActiveImagePopover] = useState<{
    imageId: string;
    isExpanded: boolean;
    top: number;
    left: number;
  } | null>(null);

  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const titleInputRef = useRef<HTMLInputElement | null>(null);

  // Live body content snapshot for real-time word and letter counter on top of note editor
  const [bodyContentSnapshot, setBodyContentSnapshot] = useState<string>(note.content || '');
  const wordLetterStats = React.useMemo(
    () => computeWordAndLetterCounts(title, bodyContentSnapshot),
    [title, bodyContentSnapshot]
  );

  const formattedLastEdited = React.useMemo(() => {
    return new Intl.DateTimeFormat('en-US', {
      month: 'short',
      day: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
    }).format(note.updatedAt || Date.now());
  }, [note.updatedAt]);

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
      const parentLine = checkbox.closest('.aurora-checklist-line');
      const targetTextDiv =
        textDiv ||
        (parentLine?.querySelector('.aurora-checklist-text') as HTMLElement | null) ||
        (checkbox.nextElementSibling as HTMLElement | null);
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
      setBodyContentSnapshot(editorRef.current.innerHTML || '');
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

    // Extract inline checklist items and embedded images for note metadata
    const extractedChecklist: ChecklistItem[] = [];
    const extractedImages: NoteImage[] = [];
    if (editorRef.current) {
      const checkboxes = editorRef.current.querySelectorAll('input[type="checkbox"]');
      checkboxes.forEach((cb, index) => {
        const checkbox = cb as HTMLInputElement;
        const line = checkbox.closest('.aurora-checklist-line') || checkbox.parentElement;
        if (line) {
          const clone = line.cloneNode(true) as HTMLElement;
          clone.querySelectorAll('input[type="checkbox"], .aurora-line-bullet').forEach((c) => c.remove());
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

      const imgBlocks = editorRef.current.querySelectorAll('.aurora-image-block');
      imgBlocks.forEach((block, idx) => {
        const el = block as HTMLElement;
        const img = el.querySelector('img') as HTMLImageElement | null;
        if (img && img.src) {
          extractedImages.push({
            id: el.getAttribute('data-image-id') || `img-${idx}-${Date.now()}`,
            dataUrl: img.src,
            isExpanded: el.getAttribute('data-expanded') === 'true',
          });
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
      images: extractedImages,
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
    const hasEmbeddedImages = Boolean(
      editorRef.current && editorRef.current.querySelectorAll('.aurora-image-block, img').length > 0
    );
    const cleanText = (rawContent || '')
      .replace(/<[^>]*>/g, '')
      .replace(/&nbsp;/g, ' ')
      .replace(/\u200B/g, '')
      .replace(/•/g, '')
      .trim();
    const cleanTitle = title.trim();

    // Discard note if user entered nothing and added no images
    if (!cleanTitle && !cleanText && !hasEmbeddedImages) {
      logger.warn('NOTE', `Discarded blank note (ID: ${note.id})`);
      onClose();
      return;
    }

    const updated = getConstructedNote();
    onSave(updated);
    logger.security('NOTE', `Saved local note: "${updated.title}" (ID: ${updated.id})`);
    onClose();
  };

  /**
   * Quick Save Handler (Checkmark button: saves note without popup, closes keyboard, stays in editor)
   */
  const handleQuickSaveInEditor = () => {
    if (isReadOnly) return;

    // Close user keyboard by blurring active input/editor while staying in the Note Editor
    editorRef.current?.blur();
    titleInputRef.current?.blur();
    if (typeof document !== 'undefined' && document.activeElement instanceof HTMLElement) {
      document.activeElement.blur();
    }
    window.getSelection()?.removeAllRanges();
    setActiveImagePopover(null);

    const rawContent = editorRef.current ? editorRef.current.innerHTML : note.content;
    const hasEmbeddedImages = Boolean(
      editorRef.current && editorRef.current.querySelectorAll('.aurora-image-block, img').length > 0
    );
    const cleanText = (rawContent || '')
      .replace(/<[^>]*>/g, '')
      .replace(/&nbsp;/g, ' ')
      .replace(/\u200B/g, '')
      .replace(/•/g, '')
      .trim();
    const cleanTitle = title.trim();

    if (!cleanTitle && !cleanText && !hasEmbeddedImages) {
      return;
    }

    const updated = getConstructedNote();
    onSave(updated);
    logger.security('NOTE', `Saved local note in editor: "${updated.title}" (ID: ${updated.id})`);
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

  const handleSelectSize = (clickedMode: 'small' | 'medium' | 'large') => {
    // Tapping H2 or H1 again toggles it off and returns to normal small size
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

  /**
   * Extracts clean inline HTML from any line or node, unwrapping block-level `<ul>`/`<li>`/`<div>`
   * tags so text NEVER drops onto a line underneath a checkbox or bullet.
   */
  const extractCleanInlineNodes = (source: Node | string | undefined): DocumentFragment => {
    const fragment = document.createDocumentFragment();
    if (!source) return fragment;

    const temp = document.createElement('div');
    if (typeof source === 'string') {
      temp.innerHTML = source;
    } else {
      temp.appendChild(source.cloneNode(true));
    }

    temp.querySelectorAll('input[type="checkbox"], .aurora-line-bullet').forEach((el) => el.remove());

    const unwrapBlocks = (container: HTMLElement) => {
      const blocks = container.querySelectorAll('ul, ol, li, div, p');
      blocks.forEach((blk) => {
        const parent = blk.parentNode;
        if (!parent) return;
        while (blk.firstChild) {
          parent.insertBefore(blk.firstChild, blk);
        }
        parent.removeChild(blk);
      });
    };
    unwrapBlocks(temp);

    while (temp.firstChild) {
      fragment.appendChild(temp.firstChild);
    }
    return fragment;
  };

  /**
   * Creates a unified single-row line element that can hold a Checkbox, a Bullet, or both
   * side-by-side with inline text right next to them (never pushing text underneath).
   */
  const createFormattedLine = (options: {
    initialContent?: Node | string;
    hasCheckbox: boolean;
    checked?: boolean;
    hasBullet: boolean;
  }): HTMLDivElement => {
    const { initialContent, hasCheckbox, checked = false, hasBullet } = options;

    if (!hasCheckbox && !hasBullet) {
      const plainLine = document.createElement('div');
      plainLine.className = 'my-1 focus:outline-none min-h-[1.5em]';
      plainLine.setAttribute('contenteditable', 'true');
      const frag = extractCleanInlineNodes(initialContent);
      if (frag.childNodes.length > 0 && (frag.textContent || '').trim().length > 0) {
        plainLine.appendChild(frag);
      } else {
        plainLine.innerHTML = '<br>';
      }
      return plainLine;
    }

    const line = document.createElement('div');
    line.className =
      'aurora-checklist-line flex items-center gap-2 my-1.5 group select-none';
    line.setAttribute('contenteditable', 'false');
    line.setAttribute('data-checklist', hasCheckbox ? 'true' : 'false');
    line.setAttribute('data-bullet', hasBullet ? 'true' : 'false');

    const textDiv = document.createElement('div');
    textDiv.className =
      'aurora-checklist-text flex-1 focus:outline-none min-w-[20px] text-sm sm:text-base leading-relaxed text-[#1E1A24] dark:text-[#F7EEFC] select-text';
    textDiv.setAttribute('contenteditable', 'true');

    const frag = extractCleanInlineNodes(initialContent);
    if (frag.childNodes.length > 0 && (frag.textContent || '').replace(/\u200B/g, '').trim().length > 0) {
      textDiv.appendChild(frag);
    } else {
      textDiv.innerHTML = '<br>';
    }

    if (hasCheckbox) {
      const checkbox = document.createElement('input');
      checkbox.type = 'checkbox';
      checkbox.checked = checked;
      if (checked) {
        checkbox.setAttribute('checked', 'checked');
      }
      checkbox.className =
        'w-4 h-4 rounded accent-[#5925DC] dark:accent-[#9D5AF5] cursor-pointer shrink-0 border-[#EADBFC] dark:border-[#322742]';
      checkbox.setAttribute('contenteditable', 'false');
      line.appendChild(checkbox);
      bindCheckboxBehavior(checkbox, textDiv);
    }

    if (hasBullet) {
      const bulletSpan = document.createElement('span');
      bulletSpan.className =
        'aurora-line-bullet text-[#5925DC] dark:text-[#C296FD] font-bold text-base leading-none select-none shrink-0';
      bulletSpan.setAttribute('contenteditable', 'false');
      bulletSpan.textContent = '•';
      line.appendChild(bulletSpan);
    }

    line.appendChild(textDiv);
    return line;
  };

  /**
   * Normalizes top-level bare text nodes, <br>-separated lines, or legacy <ul><li> lists
   * inside editorRef into clean block-level line elements.
   */
  const normalizeEditorLines = () => {
    const root = editorRef.current;
    if (!root) return;

    // Convert any legacy <ul> / <ol> lists into aurora formatted bullet lines
    const legacyLists = Array.from(root.querySelectorAll('ul, ol'));
    legacyLists.forEach((listEl) => {
      const parent = listEl.parentNode;
      if (!parent) return;
      const items = Array.from(listEl.querySelectorAll('li'));
      if (items.length === 0) {
        parent.removeChild(listEl);
        return;
      }
      items.forEach((li) => {
        const hasCb = Boolean(li.querySelector('input[type="checkbox"]'));
        const cbEl = li.querySelector('input[type="checkbox"]') as HTMLInputElement | null;
        const formatted = createFormattedLine({
          initialContent: li,
          hasCheckbox: hasCb,
          checked: Boolean(cbEl?.checked),
          hasBullet: true,
        });
        if (parent === root) {
          root.insertBefore(formatted, listEl);
        } else {
          root.insertBefore(formatted, listEl.closest('.aurora-checklist-line') || listEl);
        }
      });
      parent.removeChild(listEl);
    });

    const children = Array.from(root.childNodes);
    const hasBareInlineNodes = children.some(
      (node) =>
        node.nodeType === Node.TEXT_NODE ||
        (node.nodeType === Node.ELEMENT_NODE &&
          !['DIV', 'P', 'H1', 'H2', 'H3'].includes(
            (node as HTMLElement).tagName
          ))
    );

    if (!hasBareInlineNodes) return;

    let currentBlock: HTMLDivElement | null = null;
    children.forEach((node) => {
      if (
        node.nodeType === Node.ELEMENT_NODE &&
        ['DIV', 'P', 'H1', 'H2', 'H3'].includes(
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
   * Resolves the top-level line element inside editorRef containing `node`.
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

  const getLineFormattingFlags = (line: HTMLElement | null): {
    hasCheckbox: boolean;
    checked: boolean;
    hasBullet: boolean;
  } => {
    if (!line) return { hasCheckbox: false, checked: false, hasBullet: false };
    const cb = line.querySelector('input[type="checkbox"]') as HTMLInputElement | null;
    const hasCheckbox =
      line.getAttribute('data-checklist') === 'true' || Boolean(cb);
    const checked = Boolean(cb?.checked);
    const hasBullet =
      line.getAttribute('data-bullet') === 'true' ||
      Boolean(line.querySelector('.aurora-line-bullet')) ||
      line.tagName === 'LI' ||
      Boolean(line.querySelector('li'));
    return { hasCheckbox, checked, hasBullet };
  };

  /**
   * Updates `isCurrentLineChecklist` & `isBulletModeActive` state and clamps the caret out of the prefix slot.
   */
  const syncCursorLineStateAndEnforceMinusOneRule = useCallback(() => {
    const root = editorRef.current;
    if (!root) return;
    const selection = window.getSelection();
    if (!selection || selection.rangeCount === 0) return;

    const range = selection.getRangeAt(0);
    if (!root.contains(range.startContainer)) return;

    const line = resolveTopLevelLine(range.startContainer);
    if (!line || line.classList.contains('aurora-image-block')) return;

    const { hasCheckbox, hasBullet } = getLineFormattingFlags(line);
    setIsCurrentLineChecklist(hasCheckbox);
    setIsBulletModeActive(hasBullet);

    // Enforce the -1 rule: if the caret landed on the line container before .aurora-checklist-text,
    // move it to position 0 inside .aurora-checklist-text.
    if (line && (hasCheckbox || hasBullet) && range.collapsed) {
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
   * Replaces `line` with an updated formatted line (or plain line) preserving its inline text
   * and placing the caret inside the editable text container.
   */
  const updateLineFormatting = (
    line: HTMLElement,
    nextHasCheckbox: boolean,
    nextHasBullet: boolean,
    placeCaretAtStart: boolean = false
  ): HTMLDivElement => {
    const { checked } = getLineFormattingFlags(line);
    const sourceContent = (line.querySelector('.aurora-checklist-text') || line) as HTMLElement;
    const replacement = createFormattedLine({
      initialContent: sourceContent,
      hasCheckbox: nextHasCheckbox,
      checked: nextHasCheckbox ? checked : false,
      hasBullet: nextHasBullet,
    });

    line.parentNode?.replaceChild(replacement, line);

    const targetEditable =
      (replacement.querySelector('.aurora-checklist-text') as HTMLElement | null) ||
      replacement;
    const selection = window.getSelection();
    if (selection) {
      const newRange = document.createRange();
      newRange.selectNodeContents(targetEditable);
      newRange.collapse(placeCaretAtStart);
      selection.removeAllRanges();
      selection.addRange(newRange);
    }

    setIsCurrentLineChecklist(nextHasCheckbox);
    setIsBulletModeActive(nextHasBullet);
    if (editorRef.current) {
      setBodyContentSnapshot(editorRef.current.innerHTML || '');
    }
    return replacement;
  };

  /**
   * Ensures there is at least one editable line at the cursor and returns the target lines.
   */
  const getTargetLinesForToolbarAction = (): HTMLElement[] => {
    const root = editorRef.current;
    if (!root) return [];
    root.focus();
    normalizeEditorLines();

    const selection = window.getSelection();
    if (!selection) return [];

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

    if (!startLine || startLine.classList.contains('aurora-image-block')) {
      const plainDiv = document.createElement('div');
      plainDiv.className = 'my-1 focus:outline-none min-h-[1.5em]';
      plainDiv.setAttribute('contenteditable', 'true');
      plainDiv.innerHTML = '<br>';
      if (startLine && startLine.classList.contains('aurora-image-block')) {
        startLine.parentNode?.insertBefore(plainDiv, startLine.nextSibling);
      } else {
        root.appendChild(plainDiv);
      }
      startLine = plainDiv;
      endLine = plainDiv;
    }

    const targetLines: HTMLElement[] = [];
    if (startLine && endLine) {
      let current: HTMLElement | null = startLine;
      while (current) {
        if (!current.classList.contains('aurora-image-block')) {
          targetLines.push(current);
        }
        if (current === endLine || !current.nextElementSibling) break;
        current = current.nextElementSibling as HTMLElement;
      }
    } else if (startLine) {
      targetLines.push(startLine);
    }

    return targetLines;
  };

  /**
   * Toggles Bullet List active/inactive state on the current line(s).
   * Coexists cleanly with Checkbox on the same line and stays active across Enter presses until disabled.
   */
  const handleOutlineClick = () => {
    if (isReadOnly) return;
    const targetLines = getTargetLinesForToolbarAction();
    if (targetLines.length === 0) return;

    const firstFlags = getLineFormattingFlags(targetLines[0]);
    const nextBulletState = !firstFlags.hasBullet;

    targetLines.forEach((line) => {
      const flags = getLineFormattingFlags(line);
      updateLineFormatting(line, flags.hasCheckbox, nextBulletState, false);
    });
  };

  /**
   * Toggles Checkbox active/inactive state on the current line(s).
   * Works even when a Bullet List is on the same line (disables the checkbox while keeping the bullet).
   */
  const handleChecklistClick = () => {
    if (isReadOnly) return;
    const targetLines = getTargetLinesForToolbarAction();
    if (targetLines.length === 0) return;

    const firstFlags = getLineFormattingFlags(targetLines[0]);
    const nextCheckboxState = !firstFlags.hasCheckbox;

    targetLines.forEach((line) => {
      const flags = getLineFormattingFlags(line);
      updateLineFormatting(line, nextCheckboxState, flags.hasBullet, false);
    });
  };

  /**
   * Insert Image Handler (Lossless quality, dedicated block line, default smaller size)
   */
  const handleInsertImageClick = () => {
    if (isReadOnly) return;
    fileInputRef.current?.click();
  };

  const handleImageFileSelected = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file || !file.type.startsWith('image/')) return;

    const reader = new FileReader();
    reader.onload = () => {
      const losslessDataUrl = typeof reader.result === 'string' ? reader.result : '';
      if (!losslessDataUrl) return;
      insertLosslessImageBlock(losslessDataUrl);
    };
    reader.readAsDataURL(file);
  };

  const insertLosslessImageBlock = (dataUrl: string) => {
    const root = editorRef.current;
    if (!root || isReadOnly) return;
    root.focus();
    normalizeEditorLines();

    const imageId = `img-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
    const imageBlock = document.createElement('div');
    imageBlock.className = 'aurora-image-block my-2 select-none relative block w-full';
    imageBlock.setAttribute('contenteditable', 'false');
    imageBlock.setAttribute('data-image-id', imageId);
    imageBlock.setAttribute('data-expanded', 'false');

    const imgEl = document.createElement('img');
    imgEl.src = dataUrl;
    imgEl.alt = 'Inserted note image';
    imgEl.className =
      'aurora-note-img rounded-xl border border-[#EADBFC] dark:border-[#322742] object-contain transition-all duration-200 cursor-pointer w-36 sm:w-44 max-h-44 shadow-xs';
    imageBlock.appendChild(imgEl);

    const selection = window.getSelection();
    const currentLine =
      selection && selection.rangeCount > 0 && root.contains(selection.getRangeAt(0).startContainer)
        ? resolveTopLevelLine(selection.getRangeAt(0).startContainer)
        : (root.lastElementChild as HTMLElement | null);

    // Ensure there is an editable line above if the editor was completely empty
    if (!currentLine) {
      const lineAbove = createFormattedLine({
        hasCheckbox: false,
        hasBullet: false,
      });
      root.appendChild(lineAbove);
      root.appendChild(imageBlock);
    } else if (currentLine.nextSibling) {
      root.insertBefore(imageBlock, currentLine.nextSibling);
    } else {
      root.appendChild(imageBlock);
    }

    // Ensure there is always an editable line under the image so the user can write above and under it
    const nextEl = imageBlock.nextElementSibling as HTMLElement | null;
    let lineBelow: HTMLElement;
    if (!nextEl || nextEl.classList.contains('aurora-image-block')) {
      lineBelow = createFormattedLine({
        hasCheckbox: isCurrentLineChecklist,
        hasBullet: isBulletModeActive,
      });
      if (imageBlock.nextSibling) {
        root.insertBefore(lineBelow, imageBlock.nextSibling);
      } else {
        root.appendChild(lineBelow);
      }
    } else {
      lineBelow = nextEl;
    }

    const targetEditable =
      (lineBelow.querySelector('.aurora-checklist-text') as HTMLElement | null) || lineBelow;
    if (selection) {
      const newRange = document.createRange();
      newRange.selectNodeContents(targetEditable);
      newRange.collapse(true);
      selection.removeAllRanges();
      selection.addRange(newRange);
    }

    setBodyContentSnapshot(root.innerHTML || '');
    requestAnimationFrame(ensureCaretVisible);
  };

  /**
   * Toggles the clicked image between original smaller size and expanded larger size.
   * The image never moves above its first reserved line and only extends downward, pushing text under it.
   * Clicking again returns it to its original smaller size and pulls text under it back up.
   */
  const handleToggleImageResize = () => {
    if (!activeImagePopover || !editorRef.current) return;
    const block = editorRef.current.querySelector(
      `.aurora-image-block[data-image-id="${activeImagePopover.imageId}"]`
    ) as HTMLElement | null;
    if (!block) {
      setActiveImagePopover(null);
      return;
    }

    const nextExpanded = !activeImagePopover.isExpanded;
    block.setAttribute('data-expanded', nextExpanded ? 'true' : 'false');
    const img = block.querySelector('img') as HTMLImageElement | null;
    if (img) {
      img.className = nextExpanded
        ? 'aurora-note-img rounded-xl border border-[#5925DC] dark:border-[#9D5AF5] object-contain transition-all duration-200 cursor-pointer w-full max-w-md max-h-80 shadow-md'
        : 'aurora-note-img rounded-xl border border-[#EADBFC] dark:border-[#322742] object-contain transition-all duration-200 cursor-pointer w-36 sm:w-44 max-h-44 shadow-xs';
    }

    // Recompute popover position next to the resized image
    requestAnimationFrame(() => {
      if (!img || !scrollContainerRef.current) return;
      const imgRect = img.getBoundingClientRect();
      setActiveImagePopover({
        imageId: activeImagePopover.imageId,
        isExpanded: nextExpanded,
        top: Math.max(56, imgRect.top + 8),
        left: Math.min(window.innerWidth - 156, imgRect.right + 8),
      });
      if (editorRef.current) {
        setBodyContentSnapshot(editorRef.current.innerHTML || '');
      }
    });
  };

  /**
   * Deletes the selected image and fills the text underneath back into the empty space where it was moved from.
   */
  const handleDeleteImageBlock = () => {
    if (!activeImagePopover || !editorRef.current) return;
    const root = editorRef.current;
    const block = root.querySelector(
      `.aurora-image-block[data-image-id="${activeImagePopover.imageId}"]`
    ) as HTMLElement | null;
    if (!block) {
      setActiveImagePopover(null);
      return;
    }

    const prevLine = block.previousElementSibling as HTMLElement | null;
    const nextLine = block.nextElementSibling as HTMLElement | null;
    block.remove();

    // If the line above was empty and there is text below, remove the empty placeholder line above
    // so the text below fills right back into the original spot.
    if (
      prevLine &&
      !prevLine.classList.contains('aurora-image-block') &&
      !(prevLine.textContent || '').replace(/\u200B/g, '').trim() &&
      nextLine &&
      !nextLine.classList.contains('aurora-image-block')
    ) {
      prevLine.remove();
    }

    setActiveImagePopover(null);
    setBodyContentSnapshot(root.innerHTML || '');
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    if (activeImagePopover) {
      setActiveImagePopover(null);
    }

    const selection = window.getSelection();
    if (!selection || selection.rangeCount === 0) return;

    const range = selection.getRangeAt(0);
    const currentLine = resolveTopLevelLine(range.startContainer);

    // Prevent typing directly on an image block
    if (currentLine && currentLine.classList.contains('aurora-image-block')) {
      e.preventDefault();
      return;
    }

    if (e.key === 'Backspace' && range.collapsed && currentLine) {
      const textDiv = currentLine.querySelector('.aurora-checklist-text') as HTMLElement | null;
      if (textDiv && textDiv.contains(range.startContainer)) {
        const preRange = range.cloneRange();
        preRange.selectNodeContents(textDiv);
        preRange.setEnd(range.startContainer, range.startOffset);
        const textBeforeCursor = (preRange.toString() || '').replace(/\u200B/g, '');
        if (textBeforeCursor.length === 0) {
          e.preventDefault();
          const flags = getLineFormattingFlags(currentLine);
          if (flags.hasCheckbox) {
            updateLineFormatting(currentLine, false, flags.hasBullet, true);
          } else if (flags.hasBullet) {
            updateLineFormatting(currentLine, false, false, true);
          }
          return;
        }
      }
    }

    // Pressing Enter / Send:
    // If Bullet List and/or Checkbox is enabled, keep creating a new line with the active
    // Bullet List and/or Checkbox without ever disabling or deleting the current line's bullet/checkbox!
    if (e.key === 'Enter' && !e.shiftKey) {
      const root = editorRef.current;
      if (!root) return;
      normalizeEditorLines();

      const activeLine = resolveTopLevelLine(range.startContainer);
      const lineFlags = getLineFormattingFlags(activeLine);
      const continueCheckbox = isCurrentLineChecklist || lineFlags.hasCheckbox;
      const continueBullet = isBulletModeActive || lineFlags.hasBullet;

      if (continueCheckbox || continueBullet || (activeLine && activeLine.classList.contains('aurora-checklist-line'))) {
        e.preventDefault();

        // Extract any trailing text after the cursor to move onto the new line
        let trailingFragment: DocumentFragment | undefined;
        const textContainer =
          (activeLine?.querySelector('.aurora-checklist-text') as HTMLElement | null) || activeLine;
        if (textContainer && textContainer.contains(range.startContainer)) {
          const afterRange = range.cloneRange();
          afterRange.selectNodeContents(textContainer);
          afterRange.setStart(range.endContainer, range.endOffset);
          if ((afterRange.toString() || '').length > 0) {
            trailingFragment = afterRange.extractContents();
            if (!textContainer.innerHTML.trim()) {
              textContainer.innerHTML = '<br>';
            }
          }
        }

        const newLine = createFormattedLine({
          initialContent: trailingFragment,
          hasCheckbox: continueCheckbox,
          checked: false,
          hasBullet: continueBullet,
        });

        if (activeLine && activeLine.parentNode === root) {
          if (activeLine.nextSibling) {
            root.insertBefore(newLine, activeLine.nextSibling);
          } else {
            root.appendChild(newLine);
          }
        } else {
          root.appendChild(newLine);
        }

        const targetEditable =
          (newLine.querySelector('.aurora-checklist-text') as HTMLElement | null) || newLine;
        const newRange = document.createRange();
        newRange.setStart(targetEditable, 0);
        newRange.collapse(true);
        selection.removeAllRanges();
        selection.addRange(newRange);

        setIsCurrentLineChecklist(continueCheckbox);
        setIsBulletModeActive(continueBullet);
        setBodyContentSnapshot(root.innerHTML || '');
        requestAnimationFrame(ensureCaretVisible);
        return;
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
      // Check if user clicked on an inserted image inside the editor
      const imageBlock = target.closest('.aurora-image-block') as HTMLElement | null;
      if (imageBlock && !isReadOnly) {
        e.preventDefault();
        e.stopPropagation();
        const imageId = imageBlock.getAttribute('data-image-id') || '';
        const isExpanded = imageBlock.getAttribute('data-expanded') === 'true';
        const imgEl = (imageBlock.querySelector('img') || imageBlock) as HTMLElement;
        const rect = imgEl.getBoundingClientRect();
        setActiveImagePopover((prev) =>
          prev && prev.imageId === imageId
            ? null
            : {
                imageId,
                isExpanded,
                top: Math.max(56, rect.top + 8),
                left: Math.min(window.innerWidth - 156, rect.right + 8),
              }
        );
        return;
      }

      setActiveImagePopover(null);

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
      <div className="px-3 py-2 border-b border-[#EADBFC] dark:border-[#322742] flex items-center justify-between gap-2 shrink-0 bg-white/95 dark:bg-[#1B1524]/95 shadow-xs min-h-[48px]">
        <div className="flex items-center gap-1.5 min-w-0 flex-1 overflow-hidden">
          {/* Back Button */}
          <button
            type="button"
            onClick={handleAutoSaveAndClose}
            aria-label={isReadOnly ? 'Return to Archive' : 'Save and return'}
            className="w-8 h-8 rounded-lg bg-white dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] text-[#1E1A24] dark:text-[#F7EEFC] hover:text-[#5925DC] dark:hover:text-[#C296FD] flex items-center justify-center transition-all cursor-pointer active:scale-95 shrink-0"
            title={isReadOnly ? 'Close' : 'Auto-Save & Close'}
          >
            <ArrowLeft className="w-4 h-4" />
          </button>

          {/* Current Category Pill or Read-Only Badge */}
          {isReadOnly ? (
            <div className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[11px] font-bold text-[#5925DC] dark:text-[#C296FD] border border-[#EADBFC]/80 dark:border-[#322742] shrink-0">
              <Lock className="w-3 h-3" />
              <span>Archived</span>
            </div>
          ) : (
            <div className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[11px] font-semibold text-[#5925DC] dark:text-[#C296FD] border border-[#EADBFC]/80 dark:border-[#322742] shrink-0">
              <Folder className="w-3 h-3" />
              <span className="truncate max-w-[72px] sm:max-w-[120px]">{category}</span>
            </div>
          )}

          {/* Compact Live Word & Letter Counter on Top of Note Editor */}
          <div
            data-testid="note-word-letter-counter"
            className="flex items-center gap-1 px-2 py-1 rounded-lg bg-[#FAF8FB] dark:bg-[#120E17] text-[10.5px] font-semibold text-[#5925DC] dark:text-[#C296FD] border border-[#EADBFC] dark:border-[#322742] whitespace-nowrap shrink-0"
            title="Live word and letter counter"
          >
            <span>
              {wordLetterStats.words}w • {wordLetterStats.letters}l
            </span>
          </div>
        </div>

        {/* Right side: Restore + Delete when archived, or Share + Compact Done when active */}
        <div className="flex items-center gap-1.5 shrink-0">
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
                  className="h-7.5 px-2.5 rounded-lg bg-[#5925DC] dark:bg-[#9D5AF5] text-white text-[11px] font-bold shadow-xs flex items-center gap-1 transition-all active:scale-95 cursor-pointer whitespace-nowrap shrink-0"
                >
                  <ArchiveRestore className="w-3 h-3" />
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
                  className="h-7.5 px-2.5 rounded-lg bg-rose-600 text-white text-[11px] font-bold shadow-xs flex items-center gap-1 transition-all active:scale-95 cursor-pointer whitespace-nowrap shrink-0"
                >
                  <Trash2 className="w-3 h-3" />
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
                className="w-7.5 h-7.5 rounded-lg bg-[#FAF8FB] dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] text-[#5925DC] dark:text-[#C296FD] hover:bg-[#F4EBFF] dark:hover:bg-[#2B1E3E] flex items-center justify-center transition-all active:scale-95 cursor-pointer shrink-0"
                title="Share note"
              >
                <Share2 className="w-3.5 h-3.5" />
              </button>

              <button
                type="button"
                onClick={handleQuickSaveInEditor}
                aria-label="Save note"
                data-testid="editor-save-checkmark-btn"
                className="w-7.5 h-7.5 rounded-lg bg-gradient-to-r from-[#5925DC] to-[#9B51E0] dark:from-[#9D5AF5] dark:to-[#C296FD] text-white shadow-xs flex items-center justify-center transition-all active:scale-95 cursor-pointer shrink-0"
                title="Save note"
              >
                <Check className="w-4 h-4 stroke-[2.5]" />
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
        onScroll={() => {
          if (activeImagePopover) setActiveImagePopover(null);
        }}
        className="p-4 sm:p-8 pb-40 overflow-y-auto space-y-4 flex-1 max-w-3xl mx-auto w-full scroll-smooth"
      >
        {isReadOnly && (
          <div className="p-3 rounded-xl bg-[#F4EBFF]/70 dark:bg-[#2B1E3E]/60 border border-[#EADBFC] dark:border-[#322742] text-xs font-medium text-[#5925DC] dark:text-[#C296FD]">
            This archived note is read-only. Tap <strong>Restore</strong> above to edit it again, or <strong>Delete</strong> to move it to Trash.
          </div>
        )}

        {/* Note Title Input */}
        <input
          ref={titleInputRef}
          type="text"
          readOnly={isReadOnly}
          aria-label="Note title"
          placeholder="Title"
          value={title}
          onChange={(e) => !isReadOnly && setTitle(e.target.value)}
          className="w-full text-2xl sm:text-3xl font-bold text-[#1E1A24] dark:text-[#F7EEFC] placeholder-[#797487]/50 dark:placeholder-[#A39BAC]/50 border-none focus:outline-none focus:ring-0 bg-transparent tracking-tight py-1"
        />

        {/* Last Edited Timestamp with Day, Month, Hour, and Minute */}
        <div className="flex items-center gap-1.5 text-[11px] text-[#797487] dark:text-[#A39BAC] font-medium -mt-1 select-none">
          <Clock className="w-3.5 h-3.5 opacity-60 shrink-0" />
          <span>Edited {formattedLastEdited}</span>
        </div>

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
                  if (editorRef.current) {
                    setBodyContentSnapshot(editorRef.current.innerHTML || '');
                  }
                  requestAnimationFrame(ensureCaretVisible);
                }
          }
          onInput={
            isReadOnly
              ? undefined
              : () => {
                  if (editorRef.current) {
                    setBodyContentSnapshot(editorRef.current.innerHTML || '');
                  }
                  requestAnimationFrame(ensureCaretVisible);
                }
          }
          onClick={handleEditorClick}
          aria-label="Note content editor"
          data-placeholder="Start typing your note here..."
          className="w-full min-h-[360px] pb-36 text-sm sm:text-base leading-relaxed text-[#1E1A24] dark:text-[#F7EEFC] focus:outline-none bg-transparent font-normal space-y-2 [&_a]:text-[#8B3DFF] dark:[&_a]:text-[#C296FD] [&_a]:underline [&_a]:font-semibold [&_a]:cursor-pointer [&_h1]:text-2xl [&_h1]:font-bold [&_h1]:my-2 [&_h2]:text-xl [&_h2]:font-semibold [&_h2]:my-1.5 [&_ul]:list-disc [&_ul]:pl-6 [&_ul]:space-y-1 [&_b]:font-bold [&_strong]:font-bold [&_i]:italic [&_em]:italic empty:before:content-[attr(data-placeholder)] empty:before:text-[#797487]/50 empty:before:dark:text-[#A39BAC]/50"
        />
      </div>

      {/* Small Floating Window Next to Clicked Image (Resize Downward / Smaller + Delete) */}
      {activeImagePopover && !isReadOnly && (
        <div
          style={{
            top: `${activeImagePopover.top}px`,
            left: `${activeImagePopover.left}px`,
          }}
          className="fixed z-50 bg-white/95 dark:bg-[#1B1524]/95 backdrop-blur-md rounded-xl border border-[#5925DC]/40 dark:border-[#9D5AF5]/50 shadow-[0_10px_28px_rgba(89,37,220,0.22)] p-1.5 flex flex-col gap-1 min-w-[138px]"
          onClick={(e) => e.stopPropagation()}
        >
          <button
            type="button"
            onClick={handleToggleImageResize}
            className="w-full px-2.5 py-1.5 rounded-lg text-xs font-semibold text-[#5925DC] dark:text-[#C296FD] hover:bg-[#F4EBFF] dark:hover:bg-[#2B1E3E] flex items-center gap-2 transition-colors cursor-pointer"
          >
            {activeImagePopover.isExpanded ? (
              <>
                <Minimize2 className="w-3.5 h-3.5 shrink-0" />
                <span>Smaller Size</span>
              </>
            ) : (
              <>
                <Maximize2 className="w-3.5 h-3.5 shrink-0" />
                <span>Resize Larger</span>
              </>
            )}
          </button>

          <button
            type="button"
            onClick={handleDeleteImageBlock}
            className="w-full px-2.5 py-1.5 rounded-lg text-xs font-semibold text-rose-600 dark:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-950/40 flex items-center gap-2 transition-colors cursor-pointer"
          >
            <Trash2 className="w-3.5 h-3.5 shrink-0" />
            <span>Delete Image</span>
          </button>
        </div>
      )}

      {/* Hidden File Picker Input for Insert Image */}
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={handleImageFileSelected}
      />

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

          {/* Compact H2 and H1 Text Size Buttons */}
          <div className="flex items-center p-0.5 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742]">
            <button
              type="button"
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => handleSelectSize('medium')}
              aria-label="Toggle H2 text size"
              className={`h-8 px-2.5 rounded-lg text-xs transition-all cursor-pointer flex items-center justify-center ${
                textSizeMode === 'medium'
                  ? 'bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] shadow-xs font-bold border border-[#D6BBFB] dark:border-[#7D49D4]'
                  : 'text-[#484455] dark:text-[#A39BAC] font-semibold hover:text-[#1E1A24] dark:hover:text-white'
              }`}
              title="H2 Heading"
            >
              <span>H2</span>
            </button>

            <button
              type="button"
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => handleSelectSize('large')}
              aria-label="Toggle H1 text size"
              className={`h-8 px-2.5 rounded-lg text-xs transition-all cursor-pointer flex items-center justify-center ${
                textSizeMode === 'large'
                  ? 'bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] shadow-xs font-bold border border-[#D6BBFB] dark:border-[#7D49D4]'
                  : 'text-[#484455] dark:text-[#A39BAC] font-semibold hover:text-[#1E1A24] dark:hover:text-white'
              }`}
              title="H1 Heading"
            >
              <span>H1</span>
            </button>
          </div>

          {/* Bullet List (Active / Inactive toggle state; continues on Enter until disabled) */}
          <button
            type="button"
            onMouseDown={(e) => e.preventDefault()}
            onClick={handleOutlineClick}
            aria-label="Toggle bullet list"
            aria-pressed={isBulletModeActive}
            className={`w-9 h-9 rounded-xl border flex items-center justify-center transition-all cursor-pointer active:scale-95 shrink-0 ${
              isBulletModeActive
                ? 'bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] border-[#5925DC] dark:border-[#9D5AF5] shadow-xs'
                : 'bg-[#FAF8FB] dark:bg-[#120E17] hover:bg-[#F4EBFF] dark:hover:bg-[#2B1E3E] text-[#1E1A24] dark:text-[#F7EEFC] hover:text-[#5925DC] dark:hover:text-[#C296FD] border-[#EADBFC] dark:border-[#322742]'
            }`}
            title={isBulletModeActive ? 'Disable Bullet List' : 'Enable Bullet List'}
          >
            <List className="w-4 h-4" />
          </button>

          {/* Checklist (Active / Inactive toggle state; coexists with bullet list and continues on Enter until disabled) */}
          <button
            type="button"
            onMouseDown={(e) => e.preventDefault()}
            onClick={handleChecklistClick}
            aria-label="Toggle inline checklist"
            aria-pressed={isCurrentLineChecklist}
            className={`w-9 h-9 rounded-xl border flex items-center justify-center transition-all cursor-pointer active:scale-95 shrink-0 ${
              isCurrentLineChecklist
                ? 'bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] border-[#5925DC] dark:border-[#9D5AF5] shadow-xs'
                : 'bg-[#FAF8FB] dark:bg-[#120E17] hover:bg-[#F4EBFF] dark:hover:bg-[#2B1E3E] text-[#1E1A24] dark:text-[#F7EEFC] hover:text-[#5925DC] dark:hover:text-[#C296FD] border-[#EADBFC] dark:border-[#322742]'
            }`}
            title={
              isCurrentLineChecklist
                ? 'Disable Checkbox'
                : 'Enable Checkbox'
            }
          >
            <CheckSquare className="w-4 h-4" />
          </button>

          {/* Insert Image Button (Gallery Icon) */}
          <button
            type="button"
            onMouseDown={(e) => e.preventDefault()}
            onClick={handleInsertImageClick}
            aria-label="Insert image from gallery"
            className="w-9 h-9 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] hover:bg-[#F4EBFF] dark:hover:bg-[#2B1E3E] text-[#1E1A24] dark:text-[#F7EEFC] hover:text-[#5925DC] dark:hover:text-[#C296FD] border border-[#EADBFC] dark:border-[#322742] flex items-center justify-center transition-all cursor-pointer active:scale-95 shrink-0"
            title="Insert Image"
          >
            <ImageIcon className="w-4 h-4" />
          </button>
        </div>
      </div>
      )}
    </motion.div>
  );
};

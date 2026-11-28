export const UNCHECKED_PREFIX = '☐ ';
export const CHECKED_PREFIX = '☑ ';
export const BULLET_PREFIX = '• ';

export interface LineBounds {
  lineIndex: number;
  lineStart: number;
  lineEnd: number;
  lineText: string;
  hasCheckbox: boolean;
  isChecked: boolean;
  hasBullet: boolean;
  /**
   * The index where user-editable text begins on this line (position 0 of line content).
   * When a checkbox and/or bullet occupy the prefix slots, contentStart is after them.
   */
  contentStart: number;
}

/**
 * Returns metadata for the line containing the given character offset.
 * Supports simultaneous checkbox (`☐ ` / `☑ `) and bullet (`• `) prefixes on the same line.
 */
export function getLineBoundsAtOffset(text: string, offset: number): LineBounds {
  const safeOffset = Math.max(0, Math.min(offset, text.length));
  const prevNewline = safeOffset === 0 ? -1 : text.lastIndexOf('\n', safeOffset - 1);
  const lineStart = prevNewline === -1 ? 0 : prevNewline + 1;
  const nextNewline = text.indexOf('\n', lineStart);
  const lineEnd = nextNewline === -1 ? text.length : nextNewline;
  const lineText = text.slice(lineStart, lineEnd);

  const isUnchecked =
    lineText.startsWith(UNCHECKED_PREFIX) ||
    lineText.startsWith(`${BULLET_PREFIX}${UNCHECKED_PREFIX}`);
  const isChecked =
    lineText.startsWith(CHECKED_PREFIX) ||
    lineText.startsWith(`${BULLET_PREFIX}${CHECKED_PREFIX}`);
  const hasCheckbox = isUnchecked || isChecked;

  const hasBullet =
    lineText.startsWith(BULLET_PREFIX) ||
    (hasCheckbox && lineText.slice(2).startsWith(BULLET_PREFIX));

  const prefixLen = (hasCheckbox ? 2 : 0) + (hasBullet ? 2 : 0);

  let lineIndex = 0;
  for (let i = 0; i < lineStart; i++) {
    if (text[i] === '\n') lineIndex++;
  }

  return {
    lineIndex,
    lineStart,
    lineEnd,
    lineText,
    hasCheckbox,
    isChecked,
    hasBullet,
    contentStart: lineStart + prefixLen,
  };
}

/**
 * Strips any leading checkbox (`☐ ` / `☑ `) and/or bullet (`• `) prefixes from a line string
 * and returns its normalized components.
 */
function parseLinePrefixes(line: string): {
  hasCheckbox: boolean;
  checkboxPrefix: string;
  hasBullet: boolean;
  cleanBody: string;
} {
  let rem = line;
  let hasCheckbox = false;
  let checkboxPrefix = UNCHECKED_PREFIX;
  let hasBullet = false;

  for (let i = 0; i < 2; i++) {
    if (!hasCheckbox && (rem.startsWith(UNCHECKED_PREFIX) || rem.startsWith(CHECKED_PREFIX))) {
      hasCheckbox = true;
      checkboxPrefix = rem.slice(0, 2);
      rem = rem.slice(2);
    } else if (!hasCheckbox && (rem.startsWith('☐') || rem.startsWith('☑'))) {
      hasCheckbox = true;
      checkboxPrefix = rem.startsWith('☑') ? CHECKED_PREFIX : UNCHECKED_PREFIX;
      rem = rem.slice(1).replace(/^ /, '');
    } else if (!hasBullet && rem.startsWith(BULLET_PREFIX)) {
      hasBullet = true;
      rem = rem.slice(2);
    } else if (!hasBullet && rem.startsWith('•')) {
      hasBullet = true;
      rem = rem.slice(1).replace(/^ /, '');
    }
  }

  return { hasCheckbox, checkboxPrefix, hasBullet, cleanBody: rem };
}

/**
 * Enforces the -1 checkbox rule on a specific line while coexisting cleanly with bullet lists:
 * - If the line already has a checkbox (`☐ ` or `☑ `), disables (removes) the checkbox from that line
 *   even if a bullet list (`• `) is also on the same line (preserving the bullet).
 * - If the line does not have a checkbox, places `☐ ` in the dedicated -1 prefix slot at the start of the line
 *   (preserving any existing bullet `• ` right after the checkbox).
 */
export function toggleLineCheckboxAtCursor(
  text: string,
  selectionStart: number,
  selectionEnd: number = selectionStart
): {
  text: string;
  selectionStart: number;
  selectionEnd: number;
  checkboxEnabledOnLine: boolean;
} {
  const safeStart = Math.max(0, Math.min(selectionStart, text.length));
  const safeEnd = Math.max(safeStart, Math.min(selectionEnd, text.length));

  const firstLine = getLineBoundsAtOffset(text, safeStart);

  // Single-line cursor or selection within one line
  if (safeStart === safeEnd || getLineBoundsAtOffset(text, safeEnd).lineStart === firstLine.lineStart) {
    const parsed = parseLinePrefixes(firstLine.lineText);
    if (parsed.hasCheckbox) {
      // Disable (remove) checkbox on this specific line, keeping bullet if present
      const rebuiltLine = (parsed.hasBullet ? BULLET_PREFIX : '') + parsed.cleanBody;
      const newText =
        text.slice(0, firstLine.lineStart) +
        rebuiltLine +
        text.slice(firstLine.lineEnd);
      const minCursor = firstLine.lineStart + (parsed.hasBullet ? 2 : 0);
      const newStart = Math.max(minCursor, safeStart - 2);
      const newEnd = Math.max(minCursor, safeEnd - 2);
      return {
        text: newText,
        selectionStart: Math.min(newStart, newText.length),
        selectionEnd: Math.min(newEnd, newText.length),
        checkboxEnabledOnLine: false,
      };
    } else {
      // Enable checkbox in the -1 prefix slot of this line, keeping bullet if present
      const rebuiltLine =
        UNCHECKED_PREFIX + (parsed.hasBullet ? BULLET_PREFIX : '') + parsed.cleanBody;
      const newText =
        text.slice(0, firstLine.lineStart) +
        rebuiltLine +
        text.slice(firstLine.lineEnd);
      const minCursor = firstLine.lineStart + 2 + (parsed.hasBullet ? 2 : 0);
      const delta = rebuiltLine.length - firstLine.lineText.length;
      return {
        text: newText,
        selectionStart: Math.max(minCursor, safeStart + delta),
        selectionEnd: Math.max(minCursor, safeEnd + delta),
        checkboxEnabledOnLine: true,
      };
    }
  }

  // Multi-line selection: toggle all selected lines based on the first line's state
  const shouldDisable = firstLine.hasCheckbox;
  const lines = text.split('\n');
  let charPos = 0;
  let newStart = safeStart;
  let newEnd = safeEnd;

  const updatedLines = lines.map((line) => {
    const lStart = charPos;
    const lEnd = charPos + line.length;
    charPos = lEnd + 1;

    const overlaps = lEnd >= safeStart && lStart <= safeEnd;
    if (!overlaps) return line;

    const parsed = parseLinePrefixes(line);
    if (shouldDisable && parsed.hasCheckbox) {
      if (lStart < safeStart) newStart = Math.max(lStart, newStart - 2);
      newEnd = Math.max(newStart, newEnd - 2);
      return (parsed.hasBullet ? BULLET_PREFIX : '') + parsed.cleanBody;
    } else if (!shouldDisable && !parsed.hasCheckbox) {
      const rebuilt = UNCHECKED_PREFIX + (parsed.hasBullet ? BULLET_PREFIX : '') + parsed.cleanBody;
      const diff = rebuilt.length - line.length;
      if (lStart <= safeStart) newStart += diff;
      newEnd += diff;
      return rebuilt;
    }
    return line;
  });

  const resultText = updatedLines.join('\n');
  return {
    text: resultText,
    selectionStart: Math.max(0, Math.min(newStart, resultText.length)),
    selectionEnd: Math.max(0, Math.min(newEnd, resultText.length)),
    checkboxEnabledOnLine: !shouldDisable,
  };
}

/**
 * Toggles bullet list (`• `) on the current line while preserving any checkbox on the same line.
 */
export function toggleLineBulletAtCursor(
  text: string,
  selectionStart: number,
  selectionEnd: number = selectionStart
): {
  text: string;
  selectionStart: number;
  selectionEnd: number;
  bulletEnabledOnLine: boolean;
} {
  const safeStart = Math.max(0, Math.min(selectionStart, text.length));
  const safeEnd = Math.max(safeStart, Math.min(selectionEnd, text.length));
  const firstLine = getLineBoundsAtOffset(text, safeStart);
  const parsed = parseLinePrefixes(firstLine.lineText);

  if (parsed.hasBullet) {
    // Disable bullet on this line, keeping checkbox if present
    const rebuiltLine =
      (parsed.hasCheckbox ? parsed.checkboxPrefix : '') + parsed.cleanBody;
    const newText =
      text.slice(0, firstLine.lineStart) +
      rebuiltLine +
      text.slice(firstLine.lineEnd);
    const minCursor = firstLine.lineStart + (parsed.hasCheckbox ? 2 : 0);
    return {
      text: newText,
      selectionStart: Math.min(Math.max(minCursor, safeStart - 2), newText.length),
      selectionEnd: Math.min(Math.max(minCursor, safeEnd - 2), newText.length),
      bulletEnabledOnLine: false,
    };
  } else {
    // Enable bullet on this line, keeping checkbox in front if present
    const rebuiltLine =
      (parsed.hasCheckbox ? parsed.checkboxPrefix : '') +
      BULLET_PREFIX +
      parsed.cleanBody;
    const newText =
      text.slice(0, firstLine.lineStart) +
      rebuiltLine +
      text.slice(firstLine.lineEnd);
    const minCursor = firstLine.lineStart + (parsed.hasCheckbox ? 2 : 0) + 2;
    const delta = rebuiltLine.length - firstLine.lineText.length;
    return {
      text: newText,
      selectionStart: Math.max(minCursor, safeStart + delta),
      selectionEnd: Math.max(minCursor, safeEnd + delta),
      bulletEnabledOnLine: true,
    };
  }
}

/**
 * Handles pressing Enter / Send when bullet list and/or checkbox mode is active.
 * Never deletes the bullet or checkbox on an empty line; continues active modes on every new line
 * until the user explicitly disables the button.
 */
export function handleEnterKeyWithActiveModes(
  text: string,
  cursorOffset: number,
  isCheckboxActive: boolean,
  isBulletActive: boolean
): {
  text: string;
  cursorOffset: number;
} {
  const safeCursor = Math.max(0, Math.min(cursorOffset, text.length));
  const continuationPrefix =
    (isCheckboxActive ? UNCHECKED_PREFIX : '') +
    (isBulletActive ? BULLET_PREFIX : '');
  const inserted = `\n${continuationPrefix}`;
  const newText = text.slice(0, safeCursor) + inserted + text.slice(safeCursor);
  return {
    text: newText,
    cursorOffset: safeCursor + inserted.length,
  };
}

/**
 * Clamps cursor selection so it can never enter the -1 prefix slot (before the checkbox/bullet)
 * on any line that has a checkbox enabled.
 */
export function clampSelectionToMinusOneRule(
  text: string,
  selectionStart: number,
  selectionEnd: number = selectionStart
): { selectionStart: number; selectionEnd: number } {
  const clampPoint = (offset: number): number => {
    const bounds = getLineBoundsAtOffset(text, offset);
    if (bounds.hasCheckbox && offset < bounds.contentStart) {
      return bounds.contentStart;
    }
    return Math.max(0, Math.min(offset, text.length));
  };

  return {
    selectionStart: clampPoint(selectionStart),
    selectionEnd: clampPoint(selectionEnd),
  };
}

/**
 * Toggles the checked/unchecked state (`☐ ` <-> `☑ `) of the checkbox on the line at `offset`.
 */
export function toggleCheckboxCheckedStateAtOffset(
  text: string,
  offset: number
): { text: string; toggled: boolean; isChecked: boolean } {
  const bounds = getLineBoundsAtOffset(text, offset);
  if (!bounds.hasCheckbox) {
    return { text, toggled: false, isChecked: false };
  }
  const nextPrefix = bounds.isChecked ? UNCHECKED_PREFIX : CHECKED_PREFIX;
  const newText =
    text.slice(0, bounds.lineStart) +
    nextPrefix +
    text.slice(bounds.lineStart + 2);
  return {
    text: newText,
    toggled: true,
    isChecked: !bounds.isChecked,
  };
}

/**
 * Computes layout span & alignment properties when changing note view format.
 * In Masonry Grid view (isGridView = true), the first note stays the same single-column size
 * as every other note so notes sit side by side without stretching.
 * When Single-Column view (isGridView = false) is enabled, the first note expands widely
 * horizontally and every subsequent note aligns according to the first note.
 */
export function getNoteViewFormatAlignment(
  index: number,
  _totalNotes: number,
  isGridView: boolean
): {
  isFirstNote: boolean;
  spansFullHorizontalWidth: boolean;
  alignsToFirstNoteBounds: boolean;
} {
  const isFirstNote = index === 0;
  const spansFullHorizontalWidth = !isGridView;
  return {
    isFirstNote,
    spansFullHorizontalWidth,
    alignsToFirstNoteBounds: !isGridView,
  };
}

/**
 * Extracts the monotonic GitHub Actions CI run_number from a release tag or release notes body.
 */
export function extractReleaseRunNumber(tag: string, body: string = ''): number | null {
  const bodyMatch = body.match(/\(#(\d+)\)/);
  if (bodyMatch && bodyMatch[1]) {
    const parsed = Number(bodyMatch[1]);
    if (Number.isFinite(parsed) && parsed > 0) return parsed;
  }
  const cleanTag = tag.trim().replace(/^[vV]/, '');
  const parts = cleanTag.split(/[^0-9]+/).filter(Boolean).map(Number);
  if (parts.length >= 3 && parts[2] > 0) {
    return parts[2];
  }
  return null;
}

/**
 * Compares remote vs local release versions and CI run numbers.
 * Handles both standard semantic versions (e.g. 1.3.30 vs 1.3.29) and CI build numbers
 * when release tag prefixes transition (e.g. v1.2.29 vs 1.3.28).
 */
export function compareReleaseVersions(
  remoteVer: string,
  localVer: string,
  remoteRunNumber?: number | null,
  localBuildCode?: number | null
): number {
  if (
    typeof remoteRunNumber === 'number' &&
    remoteRunNumber > 0 &&
    typeof localBuildCode === 'number' &&
    localBuildCode > 100
  ) {
    const remoteCode = 100 + remoteRunNumber;
    if (remoteCode !== localBuildCode) {
      return remoteCode > localBuildCode ? 1 : -1;
    }
  }

  const cleanR = remoteVer.trim().replace(/^[vV]/, '');
  const cleanL = localVer.trim().replace(/^[vV]/, '');
  const rParts = cleanR.split(/[^0-9]+/).filter(Boolean).map(Number);
  const lParts = cleanL.split(/[^0-9]+/).filter(Boolean).map(Number);

  if (
    rParts.length >= 3 &&
    lParts.length >= 3 &&
    rParts[0] === lParts[0] &&
    rParts[2] > 0 &&
    lParts[2] > 0 &&
    rParts[2] !== lParts[2]
  ) {
    return rParts[2] > lParts[2] ? 1 : -1;
  }

  const len = Math.max(rParts.length, lParts.length);
  for (let i = 0; i < len; i++) {
    const r = rParts[i] || 0;
    const l = lParts[i] || 0;
    if (r !== l) return r > l ? 1 : -1;
  }
  return 0;
}

export const PRIVATE_EXTERNAL_FILE_PREFIX = 'aurora_ext_private_file_';
export const TEMP_CACHE_FILE_PREFIX = 'aurora_temp_cache_file_';
export const ENC_STORAGE_PREFIX = 'ENCv1:';
const STORAGE_CIPHER_KEY = 'aurora-keystore-aes256-gcm-v1';

/**
 * Computes live word and letter counts from the note's title and body (plain text or HTML).
 * Strips HTML tags, zero-width spaces, and inline checkbox/bullet prefixes (`☐ `, `☑ `, `• `).
 */
export function computeWordAndLetterCounts(
  title: string,
  bodyTextOrHtml: string
): { words: number; letters: number } {
  const cleanBody = (bodyTextOrHtml || '')
    .replace(/<img\b[^>]*>/gi, ' ')
    .replace(/<[^>]*>/g, ' ')
    .replace(/&nbsp;/gi, ' ')
    .replace(/\u200B/g, '')
    .replace(/[☐☑•]/g, ' ');

  const cleanTitle = (title || '').replace(/\u200B/g, '').trim();
  const combined = `${cleanTitle} ${cleanBody}`.trim();

  if (!combined) {
    return { words: 0, letters: 0 };
  }

  const tokens = combined.split(/\s+/).filter(Boolean);
  const words = tokens.filter((tok) => /[\p{L}\p{N}]/u.test(tok)).length;
  const letters = Array.from(combined).filter((ch) => !/\s/.test(ch)).length;

  return { words, letters };
}

/**
 * Deterministic reversible cipher wrapper (`ENCv1:`) for Room / local storage encryption state.
 */
export function encryptStoragePayload(plainText: string): string {
  if (!plainText || plainText.startsWith(ENC_STORAGE_PREFIX)) return plainText;
  const bytes = new TextEncoder().encode(plainText);
  const keyBytes = new TextEncoder().encode(STORAGE_CIPHER_KEY);
  const out = new Uint8Array(bytes.length);
  for (let i = 0; i < bytes.length; i++) {
    out[i] = bytes[i] ^ keyBytes[i % keyBytes.length] ^ ((i * 31) & 0xff);
  }
  let binary = '';
  for (let i = 0; i < out.length; i++) {
    binary += String.fromCharCode(out[i]);
  }
  const b64 =
    typeof btoa === 'function'
      ? btoa(binary)
      : Buffer.from(out).toString('base64');
  return `${ENC_STORAGE_PREFIX}${b64}`;
}

export function decryptStoragePayload(payload: string): string {
  if (!payload || !payload.startsWith(ENC_STORAGE_PREFIX)) return payload;
  try {
    const rawB64 = payload.slice(ENC_STORAGE_PREFIX.length);
    let cipherBytes: Uint8Array;
    if (typeof atob === 'function') {
      const bin = atob(rawB64);
      cipherBytes = new Uint8Array(bin.length);
      for (let i = 0; i < bin.length; i++) {
        cipherBytes[i] = bin.charCodeAt(i);
      }
    } else {
      cipherBytes = new Uint8Array(Buffer.from(rawB64, 'base64'));
    }
    const keyBytes = new TextEncoder().encode(STORAGE_CIPHER_KEY);
    const plainBytes = new Uint8Array(cipherBytes.length);
    for (let i = 0; i < cipherBytes.length; i++) {
      plainBytes[i] = cipherBytes[i] ^ keyBytes[i % keyBytes.length] ^ ((i * 31) & 0xff);
    }
    return new TextDecoder().decode(plainBytes);
  } catch {
    return payload;
  }
}

/**
 * Scans private external storage entries for orphaned note files (whose note ID is no longer
 * in `validNoteIds`), empty/corrupted 0-byte entries, and temporary `.tmp` / cache files.
 */
export function cleanupOrphanedPrivateStorageEntries(
  storageEntries: Record<string, string>,
  validNoteIds: string[]
): {
  cleanedKeys: string[];
  orphanedRemoved: number;
  tempRemoved: number;
  remainingFileCount: number;
  freedBytes: number;
} {
  const validSet = new Set(validNoteIds.map((id) => id.replace(/[^a-zA-Z0-9_-]/g, '_')));
  const cleanedKeys: string[] = [];
  let orphanedRemoved = 0;
  let tempRemoved = 0;
  let remainingFileCount = 0;
  let freedBytes = 0;

  for (const [key, value] of Object.entries(storageEntries)) {
    if (key.startsWith(TEMP_CACHE_FILE_PREFIX) || key.endsWith('.tmp')) {
      cleanedKeys.push(key);
      tempRemoved++;
      freedBytes += (value || '').length;
      continue;
    }

    if (key.startsWith(PRIVATE_EXTERNAL_FILE_PREFIX)) {
      const extractedId = key
        .slice(PRIVATE_EXTERNAL_FILE_PREFIX.length)
        .replace(/\.json$/, '');
      const isEmptyOrCorrupted = !value || value.trim().length === 0;
      const isOrphaned = !validSet.has(extractedId);

      if (isEmptyOrCorrupted || isOrphaned) {
        cleanedKeys.push(key);
        orphanedRemoved++;
        freedBytes += (value || '').length;
      } else {
        remainingFileCount++;
      }
    }
  }

  return {
    cleanedKeys,
    orphanedRemoved,
    tempRemoved,
    remainingFileCount,
    freedBytes,
  };
}

export type EditorEditGroupKind = 'word-insert' | 'word-delete' | 'atomic';

export interface EditorEditGroup {
  field: string;
  kind: EditorEditGroupKind;
  rangeStart: number;
  rangeEnd: number;
}

export interface TextEditDiff {
  editStart: number;
  deleted: string;
  inserted: string;
}

export function diffPlainTextEdit(oldText: string, newText: string): TextEditDiff {
  const minLen = Math.min(oldText.length, newText.length);
  let prefixLen = 0;
  while (prefixLen < minLen && oldText.charCodeAt(prefixLen) === newText.charCodeAt(prefixLen)) {
    prefixLen++;
  }
  let suffixLen = 0;
  const maxSuffix = minLen - prefixLen;
  while (
    suffixLen < maxSuffix &&
    oldText.charCodeAt(oldText.length - 1 - suffixLen) ===
      newText.charCodeAt(newText.length - 1 - suffixLen)
  ) {
    suffixLen++;
  }
  return {
    editStart: prefixLen,
    deleted: oldText.slice(prefixLen, oldText.length - suffixLen),
    inserted: newText.slice(prefixLen, newText.length - suffixLen),
  };
}

export function isWordCharacter(str: string): boolean {
  if (!str) return false;
  return /^[\p{L}\p{N}\p{M}]+$/u.test(str);
}

/**
 * Evaluates how a plain-text edit should be grouped in the undo/redo stack:
 * - Typing a whole word continuously (letters/digits) coalesces into one undo step so Undo deletes the whole word.
 * - Pasting (or any multi-character bulk insert) is always an isolated atomic step so Undo erases only the pasted item.
 * - Spaces, dots, question marks, punctuation, symbols, and newlines always start a separate undo step so Undo
 *   removes them first and the word behind them next.
 */
export function evaluateTextEditGrouping(params: {
  oldText: string;
  newText: string;
  field: string;
  prevGroup: EditorEditGroup | null;
  isPaste?: boolean;
  forceNewEntry?: boolean;
}): {
  shouldCoalesce: boolean;
  nextGroup: EditorEditGroup;
} {
  const { oldText, newText, field, prevGroup, isPaste = false, forceNewEntry = false } = params;
  const { editStart, deleted, inserted } = diffPlainTextEdit(oldText, newText);
  const editEndAfter = editStart + inserted.length;

  if (isPaste || forceNewEntry) {
    return {
      shouldCoalesce: false,
      nextGroup: {
        field,
        kind: 'atomic',
        rangeStart: editStart,
        rangeEnd: editEndAfter,
      },
    };
  }

  // Pure insertion
  if (deleted.length === 0 && inserted.length > 0) {
    // Multi-character insert (e.g. paste/drop/swipe/suggestion): standalone atomic step
    if (inserted.length > 1) {
      return {
        shouldCoalesce: false,
        nextGroup: {
          field,
          kind: 'atomic',
          rangeStart: editStart,
          rangeEnd: editEndAfter,
        },
      };
    }

    // Single word character: coalesce with contiguous word typing in the same field
    if (isWordCharacter(inserted)) {
      const canCoalesce =
        prevGroup !== null &&
        prevGroup.field === field &&
        prevGroup.kind === 'word-insert' &&
        editStart === prevGroup.rangeEnd;

      if (canCoalesce) {
        return {
          shouldCoalesce: true,
          nextGroup: {
            field,
            kind: 'word-insert',
            rangeStart: prevGroup.rangeStart,
            rangeEnd: editEndAfter,
          },
        };
      }

      return {
        shouldCoalesce: false,
        nextGroup: {
          field,
          kind: 'word-insert',
          rangeStart: editStart,
          rangeEnd: editEndAfter,
        },
      };
    }

    // Space, dot, question mark, punctuation, newline, etc. -> discrete step
    return {
      shouldCoalesce: false,
      nextGroup: {
        field,
        kind: 'atomic',
        rangeStart: editStart,
        rangeEnd: editEndAfter,
      },
    };
  }

  // Pure deletion
  if (inserted.length === 0 && deleted.length > 0) {
    if (deleted.length > 1) {
      return {
        shouldCoalesce: false,
        nextGroup: {
          field,
          kind: 'atomic',
          rangeStart: editStart,
          rangeEnd: editStart,
        },
      };
    }

    if (isWordCharacter(deleted)) {
      const canCoalesce =
        prevGroup !== null &&
        prevGroup.field === field &&
        prevGroup.kind === 'word-delete' &&
        (editStart + deleted.length === prevGroup.rangeStart || editStart === prevGroup.rangeStart);

      if (canCoalesce) {
        return {
          shouldCoalesce: true,
          nextGroup: {
            field,
            kind: 'word-delete',
            rangeStart: editStart,
            rangeEnd: editStart,
          },
        };
      }

      return {
        shouldCoalesce: false,
        nextGroup: {
          field,
          kind: 'word-delete',
          rangeStart: editStart,
          rangeEnd: editStart,
        },
      };
    }

    // Deleting space, punctuation, newline -> discrete step
    return {
      shouldCoalesce: false,
      nextGroup: {
        field,
        kind: 'atomic',
        rangeStart: editStart,
        rangeEnd: editStart,
      },
    };
  }

  // Replacement inside an active word (e.g. IME composition/autocorrect refining letters)
  if (
    prevGroup !== null &&
    prevGroup.field === field &&
    prevGroup.kind === 'word-insert' &&
    editStart >= prevGroup.rangeStart &&
    editStart + deleted.length <= prevGroup.rangeEnd &&
    isWordCharacter(inserted)
  ) {
    const delta = inserted.length - deleted.length;
    return {
      shouldCoalesce: true,
      nextGroup: {
        field,
        kind: 'word-insert',
        rangeStart: prevGroup.rangeStart,
        rangeEnd: prevGroup.rangeEnd + delta,
      },
    };
  }

  return {
    shouldCoalesce: false,
    nextGroup: {
      field,
      kind: 'atomic',
      rangeStart: editStart,
      rangeEnd: editEndAfter,
    },
  };
}

export interface EditorHistoryState<T> {
  past: T[];
  present: T;
  future: T[];
  lastPushTimestamp: number;
  maxDepth: number;
  lastEditGroup?: EditorEditGroup | null;
}

export function createEditorHistory<T>(
  initial: T,
  maxDepth: number = 500
): EditorHistoryState<T> {
  return {
    past: [],
    present: initial,
    future: [],
    lastPushTimestamp: 0,
    maxDepth: Math.max(1, maxDepth),
    lastEditGroup: null,
  };
}

export function breakEditorHistoryGroup<T>(
  history: EditorHistoryState<T>
): EditorHistoryState<T> {
  if (!history.lastEditGroup) return history;
  return {
    ...history,
    lastEditGroup: null,
  };
}

export function pushEditorHistory<T>(
  history: EditorHistoryState<T>,
  nextSnapshot: T,
  options: {
    now?: number;
    coalesceMs?: number;
    forceNewEntry?: boolean;
    isEqual?: (a: T, b: T) => boolean;
    textEdit?: {
      oldText: string;
      newText: string;
      field: string;
      isPaste?: boolean;
    };
  } = {}
): EditorHistoryState<T> {
  const {
    now = Date.now(),
    coalesceMs = 0,
    forceNewEntry = false,
    isEqual = (a, b) => a === b,
    textEdit,
  } = options;

  if (isEqual(history.present, nextSnapshot)) {
    return history;
  }

  if (textEdit) {
    const { shouldCoalesce, nextGroup } = evaluateTextEditGrouping({
      oldText: textEdit.oldText,
      newText: textEdit.newText,
      field: textEdit.field,
      prevGroup: history.future.length === 0 ? (history.lastEditGroup ?? null) : null,
      isPaste: textEdit.isPaste,
      forceNewEntry,
    });

    if (shouldCoalesce && history.past.length > 0 && history.future.length === 0) {
      return {
        ...history,
        present: nextSnapshot,
        lastPushTimestamp: now,
        lastEditGroup: nextGroup,
      };
    }

    const nextPast = [...history.past, history.present];
    const trimmedPast =
      nextPast.length > history.maxDepth
        ? nextPast.slice(nextPast.length - history.maxDepth)
        : nextPast;

    return {
      ...history,
      past: trimmedPast,
      present: nextSnapshot,
      future: [],
      lastPushTimestamp: now,
      lastEditGroup: nextGroup,
    };
  }

  // Legacy path when no textEdit metadata is supplied
  const canCoalesce =
    coalesceMs > 0 &&
    !forceNewEntry &&
    history.past.length > 0 &&
    history.future.length === 0 &&
    history.lastPushTimestamp > 0 &&
    now - history.lastPushTimestamp <= coalesceMs;

  if (canCoalesce) {
    return {
      ...history,
      present: nextSnapshot,
      lastPushTimestamp: now,
      lastEditGroup: null,
    };
  }

  const nextPast = [...history.past, history.present];
  const trimmedPast =
    nextPast.length > history.maxDepth
      ? nextPast.slice(nextPast.length - history.maxDepth)
      : nextPast;

  return {
    ...history,
    past: trimmedPast,
    present: nextSnapshot,
    future: [],
    lastPushTimestamp: now,
    lastEditGroup: null,
  };
}

export function replacePresentEditorHistory<T>(
  history: EditorHistoryState<T>,
  updatedPresent: T
): EditorHistoryState<T> {
  return {
    ...history,
    present: updatedPresent,
  };
}

export function undoEditorHistory<T>(
  history: EditorHistoryState<T>
): EditorHistoryState<T> {
  if (history.past.length === 0) return history;
  const previous = history.past[history.past.length - 1];
  const nextPast = history.past.slice(0, -1);
  return {
    ...history,
    past: nextPast,
    present: previous,
    future: [history.present, ...history.future],
    lastPushTimestamp: 0,
    lastEditGroup: null,
  };
}

export function redoEditorHistory<T>(
  history: EditorHistoryState<T>
): EditorHistoryState<T> {
  if (history.future.length === 0) return history;
  const [next, ...restFuture] = history.future;
  return {
    ...history,
    past: [...history.past, history.present],
    present: next,
    future: restFuture,
    lastPushTimestamp: 0,
    lastEditGroup: null,
  };
}

export function canUndoEditorHistory<T>(history: EditorHistoryState<T>): boolean {
  return history.past.length > 0;
}

export function canRedoEditorHistory<T>(history: EditorHistoryState<T>): boolean {
  return history.future.length > 0;
}

/**
 * Normalizes CRLF (`\r\n`) and lone CR (`\r`) line endings into single `\n` newlines
 * while adjusting the cursor offset so pressing Enter never produces two rows.
 */
export function normalizeLineEndingsWithCursor(
  text: string,
  cursorOffset: number
): { text: string; cursorOffset: number } {
  const safeCursor = Math.max(0, Math.min(cursorOffset, text.length));
  if (!text.includes('\r')) {
    return { text, cursorOffset: safeCursor };
  }
  let out = '';
  let nextCursor = safeCursor;
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (ch === '\r') {
      if (i + 1 < text.length && text[i + 1] === '\n') {
        if (i < safeCursor) {
          nextCursor -= 1;
        }
        continue;
      }
      out += '\n';
    } else {
      out += ch;
    }
  }
  return {
    text: out,
    cursorOffset: Math.max(0, Math.min(nextCursor, out.length)),
  };
}

export interface DropGlowRippleSample {
  intensity: number;
  waveOffsetPx: number;
  scaleBoost: number;
  cornerWeights: {
    topLeft: number;
    topRight: number;
    bottomLeft: number;
    bottomRight: number;
  };
}

/**
 * Computes the luminous corner glow intensity and nearby-note sympathetic wave
 * at `elapsedMs` after dropping a note card.
 */
export function computeDropGlowRipple(
  elapsedMs: number,
  durationMs: number,
  isDroppedCard: boolean,
  dxFromDropped: number,
  dyFromDropped: number,
  maxRadiusPx: number
): DropGlowRippleSample {
  const zero: DropGlowRippleSample = {
    intensity: 0,
    waveOffsetPx: 0,
    scaleBoost: 0,
    cornerWeights: { topLeft: 0, topRight: 0, bottomLeft: 0, bottomRight: 0 },
  };
  if (durationMs <= 0 || elapsedMs < 0 || elapsedMs >= durationMs) {
    return zero;
  }

  if (isDroppedCard) {
    const t = Math.min(1, Math.max(0, elapsedMs / durationMs));
    // Fast luminous bloom peaking as the card lands (~22% of duration), then silky fade-out
    const rise = Math.min(1, t / 0.22);
    const fall = t <= 0.22 ? 1 : Math.pow(1 - (t - 0.22) / 0.78, 1.75);
    const intensity = Math.max(0, Math.min(1, rise * fall));
    return {
      intensity,
      waveOffsetPx: 0,
      scaleBoost: 0,
      cornerWeights: {
        topLeft: 1,
        topRight: 1,
        bottomLeft: 1,
        bottomRight: 1,
      },
    };
  }

  const safeRadius = Math.max(1, maxRadiusPx);
  const dist = Math.hypot(dxFromDropped, dyFromDropped);
  if (dist >= safeRadius) return zero;

  const proximity = Math.pow(1 - dist / safeRadius, 1.2);
  const waveDelayMs = (dist / safeRadius) * Math.min(150, durationMs * 0.22);
  const localDuration = Math.max(1, durationMs - waveDelayMs);
  const localElapsed = elapsedMs - waveDelayMs;
  if (localElapsed <= 0 || localElapsed >= localDuration) return zero;

  const localT = localElapsed / localDuration;
  const wave = Math.sin(localT * Math.PI) * Math.pow(1 - localT, 0.45);
  const intensity = Math.max(0, Math.min(1, wave * proximity * 0.92));

  // Unit vector pointing FROM neighbor TOWARD the dropped card so facing corners glow brightest
  const ux = -dxFromDropped / Math.max(1, dist);
  const uy = -dyFromDropped / Math.max(1, dist);
  const weightCorner = (cxSign: number, cySign: number) => {
    const dot = (cxSign * ux + cySign * uy) / Math.SQRT2; // -1..1
    return Math.max(0.22, Math.min(1, 0.56 + dot * 0.48));
  };

  return {
    intensity,
    waveOffsetPx: wave * proximity * 4.5,
    scaleBoost: wave * proximity * 0.016,
    cornerWeights: {
      topLeft: weightCorner(-1, -1),
      topRight: weightCorner(1, -1),
      bottomLeft: weightCorner(-1, 1),
      bottomRight: weightCorner(1, 1),
    },
  };
}

export interface FlipRect {
  left: number;
  top: number;
  width: number;
  height: number;
}

/**
 * Computes smooth FLIP transform values (top-left origin) when morphing a note card
 * between 2-column Masonry Grid and Single-Column view with zero frame-0 width snap.
 */
export function computeFlipMorphTransform(
  startRect: FlipRect,
  targetRect: FlipRect,
  progress: number
): { x: number; y: number; scaleX: number; scaleY: number } {
  const p = Math.max(0, Math.min(1, progress));
  const rawScaleX =
    targetRect.width > 0 && startRect.width > 0 ? startRect.width / targetRect.width : 1;
  const rawScaleY =
    targetRect.height > 0 && startRect.height > 0 ? startRect.height / targetRect.height : 1;
  const clampedStartScaleX = Math.max(0.35, Math.min(2.4, rawScaleX));
  const clampedStartScaleY = Math.max(0.72, Math.min(1.38, rawScaleY));

  return {
    x: startRect.left + (targetRect.left - startRect.left) * p,
    y: startRect.top + (targetRect.top - startRect.top) * p,
    scaleX: clampedStartScaleX + (1 - clampedStartScaleX) * p,
    scaleY: clampedStartScaleY + (1 - clampedStartScaleY) * p,
  };
}

/**
 * Archives target notes while preserving their `isPinned` flag so restoring them later
 * keeps the pin intact.
 */
export function applyArchivePreservingPin<T extends { id: string; isArchived?: boolean; isPinned: boolean }>(
  notes: T[],
  idsToArchive: string[]
): T[] {
  const idSet = new Set(idsToArchive);
  return notes.map((n) => (idSet.has(n.id) ? { ...n, isArchived: true } : n));
}

/**
 * Restores archived notes back to the active Notes tab while keeping their preserved `isPinned` flag.
 */
export function applyUnarchivePreservingPin<T extends { id: string; isArchived?: boolean; isPinned: boolean }>(
  notes: T[],
  idsToRestore: string[]
): T[] {
  const idSet = new Set(idsToRestore);
  return notes.map((n) => (idSet.has(n.id) ? { ...n, isArchived: false } : n));
}

export interface PreloadedTabLayerTransform {
  alpha: number;
  translationXPx: number;
  scale: number;
  isInteractive: boolean;
}

/**
 * Computes pure GPU-layer transform (`alpha`, `translationXPx`, `scale`) for a permanently preloaded tab
 * during a smooth directional glide & depth crossfade between `fromTab` and `toTab` (`progress` in 0..1).
 * All 4 tabs remain loaded in memory; inactive tabs park cleanly offscreen without unmounting.
 */
export function computePreloadedTabLayerTransform(
  tabIndex: number,
  fromTab: number,
  toTab: number,
  progress: number,
  viewportWidthPx: number
): PreloadedTabLayerTransform {
  const p = Math.max(0, Math.min(1, progress));
  const width = Math.max(1, viewportWidthPx);
  const slideDir = toTab >= fromTab ? 1 : -1;
  const slideSpan = width * 0.26;

  if (tabIndex === toTab) {
    if (fromTab === toTab || p >= 0.999) {
      return { alpha: 1, translationXPx: 0, scale: 1, isInteractive: true };
    }
    return {
      alpha: p,
      translationXPx: (1 - p) * slideDir * slideSpan,
      scale: 0.972 + 0.028 * p,
      isInteractive: true,
    };
  }

  if (tabIndex === fromTab && p < 0.999) {
    return {
      alpha: 1 - p,
      translationXPx: -p * slideDir * (slideSpan * 0.65),
      scale: 1 - 0.018 * p,
      isInteractive: false,
    };
  }

  return {
    alpha: 0,
    translationXPx: width * 2.5,
    scale: 1,
    isInteractive: false,
  };
}

/**
 * Resolves strictly the FIRSTLY selected note (preserving selection order in `selectedNoteIds`)
 * so that even a rapid Share tap while 2+ notes are selected only ever exports that first note.
 */
export function resolveFirstSelectedNoteForShare<T extends { id: string }>(
  notes: T[],
  selectedNoteIds: string[]
): T[] {
  if (!selectedNoteIds.length || !notes.length) return [];
  for (const id of selectedNoteIds) {
    const match = notes.find((n) => n.id === id);
    if (match) return [match];
  }
  return [];
}




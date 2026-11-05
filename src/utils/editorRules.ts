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



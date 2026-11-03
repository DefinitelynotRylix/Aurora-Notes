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
   * When a checkbox occupies the -1 prefix slot, contentStart is lineStart + 2.
   */
  contentStart: number;
}

/**
 * Returns metadata for the line containing the given character offset.
 */
export function getLineBoundsAtOffset(text: string, offset: number): LineBounds {
  const safeOffset = Math.max(0, Math.min(offset, text.length));
  const prevNewline = safeOffset === 0 ? -1 : text.lastIndexOf('\n', safeOffset - 1);
  const lineStart = prevNewline === -1 ? 0 : prevNewline + 1;
  const nextNewline = text.indexOf('\n', lineStart);
  const lineEnd = nextNewline === -1 ? text.length : nextNewline;
  const lineText = text.slice(lineStart, lineEnd);

  const isUnchecked = lineText.startsWith(UNCHECKED_PREFIX);
  const isChecked = lineText.startsWith(CHECKED_PREFIX);
  const hasCheckbox = isUnchecked || isChecked;
  const hasBullet = lineText.startsWith(BULLET_PREFIX);

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
    contentStart: hasCheckbox ? lineStart + 2 : lineStart,
  };
}

/**
 * Enforces the -1 checkbox rule on a specific line:
 * - If the line does not have a checkbox, places `☐ ` in the dedicated -1 prefix slot
 *   at the start of the line (replacing a bullet if one exists) and shifts the cursor forward.
 * - If the line already has a checkbox (`☐ ` or `☑ `), disables (removes) it from that specific line
 *   and shifts the cursor back cleanly.
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
    if (firstLine.hasCheckbox) {
      // Disable (remove) checkbox on this specific line
      const newText =
        text.slice(0, firstLine.lineStart) + text.slice(firstLine.lineStart + 2);
      const newStart = Math.max(firstLine.lineStart, safeStart - 2);
      const newEnd = Math.max(firstLine.lineStart, safeEnd - 2);
      return {
        text: newText,
        selectionStart: newStart,
        selectionEnd: newEnd,
        checkboxEnabledOnLine: false,
      };
    } else if (firstLine.hasBullet) {
      // Replace bullet prefix with checkbox prefix in the -1 slot
      const newText =
        text.slice(0, firstLine.lineStart) +
        UNCHECKED_PREFIX +
        text.slice(firstLine.lineStart + 2);
      return {
        text: newText,
        selectionStart: Math.max(firstLine.lineStart + 2, safeStart),
        selectionEnd: Math.max(firstLine.lineStart + 2, safeEnd),
        checkboxEnabledOnLine: true,
      };
    } else {
      // Enable checkbox in the -1 prefix slot of this line
      const cleanLineRemainder = text
        .slice(firstLine.lineStart, firstLine.lineEnd)
        .replace(/^(?:☐ |☑ )+/, '');
      const newText =
        text.slice(0, firstLine.lineStart) +
        UNCHECKED_PREFIX +
        cleanLineRemainder +
        text.slice(firstLine.lineEnd);
      const delta = 2 - (firstLine.lineText.length - cleanLineRemainder.length);
      return {
        text: newText,
        selectionStart: Math.max(firstLine.lineStart + 2, safeStart + delta),
        selectionEnd: Math.max(firstLine.lineStart + 2, safeEnd + delta),
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

    const hasCb = line.startsWith(UNCHECKED_PREFIX) || line.startsWith(CHECKED_PREFIX);
    if (shouldDisable && hasCb) {
      if (lStart < safeStart) newStart = Math.max(lStart, newStart - 2);
      newEnd = Math.max(newStart, newEnd - 2);
      return line.slice(2);
    } else if (!shouldDisable && !hasCb) {
      const stripped = line.startsWith(BULLET_PREFIX) ? line.slice(2) : line;
      const diff = 2 - (line.length - stripped.length);
      if (lStart <= safeStart) newStart += diff;
      newEnd += diff;
      return UNCHECKED_PREFIX + stripped;
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
 * Clamps cursor selection so it can never enter the -1 prefix slot (before the checkbox)
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


export interface LineState {
  id: string;
  text: string;
  isChecklist: boolean;
  isChecked: boolean;
  isBullet: boolean;
  isBold: boolean;
  isItalic: boolean;
  fontSize: 'small' | 'medium' | 'large';
}

/**
 * Toggles a checkbox on a specific line.
 * Rule 1: One push creates a checkbox in the selected line at the -1 position,
 * the other push removes it regardless of selection on that line.
 * Multiple checkboxes cannot exist on one line.
 */
export function toggleLineCheckbox(line: LineState): LineState {
  return {
    ...line,
    isChecklist: !line.isChecklist,
    // When enabled, starts unchecked unless previously checked
    isChecked: line.isChecklist ? false : line.isChecked,
    // Cannot be bullet and checklist at the same time
    isBullet: false
  };
}

/**
 * Handles Enter key / newline insertion.
 * Rule 2: Hitting Enter creates a new line with the checkbox disabled.
 * The checkbox feature is disabled on the new line and must be manually triggered.
 * The existing line's checkbox is NOT deleted.
 */
export function handleLineEnter(
  lines: LineState[],
  currentIndex: number,
  preText: string,
  postText: string,
  activeFormatting: { isBold: boolean; isItalic: boolean; fontSize: 'small' | 'medium' | 'large' }
): { updatedLines: LineState[]; nextIndex: number } {
  const currentLine = lines[currentIndex];
  const updatedCurrent: LineState = {
    ...currentLine,
    text: preText
  };

  const newLine: LineState = {
    id: `line-${Date.now()}-${Math.random()}`,
    text: postText,
    isChecklist: false, // Rule: new line has checklist disabled
    isChecked: false,
    isBullet: false,
    isBold: activeFormatting.isBold,
    isItalic: activeFormatting.isItalic,
    fontSize: activeFormatting.fontSize
  };

  const newLines = [...lines];
  newLines[currentIndex] = updatedCurrent;
  newLines.splice(currentIndex + 1, 0, newLine);

  return {
    updatedLines: newLines,
    nextIndex: currentIndex + 1
  };
}

/**
 * Applies text formatting to a line.
 * Rule 3: Text formatting works regardless of whether the checkbox is enabled or not.
 */
export function applyFormat(
  line: LineState,
  format: { bold?: boolean; italic?: boolean; fontSize?: 'small' | 'medium' | 'large' }
): LineState {
  return {
    ...line,
    isBold: format.bold !== undefined ? format.bold : line.isBold,
    isItalic: format.italic !== undefined ? format.italic : line.isItalic,
    fontSize: format.fontSize !== undefined ? format.fontSize : line.fontSize
  };
}

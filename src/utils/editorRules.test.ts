import test from 'node:test';
import assert from 'node:assert/strict';
import { toggleLineCheckbox, handleLineEnter, applyFormat, LineState } from './editorRules';

test('Rule 1: Toggling checkbox creates and removes single checkbox at -1 position', () => {
  const line: LineState = {
    id: '1',
    text: 'Buy groceries',
    isChecklist: false,
    isChecked: false,
    isBullet: false,
    isBold: false,
    isItalic: false,
    fontSize: 'small'
  };

  // Push 1: creates checkbox
  const withCheckbox = toggleLineCheckbox(line);
  assert.equal(withCheckbox.isChecklist, true);
  assert.equal(withCheckbox.text, 'Buy groceries'); // text is not prepended into input

  // Push 2: removes checkbox
  const removedCheckbox = toggleLineCheckbox(withCheckbox);
  assert.equal(removedCheckbox.isChecklist, false);
});

test('Rule 2: Pressing enter creates new line with checkbox disabled and preserves existing checkbox', () => {
  const initialLines: LineState[] = [
    {
      id: '1',
      text: 'Milk and bread',
      isChecklist: true,
      isChecked: true,
      isBullet: false,
      isBold: false,
      isItalic: false,
      fontSize: 'small'
    }
  ];

  const result = handleLineEnter(
    initialLines,
    0,
    'Milk',
    'and bread',
    { isBold: false, isItalic: false, fontSize: 'small' }
  );

  // Line 0 still has its checkbox enabled and checked
  assert.equal(result.updatedLines[0].isChecklist, true);
  assert.equal(result.updatedLines[0].isChecked, true);
  assert.equal(result.updatedLines[0].text, 'Milk');

  // Line 1 is a new line with checklist disabled
  assert.equal(result.updatedLines[1].isChecklist, false);
  assert.equal(result.updatedLines[1].isChecked, false);
  assert.equal(result.updatedLines[1].text, 'and bread');
  assert.equal(result.nextIndex, 1);
});

test('Rule 3: Text formatting applies independently of checkbox state', () => {
  const lineWithCheckbox: LineState = {
    id: '1',
    text: 'Encrypted item',
    isChecklist: true,
    isChecked: false,
    isBullet: false,
    isBold: false,
    isItalic: false,
    fontSize: 'small'
  };

  const formatted = applyFormat(lineWithCheckbox, { bold: true, fontSize: 'large' });
  assert.equal(formatted.isChecklist, true);
  assert.equal(formatted.isBold, true);
  assert.equal(formatted.fontSize, 'large');
});

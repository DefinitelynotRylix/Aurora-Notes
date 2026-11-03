import test from 'node:test';
import assert from 'node:assert/strict';
import {
  getLineBoundsAtOffset,
  toggleLineCheckboxAtCursor,
  clampSelectionToMinusOneRule,
  toggleCheckboxCheckedStateAtOffset,
  getNoteViewFormatAlignment,
} from './editorRules';

test('toggleLineCheckboxAtCursor enables and disables checkbox on every specific line with -1 rule', () => {
  const initial = 'First line\nSecond line\nThird line';

  // Enable checkbox on line 1 (offset 3)
  const step1 = toggleLineCheckboxAtCursor(initial, 3);
  assert.equal(step1.text, '☐ First line\nSecond line\nThird line');
  assert.equal(step1.checkboxEnabledOnLine, true);
  assert.equal(step1.selectionStart, 5);

  // Enable checkbox on line 2 (offset inside "Second line")
  const line2Offset = step1.text.indexOf('Second') + 2;
  const step2 = toggleLineCheckboxAtCursor(step1.text, line2Offset);
  assert.equal(step2.text, '☐ First line\n☐ Second line\nThird line');
  assert.equal(step2.checkboxEnabledOnLine, true);

  // Pressing checkbox again on line 2 disables it on that specific line only
  const step3 = toggleLineCheckboxAtCursor(step2.text, step2.selectionStart);
  assert.equal(step3.text, '☐ First line\nSecond line\nThird line');
  assert.equal(step3.checkboxEnabledOnLine, false);

  // Pressing checkbox again on line 2 re-enables it
  const step4 = toggleLineCheckboxAtCursor(step3.text, step3.selectionStart);
  assert.equal(step4.text, '☐ First line\n☐ Second line\nThird line');
  assert.equal(step4.checkboxEnabledOnLine, true);
});

test('clampSelectionToMinusOneRule prevents cursor from entering -1 prefix slot before checkbox', () => {
  const text = '☐ Task one\n☑ Task two';
  // Trying to put cursor at index 0 or 1 on line 1 clamps to index 2 (after "☐ ")
  assert.deepEqual(clampSelectionToMinusOneRule(text, 0, 0), {
    selectionStart: 2,
    selectionEnd: 2,
  });
  assert.deepEqual(clampSelectionToMinusOneRule(text, 1, 1), {
    selectionStart: 2,
    selectionEnd: 2,
  });

  // Trying to put cursor at start of line 2 (index 11) clamps to index 13 (after "☑ ")
  const line2Start = text.indexOf('☑');
  assert.deepEqual(clampSelectionToMinusOneRule(text, line2Start, line2Start), {
    selectionStart: line2Start + 2,
    selectionEnd: line2Start + 2,
  });
});

test('toggleCheckboxCheckedStateAtOffset toggles ☐ and ☑ on the targeted line', () => {
  const text = '☐ Buy milk\n☐ Call mom';
  const toggled1 = toggleCheckboxCheckedStateAtOffset(text, 2);
  assert.equal(toggled1.toggled, true);
  assert.equal(toggled1.isChecked, true);
  assert.equal(toggled1.text, '☑ Buy milk\n☐ Call mom');

  const toggledBack = toggleCheckboxCheckedStateAtOffset(toggled1.text, 2);
  assert.equal(toggledBack.isChecked, false);
  assert.equal(toggledBack.text, '☐ Buy milk\n☐ Call mom');
});

test('getNoteViewFormatAlignment expands first note horizontally and aligns subsequent notes', () => {
  const colFirst = getNoteViewFormatAlignment(0, 4, false);
  const colSecond = getNoteViewFormatAlignment(1, 4, false);
  assert.equal(colFirst.isFirstNote, true);
  assert.equal(colFirst.spansFullHorizontalWidth, true);
  assert.equal(colSecond.spansFullHorizontalWidth, true);
  assert.equal(colSecond.alignsToFirstNoteBounds, true);

  const singleGrid = getNoteViewFormatAlignment(0, 1, true);
  assert.equal(singleGrid.spansFullHorizontalWidth, true);
});

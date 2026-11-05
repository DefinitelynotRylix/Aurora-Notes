import test from 'node:test';
import assert from 'node:assert/strict';
import {
  getLineBoundsAtOffset,
  toggleLineCheckboxAtCursor,
  toggleLineBulletAtCursor,
  handleEnterKeyWithActiveModes,
  clampSelectionToMinusOneRule,
  toggleCheckboxCheckedStateAtOffset,
  getNoteViewFormatAlignment,
  compareReleaseVersions,
  extractReleaseRunNumber,
  computeWordAndLetterCounts,
  encryptStoragePayload,
  decryptStoragePayload,
  cleanupOrphanedPrivateStorageEntries,
  PRIVATE_EXTERNAL_FILE_PREFIX,
  TEMP_CACHE_FILE_PREFIX,
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

test('checkbox can be disabled when both checkbox and bullet list are enabled on the same line', () => {
  const initial = 'Item text';

  // Enable bullet list first
  const withBullet = toggleLineBulletAtCursor(initial, 4);
  assert.equal(withBullet.text, '• Item text');
  assert.equal(withBullet.bulletEnabledOnLine, true);

  // Enable checkbox on the same line -> both checkbox and bullet coexist on the same line
  const withBoth = toggleLineCheckboxAtCursor(withBullet.text, withBullet.selectionStart);
  assert.equal(withBoth.text, '☐ • Item text');
  assert.equal(withBoth.checkboxEnabledOnLine, true);

  const bounds = getLineBoundsAtOffset(withBoth.text, withBoth.selectionStart);
  assert.equal(bounds.hasCheckbox, true);
  assert.equal(bounds.hasBullet, true);

  // Disable checkbox while bullet is on the same line -> checkbox is removed, bullet remains intact
  const disabledCb = toggleLineCheckboxAtCursor(withBoth.text, withBoth.selectionStart);
  assert.equal(disabledCb.text, '• Item text');
  assert.equal(disabledCb.checkboxEnabledOnLine, false);

  // Also test disabling bullet when both are active -> bullet is removed, checkbox remains intact
  const reEnabledBoth = toggleLineCheckboxAtCursor(disabledCb.text, disabledCb.selectionStart);
  assert.equal(reEnabledBoth.text, '☐ • Item text');
  const disabledBullet = toggleLineBulletAtCursor(reEnabledBoth.text, reEnabledBoth.selectionStart);
  assert.equal(disabledBullet.text, '☐ Item text');
  assert.equal(disabledBullet.bulletEnabledOnLine, false);
});

test('handleEnterKeyWithActiveModes continues bullet list and checkbox on every new line without deleting empty bullets', () => {
  // Bullet active on an empty bullet line: pressing Enter keeps the bullet and creates a new bullet line
  const emptyBullet = '• ';
  const afterEnter1 = handleEnterKeyWithActiveModes(emptyBullet, emptyBullet.length, false, true);
  assert.equal(afterEnter1.text, '• \n• ');
  assert.equal(afterEnter1.cursorOffset, 5);

  // Pressing Enter again keeps going on every line until disabled
  const afterEnter2 = handleEnterKeyWithActiveModes(afterEnter1.text, afterEnter1.cursorOffset, false, true);
  assert.equal(afterEnter2.text, '• \n• \n• ');

  // Both checkbox and bullet active
  const bothLine = '☐ • First item';
  const afterEnterBoth = handleEnterKeyWithActiveModes(bothLine, bothLine.length, true, true);
  assert.equal(afterEnterBoth.text, '☐ • First item\n☐ • ');
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

import { logger } from './logger';

test('getNoteViewFormatAlignment keeps first note single-column in masonry grid view and only expands in column view', () => {
  // In Masonry Grid view (isGridView = true), the first note and second note stay single-column (do not stretch full width)
  const gridFirst = getNoteViewFormatAlignment(0, 4, true);
  const gridSecond = getNoteViewFormatAlignment(1, 4, true);
  assert.equal(gridFirst.isFirstNote, true);
  assert.equal(gridFirst.spansFullHorizontalWidth, false);
  assert.equal(gridSecond.spansFullHorizontalWidth, false);

  // Even with 1 note in Masonry Grid view, it stays at single-column size
  const singleGrid = getNoteViewFormatAlignment(0, 1, true);
  assert.equal(singleGrid.spansFullHorizontalWidth, false);

  // When switching to Single-Column view (isGridView = false), the first note expands horizontally and others align to it
  const colFirst = getNoteViewFormatAlignment(0, 4, false);
  const colSecond = getNoteViewFormatAlignment(1, 4, false);
  assert.equal(colFirst.isFirstNote, true);
  assert.equal(colFirst.spansFullHorizontalWidth, true);
  assert.equal(colSecond.spansFullHorizontalWidth, true);
  assert.equal(colSecond.alignsToFirstNoteBounds, true);
});

test('logger restricts entries strictly to ERROR and WARN when one occurs', () => {
  logger.clearLogs();
  assert.equal(logger.getLogs().length, 0);

  // Routine security/informational logs must be ignored
  const secEntry = logger.security('ARCHIVE', 'Archived note');
  assert.equal(secEntry, null);
  assert.equal(logger.getLogs().length, 0);

  // Warning and Error logs must be recorded when one occurs
  const warnEntry = logger.warn('NOTE', 'Discarded blank note');
  assert.ok(warnEntry);
  assert.equal(warnEntry.level, 'WARN');

  const errEntry = logger.error('UPDATE', 'Failed checking GitHub Releases');
  assert.ok(errEntry);
  assert.equal(errEntry.level, 'ERROR');

  const allLogs = logger.getLogs();
  assert.equal(allLogs.length, 2);
  assert.equal(allLogs[0].level, 'ERROR');
  assert.equal(allLogs[1].level, 'WARN');
  logger.clearLogs();
});

test('compareReleaseVersions detects newer GitHub Releases across standard semver and CI run numbers', () => {
  // Standard semver
  assert.equal(compareReleaseVersions('v1.3.30', '1.3.29'), 1);
  assert.equal(compareReleaseVersions('v1.3.30', '1.3.30'), 0);
  assert.equal(compareReleaseVersions('v1.3.28', '1.3.29'), -1);

  // CI run_number transition (e.g. v1.2.29 release vs 1.3.28 installed build)
  const runNum = extractReleaseRunNumber('v1.2.29', 'Automated build (#29) of Aurora Notes.apk');
  assert.equal(runNum, 29);
  assert.equal(compareReleaseVersions('v1.2.29', '1.3.28', runNum, 128), 1);
});

test('computeWordAndLetterCounts accurately counts words and letters while ignoring HTML and checkbox prefixes', () => {
  assert.deepEqual(computeWordAndLetterCounts('', ''), { words: 0, letters: 0 });
  assert.deepEqual(computeWordAndLetterCounts('Hello', '<div>☐ World test</div>'), {
    words: 3,
    letters: 14, // "Hello" (5) + "World" (5) + "test" (4) = 14
  });
});

test('encryptStoragePayload and decryptStoragePayload round-trip note data with ENCv1 prefix', () => {
  const plain = JSON.stringify({ id: 'note-1', title: 'Secret Plan', content: 'Encrypted Room DB' });
  const encrypted = encryptStoragePayload(plain);
  assert.ok(encrypted.startsWith('ENCv1:'));
  assert.notEqual(encrypted, plain);
  assert.equal(decryptStoragePayload(encrypted), plain);
});

test('cleanupOrphanedPrivateStorageEntries removes orphaned note files, empty files, and temp cache files', () => {
  const mockStorage: Record<string, string> = {
    [`${PRIVATE_EXTERNAL_FILE_PREFIX}note_1.json`]: '{"id":"note_1","title":"Keep"}',
    [`${PRIVATE_EXTERNAL_FILE_PREFIX}note_orphan.json`]: '{"id":"note_orphan","title":"Remove"}',
    [`${PRIVATE_EXTERNAL_FILE_PREFIX}note_empty.json`]: '   ',
    [`${TEMP_CACHE_FILE_PREFIX}share_123.md`]: '# Temp share',
    aurora_theme_v1: 'dark',
  };

  const report = cleanupOrphanedPrivateStorageEntries(mockStorage, ['note_1']);
  assert.equal(report.orphanedRemoved, 2);
  assert.equal(report.tempRemoved, 1);
  assert.equal(report.remainingFileCount, 1);
  assert.ok(report.cleanedKeys.includes(`${PRIVATE_EXTERNAL_FILE_PREFIX}note_orphan.json`));
  assert.ok(report.cleanedKeys.includes(`${PRIVATE_EXTERNAL_FILE_PREFIX}note_empty.json`));
  assert.ok(report.cleanedKeys.includes(`${TEMP_CACHE_FILE_PREFIX}share_123.md`));
  assert.ok(!report.cleanedKeys.includes(`${PRIVATE_EXTERNAL_FILE_PREFIX}note_1.json`));
});

test('hitting Enter repeatedly (10+ times) with checkbox and bullet active never cuts off or disables either mode', () => {
  let state = { text: '☐ • ', cursorOffset: 4 };
  for (let i = 0; i < 10; i++) {
    state = handleEnterKeyWithActiveModes(state.text, state.cursorOffset, true, true);
  }
  const lines = state.text.split('\n');
  assert.equal(lines.length, 11);
  for (const line of lines) {
    assert.equal(line, '☐ • ');
  }
});





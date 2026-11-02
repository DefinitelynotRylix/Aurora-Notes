import test from 'node:test';
import assert from 'node:assert/strict';
import {
  generateSalt,
  uint8ArrayToHex,
  hexToUint8Array,
  deriveKey,
  encryptData,
  decryptData,
  createPasswordVerifier,
  verifyKey,
  evaluatePasswordStrength
} from './crypto.ts';

test('hex conversion converts round-trip accurately', () => {
  const bytes = new Uint8Array([0, 15, 255, 128, 42]);
  const hex = uint8ArrayToHex(bytes);
  assert.equal(hex, '000fff802a');
  const restored = hexToUint8Array(hex);
  assert.deepEqual(Array.from(restored), Array.from(bytes));
});

test('PBKDF2 derives key and encrypts/decrypts plaintext successfully', async () => {
  const salt = generateSalt(16);
  assert.equal(salt.length, 16);

  const password = 'CorrectHorseBatteryStaple#2026';
  const key = await deriveKey(password, salt, 1000); // reduced iterations for fast unit test

  const secretMessage = 'Classified note: Zero-knowledge client-side encryption active.';
  const { ciphertext, iv } = await encryptData(secretMessage, key);

  assert.ok(ciphertext.length > 0);
  assert.ok(iv.length > 0);
  assert.notEqual(ciphertext, secretMessage);

  const decrypted = await decryptData(ciphertext, iv, key);
  assert.equal(decrypted, secretMessage);
});

test('Decryption fails with invalid key or tampered ciphertext', async () => {
  const salt = generateSalt(16);
  const correctKey = await deriveKey('SecretPass123!', salt, 1000);
  const wrongKey = await deriveKey('WrongPass456!', salt, 1000);

  const { ciphertext, iv } = await encryptData('Top secret payload', correctKey);

  // Decryption with wrong key must reject
  await assert.rejects(async () => {
    await decryptData(ciphertext, iv, wrongKey);
  });

  // Decryption with corrupted ciphertext must reject
  const tamperedCipher = 'ab' + ciphertext.substring(2);
  await assert.rejects(async () => {
    await decryptData(tamperedCipher, iv, correctKey);
  });
});

test('Password verifier validates correct password and rejects wrong password', async () => {
  const salt = generateSalt(16);
  const keyA = await deriveKey('MyMasterPasskey@99', salt, 1000);
  const keyB = await deriveKey('IncorrectPassword', salt, 1000);

  const verifier = await createPasswordVerifier(keyA);
  assert.ok(verifier.includes(':'));

  const isValidA = await verifyKey(keyA, verifier);
  assert.equal(isValidA, true);

  const isValidB = await verifyKey(keyB, verifier);
  assert.equal(isValidB, false);
});

test('Password strength scoring detects weak and strong passwords', () => {
  const weak = evaluatePasswordStrength('abc');
  assert.equal(weak.score, 0);

  const strong = evaluatePasswordStrength('Str0ng!P@ssw0rd#2026');
  assert.ok(strong.score >= 3);
});

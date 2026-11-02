/**
 * Client-Side Cryptographic Utilities for Iris Secure Notes
 * Implements Zero-Knowledge AES-256-GCM + PBKDF2 key derivation using Web Crypto API.
 * Purely local, zero network calls.
 */

// Constant auth verification phrase encrypted to test key validity without risking data corruption
const AUTH_VERIFIER_PLAINTEXT = 'IRIS_ZERO_KNOWLEDGE_VAULT_AUTHENTICATED';
const PBKDF2_ITERATIONS = 100000;

function getCryptoSubtle(): SubtleCrypto {
  if (typeof globalThis !== 'undefined' && globalThis.crypto?.subtle) {
    return globalThis.crypto.subtle;
  }
  throw new Error('WebCrypto API (crypto.subtle) is not available in this environment.');
}

export function getRandomValues(arr: Uint8Array): Uint8Array {
  if (typeof globalThis !== 'undefined' && typeof globalThis.crypto?.getRandomValues === 'function') {
    (globalThis.crypto as { getRandomValues: (a: Uint8Array) => Uint8Array }).getRandomValues(arr);
    return arr;
  }
  for (let i = 0; i < arr.length; i++) {
    arr[i] = Math.floor(Math.random() * 256);
  }
  return arr;
}

export function generateSalt(length = 16): Uint8Array {
  return getRandomValues(new Uint8Array(length));
}

export function uint8ArrayToHex(bytes: Uint8Array): string {
  return Array.from(bytes)
    .map(b => b.toString(16).padStart(2, '0'))
    .join('');
}

export function hexToUint8Array(hex: string): Uint8Array {
  const cleanHex = hex.trim();
  if (cleanHex.length % 2 !== 0) {
    throw new Error('Invalid hex string length');
  }
  const bytes = new Uint8Array(cleanHex.length / 2);
  for (let i = 0; i < bytes.length; i++) {
    bytes[i] = parseInt(cleanHex.substring(i * 2, i * 2 + 2), 16);
  }
  return bytes;
}

/**
 * Derives an AES-GCM 256-bit encryption key from a user password and salt using PBKDF2 with SHA-256
 */
export async function deriveKey(
  password: string,
  salt: Uint8Array,
  iterations: number = PBKDF2_ITERATIONS
): Promise<CryptoKey> {
  const subtle = getCryptoSubtle();
  const encoder = new TextEncoder();
  const passwordKey = await subtle.importKey(
    'raw',
    encoder.encode(password),
    { name: 'PBKDF2' },
    false,
    ['deriveKey']
  );

  return subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt: salt as BufferSource,
      iterations,
      hash: 'SHA-256',
    },
    passwordKey,
    { name: 'AES-GCM', length: 256 },
    false, // Never allow key export from memory to prevent memory scraping
    ['encrypt', 'decrypt']
  );
}

/**
 * Encrypts arbitrary plaintext string with AES-GCM 256 and a random 12-byte IV
 */
export async function encryptData(
  plaintext: string,
  key: CryptoKey
): Promise<{ ciphertext: string; iv: string }> {
  const subtle = getCryptoSubtle();
  const encoder = new TextEncoder();
  const iv = getRandomValues(new Uint8Array(12));
  const encodedData = encoder.encode(plaintext);

  const cipherBuffer = await subtle.encrypt(
    {
      name: 'AES-GCM',
      iv: iv as BufferSource,
    },
    key,
    encodedData
  );

  return {
    ciphertext: uint8ArrayToHex(new Uint8Array(cipherBuffer)),
    iv: uint8ArrayToHex(iv),
  };
}

/**
 * Decrypts AES-GCM ciphertext using the derived CryptoKey and IV
 */
export async function decryptData(
  ciphertextHex: string,
  ivHex: string,
  key: CryptoKey
): Promise<string> {
  const subtle = getCryptoSubtle();
  const decoder = new TextDecoder();
  const iv = hexToUint8Array(ivHex);
  const cipherBytes = hexToUint8Array(ciphertextHex);

  const decryptedBuffer = await subtle.decrypt(
    {
      name: 'AES-GCM',
      iv: iv as BufferSource,
    },
    key,
    cipherBytes as BufferSource
  );

  return decoder.decode(decryptedBuffer);
}

/**
 * Creates an encrypted verification token to validate whether future password inputs are correct
 */
export async function createPasswordVerifier(key: CryptoKey): Promise<string> {
  const { ciphertext, iv } = await encryptData(AUTH_VERIFIER_PLAINTEXT, key);
  return `${iv}:${ciphertext}`;
}

/**
 * Checks if the provided derived key successfully decrypts the verification token
 */
export async function verifyKey(key: CryptoKey, verifierToken: string): Promise<boolean> {
  try {
    const parts = verifierToken.split(':');
    if (parts.length !== 2) return false;
    const [ivHex, cipherHex] = parts;
    const decrypted = await decryptData(cipherHex, ivHex, key);
    return decrypted === AUTH_VERIFIER_PLAINTEXT;
  } catch {
    return false;
  }
}

/**
 * Evaluates password strength for user feedback
 */
export interface PasswordStrength {
  score: number; // 0 to 4
  label: 'Weak' | 'Fair' | 'Good' | 'Strong' | 'Optimal';
  color: string;
  feedback: string[];
}

export function evaluatePasswordStrength(password: string): PasswordStrength {
  if (!password) {
    return { score: 0, label: 'Weak', color: '#ba1a1a', feedback: ['Password required'] };
  }

  let score = 0;
  const feedback: string[] = [];

  if (password.length >= 8) score++;
  else feedback.push('Use at least 8 characters');

  if (password.length >= 12) score++;

  if (/[A-Z]/.test(password) && /[a-z]/.test(password)) score++;
  else feedback.push('Include uppercase & lowercase letters');

  if (/\d/.test(password)) score++;
  else feedback.push('Include numbers');

  if (/[^A-Za-z0-9]/.test(password)) score++;
  else feedback.push('Include symbols (!@#$%)');

  const normalizedScore = Math.min(4, Math.max(0, score - (password.length < 8 ? 2 : 0)));

  const labels: PasswordStrength['label'][] = ['Weak', 'Fair', 'Good', 'Strong', 'Optimal'];
  const colors = ['#ba1a1a', '#e67e22', '#f39c12', '#5925dc', '#10b981'];

  return {
    score: normalizedScore,
    label: labels[normalizedScore],
    color: colors[normalizedScore],
    feedback: feedback.length ? feedback : ['Passphrase meets high security standards'],
  };
}

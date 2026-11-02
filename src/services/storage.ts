import { Note, EncryptedVaultPayload, VaultConfig } from '../types';
import {
  deriveKey,
  encryptData,
  decryptData,
  generateSalt,
  uint8ArrayToHex,
  hexToUint8Array,
  createPasswordVerifier,
  verifyKey
} from '../utils/crypto';

const STORAGE_VAULT_KEY = 'iris_vault_data_v1';
const STORAGE_CONFIG_KEY = 'iris_vault_config_v1';

export const DEFAULT_SEED_NOTES: Note[] = [
  {
    id: 'note-welcome-1',
    title: 'Zero-Knowledge Security Architecture',
    content: `Iris Secure Notes operates under a strict **Zero-Knowledge, Local-Only** model.\n\nEvery character, checklist item, and media attachment is encrypted directly in your browser using **AES-256-GCM** with a **PBKDF2-SHA256** key (100,000 iterations). \n\n### Core Safeguards:\n- **No Remote Network Calls:** Connections to outside servers or cloud storage have been stripped completely.\n- **Zero Telemetry:** No analytics, tracker cookies, or remote logging.\n- **Ephemeral In-Memory Key:** The encryption key is held only in runtime memory. Locking the vault instantly purges the key.`,
    checklist: [
      { id: 'c1', text: 'Local AES-256-GCM authenticated cipher', completed: true },
      { id: 'c2', text: '100,000 rounds PBKDF2 key derivation', completed: true },
      { id: 'c3', text: 'Strict zero-telemetry local storage', completed: true },
      { id: 'c4', text: 'Emergency panic lock & duress disguise', completed: true },
      { id: 'c5', text: 'Local trash with selective & bulk purge', completed: true }
    ],
    tags: ['Security', 'Encryption', 'Architecture'],
    category: 'Security',
    isPinned: true,
    isEncrypted: true,
    createdAt: Date.now() - 3600000 * 2,
    updatedAt: Date.now() - 3600000 * 2
  },
  {
    id: 'note-welcome-2',
    title: 'Iris Liquid Glass Design System',
    content: `Crafted according to the Iris tactile design constitution:\n\n- **Typography:** Plus Jakarta Sans with generous line heights and editorial balance.\n- **Canvas Base:** Soft warm mauve tint (\`#FAF8FB\`) soothing long focus sessions.\n- **Jeweled Accents:** Royal Violet (\`#5925DC\`) and Radiant Amethyst (\`#9B51E0\`).\n- **Command Island:** Central floating liquid glass toolbar with continuous pill geometry.`,
    checklist: [
      { id: 'c6', text: 'Liquid glass floating toolbar', completed: true },
      { id: 'c7', text: 'Tactile cards with subtle whisper borders', completed: true },
      { id: 'c8', text: 'Android native biometric & PIN lock integration', completed: true }
    ],
    tags: ['Design', 'Iris', 'Interface'],
    category: 'Ideas',
    isPinned: true,
    isEncrypted: true,
    createdAt: Date.now() - 3600000 * 4,
    updatedAt: Date.now() - 3600000 * 4
  },
  {
    id: 'note-welcome-3',
    title: 'Hardware & Key Operational Security Checklist',
    content: `When handling sensitive personal thoughts, operational hygiene is just as critical as software encryption.\n\nKeep backups exported as encrypted \`.iris\` envelopes and verify hash signatures regularly.`,
    checklist: [
      { id: 'c9', text: 'Set auto-lock interval to 5 minutes', completed: true },
      { id: 'c10', text: 'Enable Android native lock screen in settings', completed: false },
      { id: 'c11', text: 'Configure duress code for sensitive environments', completed: false },
      { id: 'c12', text: 'Export offline encrypted vault backup', completed: false }
    ],
    tags: ['OpSec', 'Checklist'],
    category: 'Personal',
    isPinned: false,
    isEncrypted: true,
    createdAt: Date.now() - 3600000 * 12,
    updatedAt: Date.now() - 3600000 * 12
  },
  {
    id: 'note-welcome-4',
    title: 'Project Chrysalis Strategy & Milestones',
    content: `Quarterly strategic objectives kept behind encrypted local partition.\n\nPhase 1: Zero-knowledge prototype verification.\nPhase 2: Local audio memo compression & spectrogram analysis.\nPhase 3: Steganographic local export package.`,
    checklist: [
      { id: 'c13', text: 'Peer review cryptographic salt generator', completed: true },
      { id: 'c14', text: 'Conduct local memory leak audit', completed: true },
      { id: 'c15', text: 'Benchmark PBKDF2 speed vs resistance', completed: false }
    ],
    tags: ['Work', 'Strategy'],
    category: 'Work',
    isPinned: false,
    isEncrypted: true,
    createdAt: Date.now() - 3600000 * 24,
    updatedAt: Date.now() - 3600000 * 24
  }
];

export const DURESS_DECOY_NOTES: Note[] = [
  {
    id: 'decoy-1',
    title: 'Weekly Grocery & Household Supplies',
    content: 'Pantry restocking list and basic home maintenance items.',
    checklist: [
      { id: 'd1', text: 'Almond milk & rolled oats', completed: true },
      { id: 'd2', text: 'Sparkling mineral water', completed: false },
      { id: 'd3', text: 'Organic olive oil', completed: false }
    ],
    tags: ['Personal', 'Chores'],
    category: 'Personal',
    isPinned: true,
    isEncrypted: true,
    createdAt: Date.now() - 100000,
    updatedAt: Date.now() - 100000
  },
  {
    id: 'decoy-2',
    title: 'Book Recommendations for Summer',
    content: 'Fiction and non-fiction reading queue for leisurely weekends.',
    checklist: [
      { id: 'd4', text: 'The Creative Act by Rick Rubin', completed: true },
      { id: 'd5', text: 'Klara and the Sun', completed: false }
    ],
    tags: ['Reading', 'Books'],
    category: 'Ideas',
    isPinned: false,
    isEncrypted: true,
    createdAt: Date.now() - 200000,
    updatedAt: Date.now() - 200000
  }
];

export function getStoredConfig(): VaultConfig {
  try {
    const raw = localStorage.getItem(STORAGE_CONFIG_KEY);
    if (raw) {
      return JSON.parse(raw);
    }
  } catch (e) {
    console.error('Failed to read vault config from localStorage', e);
  }
  return {
    isInitialized: false,
    isUnlocked: false,
    autoLockMinutes: 15,
    duressCode: '9999'
  };
}

export function saveStoredConfig(config: Partial<VaultConfig>) {
  try {
    const current = getStoredConfig();
    const updated = { ...current, ...config };
    localStorage.setItem(STORAGE_CONFIG_KEY, JSON.stringify(updated));
  } catch (e) {
    console.error('Failed to save vault config', e);
  }
}

export function getStoredPayload(): EncryptedVaultPayload | null {
  try {
    const raw = localStorage.getItem(STORAGE_VAULT_KEY);
    if (raw) {
      return JSON.parse(raw);
    }
  } catch (e) {
    console.error('Failed to read vault payload from localStorage', e);
  }
  return null;
}

export function saveStoredPayload(payload: EncryptedVaultPayload) {
  try {
    localStorage.setItem(STORAGE_VAULT_KEY, JSON.stringify(payload));
  } catch (e) {
    console.error('Failed to save encrypted vault payload', e);
  }
}

export function clearAllLocalVaultData() {
  localStorage.removeItem(STORAGE_VAULT_KEY);
  localStorage.removeItem(STORAGE_CONFIG_KEY);
}

/**
 * Initializes a new vault with a user-provided passphrase
 */
export async function initializeVaultWithPassword(
  passphrase: string,
  initialNotes: Note[] = DEFAULT_SEED_NOTES,
  duressCode = '9999'
): Promise<{ key: CryptoKey; notes: Note[] }> {
  const salt = generateSalt(16);
  const key = await deriveKey(passphrase, salt);
  const verifier = await createPasswordVerifier(key);

  const serialized = JSON.stringify(initialNotes);
  const { ciphertext, iv } = await encryptData(serialized, key);

  const payload: EncryptedVaultPayload = {
    version: 1,
    salt: uint8ArrayToHex(salt),
    iv,
    verifier,
    ciphertext,
    updatedAt: Date.now(),
    noteCount: initialNotes.length
  };

  saveStoredPayload(payload);
  saveStoredConfig({
    isInitialized: true,
    isUnlocked: true,
    duressCode,
    salt: payload.salt,
    verifier
  });

  return { key, notes: initialNotes };
}

/**
 * Unlocks an existing vault using the master passphrase
 */
export async function unlockVault(
  passphrase: string
): Promise<{ success: boolean; key?: CryptoKey; notes?: Note[]; isDuress?: boolean; error?: string }> {
  const config = getStoredConfig();
  const payload = getStoredPayload();

  // Check duress code
  if (config.duressCode && passphrase.trim() === config.duressCode.trim()) {
    return {
      success: true,
      notes: DURESS_DECOY_NOTES,
      isDuress: true
    };
  }

  if (!payload) {
    return { success: false, error: 'No encrypted vault payload found.' };
  }

  try {
    const saltBytes = hexToUint8Array(payload.salt);
    const key = await deriveKey(passphrase, saltBytes);

    const isMatch = await verifyKey(key, payload.verifier);
    if (!isMatch) {
      return { success: false, error: 'Incorrect passphrase. Master key rejected.' };
    }

    const decryptedJson = await decryptData(payload.ciphertext, payload.iv, key);
    const notes: Note[] = JSON.parse(decryptedJson);

    return {
      success: true,
      key,
      notes,
      isDuress: false
    };
  } catch (err) {
    return {
      success: false,
      error: 'Decryption failed. Invalid passphrase or corrupted ciphertext.'
    };
  }
}

/**
 * Encrypts and saves active notes into localStorage
 */
export async function saveActiveNotesToVault(
  notes: Note[],
  key: CryptoKey
): Promise<EncryptedVaultPayload> {
  const payload = getStoredPayload();
  const salt = payload ? hexToUint8Array(payload.salt) : generateSalt(16);
  const verifier = payload ? payload.verifier : await createPasswordVerifier(key);

  const serialized = JSON.stringify(notes);
  const { ciphertext, iv } = await encryptData(serialized, key);

  const updatedPayload: EncryptedVaultPayload = {
    version: 1,
    salt: uint8ArrayToHex(salt),
    iv,
    verifier,
    ciphertext,
    updatedAt: Date.now(),
    noteCount: notes.length
  };

  saveStoredPayload(updatedPayload);
  return updatedPayload;
}

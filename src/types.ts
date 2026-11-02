export interface ChecklistItem {
  id: string;
  text: string;
  completed: boolean;
}

export interface Note {
  id: string;
  title: string;
  content: string;
  checklist: ChecklistItem[];
  tags: string[];
  category: string;
  isPinned: boolean;
  isEncrypted: boolean;
  isDeleted?: boolean;
  deletedAt?: number;
  createdAt: number;
  updatedAt: number;
}

export interface EncryptedVaultPayload {
  version: number;
  salt: string;
  iv: string;
  verifier: string;
  ciphertext: string;
  updatedAt: number;
  noteCount: number;
}

export interface VaultConfig {
  isInitialized: boolean;
  isUnlocked: boolean;
  autoLockMinutes: number;
  duressCode?: string;
  salt?: string;
  verifier?: string;
}

export type ThemeOption = 'light' | 'dark' | 'system';
export type ActiveTab = 'notes' | 'pinned' | 'trash' | 'settings';

export interface ChecklistItem {
  id: string;
  text: string;
  completed: boolean;
}

export interface NoteImage {
  id: string;
  dataUrl: string;
  isExpanded?: boolean;
}

export interface Note {
  id: string;
  title: string;
  content: string;
  checklist: ChecklistItem[];
  images?: NoteImage[];
  tags: string[];
  category: string;
  isPinned: boolean;
  isArchived?: boolean;
  isDeleted?: boolean;
  deletedAt?: number;
  sortOrder?: number;
  createdAt: number;
  updatedAt: number;
}

export type ThemeOption = 'light' | 'dark' | 'system';
export type ActiveTab = 'notes' | 'archive' | 'trash' | 'settings';


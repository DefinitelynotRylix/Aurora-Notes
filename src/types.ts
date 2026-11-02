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
  isDeleted?: boolean;
  deletedAt?: number;
  createdAt: number;
  updatedAt: number;
}

export type ThemeOption = 'light' | 'dark' | 'system';
export type ActiveTab = 'notes' | 'pinned' | 'trash' | 'settings';

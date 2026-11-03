import React, { useState, useMemo } from 'react';
import { Note } from '../types';
import {
  Share2,
  FolderOpen,
  FileText,
  Check,
  X,
  Copy,
  Download
} from 'lucide-react';
import { logger } from '../utils/logger';
import { decryptStoragePayload, ENC_STORAGE_PREFIX } from '../utils/editorRules';

export type ShareFormat = 'md' | 'txt';

interface ShareNoteModalProps {
  notes: Note[];
  onClose: () => void;
}

function maybeDecryptField(val: string | undefined): string {
  if (!val) return '';
  return val.startsWith(ENC_STORAGE_PREFIX) ? decryptStoragePayload(val) : val;
}

function decryptNoteFields(note: Note): Note {
  return {
    ...note,
    title: maybeDecryptField(note.title),
    content: maybeDecryptField(note.content),
    category: maybeDecryptField(note.category),
    tags: (note.tags || []).map((t) => maybeDecryptField(t)),
    checklist: (note.checklist || []).map((item) => ({
      ...item,
      text: maybeDecryptField(item.text),
    })),
  };
}

function stripHtmlToPlain(rawHtml: string): string {
  if (!rawHtml) return '';
  try {
    const withLineBreaks = rawHtml
      .replace(/<br\s*\/?>/gi, '\n')
      .replace(/<\/(p|div|li|h1|h2|h3)>/gi, '\n');
    const doc = new DOMParser().parseFromString(withLineBreaks, 'text/html');
    return (doc.body.textContent || '').trim();
  } catch {
    return rawHtml.replace(/<[^>]*>/g, '').trim();
  }
}

export function formatSingleNote(rawNote: Note, format: ShareFormat): string {
  const note = decryptNoteFields(rawNote);
  const title = (note.title || '').trim() || 'Untitled Note';
  const updatedStr = new Date(note.updatedAt || Date.now()).toLocaleString();
  const cleanBody = stripHtmlToPlain(note.content);
  const tagsList = (note.tags || []).map((t) => t.trim().replace(/^#/, '')).filter(Boolean);

  if (format === 'md') {
    const metaParts: string[] = [];
    if (note.category) metaParts.push(`**Category:** ${note.category}`);
    if (tagsList.length > 0) metaParts.push(`**Tags:** ${tagsList.map((t) => `#${t}`).join(' ')}`);
    metaParts.push(`**Updated:** ${updatedStr}`);

    const checklistSection =
      note.checklist && note.checklist.length > 0
        ? `\n### Checklist\n` +
          note.checklist.map((item) => `- [${item.completed ? 'x' : ' '}] ${item.text}`).join('\n') +
          '\n'
        : '';

    return [
      `# ${title}`,
      '',
      `> ${metaParts.join(' | ')}`,
      '',
      cleanBody ? `${cleanBody}\n` : '',
      checklistSection,
    ]
      .filter((line) => line !== undefined)
      .join('\n')
      .trim() + '\n';
  }

  // Plain Text (.txt)
  const headerBar = '='.repeat(Math.min(40, Math.max(8, title.length)));
  const lines: string[] = [
    title.toUpperCase(),
    headerBar,
  ];
  if (note.category) lines.push(`Category: ${note.category}`);
  if (tagsList.length > 0) lines.push(`Tags: ${tagsList.map((t) => `#${t}`).join(', ')}`);
  lines.push(`Updated: ${updatedStr}`, '');

  if (cleanBody) {
    lines.push(cleanBody, '');
  }

  if (note.checklist && note.checklist.length > 0) {
    lines.push('Checklist:');
    note.checklist.forEach((item) => {
      lines.push(`[${item.completed ? 'x' : ' '}] ${item.text}`);
    });
    lines.push('');
  }

  return lines.join('\n').trim() + '\n';
}

export function formatNotesForShare(notes: Note[], format: ShareFormat): string {
  if (!notes.length) return '';
  if (notes.length === 1) return formatSingleNote(notes[0], format);
  const divider = format === 'md' ? '\n\n---\n\n' : '\n\n' + '-'.repeat(40) + '\n\n';
  return notes.map((n) => formatSingleNote(n, format).trimEnd()).join(divider) + '\n';
}

export function getSuggestedFileName(notes: Note[], format: ShareFormat): string {
  if (notes.length === 1) {
    const plain = decryptNoteFields(notes[0]);
    const raw = (plain.title || 'Untitled_Note').trim();
    const clean = raw
      .replace(/[^a-zA-Z0-9._\- ]/g, '')
      .trim()
      .replace(/\s+/g, '_')
      .slice(0, 48) || 'Aurora_Note';
    return `${clean}.${format}`;
  }
  const datePart = new Date().toISOString().slice(0, 10);
  return `Aurora_Notes_${notes.length}_${datePart}.${format}`;
}

export const ShareNoteModal: React.FC<ShareNoteModalProps> = ({ notes, onClose }) => {
  const [format, setFormat] = useState<ShareFormat>('md');
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  const formattedOutput = useMemo(() => formatNotesForShare(notes, format), [notes, format]);
  const suggestedFileName = useMemo(() => getSuggestedFileName(notes, format), [notes, format]);

  if (!notes.length) return null;

  const handleShareToAppsAndContacts = async () => {
    const title = notes.length === 1 ? notes[0].title || 'Aurora Note' : `${notes.length} Aurora Notes`;

    if (typeof navigator !== 'undefined' && navigator.share) {
      try {
        await navigator.share({
          title,
          text: formattedOutput,
        });
        logger.security('SHARE', `Shared ${notes.length} note(s) to apps & contacts (.${format})`);
        onClose();
        return;
      } catch (err) {
        if ((err as Error)?.name === 'AbortError') return;
      }
    }

    // Fallback: Copy formatted note to clipboard
    try {
      await navigator.clipboard?.writeText(formattedOutput);
      setStatusMessage(`Copied ${suggestedFileName} content to clipboard for sharing!`);
      logger.security('SHARE', `Copied ${notes.length} note(s) as .${format} to clipboard`);
    } catch {
      setStatusMessage('Could not access system share sheet in this preview.');
    }
  };

  const handleSaveToFolderPicker = async () => {
    const mimeType = format === 'md' ? 'text/markdown' : 'text/plain';

    // Try modern File System Access API folder/file picker first
    const win = window as unknown as {
      showSaveFilePicker?: (opts: unknown) => Promise<{
        createWritable: () => Promise<{ write: (data: string) => Promise<void>; close: () => Promise<void> }>;
      }>;
    };

    if (typeof win.showSaveFilePicker === 'function') {
      try {
        const handle = await win.showSaveFilePicker({
          suggestedName: suggestedFileName,
          types: [
            {
              description: format === 'md' ? 'Markdown Document (.md)' : 'Plain Text Document (.txt)',
              accept: { [mimeType]: [`.${format}`] },
            },
          ],
        });
        const writable = await handle.createWritable();
        await writable.write(formattedOutput);
        await writable.close();
        logger.security('SHARE', `Saved ${suggestedFileName} to chosen folder`);
        setStatusMessage(`Saved ${suggestedFileName} to chosen folder!`);
        setTimeout(onClose, 900);
        return;
      } catch (err) {
        if ((err as Error)?.name === 'AbortError') return;
      }
    }

    // Standard browser / Android WebView file save fallback
    const blob = new Blob([formattedOutput], { type: `${mimeType};charset=utf-8` });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = suggestedFileName;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
    logger.security('SHARE', `Downloaded ${suggestedFileName}`);
    setStatusMessage(`Saved ${suggestedFileName}!`);
    setTimeout(onClose, 900);
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-fade-in"
      onClick={onClose}
    >
      <div
        className="w-full max-w-md rounded-3xl bg-white dark:bg-[#1B1524] border border-[#EADBFC] dark:border-[#322742] shadow-[0_24px_60px_rgba(0,0,0,0.4)] p-5 space-y-4"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-xl bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] flex items-center justify-center">
              <Share2 className="w-4 h-4" />
            </div>
            <div>
              <h3 className="text-sm sm:text-base font-bold text-[#1E1A24] dark:text-[#F7EEFC]">
                {notes.length === 1 ? 'Share & Export Note' : `Share ${notes.length} Selected Notes`}
              </h3>
              <p className="text-[11px] text-[#797487] dark:text-[#A39BAC]">
                Share to apps & contacts or save to a chosen folder
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            className="w-8 h-8 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] text-[#797487] dark:text-[#A39BAC] hover:text-[#1E1A24] dark:hover:text-white flex items-center justify-center cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Format Selection */}
        <div className="space-y-1.5">
          <span className="text-[10px] font-bold uppercase tracking-wider text-[#5925DC] dark:text-[#C296FD]">
            Choose Format
          </span>
          <div className="grid grid-cols-2 gap-2.5">
            <button
              type="button"
              onClick={() => setFormat('md')}
              className={`p-3 rounded-2xl border text-left transition-all cursor-pointer flex items-center justify-between ${
                format === 'md'
                  ? 'bg-[#F4EBFF] dark:bg-[#2B1E3E] border-[#5925DC] dark:border-[#9D5AF5] text-[#5925DC] dark:text-[#C296FD]'
                  : 'bg-[#FAF8FB] dark:bg-[#120E17] border-[#EADBFC] dark:border-[#322742] text-[#484455] dark:text-[#A39BAC]'
              }`}
            >
              <div>
                <div className="text-xs font-bold">Markdown (.md)</div>
                <div className="text-[10px] opacity-75">Headings & checklists</div>
              </div>
              {format === 'md' && <Check className="w-4 h-4 shrink-0" />}
            </button>

            <button
              type="button"
              onClick={() => setFormat('txt')}
              className={`p-3 rounded-2xl border text-left transition-all cursor-pointer flex items-center justify-between ${
                format === 'txt'
                  ? 'bg-[#F4EBFF] dark:bg-[#2B1E3E] border-[#5925DC] dark:border-[#9D5AF5] text-[#5925DC] dark:text-[#C296FD]'
                  : 'bg-[#FAF8FB] dark:bg-[#120E17] border-[#EADBFC] dark:border-[#322742] text-[#484455] dark:text-[#A39BAC]'
              }`}
            >
              <div>
                <div className="text-xs font-bold">Plain Text (.txt)</div>
                <div className="text-[10px] opacity-75">Universal text format</div>
              </div>
              {format === 'txt' && <Check className="w-4 h-4 shrink-0" />}
            </button>
          </div>
        </div>

        {/* File Preview */}
        <div className="space-y-1.5">
          <div className="flex items-center justify-between text-xs">
            <span className="font-semibold text-[#1E1A24] dark:text-[#F7EEFC] flex items-center gap-1.5 truncate">
              <FileText className="w-3.5 h-3.5 text-[#5925DC] dark:text-[#C296FD]" />
              <span className="truncate">{suggestedFileName}</span>
            </span>
            <span className="text-[10px] text-[#797487] dark:text-[#A39BAC]">
              {formattedOutput.length} chars
            </span>
          </div>
          <pre className="p-3 rounded-2xl bg-[#FAF8FB] dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] text-[11px] font-mono text-[#484455] dark:text-[#A39BAC] max-h-36 overflow-y-auto whitespace-pre-wrap">
            {formattedOutput}
          </pre>
        </div>

        {statusMessage && (
          <div className="p-2.5 rounded-xl bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-200 dark:border-emerald-900 text-emerald-700 dark:text-emerald-300 text-xs font-medium text-center">
            {statusMessage}
          </div>
        )}

        {/* Action Buttons */}
        <div className="space-y-2 pt-1">
          <button
            type="button"
            onClick={handleShareToAppsAndContacts}
            className="w-full py-2.5 px-4 rounded-2xl bg-gradient-to-r from-[#5925DC] to-[#9B51E0] dark:from-[#9D5AF5] dark:to-[#C296FD] text-white text-xs font-bold shadow-sm flex items-center justify-center gap-2 active:scale-95 transition-all cursor-pointer"
          >
            <Share2 className="w-4 h-4" />
            <span>Share to Apps & Contacts (.{format.toUpperCase()})</span>
          </button>

          <div className="grid grid-cols-2 gap-2">
            <button
              type="button"
              onClick={handleSaveToFolderPicker}
              className="py-2.5 px-3 rounded-2xl bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] border border-[#EADBFC] dark:border-[#322742] text-xs font-semibold flex items-center justify-center gap-1.5 active:scale-95 transition-all cursor-pointer"
            >
              <FolderOpen className="w-3.5 h-3.5" />
              <span>Save to Folder</span>
            </button>

            <button
              type="button"
              onClick={async () => {
                await navigator.clipboard?.writeText(formattedOutput);
                setStatusMessage(`Copied .${format} content to clipboard!`);
              }}
              className="py-2.5 px-3 rounded-2xl bg-[#FAF8FB] dark:bg-[#120E17] text-[#1E1A24] dark:text-[#F7EEFC] border border-[#EADBFC] dark:border-[#322742] text-xs font-semibold flex items-center justify-center gap-1.5 active:scale-95 transition-all cursor-pointer"
            >
              <Copy className="w-3.5 h-3.5" />
              <span>Copy Text</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

import React, { useState, useMemo, useEffect } from 'react';
import { Note } from '../types';
import {
  Share2,
  FolderOpen,
  FileText,
  Image as ImageIcon,
  Check,
  X,
  Copy,
  Download
} from 'lucide-react';
import { logger } from '../utils/logger';
import { decryptStoragePayload, ENC_STORAGE_PREFIX } from '../utils/editorRules';

export type ShareFormat = 'md' | 'png';

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
    images: (note.images || []).map((img) => ({
      ...img,
      dataUrl: maybeDecryptField(img.dataUrl),
    })),
  };
}

export interface ParsedNoteBlock {
  type: 'line' | 'image';
  text?: string;
  hasCheckbox?: boolean;
  checked?: boolean;
  hasBullet?: boolean;
  imageSrc?: string;
  isExpanded?: boolean;
}

export function parseNoteHtmlToBlocks(rawHtml: string, noteImages?: Note['images']): {
  blocks: ParsedNoteBlock[];
  imageUrls: string[];
} {
  const blocks: ParsedNoteBlock[] = [];
  const imageUrls: string[] = [];

  if (!rawHtml && (!noteImages || noteImages.length === 0)) {
    return { blocks, imageUrls };
  }

  try {
    const doc = new DOMParser().parseFromString(rawHtml || '', 'text/html');
    const children = Array.from(doc.body.childNodes);

    const processElement = (node: ChildNode) => {
      if (node.nodeType === Node.TEXT_NODE) {
        const txt = (node.textContent || '').replace(/\u200B/g, '').trim();
        if (txt) {
          blocks.push({ type: 'line', text: txt });
        }
        return;
      }
      if (!(node instanceof HTMLElement)) return;

      if (node.classList.contains('aurora-image-block') || node.tagName === 'IMG') {
        const imgEl = node.tagName === 'IMG' ? (node as HTMLImageElement) : node.querySelector('img');
        const src = imgEl?.getAttribute('src') || '';
        if (src) {
          imageUrls.push(src);
          blocks.push({
            type: 'image',
            imageSrc: src,
            isExpanded: node.getAttribute('data-expanded') === 'true',
          });
        }
        return;
      }

      const inlineImgs = Array.from(node.querySelectorAll('img'));
      const cb = node.querySelector('input[type="checkbox"]') as HTMLInputElement | null;
      const hasCheckbox =
        node.getAttribute('data-checklist') === 'true' || Boolean(cb);
      const checked = Boolean(
        cb &&
          (cb.getAttribute('data-checked') === 'true' ||
            cb.checked ||
            cb.hasAttribute('checked'))
      );
      const hasBullet =
        node.getAttribute('data-bullet') === 'true' ||
        Boolean(node.querySelector('.aurora-line-bullet')) ||
        node.tagName === 'LI';

      const clone = node.cloneNode(true) as HTMLElement;
      clone.querySelectorAll('input[type="checkbox"], .aurora-line-bullet, img').forEach((el) => el.remove());
      const cleanLineText = (clone.textContent || '').replace(/\u200B/g, '').trim();

      if (cleanLineText || hasCheckbox || hasBullet) {
        blocks.push({
          type: 'line',
          text: cleanLineText,
          hasCheckbox,
          checked,
          hasBullet,
        });
      }

      inlineImgs.forEach((img) => {
        const src = img.getAttribute('src') || '';
        if (src) {
          imageUrls.push(src);
          blocks.push({
            type: 'image',
            imageSrc: src,
            isExpanded: false,
          });
        }
      });
    };

    children.forEach(processElement);
  } catch {
    const fallback = (rawHtml || '').replace(/<[^>]*>/g, '').trim();
    if (fallback) {
      fallback.split('\n').forEach((l) => blocks.push({ type: 'line', text: l }));
    }
  }

  // Also include any images from note.images not already in imageUrls
  (noteImages || []).forEach((img) => {
    if (img.dataUrl && !imageUrls.includes(img.dataUrl)) {
      imageUrls.push(img.dataUrl);
      blocks.push({
        type: 'image',
        imageSrc: img.dataUrl,
        isExpanded: Boolean(img.isExpanded),
      });
    }
  });

  return { blocks, imageUrls };
}

export function formatSingleNote(
  rawNote: Note,
  embedFullDataUriInMarkdown: boolean = false
): string {
  const note = decryptNoteFields(rawNote);
  const title = (note.title || '').trim() || 'Untitled Note';
  const updatedStr = new Date(note.updatedAt || Date.now()).toLocaleString();
  const tagsList = (note.tags || []).map((t) => t.trim().replace(/^#/, '')).filter(Boolean);
  const { blocks } = parseNoteHtmlToBlocks(note.content, note.images);

  const metaParts: string[] = [];
  if (note.category) metaParts.push(`**Category:** ${note.category}`);
  if (tagsList.length > 0) metaParts.push(`**Tags:** ${tagsList.map((t) => `#${t}`).join(' ')}`);
  metaParts.push(`**Updated:** ${updatedStr}`);

  let imgCounter = 0;
  const bodyLines = blocks.map((blk) => {
    if (blk.type === 'image' && blk.imageSrc) {
      imgCounter++;
      const targetUri = embedFullDataUriInMarkdown
        ? blk.imageSrc
        : `aurora_image_${imgCounter}.png`;
      return `\n![Note Image ${imgCounter}](${targetUri})\n`;
    }
    const text = blk.text || '';
    if (blk.hasCheckbox) {
      return `- [${blk.checked ? 'x' : ' '}] ${text}`;
    }
    if (blk.hasBullet) {
      return `- ${text}`;
    }
    return text;
  });

  const cleanBody = bodyLines.join('\n').trim();

  const checklistSection =
    note.checklist && note.checklist.length > 0
      ? `\n### Checklist\n` +
        note.checklist.map((item) => `- [${item.completed ? 'x' : ' '}] ${item.text}`).join('\n') +
        '\n'
      : '';

  return (
    [
      `# ${title}`,
      '',
      `> ${metaParts.join(' | ')}`,
      '',
      cleanBody ? `${cleanBody}\n` : '',
      checklistSection,
    ]
      .filter((line) => line !== undefined)
      .join('\n')
      .trim() + '\n'
  );
}

export function formatNotesForShare(
  notes: Note[],
  embedFullDataUriInMarkdown: boolean = false
): string {
  if (!notes.length) return '';
  if (notes.length === 1) return formatSingleNote(notes[0], embedFullDataUriInMarkdown);
  const divider = '\n\n---\n\n';
  return (
    notes
      .map((n) => formatSingleNote(n, embedFullDataUriInMarkdown).trimEnd())
      .join(divider) + '\n'
  );
}

export function getSuggestedFileName(notes: Note[], format: ShareFormat): string {
  if (notes.length === 1) {
    const plain = decryptNoteFields(notes[0]);
    const raw = (plain.title || 'Untitled_Note').trim();
    const clean =
      raw
        .replace(/[^a-zA-Z0-9._\- ]/g, '')
        .trim()
        .replace(/\s+/g, '_')
        .slice(0, 48) || 'Aurora_Note';
    return `${clean}.${format}`;
  }
  const datePart = new Date().toISOString().slice(0, 10);
  return `Aurora_Notes_${notes.length}_${datePart}.${format}`;
}

async function loadHtmlImage(src: string): Promise<HTMLImageElement | null> {
  return new Promise((resolve) => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => resolve(img);
    img.onerror = () => resolve(null);
    img.src = src;
  });
}

function wrapCanvasText(
  ctx: CanvasRenderingContext2D,
  text: string,
  maxWidth: number
): string[] {
  if (!text) return [''];
  const words = text.split(/\s+/);
  const lines: string[] = [];
  let current = '';

  for (const word of words) {
    const candidate = current ? `${current} ${word}` : word;
    if (ctx.measureText(candidate).width <= maxWidth) {
      current = candidate;
    } else {
      if (current) lines.push(current);
      current = word;
    }
  }
  if (current) lines.push(current);
  return lines.length > 0 ? lines : [''];
}

export async function renderNotesToFullLengthPngDataUrl(rawNotes: Note[]): Promise<string> {
  const plainNotes = rawNotes.map(decryptNoteFields);
  const width = 900;
  const outerPad = 36;
  const cardPad = 36;
  const contentWidth = width - outerPad * 2 - cardPad * 2;

  const measureCanvas = document.createElement('canvas');
  const mCtx = measureCanvas.getContext('2d');
  if (!mCtx) return '';

  interface MeasuredCard {
    titleLines: string[];
    dateStr: string;
    tagsStr: string;
    items: Array<
      | {
          kind: 'line';
          lines: string[];
          hasCheckbox: boolean;
          checked: boolean;
          hasBullet: boolean;
        }
      | {
          kind: 'image';
          img: HTMLImageElement;
          drawW: number;
          drawH: number;
        }
    >;
    cardHeight: number;
  }

  const measuredCards: MeasuredCard[] = [];
  let totalCanvasHeight = outerPad;

  for (const note of plainNotes) {
    let cardH = cardPad;
    const title = (note.title || '').trim() || 'Untitled Note';
    mCtx.font = 'bold 34px Inter, system-ui, sans-serif';
    const titleLines = wrapCanvasText(mCtx, title, contentWidth);
    cardH += titleLines.length * 42 + 12;

    const dateStr = `Updated ${new Date(note.updatedAt || Date.now()).toLocaleString()}`;
    cardH += 30;

    const tagsList = (note.tags || []).map((t) => t.trim().replace(/^#/, '')).filter(Boolean);
    const tagsStr = tagsList.map((t) => `#${t}`).join('   ');
    if (tagsStr) {
      cardH += 40;
    }
    cardH += 26; // divider

    const { blocks } = parseNoteHtmlToBlocks(note.content, note.images);
    const items: MeasuredCard['items'] = [];

    for (const blk of blocks) {
      if (blk.type === 'image' && blk.imageSrc) {
        const loaded = await loadHtmlImage(blk.imageSrc);
        if (loaded && loaded.width > 0 && loaded.height > 0) {
          const targetMaxW = blk.isExpanded ? contentWidth : Math.round(contentWidth * 0.62);
          const scale = targetMaxW / loaded.width;
          const drawW = Math.max(160, Math.round(loaded.width * scale));
          const drawH = Math.min(1100, Math.max(110, Math.round(loaded.height * scale)));
          items.push({ kind: 'image', img: loaded, drawW, drawH });
          cardH += drawH + 20;
        }
      } else if (blk.type === 'line') {
        const indent = (blk.hasCheckbox ? 38 : 0) + (blk.hasBullet ? 26 : 0);
        mCtx.font = '500 23px Inter, system-ui, sans-serif';
        const wrapped = wrapCanvasText(mCtx, blk.text || '', contentWidth - indent);
        items.push({
          kind: 'line',
          lines: wrapped,
          hasCheckbox: Boolean(blk.hasCheckbox),
          checked: Boolean(blk.checked),
          hasBullet: Boolean(blk.hasBullet),
        });
        cardH += wrapped.length * 32 + 10;
      }
    }

    if (note.checklist && note.checklist.length > 0) {
      for (const item of note.checklist) {
        mCtx.font = '500 23px Inter, system-ui, sans-serif';
        const wrapped = wrapCanvasText(mCtx, item.text || '', contentWidth - 38);
        items.push({
          kind: 'line',
          lines: wrapped,
          hasCheckbox: true,
          checked: Boolean(item.completed),
          hasBullet: false,
        });
        cardH += wrapped.length * 32 + 10;
      }
    }

    cardH += cardPad + 32; // footer watermark
    measuredCards.push({
      titleLines,
      dateStr,
      tagsStr,
      items,
      cardHeight: cardH,
    });
    totalCanvasHeight += cardH + 28;
  }

  totalCanvasHeight = Math.max(380, totalCanvasHeight + outerPad - 28);
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = totalCanvasHeight;
  const ctx = canvas.getContext('2d');
  if (!ctx) return '';

  // Background
  ctx.fillStyle = '#0D0B14';
  ctx.fillRect(0, 0, width, totalCanvasHeight);

  let curTop = outerPad;
  for (const card of measuredCards) {
    // Card background
    ctx.fillStyle = '#161224';
    ctx.strokeStyle = '#4C2E85';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.roundRect(outerPad, curTop, width - outerPad * 2, card.cardHeight, 28);
    ctx.fill();
    ctx.stroke();

    let y = curTop + cardPad;
    const leftX = outerPad + cardPad;
    const rightX = width - outerPad - cardPad;

    // Title
    ctx.fillStyle = '#F5F3FF';
    ctx.font = 'bold 34px Inter, system-ui, sans-serif';
    for (const tLine of card.titleLines) {
      y += 34;
      ctx.fillText(tLine, leftX, y);
      y += 8;
    }
    y += 10;

    // Date
    ctx.fillStyle = '#9CA3AF';
    ctx.font = '500 18px Inter, system-ui, sans-serif';
    ctx.fillText(card.dateStr, leftX, y + 16);
    y += 30;

    // Tags
    if (card.tagsStr) {
      ctx.font = 'bold 18px Inter, system-ui, sans-serif';
      const pillW = Math.min(contentWidth, ctx.measureText(card.tagsStr).width + 26);
      ctx.fillStyle = '#24193C';
      ctx.strokeStyle = '#6D28D9';
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.roundRect(leftX, y, pillW, 32, 10);
      ctx.fill();
      ctx.stroke();
      ctx.fillStyle = '#C4B5FD';
      ctx.fillText(card.tagsStr, leftX + 13, y + 22);
      y += 40;
    }

    // Divider
    y += 8;
    ctx.strokeStyle = '#2A243D';
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.moveTo(leftX, y);
    ctx.lineTo(rightX, y);
    ctx.stroke();
    y += 18;

    // Body items
    for (const item of card.items) {
      if (item.kind === 'line') {
        let lineX = leftX;
        if (item.hasCheckbox) {
          const boxSize = 22;
          const boxY = y + 4;
          ctx.beginPath();
          ctx.roundRect(lineX, boxY, boxSize, boxSize, 6);
          if (item.checked) {
            ctx.fillStyle = '#7C3AED';
            ctx.fill();
            ctx.strokeStyle = '#FFFFFF';
            ctx.lineWidth = 2.5;
            ctx.beginPath();
            ctx.moveTo(lineX + 5, boxY + 11);
            ctx.lineTo(lineX + 10, boxY + 16);
            ctx.lineTo(lineX + 17, boxY + 6);
            ctx.stroke();
          } else {
            ctx.strokeStyle = '#A78BFA';
            ctx.lineWidth = 2.2;
            ctx.stroke();
          }
          lineX += 38;
        }

        if (item.hasBullet) {
          ctx.fillStyle = '#A78BFA';
          ctx.beginPath();
          ctx.arc(lineX + 8, y + 15, 4.5, 0, Math.PI * 2);
          ctx.fill();
          lineX += 26;
        }

        ctx.font = '500 23px Inter, system-ui, sans-serif';
        ctx.fillStyle = item.checked ? '#9CA3AF' : '#EDE9FE';
        for (const lText of item.lines) {
          ctx.fillText(lText, lineX, y + 23);
          if (item.checked && lText) {
            const tw = ctx.measureText(lText).width;
            ctx.strokeStyle = '#9CA3AF';
            ctx.lineWidth = 2;
            ctx.beginPath();
            ctx.moveTo(lineX, y + 15);
            ctx.lineTo(lineX + tw, y + 15);
            ctx.stroke();
          }
          y += 32;
        }
        y += 10;
      } else if (item.kind === 'image') {
        ctx.save();
        ctx.beginPath();
        ctx.roundRect(leftX, y + 4, item.drawW, item.drawH, 16);
        ctx.clip();
        ctx.drawImage(item.img, leftX, y + 4, item.drawW, item.drawH);
        ctx.restore();
        ctx.strokeStyle = '#4C2E85';
        ctx.lineWidth = 1.5;
        ctx.beginPath();
        ctx.roundRect(leftX, y + 4, item.drawW, item.drawH, 16);
        ctx.stroke();
        y += item.drawH + 20;
      }
    }

    // Footer watermark
    ctx.fillStyle = '#7C3AED';
    ctx.font = 'bold 17px Inter, system-ui, sans-serif';
    ctx.fillText('Aurora Notes', leftX, curTop + card.cardHeight - 18);

    curTop += card.cardHeight + 28;
  }

  return canvas.toDataURL('image/png');
}

export const ShareNoteModal: React.FC<ShareNoteModalProps> = ({ notes, onClose }) => {
  const [format, setFormat] = useState<ShareFormat>('md');
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [fullLengthImageDataUrl, setFullLengthImageDataUrl] = useState<string>('');

  const formattedPreview = useMemo(() => formatNotesForShare(notes, false), [notes]);
  const formattedExportMarkdown = useMemo(() => formatNotesForShare(notes, true), [notes]);
  const suggestedFileName = useMemo(() => getSuggestedFileName(notes, format), [notes, format]);

  const embeddedMarkdownImages = useMemo(() => {
    const urls: string[] = [];
    notes.forEach((raw) => {
      const plain = decryptNoteFields(raw);
      const { imageUrls } = parseNoteHtmlToBlocks(plain.content, plain.images);
      imageUrls.forEach((u) => {
        if (u && !urls.includes(u)) urls.push(u);
      });
    });
    return urls;
  }, [notes]);

  useEffect(() => {
    let active = true;
    renderNotesToFullLengthPngDataUrl(notes).then((dataUrl) => {
      if (active) setFullLengthImageDataUrl(dataUrl);
    });
    return () => {
      active = false;
    };
  }, [notes]);

  if (!notes.length) return null;

  const handleSaveNoteImageToGallery = () => {
    if (!fullLengthImageDataUrl) return;
    const a = document.createElement('a');
    a.href = fullLengthImageDataUrl;
    a.download = getSuggestedFileName(notes, 'png');
    document.body.appendChild(a);
    a.click();
    a.remove();
    logger.security('SHARE', `Saved full-length note image (${a.download}) to Android Gallery`);
    setStatusMessage('Saved full-length note image to Android Gallery (Pictures/AuroraNotes)!');
  };

  const handleShareToAppsAndContacts = async () => {
    const title = notes.length === 1 ? notes[0].title || 'Aurora Note' : `${notes.length} Aurora Notes`;

    if (typeof navigator !== 'undefined' && navigator.share) {
      try {
        await navigator.share({
          title,
          text: format === 'md' ? formattedExportMarkdown : title,
        });
        logger.security('SHARE', `Shared ${notes.length} note(s) to apps & contacts (.${format})`);
        onClose();
        return;
      } catch (err) {
        if ((err as Error)?.name === 'AbortError') return;
      }
    }

    if (format === 'png') {
      handleSaveNoteImageToGallery();
      return;
    }

    try {
      await navigator.clipboard?.writeText(formattedExportMarkdown);
      setStatusMessage(`Copied ${suggestedFileName} Markdown content to clipboard!`);
      logger.security('SHARE', `Copied ${notes.length} note(s) as .${format} to clipboard`);
    } catch {
      setStatusMessage('Could not access system share sheet in this preview.');
    }
  };

  const handleSaveToFolderPicker = async () => {
    if (format === 'png') {
      handleSaveNoteImageToGallery();
      return;
    }

    const mimeType = 'text/markdown';
    const blob = new Blob([formattedExportMarkdown], { type: `${mimeType};charset=utf-8` });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = suggestedFileName;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
    logger.security('SHARE', `Downloaded ${suggestedFileName}`);
    setStatusMessage(`Saved ${suggestedFileName} with embedded images!`);
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
                Markdown with images or full-length Gallery image
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

        {/* Format Selection: Markdown (.md) vs Note Image (.png) */}
        <div className="space-y-1.5">
          <span className="text-[10px] font-bold uppercase tracking-wider text-[#5925DC] dark:text-[#C296FD]">
            Choose Export Format
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
                <div className="text-[10px] opacity-75">Full Markdown + images</div>
              </div>
              {format === 'md' && <Check className="w-4 h-4 shrink-0" />}
            </button>

            <button
              type="button"
              onClick={() => setFormat('png')}
              className={`p-3 rounded-2xl border text-left transition-all cursor-pointer flex items-center justify-between ${
                format === 'png'
                  ? 'bg-[#F4EBFF] dark:bg-[#2B1E3E] border-[#5925DC] dark:border-[#9D5AF5] text-[#5925DC] dark:text-[#C296FD]'
                  : 'bg-[#FAF8FB] dark:bg-[#120E17] border-[#EADBFC] dark:border-[#322742] text-[#484455] dark:text-[#A39BAC]'
              }`}
            >
              <div>
                <div className="text-xs font-bold">Note Image (.png)</div>
                <div className="text-[10px] opacity-75">Full-length Gallery image</div>
              </div>
              {format === 'png' && <Check className="w-4 h-4 shrink-0" />}
            </button>
          </div>
        </div>

        {/* File Preview */}
        <div className="space-y-1.5">
          <div className="flex items-center justify-between text-xs">
            <span className="font-semibold text-[#1E1A24] dark:text-[#F7EEFC] flex items-center gap-1.5 truncate">
              {format === 'png' ? (
                <ImageIcon className="w-3.5 h-3.5 text-[#5925DC] dark:text-[#C296FD]" />
              ) : (
                <FileText className="w-3.5 h-3.5 text-[#5925DC] dark:text-[#C296FD]" />
              )}
              <span className="truncate">{suggestedFileName}</span>
            </span>
            <span className="text-[10px] text-[#797487] dark:text-[#A39BAC]">
              {format === 'png' ? 'Full-length PNG' : `${formattedPreview.length} chars`}
            </span>
          </div>

          <div className="p-3 rounded-2xl bg-[#FAF8FB] dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] max-h-52 overflow-y-auto space-y-2.5">
            {format === 'png' ? (
              fullLengthImageDataUrl ? (
                <img
                  src={fullLengthImageDataUrl}
                  alt="Full-length note image preview"
                  className="w-full rounded-xl border border-[#EADBFC] dark:border-[#322742] object-contain"
                />
              ) : (
                <div className="text-xs text-center py-6 text-[#797487] dark:text-[#A39BAC]">
                  Rendering full-length note image...
                </div>
              )
            ) : (
              <>
                <pre className="text-[11px] font-mono text-[#484455] dark:text-[#A39BAC] whitespace-pre-wrap">
                  {formattedPreview}
                </pre>
                {embeddedMarkdownImages.length > 0 && (
                  <div className="pt-2 border-t border-[#EADBFC] dark:border-[#322742] space-y-1.5">
                    <div className="text-[10px] font-bold uppercase tracking-wider text-[#5925DC] dark:text-[#C296FD]">
                      Embedded Markdown Images ({embeddedMarkdownImages.length})
                    </div>
                    <div className="grid grid-cols-2 gap-2">
                      {embeddedMarkdownImages.map((imgUrl, idx) => (
                        <img
                          key={idx}
                          src={imgUrl}
                          alt={`Embedded Markdown image ${idx + 1}`}
                          className="w-full h-24 object-cover rounded-lg border border-[#EADBFC] dark:border-[#322742]"
                        />
                      ))}
                    </div>
                  </div>
                )}
              </>
            )}
          </div>
        </div>

        {statusMessage && (
          <div className="p-2.5 rounded-xl bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-200 dark:border-emerald-900 text-emerald-700 dark:text-emerald-300 text-xs font-medium text-center">
            {statusMessage}
          </div>
        )}

        {/* Action Buttons */}
        {format === 'png' ? (
          <div className="space-y-2 pt-1">
            <button
              type="button"
              onClick={handleSaveNoteImageToGallery}
              className="w-full py-2.5 px-4 rounded-2xl bg-gradient-to-r from-[#5925DC] to-[#9B51E0] dark:from-[#9D5AF5] dark:to-[#C296FD] text-white text-xs font-bold shadow-sm flex items-center justify-center gap-2 active:scale-95 transition-all cursor-pointer"
            >
              <Download className="w-4 h-4" />
              <span>Save Note as Image to Android Gallery (.PNG)</span>
            </button>

            <button
              type="button"
              onClick={handleShareToAppsAndContacts}
              className="w-full py-2.5 px-3 rounded-2xl bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] border border-[#EADBFC] dark:border-[#322742] text-xs font-semibold flex items-center justify-center gap-1.5 active:scale-95 transition-all cursor-pointer"
            >
              <Share2 className="w-3.5 h-3.5" />
              <span>Share Note Image to Apps & Contacts (PNG)</span>
            </button>
          </div>
        ) : (
          <div className="space-y-2 pt-1">
            <button
              type="button"
              onClick={handleShareToAppsAndContacts}
              className="w-full py-2.5 px-4 rounded-2xl bg-gradient-to-r from-[#5925DC] to-[#9B51E0] dark:from-[#9D5AF5] dark:to-[#C296FD] text-white text-xs font-bold shadow-sm flex items-center justify-center gap-2 active:scale-95 transition-all cursor-pointer"
            >
              <Share2 className="w-4 h-4" />
              <span>Share Markdown with Images (.MD)</span>
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
                  await navigator.clipboard?.writeText(formattedExportMarkdown);
                  setStatusMessage('Copied Markdown (.md) content to clipboard!');
                }}
                className="py-2.5 px-3 rounded-2xl bg-[#FAF8FB] dark:bg-[#120E17] text-[#1E1A24] dark:text-[#F7EEFC] border border-[#EADBFC] dark:border-[#322742] text-xs font-semibold flex items-center justify-center gap-1.5 active:scale-95 transition-all cursor-pointer"
              >
                <Copy className="w-3.5 h-3.5" />
                <span>Copy Markdown</span>
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

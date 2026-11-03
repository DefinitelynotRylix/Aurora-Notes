export interface DetectedLinkSegment {
  type: 'text' | 'link';
  text: string;
  url?: string;
}

const URL_REGEX = /((?:https?:\/\/|www\.)[^\s<>"']+)/gi;

/**
 * Strips trailing punctuation (like .,;:!?) that is likely sentence punctuation rather than part of the URL.
 */
export function splitTrailingPunctuation(rawMatch: string): { cleanUrl: string; trailing: string } {
  let cleanUrl = rawMatch;
  let trailing = '';

  while (cleanUrl.length > 0) {
    const lastChar = cleanUrl[cleanUrl.length - 1];
    if (['.', ',', ';', ':', '!', '?', "'", '"'].includes(lastChar)) {
      trailing = lastChar + trailing;
      cleanUrl = cleanUrl.slice(0, -1);
    } else if (lastChar === ')' && (cleanUrl.match(/\(/g)?.length || 0) < (cleanUrl.match(/\)/g)?.length || 0)) {
      trailing = lastChar + trailing;
      cleanUrl = cleanUrl.slice(0, -1);
    } else if (lastChar === ']' && (cleanUrl.match(/\[/g)?.length || 0) < (cleanUrl.match(/\]/g)?.length || 0)) {
      trailing = lastChar + trailing;
      cleanUrl = cleanUrl.slice(0, -1);
    } else {
      break;
    }
  }

  return { cleanUrl, trailing };
}

/**
 * Normalizes a URL string so it always includes http:// or https:// for opening in the default browser.
 */
export function normalizeBrowserUrl(rawUrl: string): string {
  const trimmed = rawUrl.trim();
  if (!trimmed) return '';
  if (/^https?:\/\//i.test(trimmed)) {
    return trimmed;
  }
  return `https://${trimmed}`;
}

/**
 * Splits plain text into text and link segments for rendering clickable purple links.
 */
export function parseTextWithLinks(text: string): DetectedLinkSegment[] {
  if (!text) return [];
  const segments: DetectedLinkSegment[] = [];
  let lastIndex = 0;

  const regex = new RegExp(URL_REGEX.source, 'gi');
  let match: RegExpExecArray | null;

  while ((match = regex.exec(text)) !== null) {
    const matchIndex = match.index;
    const rawUrl = match[0];
    const { cleanUrl, trailing } = splitTrailingPunctuation(rawUrl);

    if (!cleanUrl || cleanUrl.length < 4) {
      continue;
    }

    if (matchIndex > lastIndex) {
      segments.push({
        type: 'text',
        text: text.slice(lastIndex, matchIndex),
      });
    }

    segments.push({
      type: 'link',
      text: cleanUrl,
      url: normalizeBrowserUrl(cleanUrl),
    });

    if (trailing) {
      segments.push({
        type: 'text',
        text: trailing,
      });
    }

    lastIndex = matchIndex + rawUrl.length;
  }

  if (lastIndex < text.length) {
    segments.push({
      type: 'text',
      text: text.slice(lastIndex),
    });
  }

  return segments;
}

/**
 * Opens a URL in the system's default browser (or via Android Intent bridge when available).
 */
export function openUrlInDefaultBrowser(rawUrl: string): void {
  const normalized = normalizeBrowserUrl(rawUrl);
  if (!normalized) return;

  try {
    const win = window as unknown as {
      AndroidBridge?: { openExternalBrowser?: (url: string) => void };
    };
    if (win.AndroidBridge && typeof win.AndroidBridge.openExternalBrowser === 'function') {
      win.AndroidBridge.openExternalBrowser(normalized);
      return;
    }
  } catch {
    // fallback to standard anchor navigation
  }

  const anchor = document.createElement('a');
  anchor.href = normalized;
  anchor.target = '_blank';
  anchor.rel = 'noopener noreferrer';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
}

/**
 * Converts plain text (for example from a paste event) into safe HTML where URLs are wrapped in
 * purple clickable <a> elements.
 */
export function convertPlainTextToLinkedHtml(plainText: string): string {
  const escapeHtml = (str: string) =>
    str
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');

  const lines = plainText.split(/\r?\n/);
  return lines
    .map((line) => {
      if (!line) return '<br>';
      const segments = parseTextWithLinks(line);
      return segments
        .map((seg) => {
          if (seg.type === 'link' && seg.url) {
            const safeUrl = escapeHtml(seg.url);
            const safeLabel = escapeHtml(seg.text);
            return `<a href="${safeUrl}" target="_blank" rel="noopener noreferrer" data-aurora-link="true" class="aurora-note-link text-[#8B3DFF] dark:text-[#C296FD] underline decoration-[#8B3DFF] dark:decoration-[#C296FD] font-semibold cursor-pointer">${safeLabel}</a>`;
          }
          return escapeHtml(seg.text);
        })
        .join('');
    })
    .join('<br>');
}

/**
 * Walks a contentEditable container and wraps any unlinked URLs in text nodes with purple clickable <a> tags,
 * while also ensuring existing <a> tags have the purple clickable styling.
 */
export function linkifyDomContainer(container: HTMLElement): void {
  // Style any existing anchor tags first
  const existingAnchors = container.querySelectorAll('a');
  existingAnchors.forEach((a) => {
    const rawHref = a.getAttribute('href') || a.textContent || '';
    if (rawHref) {
      a.setAttribute('href', normalizeBrowserUrl(rawHref));
    }
    a.setAttribute('target', '_blank');
    a.setAttribute('rel', 'noopener noreferrer');
    a.setAttribute('data-aurora-link', 'true');
    a.className =
      'aurora-note-link text-[#8B3DFF] dark:text-[#C296FD] underline decoration-[#8B3DFF] dark:decoration-[#C296FD] font-semibold cursor-pointer';
  });

  const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT, {
    acceptNode(node) {
      const parent = node.parentElement;
      if (!parent) return NodeFilter.FILTER_REJECT;
      if (parent.closest('a')) return NodeFilter.FILTER_REJECT;
      if (node.nodeValue && /((?:https?:\/\/|www\.)[^\s<>"']+)/i.test(node.nodeValue)) {
        return NodeFilter.FILTER_ACCEPT;
      }
      return NodeFilter.FILTER_SKIP;
    },
  });

  const textNodesToProcess: Text[] = [];
  let currentNode = walker.nextNode();
  while (currentNode) {
    textNodesToProcess.push(currentNode as Text);
    currentNode = walker.nextNode();
  }

  textNodesToProcess.forEach((textNode) => {
    const content = textNode.nodeValue || '';
    const segments = parseTextWithLinks(content);
    if (!segments.some((s) => s.type === 'link')) return;

    const fragment = document.createDocumentFragment();
    segments.forEach((seg) => {
      if (seg.type === 'link' && seg.url) {
        const a = document.createElement('a');
        a.href = seg.url;
        a.target = '_blank';
        a.rel = 'noopener noreferrer';
        a.setAttribute('data-aurora-link', 'true');
        a.className =
          'aurora-note-link text-[#8B3DFF] dark:text-[#C296FD] underline decoration-[#8B3DFF] dark:decoration-[#C296FD] font-semibold cursor-pointer';
        a.textContent = seg.text;
        fragment.appendChild(a);
      } else {
        fragment.appendChild(document.createTextNode(seg.text));
      }
    });

    textNode.parentNode?.replaceChild(fragment, textNode);
  });
}

import React from 'react';
import {
  Sparkles,
  Lock,
  Image as ImageIcon,
  ListChecks,
  Edit3,
  Check,
  X
} from 'lucide-react';

export const CURRENT_WHATS_NEW_VERSION = '1.3.35';

interface WhatsNewModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const WhatsNewModal: React.FC<WhatsNewModalProps> = ({
  isOpen,
  onClose,
}) => {
  if (!isOpen) return null;

  const highlights = [
    {
      icon: <Edit3 className="w-5 h-5 text-[#5925DC] dark:text-[#C296FD]" />,
      title: 'Instant Menu Animation & Hold-Note Action Navbar',
      badge: 'NEW FEATURE',
      description:
        'Menu switching across Notes, Archive, Trash, and Settings is now 100% instant with zero wait delay. Hold down any note to slide the navigation bar under the screen and pop up the new Selection Action Navbar with Share, Archive, Edit, and Delete.',
    },
    {
      icon: <ImageIcon className="w-5 h-5 text-[#5925DC] dark:text-[#C296FD]" />,
      title: 'Gallery Note Image Export & Decrypted Markdown',
      badge: 'EXPORT & MEDIA',
      description:
        'Insert lossless images in dedicated vertical blocks, save any note as a full-length visual image directly into your Android Gallery, or export decrypted Markdown (.md) notes with companion .png image files.',
    },
    {
      icon: <ListChecks className="w-5 h-5 text-[#8B3DFF] dark:text-[#C296FD]" />,
      title: 'Persistent Lists, Checkboxes & Checkmark Save',
      badge: 'EDITOR',
      description:
        'Bullet lists and checkboxes stay active across unlimited Enter presses without cutting off, coexist cleanly on the same line, and pair with compact H2/H1 buttons and the top-right Checkmark quick-save indicator.',
    },
    {
      icon: <Lock className="w-5 h-5 text-emerald-600 dark:text-emerald-400" />,
      title: 'AES-256-GCM Cryptography & Codebase Optimization',
      badge: 'SECURITY',
      description:
        'Every note and attached image in your Room SQLite database and private storage is encrypted at rest with AndroidKeyStore AES-256-GCM cryptography, automatic orphaned file cleanup, and streamlined rendering.',
    },
  ];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-2.5 sm:p-5 bg-[#1E1A24]/65 dark:bg-black/80 backdrop-blur-md animate-fadeIn overflow-y-auto">
      <div className="glass-floating w-full max-w-lg rounded-2xl sm:rounded-3xl bg-white/95 dark:bg-[#1B1524]/95 border border-[#EADBFC] dark:border-[#322742] shadow-[0_24px_60px_rgba(89,37,220,0.25)] dark:shadow-[0_24px_60px_rgba(0,0,0,0.85)] flex flex-col overflow-hidden max-h-[calc(100dvh-1.25rem)] sm:max-h-[88dvh] my-auto">
        {/* Header (Anchored, never compressed) */}
        <div className="glass-panel px-4 sm:px-6 py-3 sm:py-3.5 border-b border-[#EADBFC] dark:border-[#322742] flex items-center justify-between gap-2.5 shrink-0">
          <div className="flex items-center gap-2.5 sm:gap-3 min-w-0">
            <div className="w-9 h-9 sm:w-10 sm:h-10 rounded-xl sm:rounded-2xl bg-gradient-to-tr from-[#5925DC] to-[#9B51E0] text-white flex items-center justify-center shadow-sm shrink-0">
              <Sparkles className="w-4 h-4 sm:w-5 sm:h-5" />
            </div>
            <div className="min-w-0">
              <span className="inline-block text-[9px] sm:text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-full bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] border border-[#EADBFC] dark:border-[#322742]">
                Major Update • v{CURRENT_WHATS_NEW_VERSION}
              </span>
              <h2 className="font-bold text-sm sm:text-base md:text-lg text-[#1E1A24] dark:text-[#F7EEFC] mt-0.5 leading-tight truncate">
                What&apos;s New in Aurora Notes
              </h2>
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            aria-label="Close What's New screen"
            className="p-1.5 rounded-xl text-[#797487] dark:text-[#A39BAC] hover:text-[#1E1A24] dark:hover:text-[#F7EEFC] hover:bg-[#FAF8FB] dark:hover:bg-[#120E17] transition-all cursor-pointer shrink-0"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Highlights List (Scrollable on small phones, min-h-0 allows shrinking) */}
        <div className="p-3.5 sm:p-5 overflow-y-auto space-y-2.5 sm:space-y-3 flex-1 min-h-0 custom-scrollbar">
          <p className="text-[11.5px] sm:text-xs text-[#797487] dark:text-[#A39BAC] leading-relaxed">
            Aurora Notes has been updated! Here is a quick overview of the new capabilities in this release (shown once after updating):
          </p>

          <div className="space-y-2.5">
            {highlights.map((item) => (
              <div
                key={item.title}
                className="p-3 sm:p-3.5 rounded-xl sm:rounded-2xl bg-[#FAF8FB] dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] flex items-start gap-2.5 sm:gap-3 shadow-2xs"
              >
                <div className="w-8 h-8 sm:w-9 sm:h-9 rounded-lg sm:rounded-xl bg-[#F4EBFF] dark:bg-[#2B1E3E] border border-[#EADBFC] dark:border-[#322742] flex items-center justify-center shrink-0 mt-0.5">
                  {item.icon}
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-1.5 sm:gap-2 flex-wrap">
                    <h3 className="text-xs sm:text-sm font-bold text-[#1E1A24] dark:text-[#F7EEFC] leading-snug">
                      {item.title}
                    </h3>
                    <span className="text-[8.5px] sm:text-[9px] font-bold uppercase tracking-wider px-1.5 py-0.2 rounded bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD]">
                      {item.badge}
                    </span>
                  </div>
                  <p className="text-[11px] sm:text-xs text-[#484455] dark:text-[#A39BAC] mt-1 leading-relaxed">
                    {item.description}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Footer (Always anchored and 100% visible, Got It button never cut off) */}
        <div className="glass-panel px-4 sm:px-6 py-2.5 sm:py-3.5 border-t border-[#EADBFC] dark:border-[#322742] flex flex-col-reverse sm:flex-row items-center justify-between gap-2 sm:gap-3 shrink-0 bg-white/98 dark:bg-[#1B1524]/98">
          <span className="text-[10px] sm:text-[11px] text-[#797487] dark:text-[#A39BAC] font-medium text-center sm:text-left">
            Shown once after update restart
          </span>

          <button
            type="button"
            onClick={onClose}
            aria-label="Got It, Let's Go"
            className="w-full sm:w-auto px-5 py-2.5 rounded-xl text-xs sm:text-sm font-bold bg-gradient-to-r from-[#5925DC] to-[#9B51E0] hover:from-[#4c1fc4] hover:to-[#8c42d3] text-white shadow-[0_4px_16px_rgba(89,37,220,0.3)] flex items-center justify-center gap-1.5 transition-all active:scale-95 cursor-pointer min-h-[42px]"
          >
            <Check className="w-4 h-4 stroke-[2.5]" />
            <span>Got It, Let&apos;s Go</span>
          </button>
        </div>
      </div>
    </div>
  );
};

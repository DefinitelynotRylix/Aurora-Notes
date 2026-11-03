import React from 'react';
import {
  Sparkles,
  Lock,
  Sliders,
  ShieldCheck,
  Clock,
  Check,
  X
} from 'lucide-react';

export const CURRENT_WHATS_NEW_VERSION = '1.3.30';

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
      icon: <Lock className="w-5 h-5 text-emerald-600 dark:text-emerald-400" />,
      title: 'Mandatory AES-256-GCM Cryptography & Private Storage',
      badge: 'CRYPTOGRAPHY',
      description:
        'Every note in the Room SQLite database and mirrored private device storage is now permanently encrypted at rest using hardware-backed AndroidKeyStore AES-256-GCM cryptography.',
    },
    {
      icon: <ShieldCheck className="w-5 h-5 text-[#5925DC] dark:text-[#C296FD]" />,
      title: 'Automatic Orphaned & Temp File Cleanup',
      badge: 'STORAGE',
      description:
        'Orphaned note files, incomplete .tmp writes, and stale shared or update caches are automatically swept on app launch, resume, and every note change.',
    },
    {
      icon: <Clock className="w-5 h-5 text-[#8B3DFF] dark:text-[#C296FD]" />,
      title: 'Live Word & Letter Counter + Exact Edit Timestamps',
      badge: 'EDITOR',
      description:
        'Track real-time word and letter counts at the top of the note editor, and see exact hour and minute last-edited timestamps alongside the day and month on every note.',
    },
    {
      icon: <Sliders className="w-5 h-5 text-[#5925DC] dark:text-[#C296FD]" />,
      title: 'Seamless Masonry ↔ Column Morphing & Folder Export',
      badge: 'REFINED',
      description:
        'Switch between 2-column Masonry Grid and Single Column views with continuous width and coordinate interpolation, plus export notes directly via the Android File Picker.',
    },
  ];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-5 bg-[#1E1A24]/60 dark:bg-black/75 backdrop-blur-md animate-fadeIn">
      <div className="glass-floating w-full max-w-xl rounded-3xl bg-white/95 dark:bg-[#1B1524]/95 border border-[#EADBFC] dark:border-[#322742] shadow-[0_24px_60px_rgba(89,37,220,0.25)] dark:shadow-[0_24px_60px_rgba(0,0,0,0.85)] flex flex-col overflow-hidden max-h-[90vh]">
        {/* Header */}
        <div className="glass-panel px-6 py-4 border-b border-[#EADBFC] dark:border-[#322742] flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-gradient-to-tr from-[#5925DC] to-[#9B51E0] text-white flex items-center justify-center shadow-sm shrink-0">
              <Sparkles className="w-5 h-5" />
            </div>
            <div>
              <span className="text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-full bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] border border-[#EADBFC] dark:border-[#322742]">
                Major Update • v{CURRENT_WHATS_NEW_VERSION}
              </span>
              <h2 className="font-bold text-base sm:text-lg text-[#1E1A24] dark:text-[#F7EEFC] mt-1 leading-tight">
                What&apos;s New in Aurora Notes
              </h2>
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            aria-label="Close What's New screen"
            className="p-1.5 rounded-xl text-[#797487] dark:text-[#A39BAC] hover:text-[#1E1A24] dark:hover:text-[#F7EEFC] hover:bg-[#FAF8FB] dark:hover:bg-[#120E17] transition-all cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Highlights List */}
        <div className="p-5 sm:p-6 overflow-y-auto space-y-3 flex-1">
          <p className="text-xs text-[#797487] dark:text-[#A39BAC] leading-relaxed">
            Aurora Notes has been updated! Here is a quick overview of the new capabilities in this release (shown once after updating):
          </p>

          <div className="space-y-3">
            {highlights.map((item) => (
              <div
                key={item.title}
                className="p-3.5 rounded-2xl bg-[#FAF8FB] dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] flex items-start gap-3 shadow-2xs"
              >
                <div className="w-9 h-9 rounded-xl bg-[#F4EBFF] dark:bg-[#2B1E3E] border border-[#EADBFC] dark:border-[#322742] flex items-center justify-center shrink-0 mt-0.5">
                  {item.icon}
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2 flex-wrap">
                    <h3 className="text-xs sm:text-sm font-bold text-[#1E1A24] dark:text-[#F7EEFC]">
                      {item.title}
                    </h3>
                    <span className="text-[9px] font-bold uppercase tracking-wider px-1.5 py-0.2 rounded bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD]">
                      {item.badge}
                    </span>
                  </div>
                  <p className="text-xs text-[#484455] dark:text-[#A39BAC] mt-1 leading-relaxed">
                    {item.description}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Footer */}
        <div className="glass-panel px-6 py-4 border-t border-[#EADBFC] dark:border-[#322742] flex items-center justify-between gap-3">
          <span className="text-[11px] text-[#797487] dark:text-[#A39BAC] font-medium">
            Shown once after update restart
          </span>

          <button
            type="button"
            onClick={onClose}
            className="px-5 py-2.5 rounded-xl text-xs font-bold bg-gradient-to-r from-[#5925DC] to-[#9B51E0] hover:from-[#4c1fc4] hover:to-[#8c42d3] text-white shadow-[0_4px_16px_rgba(89,37,220,0.3)] flex items-center gap-1.5 transition-all active:scale-95 cursor-pointer"
          >
            <Check className="w-4 h-4 stroke-[2.5]" />
            <span>Got It, Let&apos;s Go</span>
          </button>
        </div>
      </div>
    </div>
  );
};

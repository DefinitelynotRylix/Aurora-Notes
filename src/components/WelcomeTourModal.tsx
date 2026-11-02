import React, { useState } from 'react';
import {
  Sparkles,
  ShieldCheck,
  CheckCircle2,
  CheckSquare,
  Smartphone,
  Trash2,
  ArrowRight,
  ArrowLeft,
  X
} from 'lucide-react';

interface WelcomeTourModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const WelcomeTourModal: React.FC<WelcomeTourModalProps> = ({
  isOpen,
  onClose,
}) => {
  const [currentPage, setCurrentPage] = useState(0);

  if (!isOpen) return null;

  const pages = [
    {
      id: 'welcome',
      title: 'Welcome to Aurora Notes',
      subtitle: 'Tactile Minimalism Meets Modern Design',
      icon: <Sparkles className="w-6 h-6 text-[#5925DC] dark:text-[#C296FD]" />,
      badge: 'Design Philosophy',
      content: (
        <div className="space-y-4">
          <p className="text-sm text-[#484455] dark:text-[#C5BECD] leading-relaxed">
            Aurora Notes is an atmospheric, serene digital note-taking experience designed for deep, uninterrupted thought and fast organization.
          </p>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
            <div className="p-3.5 rounded-2xl bg-white dark:bg-[#1B1524] border border-[#EADBFC] dark:border-[#322742] shadow-xs">
              <span className="font-bold text-[#1E1A24] dark:text-[#F7EEFC] block mb-1">Atmospheric Canvas</span>
              <p className="text-[#797487] dark:text-[#A39BAC] leading-normal">
                Soft canvas with royal violet and radiant amethyst accents calibrated for long writing sessions.
              </p>
            </div>
            <div className="p-3.5 rounded-2xl bg-white dark:bg-[#1B1524] border border-[#EADBFC] dark:border-[#322742] shadow-xs">
              <span className="font-bold text-[#1E1A24] dark:text-[#F7EEFC] block mb-1">Liquid Navigation</span>
              <p className="text-[#797487] dark:text-[#A39BAC] leading-normal">
                Floating navigation with continuous active indicators and quick action buttons.
              </p>
            </div>
          </div>
          <div className="p-3.5 rounded-2xl bg-[#F4EBFF]/80 dark:bg-[#2B1E3E]/60 border border-[#EADBFC] dark:border-[#322742] flex items-center gap-3">
            <div className="w-8 h-8 rounded-xl bg-[#5925DC] text-white flex items-center justify-center font-bold text-sm shrink-0">
              ✦
            </div>
            <p className="text-xs text-[#5925DC] dark:text-[#C296FD] font-medium leading-relaxed">
              No account creation or cloud login required. Your private notes are ready immediately on first launch.
            </p>
          </div>
        </div>
      ),
    },
    {
      id: 'local-privacy',
      title: 'Local-First Storage & Minimal Internet',
      subtitle: 'Notes Stay 100% Offline • Internet Only For Updates',
      icon: <ShieldCheck className="w-6 h-6 text-emerald-600 dark:text-emerald-400" />,
      badge: 'Privacy & Minimal Permissions',
      content: (
        <div className="space-y-4">
          <p className="text-sm text-[#484455] dark:text-[#C5BECD] leading-relaxed">
            All of your notes, checklists, and custom tags are stored strictly on your device. Internet access is kept minimal and is used exclusively when you manually scan for app updates:
          </p>
          <div className="space-y-2.5">
            <div className="p-3 rounded-xl bg-emerald-50/80 dark:bg-emerald-950/40 border border-emerald-200 dark:border-emerald-800 flex items-center gap-2.5 text-xs text-emerald-900 dark:text-emerald-200 font-medium">
              <CheckCircle2 className="w-4 h-4 text-emerald-600 dark:text-emerald-400 shrink-0" />
              <span>100% Offline Notes: Your notes and tags never leave your phone&apos;s local SQLite storage.</span>
            </div>
            <div className="p-3 rounded-xl bg-emerald-50/80 dark:bg-emerald-950/40 border border-emerald-200 dark:border-emerald-800 flex items-center gap-2.5 text-xs text-emerald-900 dark:text-emerald-200 font-medium">
              <CheckCircle2 className="w-4 h-4 text-emerald-600 dark:text-emerald-400 shrink-0" />
              <span>Minimal Internet Permission: Only used when you tap &ldquo;Scan for Updates&rdquo; in Settings to pull GitHub Releases.</span>
            </div>
            <div className="p-3 rounded-xl bg-emerald-50/80 dark:bg-emerald-950/40 border border-emerald-200 dark:border-emerald-800 flex items-center gap-2.5 text-xs text-emerald-900 dark:text-emerald-200 font-medium">
              <CheckCircle2 className="w-4 h-4 text-emerald-600 dark:text-emerald-400 shrink-0" />
              <span>Zero Background Traffic or Telemetry: No analytics, cloud sync, or background network calls.</span>
            </div>
          </div>
        </div>
      ),
    },
    {
      id: 'native-security',
      title: 'Android Native Lock Screen',
      subtitle: 'System Fingerprint, Face or PIN Access',
      icon: <Smartphone className="w-6 h-6 text-[#5925DC] dark:text-[#C296FD]" />,
      badge: 'Biometric Security',
      content: (
        <div className="space-y-4">
          <p className="text-sm text-[#484455] dark:text-[#C5BECD] leading-relaxed">
            Secure access to Aurora Notes directly through your Android device's native lock screen:
          </p>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
            <div className="p-3.5 rounded-2xl bg-white dark:bg-[#1B1524] border border-[#EADBFC] dark:border-[#322742]">
              <span className="font-bold text-[#1E1A24] dark:text-[#F7EEFC] block mb-1">Native Hardware Authentication</span>
              <p className="text-[#797487] dark:text-[#A39BAC]">
                Uses your device's native biometric prompt (Fingerprint, Face unlock, or lock screen PIN).
              </p>
            </div>
            <div className="p-3.5 rounded-2xl bg-white dark:bg-[#1B1524] border border-[#EADBFC] dark:border-[#322742]">
              <span className="font-bold text-[#1E1A24] dark:text-[#F7EEFC] block mb-1">No Custom Passwords</span>
              <p className="text-[#797487] dark:text-[#A39BAC]">
                No need to memorize another password—your phone's native security gatekeeper handles verification.
              </p>
            </div>
          </div>
        </div>
      ),
    },
    {
      id: 'productivity',
      title: 'Tactile Notes, Pins & Checklists',
      subtitle: 'Fast Organization for Ideas and Tasks',
      icon: <CheckSquare className="w-6 h-6 text-[#9B51E0] dark:text-[#C296FD]" />,
      badge: 'Productivity Features',
      content: (
        <div className="space-y-4">
          <p className="text-sm text-[#484455] dark:text-[#C5BECD] leading-relaxed">
            Effortlessly organize your thoughts with custom category pins, smooth pinned note prioritization, and interactive checklists:
          </p>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs text-center">
            <div className="p-3.5 rounded-2xl bg-white dark:bg-[#1B1524] border border-[#EADBFC] dark:border-[#322742]">
              <div className="w-8 h-8 rounded-full bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] flex items-center justify-center mx-auto mb-2">
                <CheckSquare className="w-4 h-4" />
              </div>
              <span className="font-bold text-[#1E1A24] dark:text-[#F7EEFC] block mb-0.5">Inline Checklists</span>
              <p className="text-[11px] text-[#797487] dark:text-[#A39BAC]">
                Smooth checkboxes with real-time strike-through transitions.
              </p>
            </div>
            <div className="p-3.5 rounded-2xl bg-white dark:bg-[#1B1524] border border-[#EADBFC] dark:border-[#322742]">
              <div className="w-8 h-8 rounded-full bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] flex items-center justify-center mx-auto mb-2">
                <Trash2 className="w-4 h-4" />
              </div>
              <span className="font-bold text-[#1E1A24] dark:text-[#F7EEFC] block mb-0.5">Trash & Restore</span>
              <p className="text-[11px] text-[#797487] dark:text-[#A39BAC]">
                Deleted notes are safely held in Trash with one-by-one or bulk purge.
              </p>
            </div>
          </div>
        </div>
      ),
    },
  ];

  const current = pages[currentPage];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-5 bg-[#1E1A24]/50 dark:bg-black/70 backdrop-blur-md animate-fadeIn">
      <div className="glass-floating w-full max-w-2xl rounded-3xl bg-white/95 dark:bg-[#1B1524]/95 border border-[#EADBFC] dark:border-[#322742] shadow-[0_24px_60px_rgba(89,37,220,0.2)] dark:shadow-[0_24px_60px_rgba(0,0,0,0.8)] flex flex-col overflow-hidden max-h-[92vh]">
        {/* Header */}
        <div className="glass-panel px-6 py-4 border-b border-[#EADBFC] dark:border-[#322742] flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-xl bg-[#F4EBFF] dark:bg-[#2B1E3E] flex items-center justify-center border border-[#EADBFC] dark:border-[#322742]">
              {current.icon}
            </div>
            <div>
              <span className="text-[10px] font-bold uppercase tracking-wider text-[#5925DC] dark:text-[#C296FD]">
                {current.badge} • Step {currentPage + 1} of {pages.length}
              </span>
              <h2 className="font-bold text-base text-[#1E1A24] dark:text-[#F7EEFC] leading-tight">
                {current.title}
              </h2>
            </div>
          </div>

          <button
            onClick={onClose}
            aria-label="Close welcome tour"
            className="p-1.5 rounded-xl text-[#797487] dark:text-[#A39BAC] hover:text-[#1E1A24] dark:hover:text-[#F7EEFC] hover:bg-[#FAF8FB] dark:hover:bg-[#120E17] transition-all cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content Body */}
        <div className="p-6 overflow-y-auto flex-1">
          <div className="mb-2">
            <h3 className="text-xs font-semibold text-[#797487] dark:text-[#A39BAC] mb-3">
              {current.subtitle}
            </h3>
          </div>
          {current.content}
        </div>

        {/* Footer Navigation */}
        <div className="glass-panel px-6 py-4 border-t border-[#EADBFC] dark:border-[#322742] flex items-center justify-between gap-3">
          <div className="flex items-center gap-1.5">
            {pages.map((_, i) => (
              <button
                key={i}
                onClick={() => setCurrentPage(i)}
                aria-label={`Go to slide ${i + 1}`}
                className={`h-2 rounded-full transition-all cursor-pointer ${
                  currentPage === i
                    ? 'w-6 bg-[#5925DC] dark:bg-[#9D5AF5]'
                    : 'w-2 bg-[#EADBFC] dark:bg-[#322742] hover:bg-[#D6BBFB]'
                }`}
              />
            ))}
          </div>

          <div className="flex items-center gap-2">
            {currentPage > 0 && (
              <button
                type="button"
                onClick={() => setCurrentPage((prev) => prev - 1)}
                className="px-3.5 py-2 rounded-xl text-xs font-semibold text-[#484455] dark:text-[#C5BECD] hover:text-[#1E1A24] dark:hover:text-[#F7EEFC] hover:bg-[#FAF8FB] dark:hover:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] transition-all flex items-center gap-1 cursor-pointer"
              >
                <ArrowLeft className="w-3.5 h-3.5" />
                <span>Previous</span>
              </button>
            )}

            {currentPage < pages.length - 1 ? (
              <button
                type="button"
                onClick={() => setCurrentPage((prev) => prev + 1)}
                className="px-4 py-2 rounded-xl text-xs font-semibold bg-[#5925DC] hover:bg-[#4c1fc4] text-white shadow-xs flex items-center gap-1.5 transition-all active:scale-95 cursor-pointer"
              >
                <span>Next Feature</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </button>
            ) : (
              <button
                type="button"
                onClick={onClose}
                className="px-5 py-2 rounded-xl text-xs font-semibold bg-gradient-to-r from-[#5925DC] to-[#9B51E0] hover:from-[#4c1fc4] hover:to-[#8c42d3] text-white shadow-[0_4px_16px_rgba(89,37,220,0.3)] flex items-center gap-2 transition-all active:scale-95 cursor-pointer"
              >
                <span>Enter Aurora Notes</span>
                <ArrowRight className="w-4 h-4 stroke-[2.5]" />
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

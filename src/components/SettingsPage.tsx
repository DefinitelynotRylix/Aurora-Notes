import React, { useState, useEffect } from 'react';
import { motion } from 'motion/react';
import { ThemeOption, Note } from '../types';
import {
  Sun,
  Moon,
  Monitor,
  Tag,
  Plus,
  Trash2,
  Download,
  Smartphone,
  Check,
  Terminal,
  Copy,
  Sliders,
  AlertTriangle
} from 'lucide-react';
import {
  isNativeLockEnabled,
  registerNativeDeviceLock,
  verifyWithAndroidNativeLock,
  setNativeLockEnabled,
  isNativePlatformAuthenticatorAvailable
} from '../utils/nativeAuth';
import { logger, LogEntry, LogLevel } from '../utils/logger';

interface SettingsPageProps {
  currentTheme: ThemeOption;
  onSelectTheme: (theme: ThemeOption) => void;
  customCategories: string[];
  onAddCategory: (category: string) => void;
  onRemoveCategory: (category: string) => void;
  notes: Note[];
  onWipeData: () => void;
  onOpenWelcomeTour?: () => void;
}

export const SettingsPage: React.FC<SettingsPageProps> = ({
  currentTheme,
  onSelectTheme,
  customCategories,
  onAddCategory,
  onRemoveCategory,
  notes,
  onWipeData,
  onOpenWelcomeTour,
}) => {
  const [newCatInput, setNewCatInput] = useState('');
  const [showWipeConfirm, setShowWipeConfirm] = useState(false);

  // Local Diagnostic Logger State
  const [logs, setLogs] = useState<LogEntry[]>(() => logger.getLogs());
  const [logFilter, setLogFilter] = useState<'ALL' | LogLevel>('ALL');
  const [copiedNotice, setCopiedNotice] = useState(false);

  useEffect(() => {
    const unsubscribe = logger.subscribe(() => {
      setLogs(logger.getLogs());
    });
    return unsubscribe;
  }, []);

  // Android Native Lock State
  const [isNativeLockOn, setIsNativeLockOn] = useState(isNativeLockEnabled);
  const [isLockChecking, setIsLockChecking] = useState(false);
  const [lockStatusMsg, setLockStatusMsg] = useState<{ text: string; isError?: boolean } | null>(null);

  const themeOptions: { id: ThemeOption; label: string; icon: React.ElementType; desc: string }[] = [
    { id: 'system', label: 'System Default', icon: Monitor, desc: 'Syncs with device operating system theme' },
    { id: 'light', label: 'Light Mode', icon: Sun, desc: 'Warm mauve canvas with royal violet accents' },
    { id: 'dark', label: 'Dark Mode', icon: Moon, desc: 'Obsidian mauve canvas with luminous amethyst accents' },
  ];

  const handleToggleNativeLock = async () => {
    setLockStatusMsg(null);
    setIsLockChecking(true);

    if (isNativeLockOn) {
      setNativeLockEnabled(false);
      setIsNativeLockOn(false);
      setLockStatusMsg({ text: 'Android native lock disabled.' });
      setIsLockChecking(false);
      return;
    }

    const result = await registerNativeDeviceLock();
    setIsLockChecking(false);

    if (result.success) {
      setIsNativeLockOn(true);
      setLockStatusMsg({ text: 'Android native screen lock successfully activated!' });
    } else {
      setLockStatusMsg({
        text: result.error || 'Could not verify Android native lock screen.',
        isError: true,
      });
    }
  };

  const handleTestNativeLock = async () => {
    setLockStatusMsg(null);
    setIsLockChecking(true);
    const result = await verifyWithAndroidNativeLock();
    setIsLockChecking(false);

    if (result.success) {
      setLockStatusMsg({ text: 'Android biometric / device authentication verified!' });
    } else {
      setLockStatusMsg({ text: result.error || 'Authentication canceled or failed.', isError: true });
    }
  };

  const handleAddCategorySubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const clean = newCatInput.trim();
    if (clean && !customCategories.includes(clean)) {
      onAddCategory(clean);
      setNewCatInput('');
    }
  };

  const handleExportMarkdown = () => {
    const mdContent = notes
      .map((n) => {
        const checklists = (n.checklist || [])
          .map((c) => `- [${c.completed ? 'x' : ' '}] ${c.text}`)
          .join('\n');
        return `# ${n.title}\n\n*Category: ${n.category} | Created: ${new Date(n.createdAt).toLocaleString()}*\n\n${n.content}\n\n${checklists ? '### Checklist\n' + checklists + '\n' : ''}\n---\n`;
      })
      .join('\n\n');

    const dataStr = 'data:text/markdown;charset=utf-8,' + encodeURIComponent(mdContent);
    const downloadAnchor = document.createElement('a');
    downloadAnchor.setAttribute('href', dataStr);
    downloadAnchor.setAttribute('download', `aurora-notes-${new Date().toISOString().slice(0, 10)}.md`);
    document.body.appendChild(downloadAnchor);
    downloadAnchor.click();
    downloadAnchor.remove();
  };

  const filteredLogs = logs.filter((l) => logFilter === 'ALL' || l.level === logFilter);

  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -16 }}
      transition={{ duration: 0.28, ease: [0.16, 1, 0.3, 1] }}
      className="max-w-2xl mx-auto px-4 sm:px-6 pt-3 pb-24 space-y-6"
    >
      {/* Page Title & Description Header */}
      <div className="pb-2 border-b border-[#EADBFC]/60 dark:border-[#322742]/60">
        <h2 className="text-xl sm:text-2xl font-bold text-[#1E1A24] dark:text-[#F7EEFC] tracking-tight">
          Settings & Preferences
        </h2>
        <p className="text-xs text-[#797487] dark:text-[#A39BAC] mt-1 leading-relaxed">
          Manage your app theme, native biometric security, custom categories, and local diagnostic logs.
        </p>
      </div>

      {/* 1. Theme Selection Section */}
      <div className="rounded-2xl p-4 sm:p-5 border border-[#EADBFC] dark:border-[#322742] bg-white dark:bg-[#1B1524] shadow-xs space-y-3">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-semibold text-[#1E1A24] dark:text-[#F7EEFC] flex items-center gap-2">
            <Sliders className="w-4 h-4 text-[#5925DC] dark:text-[#C296FD]" />
            <span>App Appearance & Theme</span>
          </h3>
          <span className="text-[11px] font-semibold px-2.5 py-0.5 rounded-full bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD]">
            {currentTheme.toUpperCase()}
          </span>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5 pt-1">
          {themeOptions.map((opt) => {
            const Icon = opt.icon;
            const isSelected = currentTheme === opt.id;
            return (
              <button
                key={opt.id}
                type="button"
                onClick={() => onSelectTheme(opt.id)}
                className={`p-3 rounded-xl border text-left flex flex-col justify-between transition-all cursor-pointer ${
                  isSelected
                    ? 'bg-[#F4EBFF] dark:bg-[#2B1E3E] border-[#5925DC] dark:border-[#9D5AF5] text-[#5925DC] dark:text-[#C296FD] shadow-xs'
                    : 'bg-[#FAF8FB] dark:bg-[#120E17] border-[#EADBFC] dark:border-[#322742] text-[#484455] dark:text-[#A39BAC] hover:bg-white dark:hover:bg-[#1B1524]'
                }`}
              >
                <div className="flex items-center justify-between w-full mb-2">
                  <Icon className="w-5 h-5" />
                  {isSelected && <Check className="w-4 h-4 stroke-[2.5]" />}
                </div>
                <div>
                  <div className="text-xs font-bold">{opt.label}</div>
                  <div className="text-[10px] opacity-75 mt-0.5 line-clamp-1">{opt.desc}</div>
                </div>
              </button>
            );
          })}
        </div>
      </div>

      {/* 2. Android Biometric & Native Screen Lock */}
      <div className="rounded-2xl p-4 sm:p-5 border border-[#EADBFC] dark:border-[#322742] bg-white dark:bg-[#1B1524] shadow-xs space-y-3">
        <div className="flex items-center justify-between">
          <div>
            <span className="text-[10px] font-bold uppercase tracking-wider text-[#5925DC] dark:text-[#C296FD]">
              NATIVE SECURITY
            </span>
            <h3 className="text-sm font-semibold text-[#1E1A24] dark:text-[#F7EEFC] mt-0.5 flex items-center gap-2">
              <Smartphone className="w-4 h-4 text-[#5925DC] dark:text-[#C296FD]" />
              <span>Biometric / System PIN Lock</span>
            </h3>
          </div>
          <button
            type="button"
            onClick={handleToggleNativeLock}
            disabled={isLockChecking}
            className={`px-3 py-1.5 rounded-xl text-xs font-bold border transition-all cursor-pointer ${
              isNativeLockOn
                ? 'bg-[#5925DC] dark:bg-[#9D5AF5] text-white border-transparent shadow-xs'
                : 'bg-[#FAF8FB] dark:bg-[#120E17] text-[#484455] dark:text-[#A39BAC] border-[#EADBFC] dark:border-[#322742]'
            }`}
          >
            {isNativeLockOn ? 'Lock Enabled' : 'Enable Lock'}
          </button>
        </div>

        <p className="text-xs text-[#484455] dark:text-[#A39BAC] leading-relaxed">
          Require Android native fingerprint, face unlock, or system PIN to access your notes upon app launch.
        </p>

        {lockStatusMsg && (
          <div
            className={`p-2.5 rounded-xl text-xs font-medium border ${
              lockStatusMsg.isError
                ? 'bg-rose-50 dark:bg-rose-950/40 text-rose-600 dark:text-rose-400 border-rose-200 dark:border-rose-900'
                : 'bg-emerald-50 dark:bg-emerald-950/40 text-emerald-600 dark:text-emerald-400 border-emerald-200 dark:border-emerald-900'
            }`}
          >
            {lockStatusMsg.text}
          </div>
        )}

        {isNativeLockOn && (
          <div className="pt-1">
            <button
              type="button"
              onClick={handleTestNativeLock}
              disabled={isLockChecking}
              className="px-3.5 py-2 rounded-xl bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] text-xs font-semibold hover:opacity-90 active:scale-95 transition-all border border-[#EADBFC] dark:border-[#322742] cursor-pointer"
            >
              Test Biometric Auth
            </button>
          </div>
        )}
      </div>

      {/* 3. Custom Categories Management */}
      <div className="rounded-2xl p-4 sm:p-5 border border-[#EADBFC] dark:border-[#322742] bg-white dark:bg-[#1B1524] shadow-xs space-y-3">
        <h3 className="text-sm font-semibold text-[#1E1A24] dark:text-[#F7EEFC] flex items-center gap-2">
          <Tag className="w-4 h-4 text-[#5925DC] dark:text-[#C296FD]" />
          <span>Custom Category Pins</span>
        </h3>

        <div className="flex flex-wrap gap-1.5 pt-1">
          {customCategories.map((cat) => (
            <span
              key={cat}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] text-xs font-semibold border border-[#EADBFC] dark:border-[#322742]"
            >
              <span>{cat}</span>
              <button
                type="button"
                onClick={() => onRemoveCategory(cat)}
                className="hover:text-rose-600 dark:hover:text-rose-400 cursor-pointer ml-1"
                aria-label={`Remove category ${cat}`}
              >
                ✕
              </button>
            </span>
          ))}
        </div>

        <form onSubmit={handleAddCategorySubmit} className="flex gap-2 pt-1">
          <input
            type="text"
            placeholder="New category name..."
            value={newCatInput}
            onChange={(e) => setNewCatInput(e.target.value)}
            className="flex-1 text-xs sm:text-sm px-3.5 py-2 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] text-[#1E1A24] dark:text-[#F7EEFC] focus:outline-none focus:border-[#5925DC]"
          />
          <button
            type="submit"
            className="px-4 py-2 rounded-xl bg-[#5925DC] dark:bg-[#9D5AF5] text-white text-xs font-semibold flex items-center gap-1 shadow-xs cursor-pointer"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Add</span>
          </button>
        </form>
      </div>

      {/* 4. Local Diagnostic Logs & Export */}
      <div className="rounded-2xl p-4 sm:p-5 border border-[#EADBFC] dark:border-[#322742] bg-white dark:bg-[#1B1524] shadow-xs space-y-3">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-semibold text-[#1E1A24] dark:text-[#F7EEFC] flex items-center gap-2">
            <Terminal className="w-4 h-4 text-[#5925DC] dark:text-[#C296FD]" />
            <span>Local Diagnostic Logs</span>
          </h3>
          <span className="text-xs font-medium text-[#797487] dark:text-[#A39BAC]">
            {logs.length}/250 entries
          </span>
        </div>

        <p className="text-xs text-[#484455] dark:text-[#A39BAC] leading-relaxed">
          Local events logged purely inside device memory. Zero telemetry or network tracking.
        </p>

        {/* Log Viewer Container */}
        <div className="p-3 rounded-xl bg-[#0F0B14] font-mono text-[11px] text-[#A39BAC] space-y-1.5 max-h-48 overflow-y-auto custom-scrollbar border border-[#322742]">
          {filteredLogs.length === 0 ? (
            <div className="text-slate-500 py-2 text-center">No diagnostic logs recorded.</div>
          ) : (
            filteredLogs.slice(0, 50).map((log) => (
              <div key={log.id} className="leading-tight">
                <span className="text-[#5925DC] dark:text-[#C296FD]">[{log.level}]</span>{' '}
                <span className="text-slate-400">[{log.category}]</span> {log.message}
              </div>
            ))
          )}
        </div>

        <div className="flex items-center gap-2 pt-1 flex-wrap">
          <button
            type="button"
            onClick={() => {
              const dump = logger.exportLogsAsText();
              navigator.clipboard?.writeText(dump);
              setCopiedNotice(true);
              setTimeout(() => setCopiedNotice(false), 2000);
            }}
            className="px-3 py-1.5 rounded-xl bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] text-xs font-semibold border border-[#EADBFC] dark:border-[#322742] flex items-center gap-1 cursor-pointer"
          >
            <Copy className="w-3.5 h-3.5" />
            <span>{copiedNotice ? 'Copied Logs!' : 'Copy Logs'}</span>
          </button>

          <button
            type="button"
            onClick={handleExportMarkdown}
            className="px-3 py-1.5 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] text-[#1E1A24] dark:text-[#F7EEFC] text-xs font-semibold border border-[#EADBFC] dark:border-[#322742] flex items-center gap-1 cursor-pointer"
          >
            <Download className="w-3.5 h-3.5" />
            <span>Export Notes (.md)</span>
          </button>
        </div>
      </div>

      {/* 5. Danger Zone / Wipe Data */}
      <div className="rounded-2xl p-4 sm:p-5 border border-rose-200 dark:border-rose-900/50 bg-rose-50/50 dark:bg-rose-950/20 shadow-xs space-y-3">
        <h3 className="text-sm font-semibold text-rose-700 dark:text-rose-400 flex items-center gap-2">
          <AlertTriangle className="w-4 h-4" />
          <span>Wipe All Application Data</span>
        </h3>
        <p className="text-xs text-rose-600/80 dark:text-rose-300/80 leading-relaxed">
          Permanently delete all notes, custom categories, and settings from local device storage.
        </p>

        {showWipeConfirm ? (
          <div className="flex items-center gap-2 pt-1">
            <button
              type="button"
              onClick={() => {
                setShowWipeConfirm(false);
                onWipeData();
              }}
              className="px-4 py-2 rounded-xl bg-rose-600 text-white text-xs font-bold shadow-xs cursor-pointer active:scale-95"
            >
              Yes, Wipe Everything
            </button>
            <button
              type="button"
              onClick={() => setShowWipeConfirm(false)}
              className="px-4 py-2 rounded-xl bg-white dark:bg-[#1B1524] text-[#484455] dark:text-[#A39BAC] text-xs font-semibold border border-[#EADBFC] dark:border-[#322742] cursor-pointer"
            >
              Cancel
            </button>
          </div>
        ) : (
          <button
            type="button"
            onClick={() => setShowWipeConfirm(true)}
            className="px-3.5 py-2 rounded-xl bg-rose-600 text-white text-xs font-bold shadow-xs flex items-center gap-1.5 cursor-pointer active:scale-95"
          >
            <Trash2 className="w-3.5 h-3.5" />
            <span>Wipe All Notes</span>
          </button>
        )}
      </div>
    </motion.div>
  );
};

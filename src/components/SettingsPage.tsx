import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { ThemeOption, Note } from '../types';
import {
  Sun,
  Moon,
  Monitor,
  ChevronDown,
  ShieldCheck,
  Tag,
  Plus,
  Trash2,
  Download,
  FileText,
  AlertTriangle,
  Fingerprint,
  Check,
  Smartphone,
  KeyRound,
  Sparkles,
  Terminal,
  Copy,
  RotateCcw,
  FileCode,
  Sliders
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
  const [isThemeDropdownOpen, setIsThemeDropdownOpen] = useState(false);
  const [newCatInput, setNewCatInput] = useState('');
  const [showWipeConfirm, setShowWipeConfirm] = useState(false);

  // Local Diagnostic Logger State
  const [logs, setLogs] = useState<LogEntry[]>(() => logger.getLogs());
  const [isLoggingActive, setIsLoggingActive] = useState<boolean>(() => logger.isLoggingEnabled());
  const [isLogConsoleOpen, setIsLogConsoleOpen] = useState(false);
  const [logFilter, setLogFilter] = useState<'ALL' | LogLevel>('ALL');
  const [copiedLogNotice, setCopiedLogNotice] = useState(false);
  const [showClearLogsConfirm, setShowClearLogsConfirm] = useState(false);

  useEffect(() => {
    const unsubscribe = logger.subscribe(() => {
      setLogs(logger.getLogs());
      setIsLoggingActive(logger.isLoggingEnabled());
    });
    return unsubscribe;
  }, []);

  // Android Native Lock State
  const [isNativeLockOn, setIsNativeLockOn] = useState(isNativeLockEnabled);
  const [isLockChecking, setIsLockChecking] = useState(false);
  const [lockStatusMsg, setLockStatusMsg] = useState<{ text: string; isError?: boolean } | null>(null);
  const [hasPlatformAuth, setHasPlatformAuth] = useState(true);

  useEffect(() => {
    isNativePlatformAuthenticatorAvailable().then((available) => {
      setHasPlatformAuth(available);
    });
  }, []);

  const themeOptions: { id: ThemeOption; label: string; icon: React.ElementType; desc: string }[] = [
    { id: 'system', label: 'System Default', icon: Monitor, desc: 'Syncs with device operating system theme' },
    { id: 'light', label: 'Light Mode', icon: Sun, desc: 'Warm mauve canvas with royal violet accents' },
    { id: 'dark', label: 'Dark Mode', icon: Moon, desc: 'Obsidian mauve canvas with luminous amethyst accents' },
  ];

  const currentThemeObj = themeOptions.find((t) => t.id === currentTheme) || themeOptions[0];

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

    // Trigger Android Native Lock registration
    const result = await registerNativeDeviceLock();
    setIsLockChecking(false);

    if (result.success) {
      setIsNativeLockOn(true);
      setLockStatusMsg({ text: 'Android native screen lock successfully verified and activated!' });
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
      setLockStatusMsg({ text: 'Android device authentication succeeded! (Fingerprint/PIN verified)' });
    } else {
      setLockStatusMsg({ text: result.error || 'Authentication canceled or rejected.', isError: true });
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
    downloadAnchor.setAttribute('download', `iris-notes-${new Date().toISOString().slice(0, 10)}.md`);
    document.body.appendChild(downloadAnchor);
    downloadAnchor.click();
    downloadAnchor.remove();
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -16 }}
      transition={{ duration: 0.28, ease: [0.16, 1, 0.3, 1] }}
      className="max-w-2xl mx-auto px-4 sm:px-6 pt-2 pb-24 space-y-5"
    >
      {/* Title Header */}
      <div className="pb-2">
        <h2 className="text-xl sm:text-2xl font-bold text-[#1E1A24] dark:text-[#F7EEFC] tracking-tight">
          Settings & Preferences
        </h2>
        <p className="text-xs text-[#797487] dark:text-[#A39BAC] mt-1">
          Customize themes, manage your custom category pins, and configure Android native security.
        </p>
      </div>

      {/* Android Native Screen Lock Option */}
      <div className="glass-card rounded-2xl p-4 sm:p-5 border border-[#EADBFC] dark:border-[#322742] bg-white dark:bg-[#1B1524]">
        <div className="flex items-center justify-between mb-2">
          <div>
            <span className="text-xs font-bold uppercase tracking-wider text-[#5925DC] dark:text-[#C296FD]">
              Native Android Security
            </span>
            <h3 className="text-sm font-semibold text-[#1E1A24] dark:text-[#F7EEFC] mt-0.5 flex items-center gap-1.5">
              <Smartphone className="w-4 h-4 text-[#5925DC] dark:text-[#C296FD]" />
              Android Native Lock Screen
            </h3>
          </div>
          <span className="text-[11px] font-semibold px-2 py-0.5 rounded-full bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD]">
            OS Biometric / PIN
          </span>
        </div>

        <p className="text-xs text-[#484455] dark:text-[#A39BAC] mb-3 leading-relaxed">
          Uses Android's native platform lock screen. Depending on what you use on your Android device, it directly prompts your system Fingerprint, Face, or Device PIN/Pattern.
        </p>

        {lockStatusMsg && (
          <div
            className={`mb-3 p-2.5 rounded-xl text-xs flex items-center gap-2 ${
              lockStatusMsg.isError
                ? 'bg-rose-50 dark:bg-rose-950/40 text-rose-700 dark:text-rose-300 border border-rose-200 dark:border-rose-900'
                : 'bg-emerald-50 dark:bg-emerald-950/40 text-emerald-800 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800'
            }`}
          >
            <Check className="w-4 h-4 shrink-0" />
            <span>{lockStatusMsg.text}</span>
          </div>
        )}

        <div className="flex items-center justify-between p-3 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742]">
          <div className="flex items-center gap-2.5">
            <Fingerprint className="w-5 h-5 text-[#5925DC] dark:text-[#C296FD]" />
            <div>
              <div className="text-xs font-bold text-[#1E1A24] dark:text-[#F7EEFC]">
                {isNativeLockOn ? 'Android Native Lock Active' : 'Enable Android Native Lock'}
              </div>
              <div className="text-[11px] text-[#797487] dark:text-[#A39BAC]">
                {isNativeLockOn
                  ? 'App requires Android fingerprint or device PIN to access'
                  : 'Tap toggle to activate Android native authentication'}
              </div>
            </div>
          </div>

          <button
            type="button"
            onClick={handleToggleNativeLock}
            disabled={isLockChecking}
            aria-label={isNativeLockOn ? 'Disable Android Native Lock' : 'Enable Android Native Lock'}
            className={`w-12 h-6 rounded-full transition-colors relative focus:outline-none ${
              isNativeLockOn
                ? 'bg-[#5925DC] dark:bg-[#9D5AF5]'
                : 'bg-[#CAC3D8] dark:bg-[#4A3B60]'
            }`}
          >
            <div
              className={`w-5 h-5 rounded-full bg-white shadow-md transform transition-transform ${
                isNativeLockOn ? 'translate-x-6' : 'translate-x-1'
              }`}
            />
          </button>
        </div>

        {isNativeLockOn && (
          <div className="mt-2.5 flex justify-end">
            <button
              type="button"
              onClick={handleTestNativeLock}
              disabled={isLockChecking}
              className="px-3 py-1.5 rounded-xl bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] hover:bg-[#ebd8ff] text-xs font-semibold flex items-center gap-1.5 transition-all"
            >
              <KeyRound className="w-3.5 h-3.5" />
              <span>Test Native Android Prompt</span>
            </button>
          </div>
        )}
      </div>

      {/* System Theme Rolling Down Window */}
      <div className="glass-card rounded-2xl p-4 sm:p-5 border border-[#EADBFC] dark:border-[#322742] bg-white dark:bg-[#1B1524]">
        <div className="flex items-center justify-between mb-2">
          <div>
            <span className="text-xs font-bold uppercase tracking-wider text-[#5925DC] dark:text-[#C296FD]">
              Appearance
            </span>
            <h3 className="text-sm font-semibold text-[#1E1A24] dark:text-[#F7EEFC] mt-0.5">
              Theme Selection
            </h3>
          </div>
          <span className="text-xs text-[#797487] dark:text-[#A39BAC]">
            Theme-adaptive purples
          </span>
        </div>

        <p className="text-xs text-[#484455] dark:text-[#A39BAC] mb-3">
          Select your visual style. Purple highlights dynamically calibrate between light and dark modes.
        </p>

        {/* Rolling Down Window Button */}
        <div className="relative">
          <button
            type="button"
            onClick={() => setIsThemeDropdownOpen(!isThemeDropdownOpen)}
            aria-label="Theme options dropdown"
            aria-expanded={isThemeDropdownOpen}
            className="w-full p-3 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] flex items-center justify-between text-left hover:border-[#5925DC] dark:hover:border-[#9D5AF5] transition-all"
          >
            <div className="flex items-center gap-3">
              <div className="w-8 h-8 rounded-lg bg-white dark:bg-[#1B1524] border border-[#EADBFC] dark:border-[#322742] flex items-center justify-center text-[#5925DC] dark:text-[#C296FD]">
                <currentThemeObj.icon className="w-4 h-4" />
              </div>
              <div>
                <div className="text-xs font-bold text-[#1E1A24] dark:text-[#F7EEFC]">
                  {currentThemeObj.label}
                </div>
                <div className="text-[11px] text-[#797487] dark:text-[#A39BAC]">
                  {currentThemeObj.desc}
                </div>
              </div>
            </div>
            <motion.div
              animate={{ rotate: isThemeDropdownOpen ? 180 : 0 }}
              transition={{ duration: 0.2 }}
            >
              <ChevronDown className="w-4 h-4 text-[#797487] dark:text-[#A39BAC]" />
            </motion.div>
          </button>

          {/* Rolling Down Menu */}
          <AnimatePresence>
            {isThemeDropdownOpen && (
              <motion.div
                initial={{ opacity: 0, y: -8, scaleY: 0.95 }}
                animate={{ opacity: 1, y: 4, scaleY: 1 }}
                exit={{ opacity: 0, y: -8, scaleY: 0.95 }}
                transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}
                className="absolute left-0 right-0 top-full z-20 mt-1 rounded-xl bg-white dark:bg-[#1B1524] border border-[#EADBFC] dark:border-[#322742] shadow-[0_16px_36px_-4px_rgba(89,37,220,0.18)] dark:shadow-[0_16px_36px_-4px_rgba(0,0,0,0.7)] p-1.5 overflow-hidden origin-top"
              >
                {themeOptions.map((opt) => {
                  const isSelected = currentTheme === opt.id;
                  const Icon = opt.icon;
                  return (
                    <button
                      key={opt.id}
                      type="button"
                      onClick={() => {
                        onSelectTheme(opt.id);
                        setIsThemeDropdownOpen(false);
                      }}
                      className={`w-full p-2.5 rounded-lg flex items-center justify-between text-left transition-all ${
                        isSelected
                          ? 'bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] font-semibold'
                          : 'hover:bg-[#FAF8FB] dark:hover:bg-[#120E17] text-[#1E1A24] dark:text-[#F7EEFC]'
                      }`}
                    >
                      <div className="flex items-center gap-2.5">
                        <Icon className="w-4 h-4" />
                        <div>
                          <div className="text-xs font-semibold">{opt.label}</div>
                          <div className="text-[10px] text-[#797487] dark:text-[#A39BAC]">
                            {opt.desc}
                          </div>
                        </div>
                      </div>
                      {isSelected && <Check className="w-4 h-4" />}
                    </button>
                  );
                })}
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </div>

      {/* User Category Pins Manager */}
      <div className="glass-card rounded-2xl p-4 sm:p-5 border border-[#EADBFC] dark:border-[#322742] bg-white dark:bg-[#1B1524]">
        <div className="flex items-center justify-between mb-1.5">
          <div>
            <span className="text-xs font-bold uppercase tracking-wider text-[#5925DC] dark:text-[#C296FD]">
              Organization
            </span>
            <h3 className="text-sm font-semibold text-[#1E1A24] dark:text-[#F7EEFC] mt-0.5">
              Custom Category Pins
            </h3>
          </div>
        </div>

        <p className="text-xs text-[#484455] dark:text-[#A39BAC] mb-3">
          Create custom category pins to tag and filter your notes. Only "All" and "Pinned" are fixed.
        </p>

        {/* Existing Custom Categories */}
        <div className="flex flex-wrap gap-2 mb-3">
          {customCategories.length === 0 ? (
            <span className="text-xs text-[#797487] dark:text-[#A39BAC] italic">
              No custom category pins yet. Add one below!
            </span>
          ) : (
            customCategories.map((cat) => (
              <span
                key={cat}
                className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] text-xs font-semibold border border-[#EADBFC] dark:border-[#322742]"
              >
                <Tag className="w-3 h-3" />
                <span>{cat}</span>
                <button
                  type="button"
                  onClick={() => onRemoveCategory(cat)}
                  className="hover:text-rose-600 dark:hover:text-rose-400 ml-1 text-xs"
                  aria-label={`Remove category pin ${cat}`}
                >
                  ✕
                </button>
              </span>
            ))
          )}
        </div>

        {/* Add new category form */}
        <form onSubmit={handleAddCategorySubmit} className="flex items-center gap-2">
          <input
            type="text"
            placeholder="New category name (e.g., Work, Journal, Project)..."
            value={newCatInput}
            onChange={(e) => setNewCatInput(e.target.value)}
            className="flex-1 px-3.5 py-2 text-xs rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] text-[#1E1A24] dark:text-[#F7EEFC] focus:outline-none focus:border-[#5925DC] dark:focus:border-[#9D5AF5]"
          />
          <button
            type="submit"
            className="px-4 py-2 rounded-xl bg-gradient-to-r from-[#5925DC] to-[#9B51E0] text-white text-xs font-semibold flex items-center gap-1.5 shadow-xs transition-all active:scale-95"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Add Pin</span>
          </button>
        </form>
      </div>

      {/* Welcome Tour Trigger */}
      {onOpenWelcomeTour && (
        <div className="glass-card rounded-2xl p-4 sm:p-5 border border-[#EADBFC] dark:border-[#322742] bg-white dark:bg-[#1B1524] flex items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] flex items-center justify-center shrink-0">
              <Sparkles className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-xs sm:text-sm font-bold text-[#1E1A24] dark:text-[#F7EEFC]">
                Welcome Tour & Guide
              </h3>
              <p className="text-[11px] text-[#797487] dark:text-[#A39BAC]">
                Replay the introduction to Iris Notes features and privacy philosophy.
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onOpenWelcomeTour}
            className="px-3.5 py-1.5 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] hover:border-[#5925DC] dark:hover:border-[#9D5AF5] text-xs font-semibold text-[#5925DC] dark:text-[#C296FD] transition-all hover:scale-[1.02] active:scale-95 shrink-0"
          >
            Replay Tour
          </button>
        </div>
      )}

      {/* Local Diagnostic Logs Mode */}
      <div className="glass-card rounded-2xl p-4 sm:p-5 border border-[#EADBFC] dark:border-[#322742] bg-white dark:bg-[#1B1524] space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <span className="text-xs font-bold uppercase tracking-wider text-[#5925DC] dark:text-[#C296FD]">
              Local Offline Mode
            </span>
            <h3 className="text-sm font-semibold text-[#1E1A24] dark:text-[#F7EEFC] mt-0.5 flex items-center gap-1.5">
              <Terminal className="w-4 h-4 text-[#5925DC] dark:text-[#C296FD]" />
              Local App Diagnostic Logs
            </h3>
          </div>
          <span className={`text-[11px] font-semibold px-2.5 py-0.5 rounded-full ${
            isLoggingActive
              ? 'bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800'
              : 'bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-300 border border-amber-200 dark:border-amber-800'
          }`}>
            {isLoggingActive ? 'Logging Active' : 'Logging Paused'}
          </span>
        </div>

        <p className="text-xs text-[#484455] dark:text-[#A39BAC] leading-relaxed">
          In local-only mode, application events (errors, warnings, and security actions) are captured into a rolling buffer on your device. You can inspect or download these logs as a plain text file (<code className="px-1 py-0.5 rounded bg-[#FAF8FB] dark:bg-[#120E17] text-[#5925DC] dark:text-[#C296FD] font-mono text-[11px]">.txt</code>) to manually share with the developer for bug fixing.
        </p>

        {/* Status & Stats Summary Row */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
          <div className="p-2.5 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] text-center">
            <div className="text-[10px] text-[#797487] dark:text-[#A39BAC] uppercase font-bold tracking-wider">Total Logs</div>
            <div className="text-base font-bold text-[#1E1A24] dark:text-[#F7EEFC] font-mono tabular-nums mt-0.5">{logs.length} / 250</div>
          </div>
          <div className="p-2.5 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] text-center">
            <div className="text-[10px] text-rose-600 dark:text-rose-400 uppercase font-bold tracking-wider">Errors</div>
            <div className="text-base font-bold text-rose-600 dark:text-rose-400 font-mono tabular-nums mt-0.5">
              {logs.filter(l => l.level === 'ERROR').length}
            </div>
          </div>
          <div className="p-2.5 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] text-center">
            <div className="text-[10px] text-amber-600 dark:text-amber-400 uppercase font-bold tracking-wider">Warnings</div>
            <div className="text-base font-bold text-amber-600 dark:text-amber-400 font-mono tabular-nums mt-0.5">
              {logs.filter(l => l.level === 'WARN').length}
            </div>
          </div>
          <div className="p-2.5 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] text-center">
            <div className="text-[10px] text-[#5925DC] dark:text-[#C296FD] uppercase font-bold tracking-wider">Security</div>
            <div className="text-base font-bold text-[#5925DC] dark:text-[#C296FD] font-mono tabular-nums mt-0.5">
              {logs.filter(l => l.level === 'SECURITY').length}
            </div>
          </div>
        </div>

        {/* Toggle logging & action buttons */}
        <div className="flex flex-wrap items-center justify-between gap-2.5 pt-1">
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => logger.setLoggingEnabled(!isLoggingActive)}
              aria-label="Toggle diagnostic logging"
              className={`w-10 h-5 rounded-full transition-colors relative focus:outline-none cursor-pointer ${
                isLoggingActive ? 'bg-[#5925DC] dark:bg-[#9D5AF5]' : 'bg-[#CAC3D8] dark:bg-[#4A3B60]'
              }`}
            >
              <div
                className={`w-4 h-4 rounded-full bg-white shadow-md transform transition-transform ${
                  isLoggingActive ? 'translate-x-5' : 'translate-x-0.5'
                }`}
              />
            </button>
            <span className="text-xs font-medium text-[#1E1A24] dark:text-[#F7EEFC]">
              {isLoggingActive ? 'Diagnostic logging enabled' : 'Diagnostic logging paused'}
            </span>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setIsLogConsoleOpen(!isLogConsoleOpen)}
              className="px-3 py-1.5 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] hover:border-[#5925DC] dark:hover:border-[#9D5AF5] text-xs font-semibold text-[#5925DC] dark:text-[#C296FD] flex items-center gap-1.5 transition-all cursor-pointer"
            >
              <Terminal className="w-3.5 h-3.5" />
              <span>{isLogConsoleOpen ? 'Hide Console' : 'Inspect Log Console'}</span>
            </button>

            <button
              type="button"
              onClick={() => logger.downloadLogsFile()}
              className="px-3 py-1.5 rounded-xl bg-gradient-to-r from-[#5925DC] to-[#9B51E0] text-white text-xs font-semibold flex items-center gap-1.5 shadow-xs transition-all hover:scale-[1.02] active:scale-95 cursor-pointer"
            >
              <Download className="w-3.5 h-3.5" />
              <span>Download App Log (.txt)</span>
            </button>
          </div>
        </div>

        {/* Expandable Dark Monospace Terminal Console */}
        <AnimatePresence>
          {isLogConsoleOpen && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }}
              transition={{ duration: 0.25 }}
              className="overflow-hidden pt-2"
            >
              <div className="p-3 sm:p-4 rounded-2xl bg-[#0E0B12] dark:bg-[#0A070F] border border-[#322742] text-slate-200 font-mono text-xs space-y-3">
                {/* Console Header & Filters */}
                <div className="flex flex-wrap items-center justify-between gap-2 pb-2 border-b border-[#2B1E3E]">
                  <div className="flex items-center gap-1.5 text-[11px] font-bold text-[#C296FD] uppercase tracking-wider">
                    <Terminal className="w-3.5 h-3.5 text-[#9D5AF5]" />
                    <span>Local Console ({logs.filter(l => logFilter === 'ALL' || l.level === logFilter).length})</span>
                  </div>

                  {/* Filter segmented buttons */}
                  <div className="flex items-center gap-1 p-0.5 rounded-lg bg-[#1B1524] border border-[#322742]">
                    {(['ALL', 'ERROR', 'WARN', 'SECURITY'] as const).map((filterOpt) => (
                      <button
                        key={filterOpt}
                        type="button"
                        onClick={() => setLogFilter(filterOpt)}
                        className={`px-2 py-0.5 text-[10px] font-semibold rounded-md transition-colors cursor-pointer ${
                          logFilter === filterOpt
                            ? 'bg-[#5925DC] text-white shadow-xs'
                            : 'text-[#A39BAC] hover:text-white'
                        }`}
                      >
                        {filterOpt}
                      </button>
                    ))}
                  </div>

                  {/* Copy & Clear actions */}
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => {
                        navigator.clipboard.writeText(logger.exportLogsAsText());
                        setCopiedLogNotice(true);
                        setTimeout(() => setCopiedLogNotice(false), 2000);
                      }}
                      className="px-2 py-1 rounded bg-[#1B1524] border border-[#322742] hover:border-[#9D5AF5] text-[10px] font-medium text-[#C296FD] flex items-center gap-1 transition-all cursor-pointer"
                    >
                      <Copy className="w-3 h-3" />
                      <span>{copiedLogNotice ? 'Copied!' : 'Copy'}</span>
                    </button>

                    {!showClearLogsConfirm ? (
                      <button
                        type="button"
                        onClick={() => setShowClearLogsConfirm(true)}
                        className="px-2 py-1 rounded bg-rose-950/50 border border-rose-900/60 hover:bg-rose-900/60 text-[10px] font-medium text-rose-300 flex items-center gap-1 transition-all cursor-pointer"
                      >
                        <Trash2 className="w-3 h-3" />
                        <span>Clear</span>
                      </button>
                    ) : (
                      <div className="flex items-center gap-1">
                        <button
                          type="button"
                          onClick={() => {
                            logger.clearLogs();
                            setShowClearLogsConfirm(false);
                          }}
                          className="px-2 py-1 rounded bg-rose-600 text-white text-[10px] font-bold cursor-pointer"
                        >
                          Confirm Clear
                        </button>
                        <button
                          type="button"
                          onClick={() => setShowClearLogsConfirm(false)}
                          className="px-2 py-1 rounded bg-[#1B1524] text-[10px] text-slate-400 cursor-pointer"
                        >
                          Cancel
                        </button>
                      </div>
                    )}
                  </div>
                </div>

                {/* Log Entries Container */}
                <div className="max-h-64 overflow-y-auto space-y-2 pr-1">
                  {logs.filter(l => logFilter === 'ALL' || l.level === logFilter).length === 0 ? (
                    <div className="py-6 text-center text-[#797487] text-[11px] italic">
                      No matching local diagnostic logs recorded.
                    </div>
                  ) : (
                    logs
                      .filter(l => logFilter === 'ALL' || l.level === logFilter)
                      .map((entry) => {
                        const levelStyles = {
                          ERROR: 'bg-rose-500/15 text-rose-300 border-rose-500/30',
                          WARN: 'bg-amber-500/15 text-amber-300 border-amber-500/30',
                          SECURITY: 'bg-[#9D5AF5]/20 text-[#C296FD] border-[#9D5AF5]/40',
                        }[entry.level];

                        return (
                          <div
                            key={entry.id}
                            className="p-2 rounded-xl bg-[#15101E] border border-[#2B1E3E] text-[11px] leading-relaxed space-y-1"
                          >
                            <div className="flex flex-wrap items-center justify-between gap-1.5 text-[10px] text-[#A39BAC]">
                              <div className="flex items-center gap-1.5">
                                <span className={`px-1.5 py-0.2 rounded border font-bold text-[9px] ${levelStyles}`}>
                                  {entry.level}
                                </span>
                                <span className="text-slate-300 font-bold">[{entry.category}]</span>
                              </div>
                              <span className="tabular-nums text-slate-500">
                                {new Date(entry.timestamp).toLocaleTimeString()} · {new Date(entry.timestamp).toLocaleDateString()}
                              </span>
                            </div>
                            <div className="text-slate-200 font-medium">{entry.message}</div>
                            {entry.details && (
                              <div className="text-[10px] text-slate-400 bg-[#0E0B12] p-1.5 rounded border border-[#2B1E3E] overflow-x-auto whitespace-pre-wrap">
                                {entry.details}
                              </div>
                            )}
                          </div>
                        );
                      })
                  )}
                </div>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {/* Local Only Privacy Notice */}
      <div className="p-4 rounded-2xl bg-emerald-50/70 dark:bg-emerald-950/30 border border-emerald-200 dark:border-emerald-800 flex items-start gap-3">
        <ShieldCheck className="w-5 h-5 text-emerald-600 dark:text-emerald-400 shrink-0 mt-0.5" />
        <div className="text-xs text-emerald-900 dark:text-emerald-200 leading-relaxed">
          <strong className="block mb-0.5">Local Only Privacy Focused Note Taking App</strong>
          All notes and checklists remain strictly on your device. Zero network connections, zero telemetry, and zero tracking cookies.
        </div>
      </div>

      {/* Export & Data Management */}
      <div className="glass-card rounded-2xl p-4 sm:p-5 border border-[#EADBFC] dark:border-[#322742] bg-white dark:bg-[#1B1524] space-y-3">
        <span className="text-xs font-bold uppercase tracking-wider text-[#5925DC] dark:text-[#C296FD] block">
          Data Management
        </span>

        <button
          type="button"
          onClick={handleExportMarkdown}
          className="w-full p-3 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] border border-[#EADBFC] dark:border-[#322742] hover:border-[#5925DC] dark:hover:border-[#9D5AF5] text-xs font-semibold text-[#1E1A24] dark:text-[#F7EEFC] flex items-center justify-between transition-all"
        >
          <div className="flex items-center gap-2.5">
            <FileText className="w-4 h-4 text-[#5925DC] dark:text-[#C296FD]" />
            <span>Export Notes as Markdown (.md)</span>
          </div>
          <Download className="w-3.5 h-3.5 text-[#797487]" />
        </button>

        {/* Emergency Wipe */}
        <div className="pt-2 border-t border-[#EADBFC]/60 dark:border-[#322742]">
          {!showWipeConfirm ? (
            <button
              type="button"
              onClick={() => setShowWipeConfirm(true)}
              className="text-xs font-medium text-rose-600 dark:text-rose-400 hover:underline flex items-center gap-1.5"
            >
              <Trash2 className="w-3.5 h-3.5" />
              <span>Reset & Clear All Local Notes</span>
            </button>
          ) : (
            <div className="p-3 rounded-xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900 space-y-2">
              <div className="text-xs font-semibold text-rose-700 dark:text-rose-300 flex items-center gap-1.5">
                <AlertTriangle className="w-4 h-4 shrink-0" />
                <span>Confirm Local Storage Wipe</span>
              </div>
              <p className="text-[11px] text-rose-600 dark:text-rose-300">
                This permanently erases notes saved in this browser.
              </p>
              <div className="flex items-center gap-2 pt-1">
                <button
                  type="button"
                  onClick={() => {
                    onWipeData();
                    setShowWipeConfirm(false);
                  }}
                  className="px-3 py-1 rounded-lg bg-rose-600 text-white text-xs font-semibold hover:bg-rose-700"
                >
                  Confirm Wipe
                </button>
                <button
                  type="button"
                  onClick={() => setShowWipeConfirm(false)}
                  className="px-3 py-1 rounded-lg bg-white dark:bg-[#1B1524] text-rose-700 dark:text-rose-300 border border-rose-200 dark:border-rose-800 text-xs"
                >
                  Cancel
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </motion.div>
  );
};

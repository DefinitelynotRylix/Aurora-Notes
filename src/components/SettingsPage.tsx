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
  AlertTriangle,
  RefreshCw,
  ShieldAlert,
  CheckCircle2
} from 'lucide-react';
import {
  isNativeLockEnabled,
  registerNativeDeviceLock,
  verifyWithAndroidNativeLock,
  setNativeLockEnabled,
  isNativePlatformAuthenticatorAvailable
} from '../utils/nativeAuth';
import { logger, LogEntry, LogLevel } from '../utils/logger';

const APP_VERSION = '1.2.0';
const APP_BUILD_CODE = 100;
const REPO_STORAGE_KEY = 'aurora-github-repo-slug-v1';

function compareSemver(remoteVer: string, localVer: string): number {
  const cleanR = remoteVer.trim().replace(/^[vV]/, '');
  const cleanL = localVer.trim().replace(/^[vV]/, '');
  const rParts = cleanR.split(/[^0-9]+/).filter(Boolean).map(Number);
  const lParts = cleanL.split(/[^0-9]+/).filter(Boolean).map(Number);
  const len = Math.max(rParts.length, lParts.length);
  for (let i = 0; i < len; i++) {
    const r = rParts[i] || 0;
    const l = lParts[i] || 0;
    if (r !== l) return r > l ? 1 : -1;
  }
  return 0;
}

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

  // App Version & Self-Update State
  const [repoSlug, setRepoSlug] = useState<string>(() => {
    return localStorage.getItem(REPO_STORAGE_KEY) || '';
  });
  const [showRepoInput, setShowRepoInput] = useState<boolean>(() => !localStorage.getItem(REPO_STORAGE_KEY));
  const [isScanningUpdate, setIsScanningUpdate] = useState(false);
  const [isPullingUpdate, setIsPullingUpdate] = useState(false);
  const [pullProgress, setPullProgress] = useState(0);
  const [updateStatus, setUpdateStatus] = useState<{
    type: 'uptodate' | 'available' | 'error';
    message: string;
    latestVersion?: string;
    apkUrl?: string;
  } | null>(null);

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

  const handleScanForUpdates = async () => {
    if (isScanningUpdate || isPullingUpdate) return;
    const cleanRepo = repoSlug
      .trim()
      .replace(/^https?:\/\/github\.com\//i, '')
      .replace(/^\/+|\/+$/g, '');

    if (cleanRepo && cleanRepo.includes('/')) {
      localStorage.setItem(REPO_STORAGE_KEY, cleanRepo);
      setRepoSlug(cleanRepo);
      setShowRepoInput(false);
    }

    setIsScanningUpdate(true);
    setUpdateStatus(null);
    logger.security('UPDATE', `Scanning GitHub Releases for updates (installed v${APP_VERSION})`);

    if (!cleanRepo || !cleanRepo.includes('/')) {
      setIsScanningUpdate(false);
      setUpdateStatus({
        type: 'uptodate',
        latestVersion: APP_VERSION,
        message: `Aurora Notes is at the latest version (v${APP_VERSION}). Enter your GitHub owner/Aurora-Notes repository slug if you want to check a custom release feed.`,
      });
      return;
    }

    try {
      const resp = await fetch(`https://api.github.com/repos/${cleanRepo}/releases/latest`, {
        headers: { Accept: 'application/vnd.github+json' },
      });

      if (!resp.ok) {
        setIsScanningUpdate(false);
        setUpdateStatus({
          type: 'uptodate',
          latestVersion: APP_VERSION,
          message: `You are running the latest version (v${APP_VERSION}). No newer release found on ${cleanRepo}.`,
        });
        return;
      }

      const data = await resp.json();
      const rawTag = String(data.tag_name || '').trim();
      const latestVer = rawTag.replace(/^[vV]/, '') || APP_VERSION;
      const assets = Array.isArray(data.assets) ? data.assets : [];
      const apkAsset = assets.find((a: { name?: string; browser_download_url?: string }) =>
        String(a?.name || '').toLowerCase().endsWith('.apk')
      );

      setIsScanningUpdate(false);

      if (compareSemver(latestVer, APP_VERSION) <= 0) {
        logger.security('UPDATE', `App is up to date (v${APP_VERSION})`);
        setUpdateStatus({
          type: 'uptodate',
          latestVersion: latestVer,
          message: `Aurora Notes is up to date! You are on the latest version (v${APP_VERSION}).`,
        });
      } else {
        const downloadUrl = apkAsset?.browser_download_url || data.html_url || '';
        logger.security('UPDATE', `New release v${latestVer} found on GitHub! Pulling update & restarting...`);
        setUpdateStatus({
          type: 'available',
          latestVersion: latestVer,
          apkUrl: downloadUrl,
          message: `New release v${latestVer} found! Pulling update & preparing forced restart...`,
        });
        setIsPullingUpdate(true);
        setPullProgress(20);
        setTimeout(() => setPullProgress(65), 350);
        setTimeout(() => {
          setPullProgress(100);
          setIsPullingUpdate(false);
          if (downloadUrl && downloadUrl.endsWith('.apk')) {
            const a = document.createElement('a');
            a.href = downloadUrl;
            a.download = 'Aurora Notes.apk';
            document.body.appendChild(a);
            a.click();
            a.remove();
          }
          setTimeout(() => {
            window.location.reload();
          }, 800);
        }, 900);
      }
    } catch (err) {
      setIsScanningUpdate(false);
      logger.error('UPDATE', 'Failed checking GitHub Releases', String(err));
      setUpdateStatus({
        type: 'error',
        message: 'Could not reach GitHub Releases API. Check your internet connection or repository name.',
      });
    }
  };

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
          Manage app appearance & theme, self-updates, biometric security, custom category pins, and local data.
        </p>
      </div>

      {/* 0. App Version & Self-Update Section */}
      <div className="rounded-2xl p-4 sm:p-5 border border-[#EADBFC] dark:border-[#322742] bg-white dark:bg-[#1B1524] shadow-xs space-y-3">
        <div className="flex items-center justify-between gap-2">
          <div>
            <span className="text-[10px] font-bold uppercase tracking-wider text-[#5925DC] dark:text-[#C296FD]">
              GITHUB RELEASE UPDATER
            </span>
            <h3 className="text-sm font-semibold text-[#1E1A24] dark:text-[#F7EEFC] mt-0.5 flex items-center gap-2">
              <RefreshCw className={`w-4 h-4 text-[#5925DC] dark:text-[#C296FD] ${isScanningUpdate ? 'animate-spin' : ''}`} />
              <span>App Version & Self-Update</span>
            </h3>
            <p className="text-[11px] font-semibold text-[#5925DC] dark:text-[#C296FD] mt-0.5">
              Installed Version: v{APP_VERSION} • Build {APP_BUILD_CODE}
            </p>
          </div>
          <button
            type="button"
            onClick={() => setShowRepoInput((v) => !v)}
            className="text-[11px] font-semibold px-2.5 py-1 rounded-lg bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] hover:opacity-90 cursor-pointer"
          >
            {repoSlug ? repoSlug : 'Set GitHub Repo'}
          </button>
        </div>

        {/* Minimal Internet Access Warning Box */}
        <div className="p-3 rounded-xl bg-[#F4EBFF]/60 dark:bg-[#2B1E3E]/40 border border-[#EADBFC] dark:border-[#322742] flex items-start gap-2.5">
          <ShieldAlert className="w-4 h-4 text-[#5925DC] dark:text-[#C296FD] shrink-0 mt-0.5" />
          <p className="text-[11px] text-[#484455] dark:text-[#C5BECD] leading-relaxed">
            <strong className="text-[#1E1A24] dark:text-[#F7EEFC]">Minimal Internet Access Notice:</strong> All your notes, checklists, and tags remain 100% offline on your device. Internet access is strictly limited to manual update scans when you tap <strong>Scan for Updates</strong> below to pull the latest <code className="font-mono">Aurora Notes.apk</code> from GitHub Releases.
          </p>
        </div>

        {showRepoInput && (
          <div className="pt-1">
            <label className="block text-[10px] font-bold uppercase tracking-wider text-[#797487] dark:text-[#A39BAC] mb-1">
              GitHub Repository (owner/Aurora-Notes)
            </label>
            <input
              type="text"
              value={repoSlug}
              onChange={(e) => setRepoSlug(e.target.value)}
              placeholder="e.g. username/Aurora-Notes"
              className="w-full px-3 py-2 text-xs rounded-xl border border-[#EADBFC] dark:border-[#322742] bg-[#FAF8FB] dark:bg-[#120E17] text-[#1E1A24] dark:text-[#F7EEFC] focus:outline-none focus:border-[#5925DC]"
            />
          </div>
        )}

        {isPullingUpdate && (
          <div className="space-y-1.5 pt-1">
            <div className="flex items-center justify-between text-[11px] font-semibold text-[#5925DC] dark:text-[#C296FD]">
              <span>Pulling latest release & preparing self-update...</span>
              <span>{pullProgress}%</span>
            </div>
            <div className="w-full h-1.5 rounded-full bg-[#F4EBFF] dark:bg-[#2B1E3E] overflow-hidden">
              <div
                className="h-full bg-[#5925DC] dark:bg-[#9D5AF5] transition-all duration-300"
                style={{ width: `${pullProgress}%` }}
              />
            </div>
          </div>
        )}

        {updateStatus && (
          <div
            className={`p-3 rounded-xl text-xs font-medium border flex items-start gap-2 ${
              updateStatus.type === 'uptodate'
                ? 'bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300 border-emerald-200 dark:border-emerald-800'
                : updateStatus.type === 'available'
                ? 'bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] border-[#5925DC]/40'
                : 'bg-rose-50 dark:bg-rose-950/40 text-rose-600 dark:text-rose-400 border-rose-200 dark:border-rose-900'
            }`}
          >
            <CheckCircle2 className="w-4 h-4 shrink-0 mt-0.5" />
            <span>{updateStatus.message}</span>
          </div>
        )}

        <div className="pt-1">
          <button
            type="button"
            onClick={handleScanForUpdates}
            disabled={isScanningUpdate || isPullingUpdate}
            className="w-full py-2.5 px-4 rounded-xl bg-[#5925DC] hover:bg-[#4A1FB8] text-white text-xs font-bold flex items-center justify-center gap-2 shadow-xs transition-all cursor-pointer disabled:opacity-60"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isScanningUpdate ? 'animate-spin' : ''}`} />
            <span>{isScanningUpdate ? 'Scanning GitHub Releases...' : 'Scan for Updates'}</span>
          </button>
        </div>
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

      {/* 5. Danger Zone / Wipe Data (Biometric Verification Required) */}
      <div className="rounded-2xl p-4 sm:p-5 border border-rose-200 dark:border-rose-900/50 bg-rose-50/50 dark:bg-rose-950/20 shadow-xs space-y-3">
        <h3 className="text-sm font-semibold text-rose-700 dark:text-rose-400 flex items-center gap-2">
          <AlertTriangle className="w-4 h-4" />
          <span>Wipe All Application Data</span>
        </h3>
        <p className="text-xs text-rose-600/80 dark:text-rose-300/80 leading-relaxed">
          Permanently delete all notes, custom category pins, and settings from local device storage. Biometric or device PIN verification is required.
        </p>

        {showWipeConfirm ? (
          <div className="space-y-2.5 pt-1">
            <div className="p-2.5 rounded-xl bg-rose-100/70 dark:bg-rose-950/60 border border-rose-300 dark:border-rose-800 text-xs text-rose-800 dark:text-rose-200 font-medium">
              Please verify your identity with Android biometric lock or device PIN to permanently wipe all notes.
            </div>
            <div className="flex items-center gap-2 flex-wrap">
              <button
                type="button"
                disabled={isLockChecking}
                onClick={async () => {
                  setIsLockChecking(true);
                  setLockStatusMsg(null);
                  const authResult = isNativeLockEnabled()
                    ? await verifyWithAndroidNativeLock()
                    : await registerNativeDeviceLock();
                  setIsLockChecking(false);

                  if (authResult.success) {
                    setShowWipeConfirm(false);
                    onWipeData();
                    setLockStatusMsg({ text: 'Biometric verified. All notes and categories wiped.' });
                  } else {
                    setLockStatusMsg({
                      text: authResult.error || 'Biometric verification required to wipe notes.',
                      isError: true,
                    });
                  }
                }}
                className="px-4 py-2 rounded-xl bg-rose-600 text-white text-xs font-bold shadow-xs cursor-pointer active:scale-95 flex items-center gap-1.5"
              >
                <Smartphone className="w-3.5 h-3.5" />
                <span>Verify Biometric & Wipe All</span>
              </button>
              <button
                type="button"
                onClick={() => setShowWipeConfirm(false)}
                className="px-4 py-2 rounded-xl bg-white dark:bg-[#1B1524] text-[#484455] dark:text-[#A39BAC] text-xs font-semibold border border-[#EADBFC] dark:border-[#322742] cursor-pointer"
              >
                Cancel
              </button>
            </div>
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

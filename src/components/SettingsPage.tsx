import React, { useState } from 'react';
import { ThemeOption, Note } from '../types';
import {
  Sun,
  Moon,
  Monitor,
  Download,
  Smartphone,
  Sliders,
  AlertTriangle,
  RefreshCw,
  CheckCircle2,
  Sparkles,
  Send,
  Fingerprint
} from 'lucide-react';
import {
  isNativeLockEnabled,
  registerNativeDeviceLock,
  verifyWithAndroidNativeLock,
  setNativeLockEnabled,
} from '../utils/nativeAuth';
import { compareReleaseVersions, extractReleaseRunNumber } from '../utils/editorRules';

const DEFAULT_APP_VERSION = '1.3.40';
const DEFAULT_APP_BUILD_CODE = 140;
const DEFAULT_GITHUB_REPO = 'DefinitelynotRylix/Aurora-Notes';

interface SettingsPageProps {
  currentTheme: ThemeOption;
  onSelectTheme: (theme: ThemeOption) => void;
  isHapticsEnabled?: boolean;
  onToggleHaptics?: (enabled: boolean) => void;
  isDbEncryptionEnabled?: boolean;
  onToggleDbEncryption?: (enabled: boolean) => void;
  storageStats?: {
    directoryPath: string;
    syncedFileCount: number;
    orphanedFileCount: number;
    totalBytes: number;
  };
  lastCleanupSummary?: string | null;
  onCleanUpOrphanedFiles?: () => void;
  customCategories: string[];
  onAddCategory: (category: string) => void;
  onRemoveCategory: (category: string) => void;
  notes: Note[];
  onWipeData: () => void;
  onOpenWelcomeTour?: () => void;
  onOpenWhatsNew?: () => void;
}

export const SettingsPage: React.FC<SettingsPageProps> = ({
  currentTheme,
  onSelectTheme,
  isHapticsEnabled = true,
  onToggleHaptics,
  onWipeData,
  onOpenWelcomeTour,
  onOpenWhatsNew,
}) => {
  const [showWipeConfirm, setShowWipeConfirm] = useState(false);

  // App Version & Self-Update State
  const [installedVersion, setInstalledVersion] = useState<string>(() => {
    return localStorage.getItem('aurora-installed-version-v1') || DEFAULT_APP_VERSION;
  });
  const [installedBuildCode, setInstalledBuildCode] = useState<number>(() => {
    const saved = Number(localStorage.getItem('aurora-installed-build-code-v1'));
    return Number.isFinite(saved) && saved > 100 ? saved : DEFAULT_APP_BUILD_CODE;
  });
  const [isScanningUpdate, setIsScanningUpdate] = useState(false);
  const [isPullingUpdate, setIsPullingUpdate] = useState(false);
  const [pullProgress, setPullProgress] = useState(0);
  const [updateStatus, setUpdateStatus] = useState<{
    type: 'uptodate' | 'available' | 'error';
    message: string;
    latestVersion?: string;
    apkUrl?: string;
  } | null>(null);

  // Android Native Lock State
  const [isNativeLockOn, setIsNativeLockOn] = useState(isNativeLockEnabled);
  const [isLockChecking, setIsLockChecking] = useState(false);
  const [lockStatusMsg, setLockStatusMsg] = useState<{ text: string; isError?: boolean } | null>(null);

  const themeOptions: { id: ThemeOption; label: string; icon: React.ElementType }[] = [
    { id: 'system', label: 'System', icon: Monitor },
    { id: 'light', label: 'Light', icon: Sun },
    { id: 'dark', label: 'Dark', icon: Moon },
  ];

  const handleScanForUpdates = async () => {
    if (isScanningUpdate || isPullingUpdate) return;
    const storedRepo = (localStorage.getItem('aurora-github-repo-slug-v1') || DEFAULT_GITHUB_REPO)
      .trim()
      .replace(/^https?:\/\/github\.com\//i, '')
      .replace(/^\/+|\/+$/g, '');

    const activeRepo = storedRepo && storedRepo.includes('/') ? storedRepo : DEFAULT_GITHUB_REPO;

    setIsScanningUpdate(true);
    setUpdateStatus(null);

    try {
      let releaseData: {
        tag_name?: string;
        name?: string;
        body?: string;
        html_url?: string;
        draft?: boolean;
        assets?: Array<{ name?: string; browser_download_url?: string }>;
      } | null = null;

      const listResp = await fetch(`https://api.github.com/repos/${activeRepo}/releases?per_page=10`, {
        headers: { Accept: 'application/vnd.github+json' },
      });

      if (listResp.ok) {
        const listJson = await listResp.json();
        if (Array.isArray(listJson) && listJson.length > 0) {
          const nonDrafts = listJson.filter((r) => r && !r.draft);
          const withApk = nonDrafts.filter(
            (r) =>
              Array.isArray(r.assets) &&
              r.assets.some((a: { name?: string; browser_download_url?: string }) =>
                String(a?.name || '').toLowerCase().endsWith('.apk') && Boolean(a?.browser_download_url)
              )
          );
          const pool = withApk.length > 0 ? withApk : nonDrafts;
          pool.sort((a, b) => {
            const tagA = String(a?.tag_name || '');
            const runA = extractReleaseRunNumber(tagA, String(a?.body || ''));
            const tagB = String(b?.tag_name || '');
            const runB = extractReleaseRunNumber(tagB, String(b?.body || ''));
            return compareReleaseVersions(tagB, tagA, runB, runA ? 100 + runA : null);
          });
          releaseData = pool[0] || null;
        }
      }

      if (!releaseData) {
        const latestResp = await fetch(`https://api.github.com/repos/${activeRepo}/releases/latest`, {
          headers: { Accept: 'application/vnd.github+json' },
        });
        if (latestResp.ok) {
          releaseData = await latestResp.json();
        }
      }

      if (!releaseData) {
        setIsScanningUpdate(false);
        setUpdateStatus({
          type: 'uptodate',
          latestVersion: installedVersion,
          message: `Up to date (v${installedVersion}).`,
        });
        return;
      }

      const rawTag = String(releaseData.tag_name || '').trim();
      const rawBody = String(releaseData.body || '');
      const remoteRunNum = extractReleaseRunNumber(rawTag, rawBody);
      const rawCleanVer = rawTag.replace(/^[vV]/, '') || installedVersion;
      const latestVer =
        remoteRunNum && rawCleanVer.startsWith('1.2.') ? `1.3.${remoteRunNum}` : rawCleanVer;

      const assets = Array.isArray(releaseData.assets) ? releaseData.assets : [];
      const apkAsset = assets.find((a: { name?: string; browser_download_url?: string }) =>
        String(a?.name || '').toLowerCase().endsWith('.apk')
      );

      setIsScanningUpdate(false);

      const cmp = compareReleaseVersions(
        latestVer,
        installedVersion,
        remoteRunNum,
        installedBuildCode
      );

      if (cmp <= 0) {
        setUpdateStatus({
          type: 'uptodate',
          latestVersion: latestVer,
          apkUrl: apkAsset?.browser_download_url || releaseData.html_url || '',
          message: `Up to date (v${installedVersion}).`,
        });
      } else {
        const downloadUrl = apkAsset?.browser_download_url || releaseData.html_url || '';
        const nextBuildCode = remoteRunNum ? 100 + remoteRunNum : installedBuildCode + 1;
        setUpdateStatus({
          type: 'available',
          latestVersion: latestVer,
          apkUrl: downloadUrl,
          message: `New release v${latestVer} (Build ${nextBuildCode}) found! Pulling update...`,
        });
        setIsPullingUpdate(true);
        setPullProgress(25);
        setTimeout(() => setPullProgress(70), 300);
        setTimeout(() => {
          setPullProgress(100);
          setIsPullingUpdate(false);
          setInstalledVersion(latestVer);
          setInstalledBuildCode(nextBuildCode);
          localStorage.setItem('aurora-installed-version-v1', latestVer);
          localStorage.setItem('aurora-installed-build-code-v1', String(nextBuildCode));
          setUpdateStatus({
            type: 'available',
            latestVersion: latestVer,
            apkUrl: downloadUrl,
            message: `Updated to v${latestVer} (Build ${nextBuildCode}).`,
          });
        }, 700);
      }
    } catch {
      setIsScanningUpdate(false);
      setUpdateStatus({
        type: 'error',
        message: 'Could not reach GitHub Releases API. Check your internet connection.',
      });
    }
  };

  const handleToggleNativeLock = async () => {
    setLockStatusMsg(null);
    setIsLockChecking(true);

    if (isNativeLockOn) {
      const verifyResult = await verifyWithAndroidNativeLock();
      setIsLockChecking(false);
      if (verifyResult.success) {
        setNativeLockEnabled(false);
        setIsNativeLockOn(false);
        setLockStatusMsg({ text: 'Biometric verified. App lock disabled.' });
      } else {
        setLockStatusMsg({
          text: verifyResult.error || 'Biometric authentication required to disable app lock.',
          isError: true,
        });
      }
      return;
    }

    const result = await registerNativeDeviceLock();
    setIsLockChecking(false);

    if (result.success) {
      setIsNativeLockOn(true);
      setLockStatusMsg({ text: 'Biometric / device lock activated.' });
    } else {
      setLockStatusMsg({
        text: result.error || 'Could not verify Android native lock screen.',
        isError: true,
      });
    }
  };

  return (
    <div className="max-w-2xl mx-auto px-4 sm:px-6 pt-2 pb-24 space-y-3">
      {/* Compact Header */}
      <div className="pb-1">
        <h2 className="text-lg sm:text-xl font-bold text-[#1E1A24] dark:text-[#F7EEFC] tracking-tight">
          Settings
        </h2>
        <p className="text-xs text-[#797487] dark:text-[#A39BAC] mt-0.5">
          Theme, lock, updates, and guides.
        </p>
      </div>

      {/* 1. Appearance (Compact 3-column segmented row) */}
      <div className="rounded-2xl p-3.5 border border-[#EADBFC] dark:border-[#322742] bg-white dark:bg-[#1B1524] shadow-xs space-y-2.5">
        <h3 className="text-xs font-bold text-[#1E1A24] dark:text-[#F7EEFC] flex items-center gap-1.5">
          <Sliders className="w-3.5 h-3.5 text-[#5925DC] dark:text-[#C296FD]" />
          <span>Appearance</span>
        </h3>

        <div className="grid grid-cols-3 gap-2">
          {themeOptions.map((opt) => {
            const Icon = opt.icon;
            const isSelected = currentTheme === opt.id;
            return (
              <button
                key={opt.id}
                type="button"
                onClick={() => onSelectTheme(opt.id)}
                className={`py-2 px-2.5 rounded-xl border flex items-center justify-center gap-1.5 text-xs transition-all cursor-pointer ${
                  isSelected
                    ? 'bg-[#F4EBFF] dark:bg-[#2B1E3E] border-[#5925DC] dark:border-[#9D5AF5] text-[#5925DC] dark:text-[#C296FD] font-bold shadow-xs'
                    : 'bg-[#FAF8FB] dark:bg-[#120E17] border-[#EADBFC] dark:border-[#322742] text-[#484455] dark:text-[#A39BAC] font-semibold hover:bg-white dark:hover:bg-[#1B1524]'
                }`}
              >
                <Icon className="w-3.5 h-3.5 shrink-0" />
                <span>{opt.label}</span>
              </button>
            );
          })}
        </div>
      </div>

      {/* 2. Preferences & Security (Grouped Haptics + Biometric Lock) */}
      <div className="rounded-2xl px-3.5 py-2.5 border border-[#EADBFC] dark:border-[#322742] bg-white dark:bg-[#1B1524] shadow-xs divide-y divide-[#EADBFC]/60 dark:divide-[#322742]/70">
        {/* Haptics Row */}
        <div className="py-2 flex items-center justify-between gap-2">
          <div className="flex items-center gap-2.5 min-w-0">
            <Sparkles className="w-4 h-4 text-[#5925DC] dark:text-[#C296FD] shrink-0" />
            <div className="min-w-0">
              <div className="text-xs font-bold text-[#1E1A24] dark:text-[#F7EEFC]">Haptic Feedback</div>
              <div className="text-[11px] text-[#797487] dark:text-[#A39BAC] truncate">
                Subtle vibration on navigation taps
              </div>
            </div>
          </div>

          <div className="flex items-center gap-1.5 shrink-0">
            {isHapticsEnabled && (
              <button
                type="button"
                onClick={() => {
                  if (typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function') {
                    navigator.vibrate(40);
                  }
                }}
                className="px-2.5 py-1 rounded-lg bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] text-[11px] font-bold hover:opacity-90 active:scale-95 transition-all cursor-pointer"
              >
                Test
              </button>
            )}
            <button
              type="button"
              role="switch"
              aria-checked={isHapticsEnabled}
              aria-label="Toggle haptic feedback"
              onClick={() => {
                const nextState = !isHapticsEnabled;
                onToggleHaptics?.(nextState);
                if (nextState && typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function') {
                  navigator.vibrate(35);
                }
              }}
              className={`px-2.5 py-1 rounded-lg text-[11px] font-bold border transition-all cursor-pointer ${
                isHapticsEnabled
                  ? 'bg-[#5925DC] dark:bg-[#9D5AF5] text-white border-transparent'
                  : 'bg-[#FAF8FB] dark:bg-[#120E17] text-[#484455] dark:text-[#A39BAC] border-[#EADBFC] dark:border-[#322742]'
              }`}
            >
              {isHapticsEnabled ? 'On' : 'Off'}
            </button>
          </div>
        </div>

        {/* Biometric Lock Row */}
        <div className="py-2 space-y-2">
          <div className="flex items-center justify-between gap-2">
            <div className="flex items-center gap-2.5 min-w-0">
              <Fingerprint className="w-4 h-4 text-[#5925DC] dark:text-[#C296FD] shrink-0" />
              <div className="min-w-0">
                <div className="text-xs font-bold text-[#1E1A24] dark:text-[#F7EEFC]">Biometric / PIN Lock</div>
                <div className="text-[11px] text-[#797487] dark:text-[#A39BAC] truncate">
                  Require device unlock on launch
                </div>
              </div>
            </div>

            <button
              type="button"
              role="switch"
              aria-checked={isNativeLockOn}
              aria-label="Toggle biometric lock"
              onClick={handleToggleNativeLock}
              disabled={isLockChecking}
              className={`px-2.5 py-1 rounded-lg text-[11px] font-bold border transition-all cursor-pointer shrink-0 ${
                isNativeLockOn
                  ? 'bg-[#5925DC] dark:bg-[#9D5AF5] text-white border-transparent'
                  : 'bg-[#FAF8FB] dark:bg-[#120E17] text-[#484455] dark:text-[#A39BAC] border-[#EADBFC] dark:border-[#322742]'
              }`}
            >
              {isNativeLockOn ? 'On' : 'Off'}
            </button>
          </div>

          {lockStatusMsg && (
            <div
              className={`px-2.5 py-1.5 rounded-lg text-[11px] font-medium border ${
                lockStatusMsg.isError
                  ? 'bg-rose-50 dark:bg-rose-950/40 text-rose-600 dark:text-rose-400 border-rose-200 dark:border-rose-900'
                  : 'bg-emerald-50 dark:bg-emerald-950/40 text-emerald-600 dark:text-emerald-400 border-emerald-200 dark:border-emerald-900'
              }`}
            >
              {lockStatusMsg.text}
            </div>
          )}
        </div>
      </div>

      {/* 3. App Updates (Compact) */}
      <div className="rounded-2xl p-3.5 border border-[#EADBFC] dark:border-[#322742] bg-white dark:bg-[#1B1524] shadow-xs space-y-2.5">
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2.5 min-w-0">
            <RefreshCw className={`w-4 h-4 text-[#5925DC] dark:text-[#C296FD] shrink-0 ${isScanningUpdate ? 'animate-spin' : ''}`} />
            <div className="min-w-0">
              <div className="text-xs font-bold text-[#1E1A24] dark:text-[#F7EEFC]">App Updates</div>
              <div className="text-[11px] text-[#797487] dark:text-[#A39BAC] truncate">
                v{installedVersion} • Build {installedBuildCode}
              </div>
            </div>
          </div>

          <button
            type="button"
            onClick={handleScanForUpdates}
            disabled={isScanningUpdate || isPullingUpdate}
            className="px-3 py-1.5 rounded-xl bg-[#5925DC] hover:bg-[#4A1FB8] text-white text-[11px] font-bold flex items-center gap-1.5 shadow-xs transition-all cursor-pointer disabled:opacity-60 shrink-0"
          >
            <span>{isScanningUpdate ? 'Checking...' : 'Check'}</span>
          </button>
        </div>

        {isPullingUpdate && (
          <div className="space-y-1">
            <div className="flex items-center justify-between text-[11px] font-semibold text-[#5925DC] dark:text-[#C296FD]">
              <span>Pulling latest release...</span>
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
            className={`p-2.5 rounded-xl text-[11px] font-medium border space-y-1.5 ${
              updateStatus.type === 'uptodate'
                ? 'bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300 border-emerald-200 dark:border-emerald-800'
                : updateStatus.type === 'available'
                ? 'bg-[#F4EBFF] dark:bg-[#2B1E3E] text-[#5925DC] dark:text-[#C296FD] border-[#5925DC]/40'
                : 'bg-rose-50 dark:bg-rose-950/40 text-rose-600 dark:text-rose-400 border-rose-200 dark:border-rose-900'
            }`}
          >
            <div className="flex items-center gap-1.5">
              <CheckCircle2 className="w-3.5 h-3.5 shrink-0" />
              <span>{updateStatus.message}</span>
            </div>
            {updateStatus.apkUrl && (
              <a
                href={updateStatus.apkUrl}
                download="Aurora Notes.apk"
                className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-[#5925DC] text-white text-[11px] font-bold hover:bg-[#4A1FB8] transition-colors"
              >
                <Download className="w-3 h-3" />
                <span>Download APK (v{updateStatus.latestVersion})</span>
              </a>
            )}
          </div>
        )}
      </div>

      {/* 4. Guides & Community (Combined compact card) */}
      <div className="rounded-2xl p-3.5 border border-[#EADBFC] dark:border-[#322742] bg-white dark:bg-[#1B1524] shadow-xs space-y-2.5">
        <h3 className="text-xs font-bold text-[#1E1A24] dark:text-[#F7EEFC] flex items-center gap-1.5">
          <Sparkles className="w-3.5 h-3.5 text-[#5925DC] dark:text-[#C296FD]" />
          <span>Guides & Community</span>
        </h3>

        <div className="grid grid-cols-3 gap-2">
          {onOpenWelcomeTour && (
            <button
              type="button"
              data-testid="settings-open-tour-btn"
              onClick={onOpenWelcomeTour}
              className="py-2 px-2 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] text-[#5925DC] dark:text-[#C296FD] text-xs font-bold border border-[#EADBFC] dark:border-[#322742] hover:bg-[#F4EBFF] dark:hover:bg-[#2B1E3E] transition-all cursor-pointer active:scale-95"
            >
              Tour
            </button>
          )}
          {onOpenWhatsNew && (
            <button
              type="button"
              data-testid="settings-open-whats-new-btn"
              onClick={onOpenWhatsNew}
              className="py-2 px-2 rounded-xl bg-[#FAF8FB] dark:bg-[#120E17] text-[#5925DC] dark:text-[#C296FD] text-xs font-bold border border-[#EADBFC] dark:border-[#322742] hover:bg-[#F4EBFF] dark:hover:bg-[#2B1E3E] transition-all cursor-pointer active:scale-95"
            >
              What&apos;s New
            </button>
          )}
          <a
            href="https://t.me/GithubAuroraNotes"
            target="_blank"
            rel="noopener noreferrer"
            className="py-2 px-2 rounded-xl bg-gradient-to-r from-[#5925DC] to-[#9B51E0] dark:from-[#9D5AF5] dark:to-[#C296FD] text-white text-xs font-bold shadow-xs flex items-center justify-center gap-1 hover:opacity-95 active:scale-95 transition-all cursor-pointer"
          >
            <Send className="w-3 h-3 shrink-0" />
            <span>Telegram</span>
          </a>
        </div>
      </div>

      {/* 5. Danger Zone (Compact) */}
      <div className="rounded-2xl p-3.5 border border-rose-200 dark:border-rose-900/50 bg-rose-50/50 dark:bg-rose-950/20 shadow-xs space-y-2.5">
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2.5 min-w-0">
            <AlertTriangle className="w-4 h-4 text-rose-600 dark:text-rose-400 shrink-0" />
            <div className="min-w-0">
              <div className="text-xs font-bold text-rose-700 dark:text-rose-400">Wipe All Data</div>
              <div className="text-[11px] text-rose-600/80 dark:text-rose-300/80 truncate">
                Requires biometric or PIN verification
              </div>
            </div>
          </div>

          {!showWipeConfirm && (
            <button
              type="button"
              onClick={() => setShowWipeConfirm(true)}
              className="px-3 py-1.5 rounded-xl bg-rose-600 text-white text-[11px] font-bold shadow-xs cursor-pointer active:scale-95 shrink-0"
            >
              Wipe
            </button>
          )}
        </div>

        {showWipeConfirm && (
          <div className="space-y-2 pt-1">
            <div className="p-2 rounded-xl bg-rose-100/70 dark:bg-rose-950/60 border border-rose-300 dark:border-rose-800 text-[11px] text-rose-800 dark:text-rose-200 font-medium">
              Verify with biometric lock or device PIN to permanently wipe all notes.
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
                    setLockStatusMsg({ text: 'Biometric verified. All notes wiped.' });
                  } else {
                    setLockStatusMsg({
                      text: authResult.error || 'Biometric verification required to wipe notes.',
                      isError: true,
                    });
                  }
                }}
                className="px-3 py-1.5 rounded-xl bg-rose-600 text-white text-[11px] font-bold shadow-xs cursor-pointer active:scale-95 flex items-center gap-1"
              >
                <Smartphone className="w-3 h-3" />
                <span>Verify & Wipe</span>
              </button>
              <button
                type="button"
                onClick={() => setShowWipeConfirm(false)}
                className="px-3 py-1.5 rounded-xl bg-white dark:bg-[#1B1524] text-[#484455] dark:text-[#A39BAC] text-[11px] font-semibold border border-[#EADBFC] dark:border-[#322742] cursor-pointer"
              >
                Cancel
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

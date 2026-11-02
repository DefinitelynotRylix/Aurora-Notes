# Local Only App Log Mode for Iris Notes

A privacy-focused, 100% offline diagnostic logging system built directly into Iris Notes. Captures local application events (errors, warnings, and security actions) into a rolling buffer stored entirely on-device, enabling users to inspect and download diagnostic logs as plain text (`.txt`) to manually share with developers for troubleshooting without any network telemetry or third-party tracking.

## User Review & Critical Decisions

> [!IMPORTANT]
> The user confirmed the following design decisions during clarification:

- **Confirmed Log Export Format**: Plain text log format (`.txt`) with ISO timestamps, severity level badges, and detailed event messages.
- **Confirmed Log Severity Scope**: Captures **Errors, Warnings, and Security Events** (e.g., biometric authentication, note encryption/decryption events, storage wipes, and native lock toggles).
- **Confirmed Buffer Capacity**: 250 rolling log entries (~50 KB) held locally in `localStorage` (`iris_local_logs_v1`) to prevent storage overflow while retaining ample context for bug reporting.

---

## 1. Overview & Core Concept

### What It Does
Provides a dedicated **"Local Diagnostic Logs"** card inside the Iris Notes **Settings** screen. Users can:
- **Toggle Diagnostic Logging**: Enable/disable local event logging at any time.
- **View Rolling Logs**: Inspect formatted log entries sorted by timestamp with level filtering (All, Error, Warn, Security).
- **Download Diagnostic File**: Export the complete rolling log buffer as a plain `.txt` file (`iris-notes-log-<timestamp>.txt`) for manual attachment to bug reports.
- **Clear Log History**: Instantly purge cached log entries from local storage.

### Target Audience & Persona
Privacy-conscious users running Iris Notes in air-gapped or zero-telemetry environments who encounter unexpected behavior and need a safe, offline method to capture and provide debug logs to developers.

### Key Value
Maintains strict compliance with the zero-telemetry, zero-network architecture while giving users complete transparency and agency over system diagnostics.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Viewing Diagnostics in Settings**:
   - User opens the Settings tab (`ActiveTab = 'settings'`).
   - Scroll down to the **"Local Diagnostic Logs"** section.
   - Live summary displays total log count (e.g., `18 / 250 entries recorded`), logging status (Active/Paused), and last recorded event timestamp.

2. **Inspecting & Filtering Logs**:
   - User taps **"Inspect Log Buffer"** to expand an inline, scrollable, dark monospace terminal console (`bg-[#0E0B12] dark:bg-[#0A070F]`).
   - Filter chips: `All`, `Errors`, `Warnings`, `Security`.
   - Clear visual badges for levels: Crimson for `ERROR`, Amber for `WARN`, Violet for `SECURITY`.

3. **Exporting Log File**:
   - User clicks **"Download App Log (.txt)"**.
   - Generates a formatted text file containing environment details (app name, version, platform user-agent, timestamp) followed by line-by-line log records:
     `[2026-11-01T23:14:22.000Z] [SECURITY] Native Android Lock toggled: ACTIVE`
     `[2026-11-01T23:14:25.120Z] [WARN] Note decryption fallback applied for ID note-1742081`

4. **Clearing Logs**:
   - User taps **"Clear Local Logs"** with inline confirmation to clear the buffer.

### Visual Identity & Theme Integration
- **Terminal Console Container**: Sleek dark console window with rounded corners (`rounded-2xl`), subtle border (`border-[#322742]`), font monospace (`JetBrains Mono` / `tabular-nums`), and smooth custom scrollbar.
- **Color Palette & Contrast**:
  - `ERROR`: Crimson background accent (`bg-rose-500/10 text-rose-400 border-rose-500/20`)
  - `WARN`: Amber background accent (`bg-amber-500/10 text-amber-400 border-amber-500/20`)
  - `SECURITY`: Violet background accent (`bg-[#9D5AF5]/15 text-[#C296FD] border-[#9D5AF5]/30`)
- **Micro-Interactions**: Smooth scale and color feedback on copy/download buttons, entry count badge updates in real-time.

---

## 3. Key Product Decisions & Trade-Offs

### Decision 1: Single Utility Logger vs Global Console Monkey-Patching
- **Chosen Approach**: Dedicated singleton `logger` module (`src/utils/logger.ts`) with explicit calls (`logger.error()`, `logger.warn()`, `logger.security()`), plus a selective window error listener (`window.addEventListener('error')`, `window.addEventListener('unhandledrejection')`).
- **Why**: Avoids unexpected side effects from intrusive global console overrides while guaranteeing full capture of uncaught runtime errors and explicit app domain events.
- **Alternatives Considered**: Monkey-patching `console.log` / `console.error` globally. Overly noisy and captures extraneous third-party or browser extensions output.

### Decision 2: Storage Structure & Retention Strategy
- **Chosen Approach**: FIFO (First-In, First-Out) array stored in `localStorage` under key `iris_local_logs_v1`, capped strictly at 250 entries.
- **Why**: Keeps storage footprint under 50 KB, ensures lightning-fast load times, and preserves recent diagnostic history without exceeding browser quota.

---

## 4. Technical Architecture & Data Strategy

### System Architecture & Data Flow

```
┌──────────────────────────────────────────────────────────────────┐
│                           Iris Notes                             │
├───────────────────────────────┬──────────────────────────────────┤
│    App Events & Errors        │      Settings Page UI            │
│   (Crypto, Auth, Errors)      │  (Diagnostic Console & Export)   │
└───────────────┬───────────────┴─────────────────▲────────────────┘
                │                                 │
                │ logger.error() / .warn()        │ Reads logs
                ▼                                 │
┌─────────────────────────────────────────────────┴────────────────┐
│                   Local Logger Utility (logger.ts)               │
│  - FIFO Queue (Max 250 entries)                                  │
│  - Storage Persistor (localStorage: iris_local_logs_v1)          │
│  - Plain Text File Generator (.txt download)                     │
└──────────────────────────────────────────────────────────────────┘
```

### Data Model (`LogEntry`)

```typescript
export type LogLevel = 'ERROR' | 'WARN' | 'SECURITY';

export interface LogEntry {
  id: string;
  timestamp: number; // Date.now()
  level: LogLevel;
  category: string; // e.g. 'AUTH', 'CRYPTO', 'STORAGE', 'RUNTIME'
  message: string;
  details?: string; // Optional stack trace or JSON context string
}
```

### Component & File Mapping

1. `src/utils/logger.ts`
   - Class/Singleton managing `LogEntry[]` in `localStorage`.
   - Helper methods: `logger.error(category, msg, details)`, `logger.warn(...)`, `logger.security(...)`.
   - `exportLogsAsText()`: Builds plain text string formatted for developer reading.
   - `clearLogs()`: Resets storage.
   - Window global error handler setup (`setupGlobalErrorListeners()`).

2. `src/components/SettingsPage.tsx`
   - Add **"Local Diagnostic Logs"** card component with:
     - Log status banner (Active / Paused toggle).
     - Live log count and level summary pill counters.
     - Expandable dark terminal console view with filter tabs (`All`, `ERROR`, `WARN`, `SECURITY`).
     - "Download App Log (.txt)" button.
     - "Clear Logs" confirmation action.

3. `src/App.tsx` & Application Services
   - Initialize global error listeners on mount.
   - Instrument key events with `logger.security()` and `logger.error()` (e.g. native lock changes, wipe data, encryption/decryption, storage warnings).

---

## Verification & Build Plan

1. Create `src/utils/logger.ts` with FIFO buffer, storage persistence, and text formatting.
2. Update `SettingsPage.tsx` to include the Local Diagnostic Logs UI section.
3. Instrument key actions across `App.tsx`, `nativeAuth.ts`, and `crypto.ts`.
4. Validate with `compile_applet` and `lint_applet` to ensure zero compilation or lint errors.

---

## 5. Android Native App & CI Build Resolution

- **AAPT Launcher Icons**: Added adaptive XML vectors and fallback mipmaps across `mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi` (`ic_launcher.png`, `ic_launcher_round.png`).
- **Missing `dp` Import**: Added `import androidx.compose.ui.unit.dp` to `MainActivity.kt` for `tonalElevation = 6.dp`.
- **Icon Typo**: Replaced nonexistent `Icons.Default.CheckSquare` with standard Android Compose `Icons.Default.CheckBox` in `NoteEditorScreen.kt`.
- **Material 3 API Migration**: Upgraded `Divider` to `HorizontalDivider` in `NoteCard.kt` and `NoteEditorScreen.kt`.
- **Room DAO Query Fix**: Removed default parameter in `@Dao` query `moveToTrash(id: String, timestamp: Long)` in `NoteDao.kt` to avoid synthetic `$default` resolution errors in Room KSP, and passed `System.currentTimeMillis()` from `IrisRepository.kt`.
- **SDK Target Alignment**: Configured `compileSdk = 35` and `targetSdk = 35` in `app/build.gradle.kts` for AGP 8.8.0 stability.
- **Gradle Wrapper JVM Options Fix**: In `/gradlew`, resolved `Error: Could not find or load main class "-Xmx64m"` caused by escaped literal quotes in `DEFAULT_JVM_OPTS='-Dfile.encoding=UTF-8 "-Xmx64m" "-Xms64m"'`. Reset `DEFAULT_JVM_OPTS=""` so the Java invocation directly launches `org.gradle.wrapper.GradleWrapperMain`, allowing `gradle.properties` (`org.gradle.jvmargs=-Xmx2048m`) to cleanly govern heap allocation without argument parsing corruption. Also generated companion `gradlew.bat` for Windows compatibility.
- **Checklist & Formatting Behavior**: Verified in Compose `NoteEditorScreen.kt` and unit tests that checkboxes occupy a dedicated `-1` prefix slot (preventing typing before the box or duplicate checkboxes on a single line), pressing Enter creates a clean new line with checklist disabled (manual trigger only), and all text formatting applies seamlessly.



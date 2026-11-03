export type LogLevel = 'ERROR' | 'WARN' | 'SECURITY';

export interface LogEntry {
  id: string;
  timestamp: number;
  level: LogLevel;
  category: string;
  message: string;
  details?: string;
}

const STORAGE_LOGS_KEY = 'aurora_local_logs_v1';
const STORAGE_LOGGING_ENABLED_KEY = 'aurora_local_logging_enabled_v1';
const MAX_LOG_ENTRIES = 250;

class LocalLogger {
  private logs: LogEntry[] = [];
  private isEnabled: boolean = true;
  private listeners: Set<() => void> = new Set();
  private globalListenersAttached = false;

  constructor() {
    this.loadState();
  }

  private loadState() {
    try {
      const enabled = localStorage.getItem(STORAGE_LOGGING_ENABLED_KEY);
      this.isEnabled = enabled === null ? true : enabled === 'true';

      const stored = localStorage.getItem(STORAGE_LOGS_KEY);
      if (stored) {
        const parsed = JSON.parse(stored);
        if (Array.isArray(parsed)) {
          // Restrict strictly to ERROR and WARN entries
          this.logs = parsed.filter(
            (entry: LogEntry) => entry && (entry.level === 'ERROR' || entry.level === 'WARN')
          );
        }
      }
    } catch {
      this.logs = [];
    }
  }

  private persist() {
    try {
      localStorage.setItem(STORAGE_LOGS_KEY, JSON.stringify(this.logs));
    } catch (e) {
      console.warn('Failed to persist local logs', e);
    }
    this.notify();
  }

  public subscribe(listener: () => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private notify() {
    this.listeners.forEach((fn) => fn());
  }

  public isLoggingEnabled(): boolean {
    return this.isEnabled;
  }

  public setLoggingEnabled(enabled: boolean) {
    this.isEnabled = enabled;
    try {
      localStorage.setItem(STORAGE_LOGGING_ENABLED_KEY, String(enabled));
    } catch {
      // ignore
    }
    this.notify();
  }

  public getLogs(): LogEntry[] {
    return this.logs.filter((entry) => entry.level === 'ERROR' || entry.level === 'WARN');
  }

  public log(level: LogLevel, category: string, message: string, details?: string): LogEntry | null {
    if (!this.isEnabled) return null;
    // Only record warnings and errors when one occurs
    if (level !== 'ERROR' && level !== 'WARN') return null;

    const entry: LogEntry = {
      id: 'log-' + Date.now() + '-' + Math.random().toString(36).substring(2, 7),
      timestamp: Date.now(),
      level,
      category: category.toUpperCase(),
      message,
      details,
    };

    // Mirror log entry directly to browser console as well
    const consoleTag = `[IRIS DIAGNOSTICS] [${level}] [${category.toUpperCase()}] ${message}`;
    if (level === 'ERROR') {
      console.error(consoleTag, details || '');
    } else if (level === 'WARN') {
      console.warn(consoleTag, details || '');
    }

    // Prepend to maintain reverse chronological order (newest first)
    this.logs = [entry, ...this.logs].slice(0, MAX_LOG_ENTRIES);
    this.persist();
    return entry;
  }

  public error(category: string, message: string, details?: string): LogEntry | null {
    return this.log('ERROR', category, message, details);
  }

  public warn(category: string, message: string, details?: string): LogEntry | null {
    return this.log('WARN', category, message, details);
  }

  public security(_category: string, _message: string, _details?: string): LogEntry | null {
    // Restricted: routine informational/security actions are not logged; only errors and warnings are shown when one occurs.
    return null;
  }

  public clearLogs() {
    this.logs = [];
    try {
      localStorage.removeItem(STORAGE_LOGS_KEY);
    } catch {
      // ignore
    }
    this.notify();
  }

  public exportLogsAsText(): string {
    const header = [
      '================================================================',
      '                     AURORA NOTES DIAGNOSTIC LOG                  ',
      '================================================================',
      `Export Timestamp : ${new Date().toISOString()}`,
      `App Environment  : Local-Only (Zero Telemetry)`,
      `User Agent       : ${typeof navigator !== 'undefined' ? navigator.userAgent : 'Unknown'}`,
      `Total Log Entries: ${this.logs.length}`,
      '================================================================\n',
    ].join('\n');

    if (this.logs.length === 0) {
      return header + 'No local diagnostic events recorded.\n';
    }

    const lines = this.logs.map((entry) => {
      const timeStr = new Date(entry.timestamp).toISOString();
      const levelPad = entry.level.padEnd(8, ' ');
      const catPad = `[${entry.category}]`.padEnd(12, ' ');
      let line = `[${timeStr}] ${levelPad} ${catPad} ${entry.message}`;
      if (entry.details) {
        line += `\n  Details: ${entry.details.replace(/\n/g, '\n  ')}`;
      }
      return line;
    });

    return header + lines.join('\n\n') + '\n\n--- END OF LOG FILE ---\n';
  }

  public downloadLogsFile() {
    const textContent = this.exportLogsAsText();
    const dataUrl = 'data:text/plain;charset=utf-8,' + encodeURIComponent(textContent);
    const downloadAnchor = document.createElement('a');
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
    
    downloadAnchor.setAttribute('href', dataUrl);
    downloadAnchor.setAttribute('download', `iris-notes-log-${timestamp}.txt`);
    document.body.appendChild(downloadAnchor);
    downloadAnchor.click();
    downloadAnchor.remove();

    this.security('LOGGER', `Diagnostic log exported as text file (iris-notes-log-${timestamp}.txt)`);
  }

  public setupGlobalErrorListeners() {
    if (this.globalListenersAttached || typeof window === 'undefined') return;
    this.globalListenersAttached = true;

    window.addEventListener('error', (event) => {
      this.error(
        'RUNTIME_ERROR',
        event.message || 'Uncaught error occurred in runtime',
        event.error?.stack || `${event.filename}:${event.lineno}:${event.colno}`
      );
    });

    window.addEventListener('unhandledrejection', (event) => {
      const reason = event.reason;
      const msg = typeof reason === 'object' && reason !== null && 'message' in reason
        ? (reason as { message: string }).message
        : String(reason);
      const stack = typeof reason === 'object' && reason !== null && 'stack' in reason
        ? (reason as { stack: string }).stack
        : undefined;

      this.error('UNHANDLED_PROMISE', `Unhandled Promise Rejection: ${msg}`, stack);
    });
  }
}

export const logger = new LocalLogger();

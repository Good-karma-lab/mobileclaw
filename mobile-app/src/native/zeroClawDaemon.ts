/**
 * React Native bridge to embedded ZeroClaw backend daemon
 *
 * The daemon runs as an Android Foreground Service and exposes
 * the agent runtime HTTP gateway at http://127.0.0.1:8000
 */

import { NativeModules, Platform } from 'react-native';

const { ZeroClawDaemon } = NativeModules;

if (!ZeroClawDaemon) {
  console.warn('ZeroClawDaemon native module not found. Embedded backend may not be available.');
}

export interface ZeroClawDaemonStatus {
  running: boolean;
  url: string;
  mode?: string;
  version?: string;
}

export interface DaemonStartConfig {
  apiKey?: string;
  model?: string;
  telegramToken?: string;
}

/**
 * Start the embedded ZeroClaw backend daemon service
 *
 * The daemon will:
 * - Load the Rust agent runtime in-process via JNI
 * - Bind HTTP gateway to localhost:8000
 * - Run in foreground with persistent notification
 *
 * @param config Optional config overrides (apiKey, model, telegramToken)
 * @throws Error if daemon fails to start
 */
export async function startDaemon(config: DaemonStartConfig = {}): Promise<void> {
  if (Platform.OS !== 'android') {
    throw new Error('Embedded daemon only available on Android');
  }

  if (!ZeroClawDaemon) {
    throw new Error('ZeroClawDaemon native module not available');
  }

  try {
    await ZeroClawDaemon.startDaemon({
      apiKey: config.apiKey ?? '',
      model: config.model ?? '',
      telegramToken: config.telegramToken ?? '',
    });
    console.log('[ZeroClawDaemon] Start requested');
  } catch (error) {
    console.error('[ZeroClawDaemon] Failed to start:', error);
    throw error;
  }
}

/**
 * Stop the embedded ZeroClaw backend daemon service
 *
 * This will terminate the daemon process and stop the foreground service.
 *
 * @throws Error if daemon fails to stop
 */
export async function stopDaemon(): Promise<void> {
  if (Platform.OS !== 'android') {
    throw new Error('Embedded daemon only available on Android');
  }

  if (!ZeroClawDaemon) {
    throw new Error('ZeroClawDaemon native module not available');
  }

  try {
    await ZeroClawDaemon.stopDaemon();
    console.log('[ZeroClawDaemon] Stop requested');
  } catch (error) {
    console.error('[ZeroClawDaemon] Failed to stop:', error);
    throw error;
  }
}

/**
 * Restart the embedded ZeroClaw backend daemon service
 *
 * This will stop and then start the daemon, useful for recovering
 * from errors or applying configuration changes.
 *
 * @throws Error if daemon fails to restart
 */
export async function restartDaemon(): Promise<void> {
  if (Platform.OS !== 'android') {
    throw new Error('Embedded daemon only available on Android');
  }

  if (!ZeroClawDaemon) {
    throw new Error('ZeroClawDaemon native module not available');
  }

  try {
    await ZeroClawDaemon.restartDaemon();
    console.log('[ZeroClawDaemon] Restart requested');
  } catch (error) {
    console.error('[ZeroClawDaemon] Failed to restart:', error);
    throw error;
  }
}

/**
 * Check if the daemon is currently running
 *
 * @returns true if daemon process is active, false otherwise
 */
export async function isDaemonRunning(): Promise<boolean> {
  if (Platform.OS !== 'android') {
    return false;
  }

  if (!ZeroClawDaemon) {
    return false;
  }

  try {
    const running = await ZeroClawDaemon.isDaemonRunning();
    return running;
  } catch (error) {
    console.error('[ZeroClawDaemon] Failed to check status:', error);
    return false;
  }
}

/**
 * Get the daemon URL
 *
 * For embedded daemon, this is always http://127.0.0.1:8000
 *
 * @returns The daemon HTTP gateway URL
 */
export async function getDaemonUrl(): Promise<string> {
  if (Platform.OS !== 'android') {
    // Fallback for non-Android platforms (development)
    return 'http://10.0.2.2:8000';
  }

  if (!ZeroClawDaemon) {
    // Fallback if native module not available
    return 'http://127.0.0.1:8000';
  }

  try {
    const url = await ZeroClawDaemon.getDaemonUrl();
    return url;
  } catch (error) {
    console.error('[ZeroClawDaemon] Failed to get URL:', error);
    return 'http://127.0.0.1:8000';
  }
}

/**
 * Get comprehensive daemon status
 *
 * @returns Daemon status object with running state, URL, mode, and version
 */
export async function getDaemonStatus(): Promise<ZeroClawDaemonStatus> {
  if (Platform.OS !== 'android') {
    return {
      running: false,
      url: 'http://10.0.2.2:8000',
      mode: 'remote',
    };
  }

  if (!ZeroClawDaemon) {
    return {
      running: false,
      url: 'http://127.0.0.1:8000',
      mode: 'unavailable',
    };
  }

  try {
    const status = await ZeroClawDaemon.getDaemonStatus();
    return status;
  } catch (error) {
    console.error('[ZeroClawDaemon] Failed to get status:', error);

    // Fallback: try individual calls
    try {
      const [running, url] = await Promise.all([
        isDaemonRunning(),
        getDaemonUrl(),
      ]);

      return { running, url, mode: 'embedded' };
    } catch (fallbackError) {
      return {
        running: false,
        url: 'http://127.0.0.1:8000',
        mode: 'error',
      };
    }
  }
}

/**
 * Wait for daemon to be ready
 *
 * Polls the HTTP gateway /health endpoint until it responds or timeout is reached.
 * Uses HTTP polling instead of native flag to ensure the gateway is actually bound
 * and accepting connections (not just the service started).
 *
 * @param timeoutMs Maximum time to wait in milliseconds (default: 30000)
 * @param pollIntervalMs Interval between checks in milliseconds (default: 500)
 * @returns Promise that resolves when daemon is ready or rejects on timeout
 */
export async function waitForDaemonReady(
  timeoutMs: number = 30000,
  pollIntervalMs: number = 500,
): Promise<void> {
  const startTime = Date.now();

  while (Date.now() - startTime < timeoutMs) {
    try {
      // Use a manual timeout since AbortSignal.timeout() is not available in all RN versions
      const fetchWithTimeout = new Promise<Response>((resolve, reject) => {
        const timer = setTimeout(() => reject(new Error('fetch timeout')), 1000);
        fetch('http://127.0.0.1:8000/health').then(
          (r) => { clearTimeout(timer); resolve(r); },
          (e) => { clearTimeout(timer); reject(e); },
        );
      });
      const res = await fetchWithTimeout;
      if (res.ok) {
        console.log('[ZeroClawDaemon] HTTP gateway ready');
        return;
      }
    } catch {
      // Not ready yet — keep polling
    }

    await new Promise(resolve => setTimeout(resolve, pollIntervalMs));
  }

  throw new Error(`Daemon failed to start within ${timeoutMs}ms`);
}

/**
 * Get daemon constants (exported from native module)
 */
export function getDaemonConstants() {
  if (!ZeroClawDaemon || !ZeroClawDaemon.getConstants) {
    return {
      DEFAULT_URL: 'http://127.0.0.1:8000',
      DEFAULT_PORT: 8000,
      DAEMON_MODE: 'embedded',
    };
  }

  return ZeroClawDaemon.getConstants();
}

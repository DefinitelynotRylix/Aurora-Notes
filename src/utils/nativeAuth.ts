/**
 * Native Android Screen Lock Integration via WebAuthn Platform Authenticator
 * Uses Android's native biometric (fingerprint/face) or system PIN/pattern lock screen.
 */

const NATIVE_CREDENTIAL_ID_KEY = 'iris_native_cred_id_v1';
const NATIVE_LOCK_ENABLED_KEY = 'iris_native_lock_enabled_v1';

export function isNativeLockEnabled(): boolean {
  try {
    return localStorage.getItem(NATIVE_LOCK_ENABLED_KEY) === 'true';
  } catch {
    return false;
  }
}

export function setNativeLockEnabled(enabled: boolean) {
  try {
    if (enabled) {
      localStorage.setItem(NATIVE_LOCK_ENABLED_KEY, 'true');
    } else {
      localStorage.removeItem(NATIVE_LOCK_ENABLED_KEY);
      localStorage.removeItem(NATIVE_CREDENTIAL_ID_KEY);
    }
  } catch {
    // ignore
  }
}

/**
 * Checks if the platform authenticator (Android Biometrics / Device Screen Lock PIN) is available
 */
export async function isNativePlatformAuthenticatorAvailable(): Promise<boolean> {
  if (
    typeof window !== 'undefined' &&
    window.PublicKeyCredential &&
    typeof window.PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable === 'function'
  ) {
    try {
      return await window.PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable();
    } catch {
      return false;
    }
  }
  return false;
}

/**
 * Enrolls Android's native device lock screen (Fingerprint / PIN / Pattern)
 */
export async function registerNativeDeviceLock(): Promise<{ success: boolean; error?: string }> {
  try {
    if (!window.PublicKeyCredential) {
      return { success: false, error: 'WebAuthn is not supported in this browser.' };
    }

    const challenge = new Uint8Array(32);
    crypto.getRandomValues(challenge);

    const userId = new Uint8Array(16);
    crypto.getRandomValues(userId);

    const credential = (await navigator.credentials.create({
      publicKey: {
        challenge,
        rp: {
          name: 'Iris Secure Notes',
          id: window.location.hostname,
        },
        user: {
          id: userId,
          name: 'iris-user',
          displayName: 'Iris User',
        },
        pubKeyCredParams: [
          { alg: -7, type: 'public-key' },   // ES256
          { alg: -257, type: 'public-key' }, // RS256
        ],
        authenticatorSelection: {
          authenticatorAttachment: 'platform', // Specifically Android/OS native device lock
          userVerification: 'required',        // Specifically Fingerprint, Face, or Device PIN
          residentKey: 'preferred',
        },
        timeout: 60000,
      },
    })) as PublicKeyCredential | null;

    if (credential) {
      const rawId = Array.from(new Uint8Array(credential.rawId))
        .map((b) => b.toString(16).padStart(2, '0'))
        .join('');
      localStorage.setItem(NATIVE_CREDENTIAL_ID_KEY, rawId);
      setNativeLockEnabled(true);
      return { success: true };
    }

    return { success: false, error: 'Device lock verification was not completed.' };
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : 'Failed to register Android native lock';
    return { success: false, error: msg };
  }
}

/**
 * Triggers Android's native device lock screen prompt (Fingerprint or PIN/Pattern)
 */
export async function verifyWithAndroidNativeLock(): Promise<{ success: boolean; error?: string }> {
  try {
    if (!window.PublicKeyCredential) {
      return { success: false, error: 'WebAuthn is not supported in this browser.' };
    }

    const storedIdHex = localStorage.getItem(NATIVE_CREDENTIAL_ID_KEY);
    const challenge = new Uint8Array(32);
    crypto.getRandomValues(challenge);

    const allowCredentials = storedIdHex
      ? [
          {
            id: new Uint8Array(
              storedIdHex.match(/.{1,2}/g)?.map((byte) => parseInt(byte, 16)) || []
            ),
            type: 'public-key' as const,
            transports: ['internal' as const],
          },
        ]
      : [];

    const assertion = await navigator.credentials.get({
      publicKey: {
        challenge,
        rpId: window.location.hostname,
        allowCredentials: allowCredentials.length > 0 ? allowCredentials : undefined,
        userVerification: 'required', // Triggers Android native fingerprint/PIN prompt
        timeout: 60000,
      },
    });

    if (assertion) {
      return { success: true };
    }

    return { success: false, error: 'Android native authentication failed.' };
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : 'Android native verification error';
    return { success: false, error: msg };
  }
}

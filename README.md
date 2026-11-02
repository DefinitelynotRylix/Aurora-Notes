# Iris Vault — Native Android (Jetpack Compose)

Iris Vault is a privacy-first, zero-knowledge, local-only note-taking app built natively for Android using **Kotlin** and **Jetpack Compose**.

## Architecture & Features

1. **Jetpack Compose UI**:
   - Modern declarative UI with Iris Liquid Glass visual system.
   - Dual theme support (Light and Dark mode) with Royal Violet (`#5925DC`) accents.
   - Multi-column responsive grid and list layouts.

2. **100% Maximized Full-Screen Note Editor**:
   - Maximized editor window designed for mobile phones.
   - **Auto-Save on Android Back Gesture**: Seamless back gesture / hardware back button handling with `BackHandler`.
   - **Sticky Mobile Keyboard Toolbar**:
     - Outline / bullet formatting.
     - 3 distinct, mutually-exclusive text sizes (Small, Medium, Large).
     - Thick / Bold and Italic toggles.
     - **Line-Isolated Checklist**:
       - Checkbox is permanently stationed at the `-1 character position`, preventing text from being typed behind it.
       - Exactly one checkbox per line.
       - Pressing Enter preserves the checkbox and starts a clean, disabled line (must be manually triggered per line).
       - Formatting options work whether the checkbox is active or not.

3. **Zero-Knowledge Security & AES-256-GCM**:
   - Authenticated encryption with AES-GCM and PBKDF2-SHA256 (100,000 rounds).
   - Local Room SQLite database storage with no remote cloud telemetry.
   - Built-in AES Cipher Inspector to audit ciphertext and IV payloads.

4. **Local Diagnostic Logs**:
   - Rolling 250-entry FIFO local diagnostics buffer.
   - Terminal viewer with ALL, ERROR, WARN, and SECURITY filters.
   - One-tap plain text export (`.txt`).

5. **Local Trash**:
   - Restoring, individual permanent shredding, and bulk Empty Trash with confirmation dialog.
   - Full real-time search across titles, body content, checklist items, and tags in trash.

## Building the Android APK

### Local Build:
```bash
# Build debug APK
./gradlew assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk
```

### GitHub Actions CI/CD:
The automated build workflow is configured in `.github/workflows/build-apk.yml`. On every push or pull request to `main`, GitHub Actions:
1. Sets up JDK 21.
2. Caches Gradle dependencies.
3. Assembles the debug APK with the `debug.keystore`.
4. Uploads the generated APK as a downloadable GitHub Actions artifact.

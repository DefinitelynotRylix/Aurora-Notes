# Aurora Notes — Native Android (Jetpack Compose)

Aurora Notes is a privacy-first,+

+
local-only note-taking app built natively for Android using **Kotlin** and **Jetpack Compose**.

## Architecture & Features

1. **Jetpack Compose UI**:
   - Modern declarative UI.
   - Dual theme support (Light & Dark & System mode) with Royal Violet (`#5925DC`) accents.
   - Multi-column responsive grid and list layouts.

2. **100% Maximized Full-Screen Note Editor**:
   - Maximized editor window designed for mobile phones.
   - **Auto-Save**
   - **Sticky Mobile Keyboard Toolbar**:
     - Outline / bullet formatting.
     - 3 distinct text sizes (Small, Medium, Large).
     - Thick / Bold and Italic toggles.
     - Line-Isolated Checklist

3. **Local Diagnostic Logs**:
   - Rolling 250-entry FIFO local diagnostics buffer.
   - Terminal viewer with ALL, ERROR, WARN, and SECURITY filters.
   - One-tap plain text export (`.txt`).

4. **Local Trash**:
   - Restoring, individual permanent shredding, and bulk Empty Trash with confirmation dialog.
   - Full real-time search across titles, body content, checklist items, and tags in trash.

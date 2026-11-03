# Aurora Notes — Native Android (Jetpack Compose)

Aurora Notes is a privacy-first, local-only note-taking app built natively for Android using **Kotlin** and **Jetpack Compose**.

> ### Care, Maintenance & Privacy Notice
> **Aurora Notes** has been created with the help of **Argon 4 Gemini**. This app receives its deserved care and ongoing maintenance while strictly respecting the user's privacy — all notes, checklists, categories, and diagnostic logs stay 100% locally on your device with zero tracking or telemetry. I am committed to maintaining a reliable, working app that is handled with care.

---

## Architecture & Features

1. **Jetpack Compose UI**:
   - Modern declarative UI with smooth fluid navigation bar and animated masonry/list layout transitions.
   - Dual theme support (Light, Dark & System mode) with Royal Violet (`#5925DC`) accents.
   - Multi-column responsive grid and list layouts with pinned notes kept at the top of the main Notes view.

2. **100% Maximized Full-Screen Note Editor**:
   - Maximized editor window designed for mobile phones.
   - **Auto-Save** on back navigation.
   - **Sticky Mobile Keyboard Toolbar**:
     - Outline / bullet formatting.
     - 3 distinct text sizes (Small, Medium, Large).
     - Thick / Bold and Italic toggles.
     - Line-Isolated Checklist.

3. **Read-Only Archive Menu**:
   - Press and hold one or multiple notes in the **Notes** menu to archive them in a single tap.
   - Archived notes are moved to the **Archive** menu and locked in **read-only mode** so their contents cannot be accidentally modified.
   - Archived notes retain quick **Restore** (to return them to active Notes for editing) and **Delete** (to move them to Trash) options both on the card and inside the read-only viewer.

4. **Local Diagnostic Logs**:
   - Rolling 250-entry FIFO local diagnostics buffer.
   - Terminal viewer with ALL, ERROR, WARN, and SECURITY filters.
   - One-tap plain text export.

5. **Local Trash**:
   - Restoring, individual permanent shredding, and bulk Empty Trash with confirmation dialog.
   - Full real-time search across titles, body content, checklist items, and tags in trash.

---

## Open Source License

Under GitHub's terms and standard copyright law, a public repository without a license file defaults to *"All Rights Reserved"*, meaning it is not legally open source unless a license is explicitly included.

To make **Aurora Notes** properly open source, this project is licensed under the permissive **[MIT License](./LICENSE)** — allowing anyone to inspect, use, modify, and distribute the code freely while preserving copyright and license notices.


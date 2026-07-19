# Web2App 🔗

**Turn any website into a home-screen app icon.** Pick a URL, choose or crop an icon, tune a
few options, and pin a real launcher shortcut — no browser chrome, no app store, no server.

Built by [DSK Music](https://dskmusic.com) — free tools for musicians since 2002.

<p>
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Material 3" src="https://img.shields.io/badge/Material%203-View%20Binding-4285F4?logo=materialdesign&logoColor=white">
  <img alt="Android" src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white">
  <img alt="Min SDK" src="https://img.shields.io/badge/minSdk-26-blue">
  <img alt="Personal project" src="https://img.shields.io/badge/license-Personal%20project-lightgrey">
</p>

<p>
  <img alt="Latest release" src="https://img.shields.io/github/v/release/dskmusic/web2app?include_prereleases&label=latest%20release">
  <img alt="Build status" src="https://img.shields.io/github/actions/workflow/status/dskmusic/web2app/build-release.yml?label=release%20build">
</p>

---

## ✨ Features

### 🔗 Shortcut creation
- Turn any URL into a real pinned home-screen shortcut (adaptive icon, not a bookmark)
- Pick an icon from the gallery, camera, an online image search (Pixabay), or the site's own
  favicon/PWA icon — **auto-detected** from `<link rel="icon">` and the Web App Manifest
- Interactive 1:1 crop (UCrop) plus a background color picker (presets, custom RGB/hex, or
  transparent) so the final icon always matches the launcher's adaptive-icon safe zone
- Share a URL from any app straight into Web2App to create a shortcut on the spot

### ⚙️ Per-shortcut options
- Force **light / dark / system** theme for that site's content, independent of the device theme
- **Rotation lock**, **desktop mode** (wide viewport + desktop user-agent), and **incognito mode**
- Each shortcut gets its **own WebView data directory** — cookies/localStorage/cache stay isolated
  between shortcuts instead of sharing one global browser profile
- The options you last used become the default for the next shortcut you create

### 🗂️ Shortcut manager
- Full list of everything you've created: edit in place, re-pin, or delete
- **Export/import as JSON** (with icons embedded as base64) — a single self-contained backup file,
  including your app settings (theme, language, default shortcut options)

### 🎨 App
- Material 3 UI with 8 themes (light, dark, blue, green, dark blue, dark green, AMOLED, system)
- Spanish/English, switchable independently of the system language
- **Self-updating** — checks GitHub Releases on launch (and on demand from Settings) and installs
  new builds without the Play Store

---

## 🛠️ Tech stack

- **Kotlin** + View Binding, single-module Android app
- `WebView` (+ `androidx.webkit`) for the actual site rendering, per-shortcut isolated
- `ShortcutManagerCompat` for pinning/updating launcher shortcuts, backed by a local JSON catalog
  (`ShortcutManager` itself keeps no queryable history)
- **UCrop** for interactive icon cropping, **OkHttp** for favicon/manifest/Pixabay fetching
- **GitHub Actions** for signed, on-demand release builds

---

## 📦 Installation

Download the latest APK from the [Releases](../../releases) page. The app checks for updates
automatically on launch, or manually from **Settings → Buscar actualizaciones**.

See `manual_github.md` (inside `app/src/main/assets/`) for how the release/auto-update pipeline
is set up.

---

*Made with ❤️ by DSK.*

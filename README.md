# 🎵 Samsonic

[![Android](https://img.shields.io/badge/Android-31%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-M3-purple.svg)](https://developer.android.com/jetpack/compose)
[![Licence: GPL v3](https://img.shields.io/badge/Licence-GPLv3-blue.svg)](LICENSE)

**Samsonic** is a native Android music streaming player tailored for self-hosted Subsonic and Navidrome servers, featuring a **Samsung One UI 9.0 spatial visual style (Glassmorphism)**.

**Samsonic** 是一款專為 Android 打造、採用 **三星 One UI 9.0 空間視覺風格 (Glassmorphism)** 的 Subsonic / Navidrome 自建音樂伺服器串流播放器。

---

## Features | 核心特色

- **Samsung One UI 9.0 Spatial Visual Design | 三星 One UI 9.0 空間視覺設計**  
  Personalised **Glassmorphism (frosted glass with dynamic sampling blur)** interface. Obsidian dark spatial colour palette with translucent floating panels featuring refined rim-stroke highlighting. **Bottom-heavy reachability**: Floating navigation bar, floating mini player, and large rounded card design optimised for effortless one-handed operation.  
  高度個人化的 **毛玻璃與動態採樣模糊** 介面。Obsidian 深色空間調色盤與半透明懸浮面板，帶有細緻的邊緣高光。底部優化懸浮導航列與大圓角卡片設計，極致單手操作體驗。

- **Subsonic & Navidrome Compatibility | Subsonic & Navidrome 相容**  
  Native support for Subsonic / Navidrome REST API authentication and audio streaming. Supports albums, artists, playlists, random shuffle, search, and dynamic synchronised lyrics.  
  原生支援 Subsonic / Navidrome REST API 認證與數據串流。支援專輯、歌手、歌單、隨機播放、搜尋與動態歌詞。

- **AndroidX Media3 (ExoPlayer) Audio Engine | AndroidX Media3 (ExoPlayer) 播放引擎**  
  High-quality, low-latency streaming with background playback, lock screen controls, system media notifications, and MediaSession integration.  
  高品質低延遲串流，原生背景播放、鎖屏控制、系統媒體通知與播放介面整合。

- **Modern Jetpack Compose Architecture | Jetpack Compose 全現代化架構**  
  Built with MVVM architecture, Coroutines / Flow reactive data streams, and Haze frosted glass rendering.  
  採用 MVVM 架構、Coroutines / Flow 響應式資料流與 Haze 毛玻璃渲染。

---

## Download & Installation | 下載與安裝

Please visit [GitHub Releases](https://github.com/TsengBoWei/SamSonic/releases) to download the latest `Samsonic.apk`.

請至 [GitHub Releases](https://github.com/TsengBoWei/SamSonic/releases) 下載最新發佈的 `Samsonic.apk` 進行安裝。

---

## Build & Setup | 開發與建置

### Requirements | 環境需求
- **Android Studio**: Ladybug / Jellyfish (or newer)
- **JDK**: 17 or higher
- **Minimum SDK**: Android 12 (API Level 31)
- **Target SDK**: Android 15 (API Level 35)

### Build Steps | 建置步驟
1. **Clone repository | 複製專案**:
   ```bash
   git clone https://github.com/TsengBoWei/SamSonic.git
   cd SamSonic
   ```
2. **Open in Android Studio | 開啟專案**:
   Select `Open` in Android Studio and select the `SamSonic` root directory.
3. **Build & Run | 編譯並執行**:
   Wait for Gradle Sync to complete, select your connected Android device or emulator, and click **▶️ Run**.

---

## Acknowledgements | 特別致謝

Sincere gratitude to the developers of these outstanding music applications for their design inspiration:  
特別感謝以下優秀音樂播放器與其開發團隊給予的靈感與啟發：

- **[Symfonium](https://symfonium.app/)**: Inspired the highly modular library interface and outstanding user experience.
- **Obsidian-Music**: Inspired the exquisite Obsidian dark visual aesthetics and refined colour palette.
- **UAPP (USB Audio Player PRO)**: Inspired the professional-grade playback controls and architectural design.

---

## Licence & Naming Guidelines | 授權條款與命名規範

This project is open-source under the **[GNU General Public License v3.0 (GPLv3)](LICENSE)**.  
Anyone is free to download, use, modify, and distribute this project. However, **any modifications or derivative works based on this project must also be open-sourced under GPLv3, adhering to the `Samsonic by <Author Name>` naming convention**.

This project adheres to the **`Samsonic by <Author Name>`** naming convention:
- **Official Repository Name**: `Samsonic by Tseng`
- **For Modifiers & Forkers**: Developers are warmly welcomed to fork and adapt this project! If you publicly release a modified version or derivative work, please name your project **`Samsonic by <Author Name>`** (e.g. `Samsonic by Alice`).

本專案採用 **[GNU General Public License v3.0 (GPLv3)](LICENSE)** 授權開源。  
任何人均可自由下載、使用、修改與散佈本專案，但**任何引用、修改或基於本專案開發的衍生作品，也必須強制以 GPLv3 條款公開開源，並遵循 `Samsonic by <Author Name>` 的命名規範**。

本專案採用 **`Samsonic by <Author Name>`** 的命名約定：
- **官方主專案名稱**：`Samsonic by Tseng`
- **給修改者與衍生者的聲明**：歡迎任何開發者 Fork 或修改本專案！如果您公開發佈修改版或衍生版本，請遵循相同的命名規範將您的作品命名為 **`Samsonic by <Author Name>`**（例如 `Samsonic by Alice`）。

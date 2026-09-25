# 🎵 Samsonic

[![Version](https://img.shields.io/badge/Version-v2.0.0-blue.svg)](https://github.com/TsengBoWei/SamSonic/releases)
[![Android](https://img.shields.io/badge/Android-31%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-M3-purple.svg)](https://developer.android.com/jetpack/compose)
[![Licence: GPL v3](https://img.shields.io/badge/Licence-GPLv3-blue.svg)](LICENSE)

**Samsonic** is a native Android music streaming player tailored for self-hosted Subsonic and Navidrome servers, featuring a **Samsung One UI 9.0 spatial visual style (Glassmorphism)** and audiophile-grade **Bit-Perfect / Exclusive USB DAC audio output**.

**Samsonic** 是一款專為 Android 打造、採用 **三星 One UI 9.0 空間視覺風格 (Glassmorphism)** 並支援發燒級 **Bit-Perfect / USB DAC 獨佔模式** 的 Subsonic / Navidrome 自建音樂伺服器串流播放器。

---

## Features | 核心特色

- **Samsung One UI 9.0 Spatial Visual Design | 三星 One UI 9.0 空間視覺設計**  
  Personalised **Glassmorphism (frosted glass with dynamic sampling blur)** interface. Obsidian dark spatial colour palette with translucent floating panels featuring refined rim-stroke highlighting, plus ambient glows and accent companion colours derived in the OKLCH colour space. **Bottom-heavy reachability**: Floating navigation bar, floating mini player and large rounded card design optimised for effortless one-handed operation. Long-press any setting for a callout hint explaining what it does.  
  高度個人化的 **毛玻璃與動態採樣模糊** 介面。Obsidian 深色空間調色盤與半透明懸浮面板，帶有細緻的邊緣高光，並以 OKLCH 色彩空間衍生環境光暈與輔助強調色。底部優化懸浮導航列與大圓角卡片設計，極致單手操作體驗。長按任一設定選項即可查看說明提示。

- **Audiophile Bit-Perfect & DSD Engine | 發燒級 Bit-Perfect 與 DSD 播放引擎**  
  Native Android 14+ (API 34+) bit-perfect USB DAC routing that bypasses the system mixer, with a custom integer AudioTrack for loss-free 24-bit and 32-bit PCM. An in-app windowed-sinc polyphase resampler handles sample rates the DAC does not support. Full DSD playback via in-app DSD-to-PCM, DoP (DSD over PCM) or native DSD (`ENCODING_DSD`), with automatic fallback and mute protection whenever output is not routed to the DAC. Now Playing shows audio format and output tiles with the exact resolution and hardware status in real time.  
  Android 14+ 原生 USB DAC Bit-Perfect 音訊直通（繞過系統混音器），自訂整數 AudioTrack 支援 24-bit / 32-bit PCM 無損輸出。內建高精度視窗 Sinc 多相重採樣，自動適配 DAC 不支援的採樣率。完整 DSD 輸出支援：PCM 轉換、DoP (DSD over PCM) 及原生 DSD，帶有備用降級鏈與旁路斷開靜音保護。播放畫面即時顯示音訊格式、輸出採樣率與硬體狀態。

- **Subsonic & Navidrome Compatibility | Subsonic & Navidrome 相容**  
  Native support for Subsonic / Navidrome REST API authentication and audio streaming. Supports albums, artists, playlists, random shuffle, search and dynamic synchronised lyrics.  
  原生支援 Subsonic / Navidrome REST API 認證與數據串流。支援專輯、歌手、歌單、隨機播放、搜尋與動態歌詞。

- **Offline Caching & Smart Pre-buffering | 離線快取與智慧預先載入**  
  Integrated Media3 `SimpleCache` for offline listening, scoped by server, account and track. Current and upcoming songs are cached automatically in the background, with a Wi-Fi-only option, customisable cache size limits and manual cache management.  
  整合 Media3 SimpleCache 實現背景音樂快取與離線播放（依伺服器、帳號與曲目區分），智慧預先載入當前與隨後曲目，可設定僅在 Wi-Fi 載入、限制快取容量與手動清理快取。

- **Multilingual Interface | 多語言介面**  
  Complete Chinese translation. Switch languages in-app under Settings > Appearance > Language; on Android 13+ the change fades in place without restarting the activity.  
  完整漢語系。可於「設定 > 外觀 > 語言」中切換，Android 13+ 無需重啟 Activity 即可即時切換。

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

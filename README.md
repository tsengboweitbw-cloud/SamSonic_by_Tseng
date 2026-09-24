# 🎵 SamSonic

[![Android](https://img.shields.io/badge/Android-28%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-M3-purple.svg)](https://developer.android.com/jetpack/compose)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

**SamSonic** 是一款專為 Android 打造、採用 **Samsung One UI 9.0 空間視覺風格 (Glassmorphism)** 的 Subsonic / Navidrome 自建音樂伺服器串流播放器。

---

## ✨ 核心特色 (Features)

- 🎨 **Samsung One UI 9.0 空間視覺設計**
  - 高度個人化的 **Glassmorphism (毛玻璃與動態採樣模糊)** 介面。
  - **Obsidian 深色空間調色盤** 與半透明懸浮面板，帶有細緻的邊緣高光。
  - **Bottom-Heavy 手持優化**：懸浮導航列、懸浮迷你播放器與大圓角卡片設計，極致單手操作體驗。
- 🎶 **Subsonic & Navidrome 相容**
  - 原生支援 Subsonic / Navidrome REST API 認證與數據串流。
  - 支援專輯、歌手、歌單、隨機播放、搜尋與動態歌詞。
- 🔊 **AndroidX Media3 (ExoPlayer) 播放引擎**
  - 高品質低延遲串流，原生背景播放、鎖屏控制、系統媒體通知與 MediaSession 整合。
- ⚡ **Jetpack Compose 全現代化架構**
  - 採用 MVVM 架構、Coroutines / Flow 響應式資料流與 Haze 毛玻璃渲染。

---

## 📥 下載與安裝 (Download)

請至 [GitHub Releases](https://github.com/TsengBoWei/SamSonic/releases) 下載最新發佈的 `SamSonic.apk` 進行安裝。

---

## 🛠️ 開發與建置 (Build & Setup)

### 環境需求
- **Android Studio**: Ladybug / Jellyfish (或更高版本)
- **JDK**: 17 或以上
- **Minimum SDK**: Android 9.0 (API Level 28)
- **Target SDK**: Android 15 (API Level 35)

### 建置步驟
1. **Clone 專案庫**：
   ```bash
   git clone https://github.com/TsengBoWei/SamSonic.git
   cd SamSonic
   ```
2. **開啟專案**：
   在 Android Studio 中選擇 `Open` 並選取 `SamSonic` 根目錄。
3. **編譯並執行**：
   等待 Gradle Sync 完成後，選擇連接的 Android 裝置或模擬器，點擊 **▶️ Run**。

---

## 🙏 特別致謝 (Acknowledgements)

特別感謝以下優秀音樂播放器與其開發團隊給予的靈感與啟發：

- **[Symfonium](https://symfonium.app/)**：提供高度模組化的音樂庫介面與優異的使用者體驗啟發。
- **Obsidian-Music**：啟發了極致質感的 Obsidian 暗色視覺美學與調色盤靈感。
- **UAPP (USB Audio Player PRO)**：提供了專業級音樂播放控制與架構設計靈感。

---

## 📄 授權條款 (License)

本專案採用 **[GNU General Public License v3.0 (GPLv3)](LICENSE)** 授權開源。
任何人均可自由下載、使用、修改與散佈本專案，但**任何引用、修改或基於本專案開發的衍生作品，也必須強制以 GPLv3 條款公開開源**。

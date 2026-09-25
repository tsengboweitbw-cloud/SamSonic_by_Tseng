# 專案概述：One UI 9 風格之 Subsonic 音樂播放器

## 1. 核心技術棧 (Tech Stack)
- 語言與 UI：Kotlin, Jetpack Compose
- 播放引擎：AndroidX Media3 (ExoPlayer)
- 後端通訊：Ktor 或 Retrofit (負責處理 Subsonic API)
- 架構：MVVM (Model-View-ViewModel)

## 2. 設計語言：Samsung One UI 9.0 (嚴格限制)
**【警告】絕對避免使用 Google Material 3 (M3) 的預設扁平化設計。**
所有的 UI 元件必須符合以下 One UI 9.0 的特徵：
- **毛玻璃與空間感 (Glassmorphism)**：大量使用原生的 `Modifier.blur()` 搭配半透明背景（Alpha 控制在 0.2-0.85）。包含底部導航、懸浮播放列。
- **懸浮與圓角 (Floating & Rounded)**：卡片與按鈕需使用大圓角（至少 24.dp），並帶有輕微且柔和的陰影（Soft Drop Shadows），製造深度的 Z 軸空間感。
- **底部操作區 (Bottom-heavy Reachability)**：確保所有核心互動按鈕都在畫面中下半部（方便單手操作），頂部保留大面積的彈性標題或留白。
- **沉浸式全螢幕 (Edge-to-Edge)**：必須開啟 `WindowCompat.setDecorFitsSystemWindows(window, false)`，讓畫面延伸到系統狀態列與導航列後方，使毛玻璃透色更明顯。

## 3. 版面佈局參考：Symfonium
- **主畫面**：頂部為可收縮大標題，中央為高度模組化的音樂庫網格（Grid）與列表。
- **懸浮播放列 (Mini Player)**：不貼死底部邊緣，必須是一個帶有 Padding、大圓角、毛玻璃效果的懸浮 Box。當背後清單滑動時，必須透出底下的專輯封面顏色。
- **全螢幕播放頁 (Now Playing)**：動態模糊的專輯封面作為背景，中央為大尺寸卡片封面，控制按鈕需符合 One UI 的大尺寸、高點擊容錯率設計。

## 4. 音訊進階規格 (Audio Features)
- **DAC 獨佔模式 (DAC Exclusive Mode) & Bit-Perfect**：
  - **Android 14 (API level 34) 以上**：優先使用 Android 14 原生 API（如 `AudioMixerAttributes` / Lossless Audio 特性）實現 Bit-Perfect 無損音訊與 USB DAC 獨佔輸出。
  - **Android 14 以下**：由於系統混音器 (AudioFlinger) 會重採樣，若需在舊版 Android 實現 DAC 獨佔與 Bit-Perfect，需規劃使用自訂 USB 驅動（例如 libusb / AAudio / 自製 USB 音訊驅動）直通硬體。這個暫不執行。
- **DSD 輸出 (DSD Output)**：待以 iFi hip-dac 實機檢查後再實作（暫不執行）。
  - **先檢查**：接上 hip-dac 後確認 (1) Android 是否對該 DAC 提供 Bit-Perfect 及支援的取樣率；(2) `getSupportedMixerAttributes` 是否回報 `ENCODING_DSD`（原生 DSD）；(3) 現有 Bit-Perfect PCM 播放時，DAC 指示燈是否顯示歌曲原始取樣率（如 44.1 kHz）。
  - **設定選項**（Settings → Playback「DSD output」）：
    - **轉為 PCM (Convert to PCM)**：目前的行為，App 內將 DSD 轉為 PCM；無 DSD DAC 時唯一選項。
    - **DoP**：將 DSD 封裝於 32-bit PCM（DSD64 → 176.4 kHz、DSD128 → 352.8 kHz、DSD256 → 705.6 kHz）。僅在 Bit-Perfect 生效時使用；DAC 不支援該取樣率時退回 PCM。
    - **原生 DSD (Native)**：僅在 Android 回報該 DAC 支援 DSD 時才顯示。
  - **注意**：Bit-Perfect / DoP 期間不得有任何 App 內 EQ、ReplayGain、音量或淡入淡出處理，否則會破壞資料。

## 5. AI 開發規範
1. **先假後真**：撰寫 Compose UI 期間，一律先使用 `MockData`（假資料）進行視覺驗證，確認無誤後再串接 Subsonic API。
2. **模組化**：不要寫超過 300 行的巨型檔案。將 UI 切割為獨立的 Component 函數。
3. **錯誤處理**：如果編譯失敗，我只會提供 Logcat 的關鍵錯誤片段，請直接針對錯誤修復，不要隨意重構無關的程式碼。

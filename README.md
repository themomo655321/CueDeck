# CueDeck 🎛️
### Compact Live Cue Trigger & Show Control for Android & Desktop

CueDeck is a mobile-first, live-performance cue triggering application engineered with **Compose Multiplatform (Kotlin)**. It combines the structured sequential workflow of **QLab** with the instant cart matrix of **SoundShow**, designed for rock-solid reliability in high-pressure theatrical, live event, podcast, and stage environments.

---

## 🌟 Key Highlights

### 1. Dual Performance Paradigms
- **Sequential Cue Stack (QLab style)**: Linear show flow with an armed standby card, running cue countdowns, auto-follow / auto-continue chaining, and a giant thumb-friendly **GO** button.
- **Soundboard Matrix (SoundShow style)**: Configurable multi-bank cart grid for instant one-shot SFX, background loops, stingers, and retriggering.

### 2. Stage Safety & Live Protections
- **Performance Lock Mode**: Single-tap lock that freezes all editing, reordering, deleting, and file picking. Only triggering controls (GO, Cart Pads, Panic Stop) stay active to eliminate accidental touches during a live show.
- **Hardware Volume Key Triggers**:
  - **Volume Up** $\rightarrow$ **GO** (fires the armed cue)
  - **Volume Down** $\rightarrow$ **Panic All Stop** (instant emergency cutoff)
- **Emergency Panic Stop**: High-contrast, always-accessible red button to immediately halt all active audio.
- **Android Foreground Service (`ShowPlaybackService`)**: Runs with an active `PARTIAL_WAKE_LOCK` so the device going to sleep or screen turning off never interrupts playback mid-show.

### 3. Rekordbox-Style Audio Management
- When an audio track is imported into a show, CueDeck copies the file into its internal managed storage (`shows/<show_id>/audio/`).
- Shows are completely self-contained; moving, renaming, or deleting files in your Downloads folder will **never** break your cues.

### 4. Optimized for Older Hardware (e.g., ADVAN Sketsa 2)
- **Minimum SDK 24** (Android 7.0 Nougat+) — fully compatible with Android 11 (API 30) and earlier budget hardware.
- **Lightweight Audio Engine**: Utilizes native Android `MediaPlayer` decoders and hardware audio pipelines to eliminate buffer underruns on quad-core chipsets (e.g. Unisoc Tiger T310).
- **Adaptive Tablet Layout**: Automatically detects widescreen devices (such as the 10.1" 1280x800 display on the ADVAN Sketsa 2) and renders a dual-pane booth layout (Cue List / Cart Grid on the left; Armed Cue, Active Countdown, and GO Button on the right).

---

## 🏗️ Project Architecture

```
mobile-cue-trigger-app/
├── gradle/
│   └── libs.versions.toml             # Centralized version catalog
├── shared/                             # Common Kotlin Multiplatform module
│   ├── src/commonMain/kotlin/com/cuedeck/
│   │   ├── model/                     # Cue, Show, CartBank, Enums, ActivePlayback
│   │   ├── audio/                     # AudioEngine abstraction
│   │   ├── storage/                   # Rekordbox-style ShowRepository
│   │   ├── viewmodel/                 # Central CueDeckViewModel
│   │   └── ui/                        # Adaptive Compose UI & Theme
│   │       ├── components/            # TopSafetyBar, GoButton, ArmedCueCard,
│   │       │                          # ActiveCuesDrawer, CueListSection,
│   │       │                          # CartGridSection, CueEditorModal
│   │       └── MainScreen.kt          # Dual-pane tablet & portrait phone layout
│   ├── src/androidMain/               # AndroidAudioEngine & ShowPlaybackService
│   └── src/desktopMain/               # DesktopAudioEngine (JavaSound) & PlatformStorage
├── androidApp/                         # Native Android entrypoint
│   └── src/main/kotlin/com/cuedeck/android/
│       └── MainActivity.kt            # Volume key interception & SAF file picker
└── desktopApp/                         # Windows/macOS/Linux desktop entrypoint
    └── src/jvmMain/kotlin/com/cuedeck/desktop/
        └── Main.kt                    # Instant PC testing runner with JFileChooser
```

---

## 🚀 Running & Building

### Running on Desktop (for instant PC testing)
To test UI, cue sequences, and audio playback immediately on your PC:
```bash
./gradlew :desktopApp:run
```

### Running on Android Device / Tablet (e.g. ADVAN Sketsa 2)
1. Enable **Developer Options** and **USB Debugging** on your Android device.
2. Connect the device via USB.
3. Build and install:
```bash
./gradlew :androidApp:installDebug
```
Or open the project folder in **Android Studio** and click **Run**.

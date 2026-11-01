# 📖 StoryFlip (Flipbook)

> Interactive Flipbook Document Reader & Cinematic Storyboard Studio with realistic 3D page curl physics.

A native Android application written in **Kotlin** with **Jetpack Compose**, which turns documents and scripts into a book-like reading experience — complete with a cover view, single/double page layouts, AI-ready read-aloud narration, a vocabulary notebook, and a Gemini-powered cinematic storyboard studio.

---

## Table of Contents

1. [Project Overview](#-project-overview)
2. [About the Application](#-about-the-application)
3. [Key Features](#-key-features)
4. [Screenshots](#-screenshots)
5. [Technology Stack](#-technology-stack)
6. [Project Architecture](#-project-architecture)
7. [Folder Structure](#-folder-structure)
8. [Prerequisites](#-prerequisites)
9. [Installation and Setup](#-installation-and-setup)
10. [Dependencies and Packages](#-dependencies-and-packages)
11. [How to Run the Application](#-how-to-run-the-application)
12. [How to Build the Application](#-how-to-build-the-application)
13. [Build Output Location](#-build-output-location)
14. [Configuration and Environment Variables](#-configuration-and-environment-variables)
15. [Permissions](#-permissions)
16. [Known Limitations](#-known-limitations)
17. [Future Improvements](#-future-improvements)
18. [Contributing](#-contributing)
19. [License](#-license)
20. [Author / Developer](#-author--developer)
21. [GitHub Repository](#-github-repository)

---

## 📋 Project Overview

**StoryFlip** is a document reader for Android that presents text content as a virtual book:

- Import **PDF, EPUB, DOCX or TXT** documents (or paste plain text) into a personal library.
- Read them with realistic **3D page-curl flip animation**, switching automatically between a single-page portrait layout and a double-page landscape spread as the device rotates.
- Listen to any book with an integrated **Text-to-Speech flying player** (multiple Indian and English languages, voice presets, live speed control, and voice commands).
- Build a personal **vocabulary notebook** by triple-tapping any word, then look it up in an online dictionary with an offline fallback.
- Convert a screenplay into a cinematic **storyboard** — broken down into scenes and shots with camera framing and movement — using the **Gemini API**, and export it as a PDF deck.

## 🔍 About the Application

| Property | Value |
| --- | --- |
| App display name | **StoryFlip** (`app_name` in `strings.xml`) |
| Package (`applicationId`) | `com.aistudio.storyflip.vxmpk` |
| Code namespace | `com.example` |
| Platform | Android (min SDK 24, target SDK 36) |
| Language | Kotlin 2.2.10 |
| UI framework | Jetpack Compose (Material 3) |
| Database | Room v2 (schema migrated 1 → 2) |
| AI service | Google Gemini (`gemini-3.5-flash`) via REST |
| Current version | 1.0 (versionCode 1) |

The project was originally generated in **Google AI Studio** and retains its signing/secrets conventions (documented below).

## ✨ Key Features

All features below are verified in the source code:

- **Document Library** — import PDF/EPUB/DOCX/TXT files from the device, paste text to create a book instantly, browse and delete documents; parsing runs asynchronously with a progress stage indicator and can be cancelled.
- **Flippable book cover** — an animated cover screen (`BookCoverView`) before entering any book.
- **Dual reader layouts** — single-page focus mode in portrait and a left/right double-page spread in landscape; the layout switches automatically when the device is physically rotated, with an optional recommendation banner.
- **3D page curl physics** — a shared page-flip engine (`PageFlipEngine.kt` + `rememberPageCurlState`) drives flips from gestures *and* buttons.
- **Reading themes** — Paper, Sepia and Midnight.
- **Typography options** — Literata, Merriweather, Georgia and Atkinson Hyperlegible, with adjustable font size (14–32 sp), highlight colours, page-turn sound and auto-bookmarking.
- **Text-to-Speech narration** — a floating reading player with equalizer animation, play/pause, previous/next page and **live speed control** (0.5×–2.0×).
- **Voice presets & languages** — Female / Male / Storyteller / Youthful presets (pitch-tuned) and locales for Hindi, Bengali, Tamil and English (India/UK/US).
- **Voice commands** — speech recognition in the long-press glass control panel (`RECORD_AUDIO` permission).
- **Long-press control panel** — a translucent glass panel for reader preferences, speed, voice preview and back navigation.
- **Vocabulary notebook** — triple-tap a word to save it; edit entries in a dedicated screen (`EditVocabularyScreen`).
- **Dictionary lookup** — online definitions via the [Free Dictionary API](https://dictionaryapi.dev) with a built-in lexical fallback when offline.
- **Storyboard Studio** — Gemini breaks a script into sequential scenes and shots, each annotated with shot type (Extreme Wide → Dutch Angle) and camera movement (Static → Crane/Boom). Custom shots can be added and the storyboard regenerated.
- **Graceful AI fallback** — without a configured Gemini key the app generates a **local heuristic storyboard breakdown** instead of failing.
- **OCR support** — Google ML Kit text recognition for Latin script and **Devanagari (Hindi)** with automatic recognition fallback.
- **PDF export** — export the storyboard deck as a PDF (`PdfExporter`).
- **Reading progress** — last-read page, bookmarks and auto-bookmark preference, persisted in Room.

## 🖼️ Screenshots

_No application screenshots are currently available in this repository. Only the launcher icon assets are included (at `app/src/main/res/mipmap-*` and `app/src/main/res/drawable/`). This section will be updated when real screenshots are added._

## 🛠️ Technology Stack

| Category | Technology |
| --- | --- |
| Language | Kotlin 2.2.10 |
| UI | Jetpack Compose (BOM 2024.09.00), Material 3, Material Icons Extended |
| Architecture | MVVM — `ReaderViewModel` + Kotlin `StateFlow`, unidirectional state flow |
| Persistence | Room 2.7.0 (via KSP) |
| AI | Google Gemini API (`gemini-3.5-flash`) called over OkHttp REST |
| Networking | OkHttp 4.10, Retrofit 2.12, Moshi (codegen), OkHttp Logging Interceptor |
| Document parsing | PDFBox Android 2.0.27.0, ML Kit Text Recognition 16.0.1 (Latin + Devanagari) |
| Images | Coil 2.7 |
| Async | Kotlin Coroutines |
| Secrets | Secrets Gradle Plugin 2.0.1 (`.env` convention) |
| Build | Android Gradle Plugin 9.1.1, Gradle 9.3.1, KSP 2.3.5 |
| Testing | JUnit 4, Robolectric 4.16.1, MockWebServer 4.10, Compose UI tests, Espresso |

## 🏗️ Project Architecture

The app follows a lightweight **MVVM** structure with a single `ReaderViewModel` exposing UI state as `StateFlow`:

```
┌─────────────────────────────────────────────────────┐
│  UI (Jetpack Compose)                               │
│  Library · Paste Text · Reader · Vocabulary ·       │
│  Storyboard Studio                                  │
└──────────────────────┬──────────────────────────────┘
                       │ StateFlow / events
┌──────────────────────▼──────────────────────────────┐
│  ReaderViewModel  (ui/)                             │
│  screen routing, layout modes, parsing progress,    │
│  preferences, storyboard state                      │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│  Services (service/)                                │
│  DocumentParserService · GeminiStoryboardService ·  │
│  TtsManager · DictionaryService · OcrService ·      │
│  PdfExporter · VocabularyManager · DemoCatalog      │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│  Data (data/)                                       │
│  Room: AppDatabase (v2) · DocumentDao · models      │
└─────────────────────────────────────────────────────┘
```

## 📂 Folder Structure

```
flipbook/
├── app/
│   ├── build.gradle.kts              # App module build config (Compose, Room, secrets)
│   ├── proguard-rules.pro
│   └── src/
│       ├── androidTest/              # Instrumented tests
│       ├── main/
│       │   ├── AndroidManifest.xml   # Permissions, launcher activity, FileProvider
│       │   ├── assets/fonts/         # NotoSansDevanagari-Regular.ttf (bundled font)
│       │   ├── java/com/example/
│       │   │   ├── MainActivity.kt   # Entry point & screen routing
│       │   │   ├── data/
│       │   │   │   ├── db/           # AppDatabase, DocumentDao
│       │   │   │   └── model/        # Room entities & reader models/enums
│       │   │   ├── service/          # Parsing, Gemini, TTS, OCR, dictionary, PDF
│       │   │   └── ui/               # Compose screens, reader views, theme
│       │   └── res/                  # Drawables, mipmaps, values, xml rules
│       └── test/                     # Robolectric & JUnit unit tests
├── assets/5_Flipbook.txt             # Sample script/document asset
├── build.gradle.kts                  # Root build file
├── settings.gradle.kts               # Repositories & modules (rootProject: StoryFlip)
├── gradle.properties                 # JVM args, caches, google-services passthrough
├── gradle/
│   ├── libs.versions.toml            # Version catalog (all dependencies)
│   └── wrapper/gradle-wrapper.properties
├── .env.example                      # Template for GEMINI_API_KEY
├── .gitignore
└── metadata.json                     # AI Studio project metadata
```

## ✅ Prerequisites

- **Android Studio** (recent version compatible with AGP 9.1.1 / Gradle 9.3.1) — *recommended*. The Gradle wrapper **JAR and launch scripts are not committed** to this repository, so CLI builds require either a local Gradle 9.3.1 installation or Android Studio's bundled Gradle.
- **JDK 11** for the app build; unit tests auto-provision a **JDK 21** toolchain via the foojay resolver (configured in `settings.gradle.kts`).
- An Android device or emulator running **Android 7.0 (API 24)** or later.
- *(Optional)* A **Gemini API key** for AI storyboard generation — the app works without one by falling back to local heuristic breakdowns.

## 📥 Installation and Setup

```bash
# 1. Clone the repository
git clone https://github.com/codingtutorialsjamshedpur/flipbook.git
cd flipbook

# 2. (Optional) Configure your Gemini API key — see Configuration section
cp .env.example .env      # then edit .env with your key
```

Then open the folder in **Android Studio** and let it sync the Gradle project. This is not a Flutter project, so no `flutter` commands apply.

## 📦 Dependencies and Packages

All dependencies are declared in the version catalog [`gradle/libs.versions.toml`](gradle/libs.versions.toml):

- **AndroidX / Compose** — `compose-ui`, `compose-material3`, `material-icons-extended`, `activity-compose`, `lifecycle-*`, `core-ktx`
- **Room** — `room-runtime`, `room-ktx`, `room-compiler` (KSP)
- **AI / Networking** — OkHttp, Retrofit + Moshi converter, Gemini via REST; Firebase BoM with `firebase-ai` and App Check modules wired in
- **Documents** — PDFBox Android, ML Kit Text Recognition (Latin & Devanagari variants)
- **Testing** — JUnit, Robolectric, MockWebServer, Compose UI test JUnit4, Espresso

Note: several optional dependencies (CameraX, Accompanist, Navigation Compose, DataStore, Firestore, Firebase Auth/Credentials) are commented out in `app/build.gradle.kts` and can be re-enabled as needed.

## ▶️ How to Run the Application

**Option A — Android Studio (recommended)**
1. Open the cloned folder in Android Studio and wait for the Gradle sync to finish.
2. Select a device/emulator (API 24+).
3. Press **Run ▶** (installs the `debug` build, which is signed automatically with the bundled-style `debug.keystore` convention).

**Option B — Command line** (requires a local Gradle 9.3.1 installation, since the wrapper JAR is not committed):

```bash
gradle :app:installDebug
```

## 🏭 How to Build the Application

```bash
# Assemble a debug APK
gradle :app:assembleDebug

# Assemble a release APK (requires release signing configuration, see below)
gradle :app:assembleRelease

# Run the Robolectric/JUnit unit test suite (Java 21 toolchain is auto-provisioned)
gradle :app:testDebugUnitTest
```

**Release signing** — the release build type expects an upload keystore via environment variables:

| Variable | Purpose |
| --- | --- |
| `KEYSTORE_PATH` | Path to the upload keystore (defaults to `<root>/my-upload-key.jks` if not set) |
| `STORE_PASSWORD` | Keystore password |
| `KEY_PASSWORD` | Key password (alias `upload`) |

This is the standard AI Studio release-signing convention; no actual keystores or passwords are committed to this repository (`debug.keystore` and `*.jks` are gitignored).

## 📁 Build Output Location

| Build type | Output path |
| --- | --- |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| Release APK | `app/build/outputs/apk/release/` |
| Unit test reports | `app/build/unit-test-tmp/` (temp dir) and standard test reports |

## ⚙️ Configuration and Environment Variables

The project uses the **Secrets Gradle Plugin** with the web-style `.env` convention:

- `secrets.propertiesFileName = ".env"` — your private local file (gitignored)
- `defaultPropertiesFileName = ".env.example"` — committed placeholder template

| Variable | Required | Purpose |
| --- | --- | --- |
| `GEMINI_API_KEY` | Optional | Enables real Gemini storyboard generation. If missing or left as the placeholder `MY_GEMINI_API_KEY`, the app silently uses a local heuristic script breakdown instead. |

Steps:

1. Copy `.env.example` to `.env` in the project root.
2. Replace `MY_GEMINI_API_KEY` with a key from [Google AI Studio](https://aistudio.google.com).
3. Rebuild the app — the value is injected into `BuildConfig` at compile time.

Also note: `google-services.json` is **not committed**; `gradle.properties` sets `googleServices.missing.passthrough=true` so the build completes with a warning instead of failing.

## 🔐 Permissions

Declared in `app/src/main/AndroidManifest.xml`:

| Permission | Why |
| --- | --- |
| `INTERNET` | Dictionary API lookups and Gemini storyboard generation |
| `ACCESS_NETWORK_STATE` | Detect connectivity for online/offline fallbacks |
| `RECORD_AUDIO` | Voice commands via the speech recognizer in the control panel |

## ⚠️ Known Limitations

- The Gradle wrapper JAR and `gradlew` scripts are not committed — CLI builds need a local Gradle install or Android Studio.
- `google-services.json` is absent, so Firebase-dependent features (App Check etc.) are not fully configured for release distribution even though the dependencies are wired in.
- Release builds require providing your own upload keystore and signing environment variables.
- Storyboard generation depends on the external Gemini API rate limits/latency (30 s connect / 60 s read timeouts are configured).
- Import support for EPUB/DOCX exists in the parser, but heavily styled/DRM-protected files may not convert cleanly.
- No automated UI tests cover every screen; test coverage focuses on services, view models and reader interactions.

## 🚀 Future Improvements

- Add the Gradle wrapper JAR and scripts for friction-free CLI builds.
- Complete Firebase setup (config `google-services.json`, optional Auth/Firestore — dependencies are already present but commented where unused).
- Add real screenshots and a demo video to this README.
- Re-enable Navigation Compose for multi-screen routing instead of the current manual screen state.
- Support additional document formats and in-app file previews in the library.
- Cloud sync of library progress and vocabulary once Firebase is configured.

## 🤝 Contributing

Contributions are welcome!

1. Fork the repository and create a feature branch.
2. Keep the code style consistent (official Kotlin code style; `kotlin.code.style=official` is set in `gradle.properties`).
3. Add or update unit tests for behaviour changes (`gradle :app:testDebugUnitTest`).
4. Submit a pull request with a clear description.

Please do not commit secrets (`.env`, keystores, `google-services.json` or `local.properties`) — they are gitignored on purpose.

## 📄 License

This project currently has **no LICENSE file** in the repository. Until a license is added, the default applies: **the author retains all rights**, and others may not copy or redistribute the code without permission. If you are the author, consider adding a LICENSE (e.g. MIT or Apache-2.0) to clarify usage.

## 👨‍💻 Author / Developer

**codingtutorialsjamshedpur** — GitHub: [@codingtutorialsjamshedpur](https://github.com/codingtutorialsjamshedpur)

## 🔗 GitHub Repository

👉 **https://github.com/codingtutorialsjamshedpur/flipbook**

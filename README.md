# GBA Studio

**GBA Studio** is a local-first Android development environment for creating, studying, porting and testing Game Boy Advance software.

The primary development and usability target is the Lenovo Yoga Tab Plus. The interface is designed to adapt to other Android devices too.

## Project goals

- Native Android interface optimized for tablets and compact screens.
- C and C++ development workflow targeting the GBA ARM7TDMI.
- Local ARM toolchain and GBA ROM generation.
- ROM inspection, ARM/Thumb disassembly and research-oriented reconstruction.
- Asset tools for sprites, tiles, palettes, maps and audio.
- Porting workspace for projects whose source and licenses permit adaptation.
- Integrated emulator and debugging tools.
- Optional ChatGPT assistance with explicit project-context permissions.
- Built-in practical tutorials for beginners.

## Current status: Android foundation

The initial foundation includes:

- Kotlin and Jetpack Compose application shell.
- Responsive navigation for compact and tablet layouts.
- Introductory tutorial screen.
- Local GBA ROM header analyzer using Android's document picker.
- Header metadata extraction and checksum validation with JVM unit tests.
- Placeholder module screens that clearly identify planned functionality.
- CMake/NDK native library and JNI bootstrap function.
- Android minimum SDK 29, Java 17 and compile SDK 35.

**Important:** compiler, ROM analyzer, decompiler, porting tools, asset editors, emulator and ChatGPT API connection are not implemented yet. The current UI is a foundation, not a finished IDE.

## Build requirements

- Android Studio with Android SDK 35.
- JDK 17.
- Android NDK (side-by-side).
- CMake 3.22.1.
- Gradle 8.9, as required by Android Gradle Plugin 8.7.3.

Open this repository in Android Studio, allow Gradle sync, install the requested SDK/NDK components, then run the app configuration on an Android device or emulator.

## Architecture

- app/src/main/java: Kotlin and Compose UI.
- app/src/main/cpp: native C++ core and JNI boundary.
- core (planned): compiler, ROM analysis, disassembly, porting, assets, emulation and debugging modules.

Native tools will be integrated as pinned, license-reviewed dependencies. Core workflows are intended to work offline. ChatGPT features will be optional and require network access and separately configured API access; no API secret will be embedded in the APK.

## Legal notes

Only import, modify, decompile or distribute software and ROMs that you own or are authorized to analyze. GBA Studio will not bundle commercial ROMs, proprietary game assets or copyrighted source code.

Third-party components will retain their own licenses and notices.

## License

Original project code is distributed under the MIT License. Third-party dependencies are governed by their respective licenses.

# GBA Studio

**GBA Studio** is a local-first Android workspace for developing and studying Game Boy Advance software. The interface is designed for tablets and compact Android devices, with the Lenovo Yoga Tab Plus as the primary usability target.

## Current implementation

- Kotlin + Jetpack Compose application shell and responsive navigation.
- Introductory tutorial.
- Local project manager: create, open, rename and delete projects.
- Basic C/C++ and text editor with file creation, lightweight syntax highlighting, manual save and autosave.
- ZIP project import/export through Android's Storage Access Framework, with path traversal and uncompressed-size checks.
- GBA ROM header analyzer, checksum validation and paged hexadecimal viewer.
- ARM/Thumb disassembler for a documented subset of common instructions; unknown opcodes are displayed as data.
- C/C++ quick diagnostics for common structural mistakes. These are heuristics, not compiler diagnostics.
- Experimental raw-section ROM image builder with named symbols and ABS32, REL32, ARM_BRANCH24 and THUMB_BRANCH11 relocations, plus GBA memory-region placement and GNU ld memory-map script.
- Asset Studio image import and conversion to 8x8 4bpp tile data, with transparent index 0 and deterministic frequency-based BGR555 palette reduction.
- Experimental ARM7TDMI instruction stepper with a small ARM/Thumb instruction subset.
- CMake/NDK native library and JNI bootstrap.
- JVM tests for ROM headers, disassembly, diagnostics, raw ROM image generation and palette quantization.

## Important limitations

This is an evolving development build, not yet a complete GBA IDE.

- **Compiler:** a bare-metal ARM7TDMI C/C++ toolchain is not integrated yet.
- **Linker:** the in-app builder links raw sections, resolves explicitly supplied symbols and applies four relocation types. It does not parse ELF object files, support the complete ARM relocation ABI or provide a complete compiler toolchain. The GNU ld memory-map script is an initial integration artifact.
- **ROM bootability:** the experimental image builder does not include the GBA BIOS logo/header template or startup runtime. Its output is not guaranteed to boot on hardware or in an emulator.
- **Disassembler:** decoding coverage is partial; unknown instructions remain \`.word\` / \`.hword\`.
- **Diagnostics:** fast heuristic checks only; use a real compiler for authoritative errors.
- **Asset Studio:** converts raster images to tiled 4bpp and BGR555 palette data. When an image exceeds 15 opaque colors, a deterministic frequency-based palette reduction maps excess colors to the nearest selected color. Tilemaps, animation, dithering and advanced palette optimization remain future work.
- **Emulator:** currently a CPU instruction stepper for a small subset. It does not emulate the PPU/video, audio, DMA, timers, interrupts, memory-mapped peripherals or full ARM7TDMI behavior. It cannot yet run complete GBA games.
- **ChatGPT:** API integration is not connected yet.

The Android build has not yet been confirmed green on CI or tested on physical hardware. No APK release is claimed by this repository state.

## Build requirements

- Android Studio with Android SDK 35.
- JDK 17.
- Android NDK side-by-side.
- CMake 3.22.1.
- Gradle 8.9 / Android Gradle Plugin 8.7.3.

Open the project in Android Studio, sync Gradle, install the requested SDK/NDK components and run the app on an Android device or emulator.

## Repository layout

- \`app/src/main/java\`: Kotlin and Compose UI.
- \`app/src/main/cpp\`: native C++ core and JNI boundary.
- \`toolchain/gba/gba.ld\`: initial GNU ld memory map for GBA.
- \`docs/ROADMAP.md\`: implementation roadmap.

## Legal notes

Only inspect, modify, decompile or distribute software and ROMs that you own or are authorized to analyze. The repository does not bundle commercial ROMs or proprietary game assets. Third-party dependencies must retain their own license notices.

## License

Original project code is distributed under the MIT License. Third-party components are governed by their respective licenses.

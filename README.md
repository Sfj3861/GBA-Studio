# GBA Studio

**GBA Studio** is a local-first Android workspace for developing and studying Game Boy Advance software. The interface is designed for tablets and compact Android devices, with the Lenovo Yoga Tab Plus as the primary usability target.

## Current implementation

- Kotlin + Jetpack Compose application shell and responsive navigation.
- Introductory tutorial.
- Local project manager: create, open, rename and delete projects.
- Basic C/C++ and text editor with file creation, lightweight syntax highlighting, manual save and autosave.
- ZIP project import/export through Android's Storage Access Framework, with path traversal and uncompressed-size checks.
- GBA ROM header analyzer: complement checksum, fixed byte, entry branch and logo-data presence checks, plus a paged hexadecimal viewer (logo bytes are not verified against BIOS data).
- ARM/Thumb disassembler for a documented subset of common instructions, including several immediate/register transfers, rotated ARM immediates and branch targets; unknown opcodes are displayed as data.
- C/C++ quick diagnostics for common structural mistakes. These are heuristics, not compiler diagnostics.
- Experimental raw-section ROM image builder with named symbols, ABS32/REL32/ARM_BRANCH24/THUMB_BRANCH11 relocations, overlap/range/alignment checks, a GBA entry branch and validated header fields. An optional 156-byte logo block can be supplied by the user; no logo art is bundled.
- Asset Studio image import (bounded to 4 megapixels), conversion to 8x8 4bpp tiles, transparent index 0, deterministic BGR555 palette reduction, tile deduplication, GBA screenblock maps, reconstructed map preview and one-click ZIP export with raw files plus generated C arrays/header.
- Experimental ARM7TDMI instruction stepper with a small ARM/Thumb arithmetic/branch subset, N/Z/C/V flags, ARM condition evaluation, BX ARM/Thumb state changes and partial ROM/EWRAM/IWRAM/IO/palette/VRAM/OAM memory regions. IO register side effects, DMA, timers and a complete bus are not implemented.
- Experimental mode 0 PPU renderer for BG0–BG3 text backgrounds, scroll, screenblocks, 4bpp/8bpp tiles and palette transparency, with a 240×160 preview. OBJ rendering, bitmap modes, blending, windows and scanline timing remain future work.
- Sprite Studio slices sprite sheets into legal GBA OBJ dimensions, shares a 4bpp palette, generates OAM attributes and exports frame data/durations as C arrays. Frames reuse one VRAM slot; a visual OAM canvas and playback preview remain future work.
- Audio Studio imports bounded RIFF/WAVE PCM (mono/stereo, 8/16-bit), downmixes and resamples to mono unsigned 8-bit samples, and exports C arrays or normalized WAV. ADPCM, looping and emulator playback are not implemented.
- Porting analyzer scans project C/C++ sources for common desktop-only APIs and portability risks; findings are heuristic and do not certify compatibility.
- Debugger controller adds PC breakpoints, bounded execution traces and mapped-memory hex reads around the experimental CPU stepper.
- ChatGPT foundation includes Android Keystore AES-GCM encrypted local API-key storage, explicit allow-list project-context construction with size limits and credential-file exclusions, and an approval gate for proposed file changes. No network/API requests are implemented yet.
- CMake/NDK native library and JNI bootstrap.
- JVM tests for ROM headers, disassembly, diagnostics, raw ROM image generation, palette quantization and CPU-stepper behavior.

## Important limitations

This is an evolving development build, not yet a complete GBA IDE.

- **Compiler:** a bare-metal ARM7TDMI C/C++ toolchain is not integrated yet.
- **Linker:** the in-app builder links raw sections, resolves explicitly supplied symbols and applies four relocation types. It does not parse ELF object files, support the complete ARM relocation ABI or provide a complete compiler toolchain. The GNU ld memory-map script is an initial integration artifact.
- **ROM bootability:** the image builder writes the entry branch and standard metadata/checksum fields and accepts a caller-supplied 156-byte logo block. It does not verify that logo against the BIOS reference, nor generate startup/runtime code or initialize RAM sections. Its output is not guaranteed to boot on hardware or in an emulator.
- **Disassembler:** decoding coverage is partial; unknown instructions remain \`.word\` / \`.hword\`.
- **Diagnostics:** fast heuristic checks only; use a real compiler for authoritative errors.
- **Asset Studio:** converts raster images to tiled 4bpp and BGR555 palette data. When an image exceeds 15 opaque colors, a deterministic frequency-based palette reduction maps excess colors to the nearest selected color. It can deduplicate tiles, build hardware-ordered screenblock maps, preview the reconstructed map, and export a ZIP with typed `asset.c` / `asset.h` arrays. Sprite/OAM editing, animation, audio, dithering and advanced palette optimization remain future work.
- **Emulator:** still experimental and cannot run complete GBA games. CPU instruction coverage is partial; memory regions now include ROM, EWRAM, IWRAM, IO storage, palette RAM, VRAM and OAM, but IO side effects, BIOS, DMA, timers, interrupts, audio and cartridge peripherals are not implemented. The software PPU preview currently renders mode 0 text backgrounds only; OBJ sprites, bitmap modes, blending, windows and scanline timing are absent. Debugging adds PC breakpoints, bounded traces and mapped-memory reads.
- **ChatGPT:** only secure local configuration/context/change-review foundations exist. There is no OpenAI API client, network transport, chat UI, token streaming, rate-limit handling or provider request yet.

GitHub Actions runs JVM unit tests and assembles a debug APK for branch pushes. Check the latest workflow run to confirm the status of the current head; physical-device testing remains required. No release APK is claimed by this repository state.

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

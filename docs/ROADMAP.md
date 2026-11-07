# Development roadmap

## Milestone 0 — Android foundation
- [x] Kotlin/Compose application shell
- [x] Responsive compact/tablet navigation
- [x] Introductory tutorial
- [x] Native C++/JNI bootstrap
- [x] Green CI build and debug APK artifact generation
- [ ] Physical-device verification

## Milestone 1 — Project workspace
- [x] Project creation, open, rename and delete
- [x] Storage Access Framework ZIP import/export
- [x] Project manifest and GBA C starter template
- [ ] Persistent tutorial progress

## Milestone 2 — Code and compiler
- [x] Basic C/C++/text editor with file creation and autosave
- [x] Basic syntax highlighting
- [x] First-pass C/C++ structural diagnostics (heuristic)
- [ ] Clang-based diagnostics and compiler error mapping
- [ ] Code completion and symbol navigation
- [ ] ARM7TDMI bare-metal compiler integration
- [x] Initial GNU ld GBA memory-map script
- [x] Initial raw-section symbol resolution and ABS32/REL32/ARM_BRANCH24/THUMB_BRANCH11 relocations
- [x] Relocation overlap, address-range and alignment validation
- [x] Initial GBA header writer with entry branch, fixed byte and complement checksum
- [ ] ELF object parsing and complete ARM relocation support
- [ ] Startup code, runtime libraries and valid ROM header pipeline
- [ ] Reproducible end-to-end build of a homebrew ROM

## Milestone 3 — ROM inspection
- [x] ROM header parser, complement checksum and fixed-byte validation
- [x] Entry branch decoding and logo-data presence reporting (not BIOS-logo verification)
- [x] Read-only paged hexadecimal viewer
- [x] Initial ARM/Thumb disassembler for a limited instruction subset, including immediate transfers, ARM rotated immediates and ARM LDM/STM addressing modes
- [ ] Complete ARM7TDMI ARM/Thumb decoding coverage
- [ ] Symbols, cross-references and function analysis
- [ ] Export analysis reports

## Milestone 4 — Assets and porting
- [x] Raster image import
- [x] 8x8 4bpp tile conversion
- [x] BGR555 palette export
- [x] Tile deduplication and hardware-ordered screenblock tilemap generation
- [x] Reconstructed tilemap preview and ZIP bundle export with generated C arrays/header
- [x] Sprite-sheet slicing into GBA OBJ frames with shared 4bpp palette and OAM attributes
- [x] Basic animation frame metadata and generated C arrays/OAM tables
- [x] PCM WAV import (mono/stereo, 8/16-bit), mono 8-bit resampling and C/WAV export
- [ ] Interactive tilemap editor (generation, screenblock preview and export are implemented)
- [ ] Visual sprite canvas, drag-and-drop OAM placement and live animation preview
- [ ] ADPCM compression, looping metadata and in-emulator audio playback
- [x] Basic deterministic 15-color palette reduction for 4bpp assets (source images capped at 4 megapixels)
- [ ] Dithering and advanced palette optimization
- [x] Initial source compatibility report for common desktop APIs, filesystem and runtime patterns
- [ ] Compiler-backed compatibility analysis and automated source transformations

## Milestone 5 — Emulation and debugging
- [x] Experimental ARM/Thumb stepper with condition flags, BX state switching, MUL/MLA, selected loads/stores, stack operations, Thumb multiple-register transfer and ARM LDM/STM
- [x] Initial ROM/EWRAM/IWRAM/IO/palette/VRAM/OAM memory regions (register side effects and DMA are not modeled)
- [ ] Full ARM7TDMI execution and complete GBA memory bus (current ROM/EWRAM/IWRAM implementation is partial)
- [x] Software renderer for mode 0 text backgrounds BG0–BG3, scroll, screenblocks, 4bpp/8bpp tiles and palette transparency
- [ ] OBJ/OAM rendering, bitmap modes, windows, blending and scanline timing
- [ ] Timers, DMA, interrupts, keypad and memory-mapped peripherals
- [ ] Audio engine
- [ ] License-reviewed full GBA emulator core integration
- [x] Debugger controller with PC breakpoints, bounded execution trace and mapped-memory hex reads
- [ ] Conditional breakpoints, watchpoints, editable register panel and symbol-aware disassembly

## Milestone 6 — ChatGPT integration
- [x] Local API-key settings foundation encrypted with Android Keystore AES-GCM
- [x] Encrypted local secret storage; no embedded API keys
- [x] Explicit allow-list context builder with size limits and credential-file exclusions
- [x] Change proposal approval gate before project file writes
- [x] Existing local tools remain usable without AI

## Quality gates

A milestone is not complete because a screen exists. Each feature needs implementation, automated tests where practical, device testing, clear limitations and updated documentation.

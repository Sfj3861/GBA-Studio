# Development roadmap

## Milestone 0 — Android foundation
- [x] Kotlin/Compose application shell
- [x] Responsive compact/tablet navigation
- [x] Introductory tutorial
- [x] Native C++/JNI bootstrap
- [ ] Green CI build and physical-device verification

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
- [x] Initial ARM/Thumb disassembler for a limited instruction subset, including immediate transfers and ARM rotated immediates
- [ ] Complete ARM7TDMI ARM/Thumb decoding coverage
- [ ] Symbols, cross-references and function analysis
- [ ] Export analysis reports

## Milestone 4 — Assets and porting
- [x] Raster image import
- [x] 8x8 4bpp tile conversion
- [x] BGR555 palette export
- [x] Tile deduplication and hardware-ordered screenblock tilemap generation
- [x] Reconstructed tilemap preview and ZIP bundle export with generated C arrays/header
- [ ] Interactive tilemap editor (generation, screenblock preview and export are implemented)
- [ ] Sprite/OAM editor and animation timeline
- [ ] Audio conversion pipeline
- [x] Basic deterministic 15-color palette reduction for 4bpp assets (source images capped at 4 megapixels)
- [ ] Dithering and advanced palette optimization
- [ ] Source project compatibility report and porting helpers

## Milestone 5 — Emulation and debugging
- [x] Experimental ARM/Thumb stepper with condition flags, BX state switching, MUL/MLA, selected loads/stores, stack operations and multiple-register transfer
- [x] Initial ROM/EWRAM/IWRAM data bus for the supported instruction subset
- [ ] Full ARM7TDMI execution and complete GBA memory bus (current ROM/EWRAM/IWRAM implementation is partial)
- [ ] GBA PPU/video rendering
- [ ] Timers, DMA, interrupts, keypad and memory-mapped peripherals
- [ ] Audio engine
- [ ] License-reviewed full GBA emulator core integration
- [ ] Breakpoints, logs and memory inspection

## Milestone 6 — ChatGPT integration
- [ ] Optional OpenAI API configuration
- [ ] Secure secret storage; no embedded API keys
- [ ] Explicit per-project context sharing
- [ ] Tool permission prompts and diff review
- [ ] Offline fallback for non-AI workflows

## Quality gates

A milestone is not complete because a screen exists. Each feature needs implementation, automated tests where practical, device testing, clear limitations and updated documentation.

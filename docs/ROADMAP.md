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
- [ ] ELF linking, symbol resolution and relocation support
- [ ] Startup code, runtime libraries and valid ROM header pipeline
- [ ] Reproducible end-to-end build of a homebrew ROM

## Milestone 3 — ROM inspection
- [x] ROM header parser and checksum validation
- [x] Read-only paged hexadecimal viewer
- [x] Initial ARM/Thumb disassembler for a limited instruction subset
- [ ] Complete ARM7TDMI ARM/Thumb decoding coverage
- [ ] Symbols, cross-references and function analysis
- [ ] Export analysis reports

## Milestone 4 — Assets and porting
- [x] Raster image import
- [x] 8x8 4bpp tile conversion
- [x] BGR555 palette export
- [ ] Tilemap editor and preview
- [ ] Sprite/OAM editor and animation timeline
- [ ] Audio conversion pipeline
- [ ] Palette optimization and dithering
- [ ] Source project compatibility report and porting helpers

## Milestone 5 — Emulation and debugging
- [x] Experimental ARM/Thumb instruction stepper
- [ ] Full ARM7TDMI execution and memory bus
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

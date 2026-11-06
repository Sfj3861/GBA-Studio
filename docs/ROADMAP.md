# Development roadmap

## Milestone 0 — Android foundation
- [x] Kotlin/Compose application shell
- [x] Responsive compact/tablet navigation
- [x] Introductory tutorial
- [x] Native C++/JNI bootstrap
- [ ] Verify Android build on CI and Lenovo Yoga Tab Plus

## Milestone 1 — Project workspace
- [x] Project creation, open, rename and delete
- [x] Storage Access Framework ZIP import/export
- [x] Project manifest and GBA C starter template
- [ ] Persistent tutorial progress

## Milestone 2 — Code and compiler
- [x] Basic C/C++/text editor with file creation and autosave
- [x] Basic C/C++ syntax highlighting
- [ ] Code completion and compiler diagnostics
- [ ] Local ARM/Thumb cross-toolchain integration
- [ ] GBA linker script and startup/runtime support
- [ ] Build output, diagnostics and reproducible builds
- [ ] Build a minimal homebrew ROM

## Milestone 3 — ROM inspection
- [x] ROM header parser and validation (metadata fields and header checksum)
- [x] Read-only paged hexadecimal viewer for the selected ROM
- [ ] ARM/Thumb disassembly
- [ ] Symbols, cross-references and function analysis
- [ ] Export analysis reports
- [x] JVM unit tests for header parsing and checksum

## Milestone 4 — Assets and porting
- [ ] Sprite, tile, palette and tilemap tools
- [ ] Supported graphics/audio conversion pipelines
- [ ] Source project compatibility report
- [ ] Porting checklist and helper transformations

## Milestone 5 — Emulation and debugging
- [ ] Integrate a license-compatible GBA emulator core
- [ ] Run local homebrew ROMs
- [ ] Breakpoints, logs and memory inspection where supported

## Milestone 6 — ChatGPT integration
- [ ] Optional OpenAI API configuration
- [ ] Secure secret storage; no embedded API keys
- [ ] Explicit per-project context sharing
- [ ] Tool permission prompts and diff review
- [ ] Offline fallback for all non-AI workflows

## Quality gates

A milestone is not complete because a screen exists. Each feature needs implementation, automated tests where practical, device testing, clear limitations and updated documentation.

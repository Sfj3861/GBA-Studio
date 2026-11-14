# GBA ARM7TDMI linker layout

`gba.ld` is the GNU ld memory map for a bare-metal ARM7TDMI GBA executable.

- ROM: `0x08000000`, up to 32 MiB.
- EWRAM: `0x02000000`, 256 KiB.
- IWRAM: `0x03000000`, 32 KiB.
- `.data` and `.iwram` have ROM load addresses and RAM execution addresses.
- `.bss` is NOLOAD and the script exports startup-copy/clear symbols.

The Android NDK toolchain used to build GBA Studio is an Android toolchain, not a ready-made GBA SDK. A real build pipeline still needs an ARM7TDMI bare-metal compiler/binutils (or a verified LLVM equivalent), ELF object parsing, full ARM relocation coverage, startup code, a valid GBA header/logo block and runtime libraries.

The in-app experimental linker currently places raw sections, resolves explicitly supplied symbols and supports ABS32, REL32, ARM_BRANCH24 and THUMB_BRANCH11 relocations. It does **not** parse ELF objects, implement the complete ARM relocation ABI, create startup code or guarantee a BIOS-bootable ROM. Do not treat its output as a finished game.

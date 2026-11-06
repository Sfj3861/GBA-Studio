# GBA ARM7TDMI linker layout

`gba.ld` is the GNU ld memory map for a bare-metal ARM7TDMI GBA executable.

- ROM: `0x08000000`, up to 32 MiB.
- EWRAM: `0x02000000`, 256 KiB.
- IWRAM: `0x03000000`, 32 KiB.
- `.data` and `.iwram` have ROM load addresses and RAM execution addresses.
- `.bss` is NOLOAD and the script exports startup-copy/clear symbols.

The Android NDK toolchain used to build GBA Studio is an Android toolchain, not a ready-made GBA SDK. A real build pipeline still needs an ARM7TDMI bare-metal compiler/binutils (or a verified LLVM equivalent), startup code, a valid GBA header/logo block, relocation handling and runtime libraries.

The in-app experimental ROM image builder currently places raw bytes and fills selected header fields. It does **not** link ELF objects, apply relocations, create startup code or guarantee a BIOS-bootable ROM. Do not treat its output as a finished game.

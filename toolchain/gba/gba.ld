OUTPUT_FORMAT("elf32-littlearm")
OUTPUT_ARCH(arm)
ENTRY(_start)

MEMORY
{
    ROM   (rx)  : ORIGIN = 0x08000000, LENGTH = 32M
    EWRAM (rwx) : ORIGIN = 0x02000000, LENGTH = 256K
    IWRAM (rwx) : ORIGIN = 0x03000000, LENGTH = 32K
}

SECTIONS
{
    .text 0x08000000 :
    {
        __rom_start__ = .;
        KEEP(*(.gba.entry))
        . = 0x080000A0;
        KEEP(*(.gba.header))
        *(.text*)
        *(.rodata*)
        *(.glue_7*)
        *(.glue_7t*)
        . = ALIGN(4);
    } > ROM

    .data :
    {
        . = ALIGN(4);
        __data_start__ = .;
        *(.data*)
        . = ALIGN(4);
        __data_end__ = .;
    } > EWRAM AT> ROM
    __data_load__ = LOADADDR(.data);

    .iwram :
    {
        . = ALIGN(4);
        __iwram_start__ = .;
        *(.iwram*)
        . = ALIGN(4);
        __iwram_end__ = .;
    } > IWRAM AT> ROM
    __iwram_load__ = LOADADDR(.iwram);

    .bss (NOLOAD) :
    {
        . = ALIGN(4);
        __bss_start__ = .;
        *(.bss*)
        *(COMMON)
        . = ALIGN(4);
        __bss_end__ = .;
    } > EWRAM

    .stack (NOLOAD) :
    {
        . = ALIGN(8);
        __stack_bottom__ = .;
        . += 0x400;
        __stack_top__ = .;
    } > IWRAM

    __rom_end__ = LOADADDR(.iwram) + SIZEOF(.iwram);
    /DISCARD/ : { *(.comment*) *(.note*) *(.eh_frame*) }
}

ASSERT(SIZEOF(.text) <= LENGTH(ROM), "GBA ROM section exceeds 32 MiB")
ASSERT(__bss_end__ <= ORIGIN(EWRAM) + LENGTH(EWRAM), "EWRAM overflow")
ASSERT(__stack_top__ <= ORIGIN(IWRAM) + LENGTH(IWRAM), "IWRAM stack overflow")

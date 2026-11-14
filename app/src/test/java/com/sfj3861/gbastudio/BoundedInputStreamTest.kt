package com.sfj3861.gbastudio

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BoundedInputStreamTest {
    @Test fun readsDataWithinLimit() {
        val input = byteArrayOf(1, 2, 3, 4)
        assertArrayEquals(input, ByteArrayInputStream(input).readBoundedBytes(4))
    }

    @Test fun allowsEmptyStreamWithZeroLimit() {
        assertEquals(0, ByteArrayInputStream(byteArrayOf()).readBoundedBytes(0).size)
    }

    @Test(expected = IOException::class)
    fun rejectsStreamLargerThanLimit() {
        ByteArrayInputStream(byteArrayOf(1, 2, 3)).readBoundedBytes(2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeLimit() {
        ByteArrayInputStream(byteArrayOf()).readBoundedBytes(-1)
    }
}

package com.sfj3861.gbastudio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticAudioPlaybackProgressTest {
    @Test
    fun doesNotFinishBeforeAllFramesHavePlayed() {
        assertFalse(hasStaticPlaybackReachedEnd(0, 100))
        assertFalse(hasStaticPlaybackReachedEnd(99, 100))
    }

    @Test
    fun finishesWhenAllFramesHavePlayed() {
        assertTrue(hasStaticPlaybackReachedEnd(100, 100))
        assertTrue(hasStaticPlaybackReachedEnd(101, 100))
    }

    @Test
    fun interpretsPlaybackHeadAsUnsigned32BitPosition() {
        assertTrue(hasStaticPlaybackReachedEnd(Int.MIN_VALUE, 100))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeTotalFrameCount() {
        hasStaticPlaybackReachedEnd(0, -1)
    }
}

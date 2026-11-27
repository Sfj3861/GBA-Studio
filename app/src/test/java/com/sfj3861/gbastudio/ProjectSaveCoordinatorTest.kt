package com.sfj3861.gbastudio

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectSaveCoordinatorTest {
    @Test fun serializesWritesSoTheNewestSnapshotIsWrittenLast() = runBlocking {
        val coordinator = ProjectSaveCoordinator()
        var revision = 1L
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondScheduled = CompletableDeferred<Unit>()
        val writes = mutableListOf<String>()

        val first = launch {
            assertTrue(coordinator.saveIfCurrent(1L, { revision }) {
                firstStarted.complete(Unit)
                releaseFirst.await()
                writes += "old"
            })
        }
        firstStarted.await()

        val second = async {
            secondScheduled.complete(Unit)
            coordinator.saveIfCurrent(2L, { revision }) {
                writes += "new"
            }
        }
        secondScheduled.await()
        revision = 2L
        releaseFirst.complete(Unit)

        first.join()
        assertTrue(second.await())
        assertEquals(listOf("old", "new"), writes)
    }

    @Test fun skipsAnAutosaveSnapshotThatBecameStaleWhileWaitingForTheLock() = runBlocking {
        val coordinator = ProjectSaveCoordinator()
        var revision = 1L
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val staleScheduled = CompletableDeferred<Unit>()
        val writes = mutableListOf<String>()

        val first = launch {
            assertTrue(coordinator.saveIfCurrent(1L, { revision }) {
                firstStarted.complete(Unit)
                releaseFirst.await()
            })
        }
        firstStarted.await()

        val stale = async {
            staleScheduled.complete(Unit)
            coordinator.saveIfCurrent(1L, { revision }) {
                writes += "stale"
            }
        }
        staleScheduled.await()
        revision = 2L
        releaseFirst.complete(Unit)

        first.join()
        assertFalse(stale.await())
        assertTrue(writes.isEmpty())
    }
}

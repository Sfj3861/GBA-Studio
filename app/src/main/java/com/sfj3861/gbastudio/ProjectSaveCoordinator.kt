package com.sfj3861.gbastudio

import kotlinx.coroutines.sync.Mutex

/**
 * Serializes editor writes and discards queued snapshots once a newer edit exists.
 * The revision is checked after acquiring the lock, not when the save is scheduled.
 */
internal class ProjectSaveCoordinator {
    private val mutex = Mutex()

    suspend fun saveIfCurrent(
        revision: Long,
        currentRevision: () -> Long,
        save: suspend () -> Unit
    ): Boolean {
        mutex.lock()
        try {
            if (currentRevision() != revision) return false
            save()
            return true
        } finally {
            mutex.unlock()
        }
    }
}

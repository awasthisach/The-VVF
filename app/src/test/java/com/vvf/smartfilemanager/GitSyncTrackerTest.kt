package com.vvf.smartfilemanager

import com.vvf.smartfilemanager.data.*
import org.junit.Assert.*
import org.junit.Test

class GitSyncTrackerTest {

    @Test
    fun testInitialSyncState() {
        // Test that the GitSyncState initializes properly
        val state = GitSyncState(
            status = GitSyncStatus.CONFLICT,
            lastCheckedSecondsAgo = 0,
            localAheadCount = 0,
            remoteAheadCount = 0,
            repositoryName = "google/dagger",
            latestSyncActionMessage = "Ongoing conflict resolution active"
        )

        assertEquals(GitSyncStatus.CONFLICT, state.status)
        assertEquals(0, state.lastCheckedSecondsAgo)
        assertEquals(0, state.localAheadCount)
        assertEquals(0, state.remoteAheadCount)
        assertEquals("google/dagger", state.repositoryName)
        assertEquals("Ongoing conflict resolution active", state.latestSyncActionMessage)
    }

    @Test
    fun testSimulateBehindState() {
        // Test transition to OUT_OF_SYNC (Local is behind)
        val state = GitSyncState(
            status = GitSyncStatus.SYNCED,
            lastCheckedSecondsAgo = 0,
            repositoryName = "google/dagger"
        )

        val updatedState = state.copy(
            status = GitSyncStatus.OUT_OF_SYNC,
            remoteAheadCount = 3,
            latestSyncActionMessage = "Remote repository matches 3 new commits."
        )

        assertEquals(GitSyncStatus.OUT_OF_SYNC, updatedState.status)
        assertEquals(3, updatedState.remoteAheadCount)
        assertEquals(0, updatedState.localAheadCount)
    }

    @Test
    fun testSimulateAheadState() {
        // Test transition to AHEAD (Local is ahead of remote)
        val state = GitSyncState(
            status = GitSyncStatus.SYNCED,
            lastCheckedSecondsAgo = 0,
            repositoryName = "google/dagger"
        )

        val updatedState = state.copy(
            status = GitSyncStatus.AHEAD,
            localAheadCount = 2,
            latestSyncActionMessage = "Local commits ahead of origins."
        )

        assertEquals(GitSyncStatus.AHEAD, updatedState.status)
        assertEquals(2, updatedState.localAheadCount)
        assertEquals(0, updatedState.remoteAheadCount)
    }
}

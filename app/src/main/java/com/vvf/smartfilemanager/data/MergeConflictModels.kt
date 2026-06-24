package com.vvf.smartfilemanager.data

enum class GitSyncStatus {
    SYNCED,
    OUT_OF_SYNC,
    AHEAD,
    SYNCING,
    CONFLICT
}

data class GitSyncState(
    val status: GitSyncStatus,
    val lastCheckedSecondsAgo: Int = 0,
    val localAheadCount: Int = 0,
    val remoteAheadCount: Int = 0,
    val repositoryName: String = "",
    val latestSyncActionMessage: String = "No recent actions"
)

data class TrackedRepository(
    val path: String,
    val lastSynced: Long,
    val status: String // "Synced", "Failed", "Syncing"
)

data class ConflictBlock(
    val id: String,
    val lineStart: Int,
    val currentCode: String,
    val incomingCode: String,
    val description: String,
    val resolutionChoice: String? = null, // "ours" (Current), "theirs" (Incoming), "both", "ai" (Automated AI resolution)
    val resolvedCode: String? = null
)

data class ConflictFile(
    val id: String,
    val name: String,
    val path: String,
    val currentBranch: String = "main",
    val incomingBranch: String = "feature/dynamic-themes",
    val blocks: List<ConflictBlock>,
    val isFullyResolved: Boolean = false
)

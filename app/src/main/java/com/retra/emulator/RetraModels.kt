package com.retra.emulator

import java.io.File

/**
 * Shared immutable models used across Retra's Activity-level controllers.
 *
 * Keeping these outside MainActivity prevents UI lifecycle orchestration from
 * becoming the owner of ROM/import domain data and avoids nested-type coupling
 * between RomUiController and the Activity.
 */
internal data class NativeLibraryItem(
    val id: String,
    val title: String,
    val fileName: String,
    val size: Long,
    val lastModified: Long,
    val system: String,
    val sourceExtension: String,
    val launchPath: String?,
    val patchPath: String?,
    val contentHash: String,
    val sourceUri: String?,
    val legacyIdentityHash: String? = null,
    val importTransactionId: String? = null,
    val reusedExisting: Boolean = false,
    val restoredArchived: Boolean = false
)

internal data class PendingPatchLaunch(
    val id: String,
    val title: String,
    val patchFile: File
)

internal data class PendingLocate(
    val id: String,
    val title: String,
    val fileName: String,
    val system: String
)

package com.vocalarm.app.domain

sealed interface StoreIssue {
    data class Corrupt(val backupFileName: String?) : StoreIssue
    data class SchemaTooNew(val found: Int, val supported: Int) : StoreIssue
    data class DroppedEntries(val count: Int) : StoreIssue
    data class SaveFailed(val message: String) : StoreIssue
}

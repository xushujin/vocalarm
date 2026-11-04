package com.vocalarm.app.ui.vm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * Each editor session gets its own store so closing the sheet clears the ViewModel. Without this,
 * reopening the same alarm would show the previous draft from the activity-scoped store.
 */
class EditorViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()

    fun clear() {
        viewModelStore.clear()
    }
}

@Composable
fun rememberEditorViewModelStoreOwner(): EditorViewModelStoreOwner {
    val owner = remember { EditorViewModelStoreOwner() }
    DisposableEffect(owner) {
        onDispose { owner.clear() }
    }
    return owner
}

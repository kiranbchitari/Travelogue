package com.travelogue.app.ui.common

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle

/**
 * An editor whose unsaved form should survive Android killing the app, which happens routinely
 * while the camera or photo picker is in front. [appViewModel] wires these up automatically.
 *
 * Only what the user typed or chose is kept. Photos picked but not yet saved are not: the system
 * withdraws access to picked files when the process dies.
 */
interface DraftHolder {
    /** The form as it stands now, or an empty bundle if the editor has not finished loading. */
    fun saveDraft(): Bundle

    /** Called once after a restart with what [saveDraft] returned. */
    fun restoreDraft(draft: Bundle)
}

/**
 * A restored draft can arrive before or after the editor has loaded its record from the database.
 * This applies it exactly once, always after that load, so the draft wins over the stored values.
 */
class DraftSlot(private val apply: (Bundle) -> Unit) {
    private var waiting: Bundle? = null
    var ready = false
        private set

    fun offer(draft: Bundle) {
        if (draft.isEmpty) return
        if (ready) apply(draft) else waiting = draft
    }

    /** Call when the editor's own load has finished. */
    fun ready() {
        ready = true
        waiting?.let(apply)
        waiting = null
    }
}

/** Connects a [DraftHolder] to its saved-state slot. */
fun keepDraft(holder: DraftHolder, handle: SavedStateHandle) {
    handle.get<Bundle>(DRAFT_KEY)?.let(holder::restoreDraft)
    handle.setSavedStateProvider(DRAFT_KEY) { holder.saveDraft() }
}

private const val DRAFT_KEY = "draft"

/** Typed read of a value stored with `bundleOf`. */
@Suppress("UNCHECKED_CAST", "DEPRECATION")
fun <T> Bundle.read(key: String): T? = get(key) as T?

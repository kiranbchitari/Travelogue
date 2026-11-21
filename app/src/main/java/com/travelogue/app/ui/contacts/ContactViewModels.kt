package com.travelogue.app.ui.contacts

import com.travelogue.app.ui.common.read
import com.travelogue.app.ui.common.DraftSlot
import com.travelogue.app.ui.common.DraftHolder
import androidx.core.os.bundleOf
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelogue.app.data.db.entity.ContactType
import com.travelogue.app.data.db.entity.TravelContactEntity
import com.travelogue.app.data.repository.JournalRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TripContactsViewModel(journal: JournalRepository, tripId: Long) : ViewModel() {
    val contacts: StateFlow<List<TravelContactEntity>?> = journal.observeContacts(tripId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

/** Allowed characters in a phone number: digits, spaces, + - ( ) and dots. */
private val PhoneChars = Regex("^[0-9+()\\-. ]*$")

fun phoneError(value: String): String? {
    val v = value.trim()
    if (v.isEmpty()) return null
    if (!PhoneChars.matches(v)) return "Use digits, spaces, + - ( ) only"
    if (v.count { it.isDigit() } < 5) return "Number looks too short"
    if (v.drop(1).contains('+')) return "+ can only come first"
    return null
}

data class ContactForm(
    val name: String = "",
    val type: ContactType = ContactType.OTHER,
    val phone: String = "",
    val whatsapp: String = "",
    val sameAsPhone: Boolean = true,
    val address: String = "",
    val associatedWith: String = "",
    val description: String = "",
    val notes: String = "",
) : java.io.Serializable {
    val effectiveWhatsapp: String get() = if (sameAsPhone) phone else whatsapp
}

data class ContactErrors(val name: String? = null, val phone: String? = null, val whatsapp: String? = null) {
    val any: Boolean get() = listOfNotNull(name, phone, whatsapp).isNotEmpty()
}

fun ContactForm.validate() = ContactErrors(
    name = if (name.isBlank()) "Who is this?" else null,
    phone = phoneError(phone),
    whatsapp = if (sameAsPhone) null else phoneError(whatsapp),
)

class ContactEditorViewModel(
    private val journal: JournalRepository,
    val tripId: Long,
    val contactId: Long,
) : ViewModel(), DraftHolder {
    val isNew = contactId == 0L
    var form by mutableStateOf(ContactForm())
        private set
    var loaded by mutableStateOf(false)
        private set
    var showErrors by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var finished by mutableStateOf(false)
        private set
    private var original: TravelContactEntity? = null

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    /** Names of this trip's hotels and places, offered for "associated with". */
    val suggestions: StateFlow<List<String>> = combine(journal.observeHotels(tripId), journal.observePlaces(tripId)) { h, p ->
        (h.map { it.name } + p.map { it.name }).filter { it.isNotBlank() }.distinct()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val draft = DraftSlot(::applyDraft)
    override fun restoreDraft(draft: Bundle) = this.draft.offer(draft)
    override fun saveDraft(): Bundle = if (!draft.ready || finished) Bundle() else bundleOf("form" to form)

    private fun applyDraft(b: Bundle) { form = b.read("form") ?: form }

    init {
        viewModelScope.launch {
            if (!isNew) {
                journal.getContact(contactId)?.let { c ->
                    original = c
                    form = ContactForm(
                        name = c.name, type = c.type, phone = c.phone, whatsapp = c.whatsapp,
                        sameAsPhone = c.whatsapp.isBlank() || c.whatsapp == c.phone,
                        address = c.address, associatedWith = c.associatedWith, description = c.description, notes = c.notes,
                    )
                }
            }
            loaded = true
            draft.ready()
        }
    }

    val errors: ContactErrors get() = if (showErrors) form.validate() else ContactErrors()

    fun update(transform: ContactForm.() -> ContactForm) { form = form.transform() }

    fun save() {
        if (saving) return
        showErrors = true
        val f = form
        if (f.validate().any) {
            _messages.tryEmit("Please fix the highlighted fields")
            return
        }
        saving = true
        viewModelScope.launch {
            try {
                val base = original ?: TravelContactEntity(tripId = tripId, name = "")
                journal.saveContact(
                    base.copy(
                        name = f.name.trim(),
                        type = f.type,
                        phone = f.phone.trim(),
                        whatsapp = f.effectiveWhatsapp.trim(),
                        address = f.address.trim(),
                        associatedWith = f.associatedWith.trim(),
                        description = f.description.trim(),
                        notes = f.notes.trim(),
                    ),
                )
                finished = true
            } catch (e: Exception) {
                saving = false
                _messages.tryEmit(e.message ?: "Couldn't save this contact")
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            journal.deleteContact(contactId)
            finished = true
        }
    }
}

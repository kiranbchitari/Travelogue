package com.travelogue.app.ui.contacts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContactPhone
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.util.Intents
import com.travelogue.app.data.db.entity.ContactType
import com.travelogue.app.data.db.entity.TravelContactEntity
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.navigation.Nav

@Composable
fun TripContactsTab(nav: Nav, tripId: Long, modifier: Modifier = Modifier) {
    val vm = appViewModel(key = "contacts-$tripId") { TripContactsViewModel(journal, tripId) }
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier.fillMaxSize()) {
        val all = contacts
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = Space.lg, bottom = navBottom + 96.dp),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            when {
                all == null -> item { LoadingState() }
                all.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Rounded.ContactPhone,
                        title = "No contacts yet",
                        message = "Save the hotel desk, your driver, a great guide or an emergency number — one tap to call or message.",
                        actionLabel = "Add a contact",
                        onAction = { nav.contactEditor(tripId) },
                    )
                }
                else -> {
                    if (all.size > 6) {
                        item {
                            FormField(
                                label = "Search",
                                value = query,
                                onValueChange = { query = it },
                                placeholder = "Name, number, hotel…",
                                leadingIcon = Icons.Rounded.Search,
                                modifier = Modifier.padding(horizontal = Space.gutter),
                            )
                        }
                    }
                    // The search box only shows for longer lists; without it a stale query must not filter.
                    val q = if (all.size > 6) query.trim() else ""
                    val shown = if (q.isEmpty()) all else all.filter {
                        it.name.contains(q, true) || it.phone.contains(q) || it.whatsapp.contains(q) ||
                            it.associatedWith.contains(q, true) || it.type.label.contains(q, true) || it.address.contains(q, true)
                    }
                    if (shown.isEmpty()) {
                        item {
                            Text(
                                "No contacts match “$q”.",
                                style = Travel.type.body,
                                color = Travel.colors.inkSoft,
                                modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.xl),
                            )
                        }
                    }
                    val emergency = shown.filter { it.type == ContactType.EMERGENCY }
                    if (emergency.isNotEmpty()) {
                        item(key = "h-emergency") {
                            SectionHeader("Emergency", subtitle = "Pinned for quick access", modifier = Modifier.padding(horizontal = Space.gutter).padding(top = Space.sm))
                        }
                        items(emergency, key = { it.id }) { c ->
                            ContactCard(c, emergency = true, onClick = { nav.contactEditor(tripId, c.id) }, modifier = Modifier.padding(horizontal = Space.gutter))
                        }
                    }
                    ContactType.entries.filter { it != ContactType.EMERGENCY }.forEach { type ->
                        val group = shown.filter { it.type == type }
                        if (group.isNotEmpty()) {
                            item(key = "h-${type.name}") {
                                SectionHeader(
                                    type.label,
                                    subtitle = if (group.size > 1) "${group.size} contacts" else null,
                                    modifier = Modifier.padding(horizontal = Space.gutter).padding(top = Space.sm),
                                )
                            }
                            items(group, key = { it.id }) { c ->
                                ContactCard(c, emergency = false, onClick = { nav.contactEditor(tripId, c.id) }, modifier = Modifier.padding(horizontal = Space.gutter))
                            }
                        }
                    }
                }
            }
        }
        AccentFab(
            Icons.Rounded.Add,
            { nav.contactEditor(tripId) },
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(Space.gutter),
            contentDescription = "Add contact",
            label = "Contact",
        )
    }
}

@Composable
private fun ContactCard(contact: TravelContactEntity, emergency: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Travel.colors
    val context = LocalContext.current
    val tint = if (emergency) c.danger else c.secondary
    val bg = if (emergency) lerpColor(c.card, c.danger, if (c.isDark) 0.12f else 0.06f) else c.card
    val whatsapp = contact.whatsapp.ifBlank { contact.phone }
    SurfaceCard(modifier.fillMaxWidth(), onClick = onClick, color = bg) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(contact.type.icon, tint, size = 44.dp, iconSize = 22.dp)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(contact.name, style = Travel.type.titleSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOf(contact.associatedWith, if (contact.associatedWith.isBlank()) contact.type.label else "").filter { it.isNotBlank() }.joinToString()
                if (sub.isNotBlank()) Text(sub, style = Travel.type.bodySmall, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (contact.phone.isNotBlank()) {
                    Text(contact.phone, style = Travel.type.label.copy(fontFamily = Travel.type.numberSmall.fontFamily), color = if (emergency) c.danger else c.ink)
                }
            }
        }
        if (contact.description.isNotBlank()) {
            Spacer(Modifier.height(Space.sm))
            Text(contact.description, style = Travel.type.bodySmall, color = c.inkFaint, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (contact.phone.isNotBlank() || contact.whatsapp.isNotBlank()) {
            Spacer(Modifier.height(Space.md))
            Hairline()
            Spacer(Modifier.height(Space.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                if (contact.phone.isNotBlank()) {
                    QuickAction(Icons.Rounded.Call, "Call", { Intents.dial(context, contact.phone) }, tint = tint)
                    QuickAction(Icons.Rounded.Sms, "SMS", { Intents.sms(context, contact.phone) }, tint = tint)
                }
                if (whatsapp.isNotBlank()) {
                    QuickAction(Icons.AutoMirrored.Rounded.Chat, "WhatsApp", { Intents.whatsapp(context, whatsapp) }, tint = Color(0xFF25A35A))
                }
                QuickAction(Icons.Rounded.ContentCopy, "Copy", { Intents.copy(context, "Phone number", contact.phone.ifBlank { whatsapp }) }, tint = c.inkSoft)
            }
        }
    }
}


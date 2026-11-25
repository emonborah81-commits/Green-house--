package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.EventCategory
import com.example.data.model.ManagementPermissions
import com.example.data.model.ManagementRole
import com.example.data.model.PortalEvent
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.CrestGoldDeep
import com.example.ui.theme.CrestGoldWash
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldSoft
import com.example.ui.theme.ForestDeep
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PureWhite
import com.example.ui.viewmodel.EventStatisticRow
import com.example.ui.viewmodel.UiState

@Composable
fun EventsScreen(
    role: ManagementRole,
    eventsState: UiState<List<PortalEvent>>,
    eventStats: List<EventStatisticRow>,
    isSubmitting: Boolean,
    onAddEvent: (name: String, category: EventCategory, onDone: () -> Unit) -> Unit,
    onRenameEvent: (PortalEvent, String, onDone: () -> Unit) -> Unit,
    onToggleEnabled: (PortalEvent, Boolean) -> Unit,
    onArchiveOrDelete: (PortalEvent, Int, Boolean, onDone: () -> Unit) -> Unit
) {
    var selectedCategory by rememberSaveable { mutableStateOf(EventCategory.SPORTS) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showArchived by rememberSaveable { mutableStateOf(false) }

    var showAddDialog by remember { mutableStateOf(false) }
    var renamingEvent by remember { mutableStateOf<PortalEvent?>(null) }
    var removingTarget by remember { mutableStateOf<Pair<PortalEvent, Int>?>(null) }

    val canDeletePermanently = ManagementPermissions.canDeleteEvents(role)
    val countMap = remember(eventStats) {
        eventStats.associate { it.event.id to it.participantCount }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        when (eventsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ForestPrimary)
                }
            }

            is UiState.Error -> {
                Box(modifier = Modifier.padding(20.dp)) {
                    EmptyStateCard(
                        icon = Icons.Filled.EmojiEvents,
                        title = "Unable to Load Events",
                        subtitle = eventsState.message
                    )
                }
            }

            is UiState.Success -> {
                val filteredEvents = remember(eventsState.data, selectedCategory, searchQuery, showArchived) {
                    val q = searchQuery.trim().lowercase()
                    eventsState.data.filter { evt ->
                        evt.categoryEnum == selectedCategory &&
                            (showArchived || !evt.archived) &&
                            (q.isEmpty() || evt.name.lowercase().contains(q))
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 860.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "events_header") {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // SPORTS EVENTS / CULTURAL EVENTS segmented switch
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                    SegmentedButton(
                                        selected = selectedCategory == EventCategory.SPORTS,
                                        onClick = { selectedCategory = EventCategory.SPORTS },
                                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                                        icon = {
                                            Icon(
                                                Icons.Filled.DirectionsRun,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        modifier = Modifier.testTag("tab_sports_events")
                                    ) {
                                        Text("SPORTS EVENTS", fontWeight = FontWeight.SemiBold)
                                    }
                                    SegmentedButton(
                                        selected = selectedCategory == EventCategory.CULTURAL,
                                        onClick = { selectedCategory = EventCategory.CULTURAL },
                                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                                        icon = {
                                            Icon(
                                                Icons.Filled.MusicNote,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        modifier = Modifier.testTag("tab_cultural_events")
                                    ) {
                                        Text("CULTURAL EVENTS", fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Search ${selectedCategory.label.lowercase()}...") },
                                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("event_search_input")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilterChip(
                                        selected = showArchived,
                                        onClick = { showArchived = !showArchived },
                                        label = { Text(if (showArchived) "Showing Archived" else "Show Archived") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Filled.Archive,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )

                                    Button(
                                        onClick = { showAddDialog = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ForestPrimary,
                                            contentColor = PureWhite
                                        ),
                                        modifier = Modifier.testTag("add_event_button")
                                    ) {
                                        Icon(
                                            Icons.Filled.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Add Event")
                                    }
                                }
                            }
                        }
                    }

                    if (filteredEvents.isEmpty()) {
                        item(key = "empty_events") {
                            EmptyStateCard(
                                icon = Icons.Filled.EmojiEvents,
                                title = "No ${selectedCategory.label}",
                                subtitle = "Add an event or clear your search filter.",
                                actionLabel = "Add ${selectedCategory.label.removeSuffix("s")}",
                                onAction = { showAddDialog = true }
                            )
                        }
                    } else {
                        items(filteredEvents, key = { it.id }) { event ->
                            val count = countMap[event.id] ?: 0
                            EventManageCard(
                                event = event,
                                participantCount = count,
                                onToggleEnabled = { onToggleEnabled(event, it) },
                                onRename = { renamingEvent = event },
                                onRemove = { removingTarget = event to count }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var newName by remember { mutableStateOf("") }
        var newCategory by remember { mutableStateOf(selectedCategory) }
        var localError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showAddDialog = false },
            title = { Text("Add New Event") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = newCategory == EventCategory.SPORTS,
                            onClick = { newCategory = EventCategory.SPORTS },
                            shape = SegmentedButtonDefaults.itemShape(0, 2)
                        ) { Text("Sports") }
                        SegmentedButton(
                            selected = newCategory == EventCategory.CULTURAL,
                            onClick = { newCategory = EventCategory.CULTURAL },
                            shape = SegmentedButtonDefaults.itemShape(1, 2)
                        ) { Text("Cultural") }
                    }

                    OutlinedTextField(
                        value = newName,
                        onValueChange = { if (it.length <= 80) newName = it; localError = null },
                        label = { Text("Event Name") },
                        placeholder = { Text("e.g., Relay Race or Debate") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_event_name_input")
                    )

                    if (localError != null) {
                        Text(localError!!, color = CrimsonError, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newName.trim()
                        if (trimmed.length < 2) {
                            localError = "Event name must be at least 2 characters."
                        } else {
                            onAddEvent(trimmed, newCategory) { showAddDialog = false }
                        }
                    },
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary)
                ) {
                    Text(if (isSubmitting) "Adding..." else "Add Event")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }, enabled = !isSubmitting) {
                    Text("Cancel")
                }
            }
        )
    }

    renamingEvent?.let { evt ->
        var updatedName by remember(evt.id) { mutableStateOf(evt.name) }
        var localError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!isSubmitting) renamingEvent = null },
            title = { Text("Rename Event") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = updatedName,
                        onValueChange = { if (it.length <= 80) updatedName = it; localError = null },
                        label = { Text("New Event Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (localError != null) {
                        Text(localError!!, color = CrimsonError, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = updatedName.trim()
                        if (trimmed.length < 2) {
                            localError = "Event name must be at least 2 characters."
                        } else {
                            onRenameEvent(evt, trimmed) { renamingEvent = null }
                        }
                    },
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary)
                ) {
                    Text("Save Name")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingEvent = null }, enabled = !isSubmitting) {
                    Text("Cancel")
                }
            }
        )
    }

    removingTarget?.let { (evt, count) ->
        val hasRegistrations = count > 0
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) removingTarget = null },
            icon = {
                Icon(
                    imageVector = if (hasRegistrations) Icons.Filled.Warning else Icons.Filled.DeleteOutline,
                    contentDescription = null,
                    tint = if (hasRegistrations) CrestGoldDeep else CrimsonError
                )
            },
            title = {
                Text(if (hasRegistrations) "Archive Event with Registrations?" else "Remove \"${evt.name}\"?")
            },
            text = {
                Text(
                    if (hasRegistrations) {
                        "$count student(s) have already registered for \"${evt.name}\". To protect historical participation records, this event will be archived and hidden from new student registrations."
                    } else if (canDeletePermanently) {
                        "No students have registered for \"${evt.name}\" yet. You can archive it or permanently delete it."
                    } else {
                        "Archive \"${evt.name}\" to hide it from student registration while preserving history."
                    }
                )
            },
            confirmButton = {
                if (!hasRegistrations && canDeletePermanently) {
                    Button(
                        onClick = {
                            onArchiveOrDelete(evt, count, false) { removingTarget = null }
                        },
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonError)
                    ) {
                        Text("Delete Permanently")
                    }
                } else {
                    Button(
                        onClick = {
                            onArchiveOrDelete(evt, count, true) { removingTarget = null }
                        },
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary)
                    ) {
                        Text("Archive Event")
                    }
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!hasRegistrations && canDeletePermanently) {
                        OutlinedButton(
                            onClick = {
                                onArchiveOrDelete(evt, count, true) { removingTarget = null }
                            },
                            enabled = !isSubmitting
                        ) {
                            Text("Archive")
                        }
                    }
                    TextButton(onClick = { removingTarget = null }, enabled = !isSubmitting) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

@Composable
private fun EventManageCard(
    event: PortalEvent,
    participantCount: Int,
    onToggleEnabled: (Boolean) -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit
) {
    val isSports = event.categoryEnum == EventCategory.SPORTS
    val countBg = if (isSports) EmeraldSoft else CrestGoldWash
    val countText = if (isSports) ForestDeep else CrestGoldDeep

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = event.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (event.enabled && !event.archived) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = countBg
                    ) {
                        Text(
                            text = "$participantCount REGISTERED",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = countText,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    val statusLabel = when {
                        event.archived -> "ARCHIVED"
                        event.enabled -> "ACTIVE"
                        else -> "DISABLED"
                    }
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (event.enabled && !event.archived) ForestPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = event.enabled && !event.archived,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PureWhite,
                        checkedTrackColor = ForestPrimary
                    ),
                    modifier = Modifier.testTag("toggle_event_${event.id}")
                )
                IconButton(
                    onClick = onRename,
                    modifier = Modifier.testTag("rename_event_${event.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Rename ${event.name}",
                        tint = ForestPrimary
                    )
                }
                if (!event.archived) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.testTag("remove_event_${event.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Archive or delete ${event.name}",
                            tint = CrimsonError
                        )
                    }
                }
            }
        }
    }
}

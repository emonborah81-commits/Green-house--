package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.EventCategory
import com.example.data.model.ManagementPermissions
import com.example.data.model.ManagementRole
import com.example.data.model.Participant
import com.example.data.model.PortalEvent
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.CrestGoldBorder
import com.example.ui.theme.CrestGoldDeep
import com.example.ui.theme.CrestGoldWash
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldBorder
import com.example.ui.theme.EmeraldSoft
import com.example.ui.theme.ForestDeep
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PureWhite
import com.example.ui.viewmodel.ParticipantSortOption
import com.example.ui.viewmodel.UiState
import com.example.util.SecurityUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParticipantsScreen(
    role: ManagementRole,
    participantsState: UiState<List<Participant>>,
    events: List<PortalEvent>,
    isSubmitting: Boolean,
    onUpdateParticipant: (
        participant: Participant,
        newName: String,
        newRoll: String,
        newSports: List<PortalEvent>,
        newCultural: List<PortalEvent>,
        onDone: () -> Unit
    ) -> Unit,
    onDeleteParticipant: (Participant, onDone: () -> Unit) -> Unit,
    onNavigateToExports: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedSportsFilterId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCulturalFilterId by rememberSaveable { mutableStateOf<String?>(null) }
    var sortOption by rememberSaveable { mutableStateOf(ParticipantSortOption.LATEST) }

    var showSportsDropdown by remember { mutableStateOf(false) }
    var showCulturalDropdown by remember { mutableStateOf(false) }
    var showSortDropdown by remember { mutableStateOf(false) }

    var editingParticipant by remember { mutableStateOf<Participant?>(null) }
    var deletingParticipant by remember { mutableStateOf<Participant?>(null) }

    val sportsEvents = remember(events) {
        events.filter { it.categoryEnum == EventCategory.SPORTS && !it.archived }
    }
    val culturalEvents = remember(events) {
        events.filter { it.categoryEnum == EventCategory.CULTURAL && !it.archived }
    }

    val canEdit = ManagementPermissions.canEditParticipants(role)
    val canDelete = ManagementPermissions.canDeleteParticipants(role)
    val canExport = ManagementPermissions.canExportCsv(role)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        when (participantsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ForestPrimary)
                }
            }

            is UiState.Error -> {
                Box(modifier = Modifier.padding(20.dp)) {
                    EmptyStateCard(
                        icon = Icons.Filled.Groups,
                        title = "Unable to Load Participants",
                        subtitle = participantsState.message
                    )
                }
            }

            is UiState.Success -> {
                val allParticipants = participantsState.data
                val filteredParticipants = remember(
                    allParticipants,
                    searchQuery,
                    selectedSportsFilterId,
                    selectedCulturalFilterId,
                    sortOption
                ) {
                    val q = searchQuery.trim().lowercase()
                    allParticipants
                        .filter { p ->
                            val matchesSearch = q.isEmpty() ||
                                p.studentName.lowercase().contains(q) ||
                                p.rollNumber.lowercase().contains(q) ||
                                p.normalizedRollNumber.lowercase().contains(q)
                            val matchesSports = selectedSportsFilterId == null ||
                                selectedSportsFilterId in p.sportsEventIds
                            val matchesCultural = selectedCulturalFilterId == null ||
                                selectedCulturalFilterId in p.culturalEventIds
                            matchesSearch && matchesSports && matchesCultural
                        }
                        .let { list ->
                            when (sortOption) {
                                ParticipantSortOption.LATEST ->
                                    list.sortedByDescending { it.submittedAt?.seconds ?: 0L }
                                ParticipantSortOption.NAME_ASC ->
                                    list.sortedBy { it.studentName.lowercase() }
                                ParticipantSortOption.ROLL_ASC ->
                                    list.sortedBy { it.normalizedRollNumber }
                            }
                        }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 880.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Search + Filter + Export Controls
                    item(key = "controls_header") {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Live Participants (${filteredParticipants.size}/${allParticipants.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (canExport) {
                                        TextButton(
                                            onClick = onNavigateToExports,
                                            modifier = Modifier.testTag("participants_export_shortcut")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Download,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Export CSV")
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Search by student name or roll number...") },
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
                                        .testTag("participant_search_input")
                                )

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Sports Event Filter
                                    Box {
                                        val selectedSportsName = sportsEvents.firstOrNull { it.id == selectedSportsFilterId }?.name
                                        FilterChip(
                                            selected = selectedSportsFilterId != null,
                                            onClick = { showSportsDropdown = true },
                                            label = {
                                                Text(selectedSportsName?.let { "Sports: $it" } ?: "Filter Sports")
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Filled.FilterList,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        )
                                        DropdownMenu(
                                            expanded = showSportsDropdown,
                                            onDismissRequest = { showSportsDropdown = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("All Sports Events") },
                                                onClick = {
                                                    selectedSportsFilterId = null
                                                    showSportsDropdown = false
                                                }
                                            )
                                            sportsEvents.forEach { evt ->
                                                DropdownMenuItem(
                                                    text = { Text(evt.name) },
                                                    onClick = {
                                                        selectedSportsFilterId = evt.id
                                                        showSportsDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Cultural Event Filter
                                    Box {
                                        val selectedCulturalName = culturalEvents.firstOrNull { it.id == selectedCulturalFilterId }?.name
                                        FilterChip(
                                            selected = selectedCulturalFilterId != null,
                                            onClick = { showCulturalDropdown = true },
                                            label = {
                                                Text(selectedCulturalName?.let { "Cultural: $it" } ?: "Filter Cultural")
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Filled.FilterList,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        )
                                        DropdownMenu(
                                            expanded = showCulturalDropdown,
                                            onDismissRequest = { showCulturalDropdown = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("All Cultural Events") },
                                                onClick = {
                                                    selectedCulturalFilterId = null
                                                    showCulturalDropdown = false
                                                }
                                            )
                                            culturalEvents.forEach { evt ->
                                                DropdownMenuItem(
                                                    text = { Text(evt.name) },
                                                    onClick = {
                                                        selectedCulturalFilterId = evt.id
                                                        showCulturalDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Sort Selector
                                    Box {
                                        FilterChip(
                                            selected = true,
                                            onClick = { showSortDropdown = true },
                                            label = { Text("Sort: ${sortOption.label}") },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Filled.Sort,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        )
                                        DropdownMenu(
                                            expanded = showSortDropdown,
                                            onDismissRequest = { showSortDropdown = false }
                                        ) {
                                            ParticipantSortOption.entries.forEach { opt ->
                                                DropdownMenuItem(
                                                    text = { Text(opt.label) },
                                                    onClick = {
                                                        sortOption = opt
                                                        showSortDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (filteredParticipants.isEmpty()) {
                        item(key = "empty_participants") {
                            EmptyStateCard(
                                icon = Icons.Filled.Groups,
                                title = if (allParticipants.isEmpty()) "No Registrations Yet" else "No Matching Students",
                                subtitle = if (allParticipants.isEmpty()) {
                                    "Student submissions will appear here live in real time as soon as students register."
                                } else {
                                    "Try clearing your search or event filters."
                                }
                            )
                        }
                    } else {
                        items(filteredParticipants, key = { it.id }) { participant ->
                            ParticipantRecordCard(
                                participant = participant,
                                canEdit = canEdit,
                                canDelete = canDelete,
                                onEdit = { editingParticipant = participant },
                                onDelete = { deletingParticipant = participant }
                            )
                        }
                    }
                }
            }
        }
    }

    editingParticipant?.let { target ->
        EditParticipantDialog(
            participant = target,
            allSports = events.filter { it.categoryEnum == EventCategory.SPORTS && !it.archived },
            allCultural = events.filter { it.categoryEnum == EventCategory.CULTURAL && !it.archived },
            isSubmitting = isSubmitting,
            onDismiss = { editingParticipant = null },
            onSave = { newName, newRoll, newSports, newCultural ->
                onUpdateParticipant(target, newName, newRoll, newSports, newCultural) {
                    editingParticipant = null
                }
            }
        )
    }

    deletingParticipant?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) deletingParticipant = null },
            title = { Text("Delete Participant Record?") },
            text = {
                Text(
                    "Remove ${target.studentName} (Roll ${target.rollNumber}) and all event selections? This action will be recorded in the audit log."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteParticipant(target) { deletingParticipant = null }
                    },
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonError)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deletingParticipant = null },
                    enabled = !isSubmitting
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParticipantRecordCard(
    participant: Participant,
    canEdit: Boolean,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = participant.studentName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "ROLL ${participant.rollNumber}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = ForestDeep,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Text(
                            text = SecurityUtils.formatShortTime(participant.submittedAt ?: participant.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (canEdit || canDelete) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (canEdit) {
                            IconButton(
                                onClick = onEdit,
                                modifier = Modifier.testTag("edit_participant_${participant.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = "Edit ${participant.studentName}",
                                    tint = ForestPrimary
                                )
                            }
                        }
                        if (canDelete) {
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.testTag("delete_participant_${participant.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "Delete ${participant.studentName}",
                                    tint = CrimsonError
                                )
                            }
                        }
                    }
                }
            }

            if (participant.sportsEventNames.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "SPORTS (${participant.sportsEventNames.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = ForestPrimary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        participant.sportsEventNames.forEach { name ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = EmeraldSoft,
                                border = BorderStroke(1.dp, EmeraldBorder)
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ForestDeep,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (participant.culturalEventNames.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "CULTURAL (${participant.culturalEventNames.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = CrestGoldDeep
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        participant.culturalEventNames.forEach { name ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = CrestGoldWash,
                                border = BorderStroke(1.dp, CrestGoldBorder)
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CrestGoldDeep,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditParticipantDialog(
    participant: Participant,
    allSports: List<PortalEvent>,
    allCultural: List<PortalEvent>,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, List<PortalEvent>, List<PortalEvent>) -> Unit
) {
    var name by remember { mutableStateOf(participant.studentName) }
    var roll by remember { mutableStateOf(participant.rollNumber) }
    var selectedSportsIds by remember { mutableStateOf(participant.sportsEventIds.toSet()) }
    var selectedCulturalIds by remember { mutableStateOf(participant.culturalEventIds.toSet()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Edit Participant") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 100) name = it; error = null },
                    label = { Text("Student Full Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = roll,
                    onValueChange = { if (it.length <= 40) roll = it; error = null },
                    label = { Text("Roll Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()
                Text(
                    text = "SPORTS EVENTS (${selectedSportsIds.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = ForestPrimary
                )
                allSports.forEach { evt ->
                    val checked = evt.id in selectedSportsIds
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                selectedSportsIds = if (it) selectedSportsIds + evt.id else selectedSportsIds - evt.id
                            },
                            colors = CheckboxDefaults.colors(checkedColor = ForestPrimary)
                        )
                        Text(evt.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                HorizontalDivider()
                Text(
                    text = "CULTURAL EVENTS (${selectedCulturalIds.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = CrestGoldDeep
                )
                allCultural.forEach { evt ->
                    val checked = evt.id in selectedCulturalIds
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                selectedCulturalIds = if (it) selectedCulturalIds + evt.id else selectedCulturalIds - evt.id
                            },
                            colors = CheckboxDefaults.colors(checkedColor = CrestGoldDeep)
                        )
                        Text(evt.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (error != null) {
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = CrimsonError
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanName = name.trim()
                    val normRoll = SecurityUtils.normalizeRollNumber(roll)
                    val chosenSports = allSports.filter { it.id in selectedSportsIds }
                    val chosenCultural = allCultural.filter { it.id in selectedCulturalIds }
                    when {
                        cleanName.length < 2 -> error = "Enter a valid student name."
                        !SecurityUtils.isValidNormalizedRoll(normRoll) -> error = "Enter a valid roll number."
                        chosenSports.isEmpty() && chosenCultural.isEmpty() ->
                            error = "Select at least one event."
                        else -> onSave(cleanName, normRoll, chosenSports, chosenCultural)
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary)
            ) {
                Text(if (isSubmitting) "Saving..." else "Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancel")
            }
        }
    )
}

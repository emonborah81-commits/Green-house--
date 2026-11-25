package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.AuditLog
import com.example.data.model.ManagementInvite
import com.example.data.model.ManagementPermissions
import com.example.data.model.ManagementProfile
import com.example.data.model.ManagementRole
import com.example.data.model.Participant
import com.example.data.model.PortalEvent
import com.example.data.model.PortalSettings
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.PortalStatusPill
import com.example.ui.components.RoleBadge
import com.example.ui.theme.CrestGoldBorder
import com.example.ui.theme.CrestGoldDeep
import com.example.ui.theme.CrestGoldWash
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldSoft
import com.example.ui.theme.ForestDeep
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PureWhite
import com.example.ui.viewmodel.UiState
import com.example.util.CsvExportType
import com.example.util.CsvExporter
import com.example.util.SecurityUtils

enum class PortalSubSection(val label: String) {
    EXPORTS("CSV Reports"),
    REGISTRATION("Registration"),
    TEAM("Team & Codes"),
    AUDIT("Audit Logs")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlsAndReportsScreen(
    role: ManagementRole,
    currentUserId: String,
    settings: PortalSettings?,
    participants: List<Participant>,
    events: List<PortalEvent>,
    teamState: UiState<List<ManagementProfile>>,
    invitesState: UiState<List<ManagementInvite>>,
    auditLogsState: UiState<List<AuditLog>>,
    generatedSetupCode: String?,
    isSubmitting: Boolean,
    onSaveSettings: (Boolean, String, Long, Long, String) -> Unit,
    onIssueSetupCode: (ManagementRole, String, String) -> Unit,
    onDismissGeneratedCode: () -> Unit,
    onUpdateTeamMember: (ManagementProfile, ManagementRole, Boolean) -> Unit,
    onRevokeInvite: (ManagementInvite) -> Unit,
    onNotify: (String, Boolean) -> Unit
) {
    val availableSections = remember(role) {
        buildList {
            if (ManagementPermissions.canExportCsv(role)) add(PortalSubSection.EXPORTS)
            if (ManagementPermissions.canControlRegistration(role)) add(PortalSubSection.REGISTRATION)
            if (ManagementPermissions.canManageTeam(role)) add(PortalSubSection.TEAM)
            if (ManagementPermissions.canViewAuditLogs(role)) add(PortalSubSection.AUDIT)
        }
    }

    var activeSection by rememberSaveable(role) {
        mutableStateOf(availableSections.firstOrNull() ?: PortalSubSection.EXPORTS)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 880.dp)
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                availableSections.forEach { section ->
                    FilterChip(
                        selected = activeSection == section,
                        onClick = { activeSection = section },
                        label = { Text(section.label) },
                        modifier = Modifier.testTag("portal_section_${section.name.lowercase()}")
                    )
                }
            }

            when (activeSection) {
                PortalSubSection.EXPORTS -> CsvExportSection(
                    participants = participants,
                    events = events,
                    onNotify = onNotify
                )

                PortalSubSection.REGISTRATION -> RegistrationControlSection(
                    settings = settings,
                    isSubmitting = isSubmitting,
                    onSaveSettings = onSaveSettings
                )

                PortalSubSection.TEAM -> TeamAndCodesSection(
                    currentUserId = currentUserId,
                    teamState = teamState,
                    invitesState = invitesState,
                    isSubmitting = isSubmitting,
                    onIssueSetupCode = onIssueSetupCode,
                    onUpdateTeamMember = onUpdateTeamMember,
                    onRevokeInvite = onRevokeInvite
                )

                PortalSubSection.AUDIT -> AuditLogsSection(auditLogsState = auditLogsState)
            }
        }
    }

    // One-time setup code display modal (shown ONCE after creation)
    generatedSetupCode?.let { rawCode ->
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = onDismissGeneratedCode,
            icon = { Icon(Icons.Filled.Key, contentDescription = null, tint = CrestGoldDeep) },
            title = { Text("One-Time Setup Code Ready") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Share this single-use code securely with the assigned management member. For security, only its SHA-256 hash is stored and it cannot be viewed again.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = CrestGoldWash,
                        border = BorderStroke(1.dp, CrestGoldBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = rawCode,
                                style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = ForestDeep,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(rawCode))
                                    onNotify("Setup code copied to clipboard.", false)
                                }
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy setup code")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onDismissGeneratedCode,
                    colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary)
                ) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
private fun CsvExportSection(
    participants: List<Participant>,
    events: List<PortalEvent>,
    onNotify: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    var selectedEvent by remember(events) { mutableStateOf(events.firstOrNull { !it.archived }) }
    var showEventPicker by remember { mutableStateOf(false) }
    var pendingCsvContent by remember { mutableStateOf<String?>(null) }

    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val content = pendingCsvContent
        pendingCsvContent = null
        if (uri != null && content != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(content.toByteArray(Charsets.UTF_8))
                }
                onNotify("CSV report saved successfully (Excel & Sheets compatible).", false)
            } catch (e: Exception) {
                onNotify("Failed to save CSV: ${e.localizedMessage}", true)
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "csv_intro") {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = EmeraldSoft,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "All CSV downloads include UTF-8 BOM for Excel & Google Sheets compatibility and neutralize spreadsheet formula injection automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ForestDeep,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        items(CsvExportType.entries) { exportType ->
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = null,
                            tint = ForestPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = exportType.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = exportType.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (exportType == CsvExportType.EVENT_SPECIFIC) {
                        Box {
                            OutlinedButton(
                                onClick = { showEventPicker = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(selectedEvent?.let { "Event: ${it.name} (${it.category})" } ?: "Select an Event")
                            }
                            DropdownMenu(
                                expanded = showEventPicker,
                                onDismissRequest = { showEventPicker = false }
                            ) {
                                events.forEach { evt ->
                                    DropdownMenuItem(
                                        text = { Text("${evt.name} (${evt.category})") },
                                        onClick = {
                                            selectedEvent = evt
                                            showEventPicker = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (exportType == CsvExportType.EVENT_SPECIFIC && selectedEvent == null) {
                                    onNotify("Select an event first.", true)
                                    return@Button
                                }
                                val (fileName, csv) = CsvExporter.buildCsvContent(
                                    participants = participants,
                                    type = exportType,
                                    specificEvent = selectedEvent
                                )
                                pendingCsvContent = csv
                                saveDocumentLauncher.launch(fileName)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ForestPrimary,
                                contentColor = PureWhite
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_csv_${exportType.name.lowercase()}")
                        ) {
                            Icon(Icons.Filled.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save CSV")
                        }

                        OutlinedButton(
                            onClick = {
                                if (exportType == CsvExportType.EVENT_SPECIFIC && selectedEvent == null) {
                                    onNotify("Select an event first.", true)
                                    return@OutlinedButton
                                }
                                val (fileName, csv) = CsvExporter.buildCsvContent(
                                    participants = participants,
                                    type = exportType,
                                    specificEvent = selectedEvent
                                )
                                CsvExporter.shareCsv(context, fileName, csv)
                                    .onFailure { onNotify("Unable to share CSV: ${it.localizedMessage}", true) }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("share_csv_${exportType.name.lowercase()}")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegistrationControlSection(
    settings: PortalSettings?,
    isSubmitting: Boolean,
    onSaveSettings: (Boolean, String, Long, Long, String) -> Unit
) {
    var registrationOpen by remember(settings?.registrationOpen) {
        mutableStateOf(settings?.registrationOpen ?: true)
    }
    var scope by remember(settings?.registrationScope) {
        mutableStateOf(settings?.registrationScope ?: "2026")
    }
    var maxSports by remember(settings?.maxSportsEvents) {
        mutableStateOf((settings?.maxSportsEvents ?: 3L).toString())
    }
    var maxCultural by remember(settings?.maxCulturalEvents) {
        mutableStateOf((settings?.maxCulturalEvents ?: 3L).toString())
    }
    var closedMsg by remember(settings?.closedMessage) {
        mutableStateOf(
            settings?.closedMessage ?: "Student registrations are currently closed by Green House Management."
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "reg_control_card") {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Registration Gate", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = "Enforced server-side via Firestore security rules.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = registrationOpen,
                            onCheckedChange = { registrationOpen = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PureWhite,
                                checkedTrackColor = ForestPrimary
                            ),
                            modifier = Modifier.testTag("registration_open_switch")
                        )
                    }

                    PortalStatusPill(isOpen = registrationOpen)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    OutlinedTextField(
                        value = scope,
                        onValueChange = { if (it.length <= 32) scope = it },
                        label = { Text("Active Registration Scope / Year") },
                        placeholder = { Text("e.g., 2026") },
                        supportingText = { Text("Duplicate roll numbers are prevented per scope.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = maxSports,
                            onValueChange = { if (it.length <= 2) maxSports = it.filter(Char::isDigit) },
                            label = { Text("Max Sports (1–20)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = maxCultural,
                            onValueChange = { if (it.length <= 2) maxCultural = it.filter(Char::isDigit) },
                            label = { Text("Max Cultural (1–20)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = closedMsg,
                        onValueChange = { if (it.length <= 300) closedMsg = it },
                        label = { Text("Closed Registration Notice") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            val ms = (maxSports.toLongOrNull() ?: 3L).coerceIn(1L, 20L)
                            val mc = (maxCultural.toLongOrNull() ?: 3L).coerceIn(1L, 20L)
                            onSaveSettings(registrationOpen, scope.trim(), ms, mc, closedMsg.trim())
                        },
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ForestPrimary,
                            contentColor = PureWhite
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_registration_settings_button")
                    ) {
                        Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isSubmitting) "Saving..." else "Apply Registration Controls")
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamAndCodesSection(
    currentUserId: String,
    teamState: UiState<List<ManagementProfile>>,
    invitesState: UiState<List<ManagementInvite>>,
    isSubmitting: Boolean,
    onIssueSetupCode: (ManagementRole, String, String) -> Unit,
    onUpdateTeamMember: (ManagementProfile, ManagementRole, Boolean) -> Unit,
    onRevokeInvite: (ManagementInvite) -> Unit
) {
    var showIssueDialog by remember { mutableStateOf(false) }

    val members = (teamState as? UiState.Success)?.data.orEmpty()
    val invites = (invitesState as? UiState.Success)?.data.orEmpty()

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "issue_code_card") {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Onboard Management Accounts", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Generate a single-use cryptographic setup code bound to a specific role (Captain, Vice Captain, Volunteer, or Teacher). Codes are hashed via SHA-256 and burned after one activation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { showIssueDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ForestPrimary,
                            contentColor = PureWhite
                        ),
                        modifier = Modifier.testTag("issue_setup_code_button")
                    ) {
                        Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate One-Time Setup Code")
                    }
                }
            }
        }

        item(key = "team_header") {
            Text(
                text = "ACTIVE MANAGEMENT ACCOUNTS (${members.size})",
                style = MaterialTheme.typography.labelMedium,
                color = ForestPrimary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(members, key = { it.userId }) { member ->
            var showRoleMenu by remember { mutableStateOf(false) }
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = member.displayName + if (member.userId == currentUserId) " (You)" else "",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = member.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            member.roleEnum?.let { RoleBadge(role = it) }
                            if (!member.active) {
                                Text("REVOKED", style = MaterialTheme.typography.labelSmall, color = CrimsonError)
                            }
                        }
                    }

                    if (member.userId != currentUserId) {
                        Box {
                            TextButton(onClick = { showRoleMenu = true }) {
                                Text("Manage")
                            }
                            DropdownMenu(
                                expanded = showRoleMenu,
                                onDismissRequest = { showRoleMenu = false }
                            ) {
                                ManagementRole.entries.forEach { newRole ->
                                    DropdownMenuItem(
                                        text = { Text("Set ${newRole.displayName}") },
                                        onClick = {
                                            showRoleMenu = false
                                            onUpdateTeamMember(member, newRole, true)
                                        }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (member.active) "Revoke Access" else "Restore Access",
                                            color = if (member.active) CrimsonError else ForestPrimary
                                        )
                                    },
                                    onClick = {
                                        showRoleMenu = false
                                        onUpdateTeamMember(
                                            member,
                                            member.roleEnum ?: ManagementRole.VOLUNTEER,
                                            !member.active
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (invites.isNotEmpty()) {
            item(key = "invites_header") {
                Text(
                    text = "ISSUED SETUP PASSES (${invites.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = CrestGoldDeep,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            items(invites, key = { it.id }) { invite ->
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(invite.label, style = MaterialTheme.typography.titleSmall)
                            if (invite.targetEmail.isNotBlank()) {
                                Text(
                                    text = "Locked to: ${invite.targetEmail}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                invite.roleEnum?.let { RoleBadge(role = it) }
                                Text(
                                    text = if (invite.redeemed) "REDEEMED" else "ACTIVE PASS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (invite.redeemed) MaterialTheme.colorScheme.onSurfaceVariant else ForestPrimary
                                )
                            }
                        }
                        IconButton(onClick = { onRevokeInvite(invite) }) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Revoke pass",
                                tint = CrimsonError
                            )
                        }
                    }
                }
            }
        }
    }

    if (showIssueDialog) {
        var selectedRole by remember { mutableStateOf(ManagementRole.VICE_CAPTAIN) }
        var label by remember { mutableStateOf("") }
        var targetEmail by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showIssueDialog = false },
            title = { Text("Issue One-Time Setup Code") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Assign Role:", style = MaterialTheme.typography.labelMedium)
                    ManagementRole.entries.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { r ->
                                FilterChip(
                                    selected = selectedRole == r,
                                    onClick = { selectedRole = r },
                                    label = { Text(r.badgeLabel) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = label,
                        onValueChange = { if (it.length <= 100) label = it },
                        label = { Text("Pass Label (optional)") },
                        placeholder = { Text("e.g., Vice Captain 2026") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = targetEmail,
                        onValueChange = { if (it.length <= 120) targetEmail = it },
                        label = { Text("Restrict to Email (optional)") },
                        placeholder = { Text("staff@school.edu") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onIssueSetupCode(selectedRole, label, targetEmail)
                        showIssueDialog = false
                    },
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary)
                ) {
                    Text("Generate Code")
                }
            },
            dismissButton = {
                TextButton(onClick = { showIssueDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AuditLogsSection(auditLogsState: UiState<List<AuditLog>>) {
    when (auditLogsState) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading immutable audit log...")
            }
        }

        is UiState.Error -> {
            Box(modifier = Modifier.padding(16.dp)) {
                EmptyStateCard(
                    icon = Icons.Filled.History,
                    title = "Audit Log Unavailable",
                    subtitle = auditLogsState.message
                )
            }
        }

        is UiState.Success -> {
            val logs = auditLogsState.data
            if (logs.isEmpty()) {
                Box(modifier = Modifier.padding(16.dp)) {
                    EmptyStateCard(
                        icon = Icons.Filled.History,
                        title = "No Audit Entries Yet",
                        subtitle = "Management actions such as edits, deletions, event changes, and registration toggles are recorded here permanently."
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(logs, key = { it.id }) { log ->
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = EmeraldSoft
                                    ) {
                                        Text(
                                            text = log.action.replace('_', ' '),
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                            color = ForestDeep,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Text(
                                        text = SecurityUtils.formatShortTime(log.createdAt),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = log.details,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AdminPanelSettings,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "${log.actorName} • ${log.actorRole.replace('_', ' ')}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

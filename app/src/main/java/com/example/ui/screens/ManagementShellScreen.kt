package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AuditLog
import com.example.data.model.EventCategory
import com.example.data.model.ManagementInvite
import com.example.data.model.ManagementPermissions
import com.example.data.model.ManagementProfile
import com.example.data.model.ManagementRole
import com.example.data.model.Participant
import com.example.data.model.PortalEvent
import com.example.data.model.PortalSettings
import com.example.ui.components.GreenHouseCrest
import com.example.ui.components.KpiStatCard
import com.example.ui.components.PortalStatusPill
import com.example.ui.components.RoleBadge
import com.example.ui.theme.CrestGoldDeep
import com.example.ui.theme.EmeraldMuted
import com.example.ui.theme.EmeraldSoft
import com.example.ui.theme.ForestDeep
import com.example.ui.theme.ForestPrimary
import com.example.ui.viewmodel.DashboardSummary
import com.example.ui.viewmodel.EventStatisticRow
import com.example.ui.viewmodel.UiState
import com.example.util.SecurityUtils

enum class ManagementTab(val label: String, val icon: ImageVector) {
    OVERVIEW("Overview", Icons.Filled.Dashboard),
    PARTICIPANTS("Participants", Icons.Filled.Groups),
    EVENTS("Events", Icons.Filled.EmojiEvents),
    STATISTICS("Stats", Icons.Filled.BarChart),
    CONTROLS("Portal", Icons.Filled.Tune)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagementShellScreen(
    managerProfile: ManagementProfile,
    settings: PortalSettings?,
    summary: DashboardSummary,
    participantsState: UiState<List<Participant>>,
    eventsState: UiState<List<PortalEvent>>,
    eventStats: List<EventStatisticRow>,
    teamState: UiState<List<ManagementProfile>>,
    invitesState: UiState<List<ManagementInvite>>,
    auditLogsState: UiState<List<AuditLog>>,
    generatedSetupCode: String?,
    isSubmitting: Boolean,
    onUpdateParticipant: (Participant, String, String, List<PortalEvent>, List<PortalEvent>, () -> Unit) -> Unit,
    onDeleteParticipant: (Participant, () -> Unit) -> Unit,
    onAddEvent: (String, EventCategory, () -> Unit) -> Unit,
    onRenameEvent: (PortalEvent, String, () -> Unit) -> Unit,
    onToggleEventEnabled: (PortalEvent, Boolean) -> Unit,
    onArchiveOrDeleteEvent: (PortalEvent, Int, Boolean, () -> Unit) -> Unit,
    onSaveSettings: (Boolean, String, Long, Long, String) -> Unit,
    onIssueSetupCode: (ManagementRole, String, String) -> Unit,
    onDismissGeneratedCode: () -> Unit,
    onUpdateTeamMember: (ManagementProfile, ManagementRole, Boolean) -> Unit,
    onRevokeInvite: (ManagementInvite) -> Unit,
    onNotify: (String, Boolean) -> Unit,
    onGoHome: () -> Unit,
    onSignOut: () -> Unit
) {
    val role = managerProfile.roleEnum ?: ManagementRole.VOLUNTEER
    val allowedTabs = remember(role) {
        buildList {
            add(ManagementTab.OVERVIEW)
            add(ManagementTab.PARTICIPANTS)
            if (ManagementPermissions.canManageEvents(role)) add(ManagementTab.EVENTS)
            if (ManagementPermissions.canViewStatistics(role)) add(ManagementTab.STATISTICS)
            if (ManagementPermissions.canExportCsv(role) || ManagementPermissions.canControlRegistration(role)) {
                add(ManagementTab.CONTROLS)
            }
        }
    }

    var currentTab by rememberSaveable(role) { mutableStateOf(ManagementTab.OVERVIEW) }
    if (currentTab !in allowedTabs) {
        currentTab = ManagementTab.OVERVIEW
    }

    BackHandler {
        if (currentTab != ManagementTab.OVERVIEW) {
            currentTab = ManagementTab.OVERVIEW
        } else {
            onGoHome()
        }
    }

    val allEvents = (eventsState as? UiState.Success)?.data.orEmpty()
    val allParticipants = (participantsState as? UiState.Success)?.data.orEmpty()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 720.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GreenHouseCrest(size = 36.dp, framed = false)
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Green House",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = EmeraldSoft
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(EmeraldMuted)
                                            )
                                            Text(
                                                text = "LIVE",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ForestDeep,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${role.displayName} • ${currentTab.label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onGoHome,
                            modifier = Modifier.testTag("dashboard_home_button")
                        ) {
                            Icon(Icons.Filled.Home, contentDescription = "Return to Home")
                        }
                        IconButton(
                            onClick = onSignOut,
                            modifier = Modifier.testTag("dashboard_sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        allowedTabs.forEach { tab ->
                            NavigationBarItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        allowedTabs.forEach { tab ->
                            NavigationRailItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (currentTab) {
                        ManagementTab.OVERVIEW -> DashboardOverviewTab(
                            profile = managerProfile,
                            role = role,
                            settings = settings,
                            summary = summary,
                            onNavigate = { tab ->
                                if (tab in allowedTabs) currentTab = tab
                            }
                        )

                        ManagementTab.PARTICIPANTS -> ParticipantsScreen(
                            role = role,
                            participantsState = participantsState,
                            events = allEvents,
                            isSubmitting = isSubmitting,
                            onUpdateParticipant = onUpdateParticipant,
                            onDeleteParticipant = onDeleteParticipant,
                            onNavigateToExports = {
                                if (ManagementTab.CONTROLS in allowedTabs) {
                                    currentTab = ManagementTab.CONTROLS
                                }
                            }
                        )

                        ManagementTab.EVENTS -> EventsScreen(
                            role = role,
                            eventsState = eventsState,
                            eventStats = eventStats,
                            isSubmitting = isSubmitting,
                            onAddEvent = onAddEvent,
                            onRenameEvent = onRenameEvent,
                            onToggleEnabled = onToggleEventEnabled,
                            onArchiveOrDelete = onArchiveOrDeleteEvent
                        )

                        ManagementTab.STATISTICS -> StatisticsScreen(
                            summary = summary,
                            eventStats = eventStats
                        )

                        ManagementTab.CONTROLS -> ControlsAndReportsScreen(
                            role = role,
                            currentUserId = managerProfile.userId,
                            settings = settings,
                            participants = allParticipants,
                            events = allEvents,
                            teamState = teamState,
                            invitesState = invitesState,
                            auditLogsState = auditLogsState,
                            generatedSetupCode = generatedSetupCode,
                            isSubmitting = isSubmitting,
                            onSaveSettings = onSaveSettings,
                            onIssueSetupCode = onIssueSetupCode,
                            onDismissGeneratedCode = onDismissGeneratedCode,
                            onUpdateTeamMember = onUpdateTeamMember,
                            onRevokeInvite = onRevokeInvite,
                            onNotify = onNotify
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardOverviewTab(
    profile: ManagementProfile,
    role: ManagementRole,
    settings: PortalSettings?,
    summary: DashboardSummary,
    onNavigate: (ManagementTab) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 880.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Management Identity & Live Status Banner
            item(key = "overview_identity") {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = profile.displayName.ifBlank { "Green House Management" },
                                    style = MaterialTheme.typography.headlineMedium
                                )
                                Text(
                                    text = profile.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            RoleBadge(role = role)
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PortalStatusPill(isOpen = settings?.registrationOpen ?: true)
                            Text(
                                text = "Scope: ${settings?.registrationScope ?: "2026"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Real-time KPI Grid (100% live Firestore counts)
            item(key = "overview_kpis") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        KpiStatCard(
                            label = "Total Participants",
                            value = "${summary.totalParticipants}",
                            subtitle = "Unique registered students",
                            icon = Icons.Filled.Groups,
                            accentColor = ForestPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        KpiStatCard(
                            label = "Total Events",
                            value = "${summary.totalAllEvents}",
                            subtitle = "${summary.totalActiveEvents} active on form",
                            icon = Icons.Filled.EmojiEvents,
                            accentColor = CrestGoldDeep,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        KpiStatCard(
                            label = "Sports Entries",
                            value = "${summary.totalSportsEntries}",
                            subtitle = "Total sports selections",
                            icon = Icons.Filled.DirectionsRun,
                            accentColor = ForestPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        KpiStatCard(
                            label = "Cultural Entries",
                            value = "${summary.totalCulturalEntries}",
                            subtitle = "Total cultural selections",
                            icon = Icons.Filled.MusicNote,
                            accentColor = CrestGoldDeep,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Latest Submission Card
            item(key = "latest_submission") {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = ForestPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("Latest Submission", style = MaterialTheme.typography.titleMedium)
                        }

                        val latest = summary.latestParticipant
                        if (latest == null) {
                            Text(
                                text = "No student registrations recorded yet. Live updates will appear here automatically.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "${latest.studentName} • Roll ${latest.rollNumber}",
                                style = MaterialTheme.typography.titleLarge,
                                color = ForestDeep
                            )
                            Text(
                                text = "${latest.sportsEventNames.size} Sports, ${latest.culturalEventNames.size} Cultural • ${SecurityUtils.formatTimestamp(latest.submittedAt ?: latest.createdAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Quick Actions
            item(key = "quick_actions") {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Quick Navigation", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onNavigate(ManagementTab.PARTICIPANTS) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Participants")
                            }
                            if (ManagementPermissions.canManageEvents(role)) {
                                OutlinedButton(
                                    onClick = { onNavigate(ManagementTab.EVENTS) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Manage Events")
                                }
                            }
                        }
                        if (ManagementPermissions.canViewStatistics(role)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onNavigate(ManagementTab.STATISTICS) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Statistics")
                                }
                                OutlinedButton(
                                    onClick = { onNavigate(ManagementTab.CONTROLS) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("CSV & Controls")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

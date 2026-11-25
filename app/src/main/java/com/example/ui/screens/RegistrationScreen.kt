package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.EventCategory
import com.example.data.model.Participant
import com.example.data.model.PortalEvent
import com.example.data.model.PortalSettings
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.EventSelectionCard
import com.example.ui.components.GreenHouseCrest
import com.example.ui.components.PortalStatusPill
import com.example.ui.theme.CrestGold
import com.example.ui.theme.CrestGoldBorder
import com.example.ui.theme.CrestGoldDeep
import com.example.ui.theme.CrestGoldWash
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CrimsonWash
import com.example.ui.theme.EmeraldBorder
import com.example.ui.theme.EmeraldMuted
import com.example.ui.theme.EmeraldSoft
import com.example.ui.theme.ForestDeep
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PureWhite
import com.example.ui.viewmodel.UiState
import com.example.util.SecurityUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(
    settingsState: UiState<PortalSettings?>,
    activeEventsState: UiState<List<PortalEvent>>,
    isSubmitting: Boolean,
    submittedParticipant: Participant?,
    isBootstrappedOwner: Boolean,
    onInitializePortal: () -> Unit,
    onSubmit: (
        studentName: String,
        rollNumber: String,
        selectedSports: List<PortalEvent>,
        selectedCultural: List<PortalEvent>
    ) -> Unit,
    onRegisterAnother: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    if (submittedParticipant != null) {
        RegistrationSuccessScreen(
            participant = submittedParticipant,
            onRegisterAnother = onRegisterAnother,
            onReturnHome = {
                onRegisterAnother()
                onBack()
            }
        )
        return
    }

    var studentName by rememberSaveable { mutableStateOf("") }
    var rollNumber by rememberSaveable { mutableStateOf("") }
    var selectedSportsIds by remember { mutableStateOf(setOf<String>()) }
    var selectedCulturalIds by remember { mutableStateOf(setOf<String>()) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val normalizedRollPreview = remember(rollNumber) {
        SecurityUtils.normalizeRollNumber(rollNumber)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GreenHouseCrest(size = 34.dp, framed = false)
                        Column {
                            Text(
                                text = "Student Registration",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "GREEN HOUSE • SPORTS & CULTURAL WEEK",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("registration_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Home"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when {
                settingsState is UiState.Loading || activeEventsState is UiState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = ForestPrimary)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Loading active Green House events...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                settingsState is UiState.Error -> {
                    Box(modifier = Modifier.padding(20.dp)) {
                        EmptyStateCard(
                            icon = Icons.Filled.Schedule,
                            title = "Unable to Load Portal",
                            subtitle = settingsState.message
                        )
                    }
                }

                activeEventsState is UiState.Error -> {
                    Box(modifier = Modifier.padding(20.dp)) {
                        EmptyStateCard(
                            icon = Icons.Filled.Schedule,
                            title = "Unable to Load Events",
                            subtitle = activeEventsState.message
                        )
                    }
                }

                else -> {
                    val settings = (settingsState as? UiState.Success)?.data
                    val activeEvents = (activeEventsState as? UiState.Success)?.data.orEmpty()

                    if (settings == null) {
                        Box(
                            modifier = Modifier
                                .padding(20.dp)
                                .widthIn(max = 560.dp)
                        ) {
                            EmptyStateCard(
                                icon = Icons.Filled.Schedule,
                                title = "Portal Setup Pending",
                                subtitle = "Green House Management has not initialized the current registration session yet.",
                                actionLabel = if (isBootstrappedOwner) "Initialize Portal & Seed Events" else null,
                                onAction = if (isBootstrappedOwner) onInitializePortal else null
                            )
                        }
                    } else if (!settings.registrationOpen) {
                        RegistrationClosedView(
                            settings = settings,
                            onBack = onBack
                        )
                    } else {
                        val sportsEvents = remember(activeEvents) {
                            activeEvents.filter { it.categoryEnum == EventCategory.SPORTS && it.enabled && !it.archived }
                        }
                        val culturalEvents = remember(activeEvents) {
                            activeEvents.filter { it.categoryEnum == EventCategory.CULTURAL && it.enabled && !it.archived }
                        }

                        val maxSports = settings.maxSportsEvents.toInt()
                        val maxCultural = settings.maxCulturalEvents.toInt()

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 680.dp)
                                .verticalScroll(rememberScrollState())
                                .navigationBarsPadding()
                                .padding(horizontal = 20.dp, vertical = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            // Header Banner
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = EmeraldSoft,
                                border = BorderStroke(1.dp, EmeraldBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Active Session: ${settings.registrationScope}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = ForestDeep
                                        )
                                        Text(
                                            text = "Select up to $maxSports Sports and up to $maxCultural Cultural events.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ForestPrimary
                                        )
                                    }
                                    PortalStatusPill(isOpen = true)
                                }
                            }

                            // Student Details Card
                            Card(
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Text(
                                        text = "STUDENT INFORMATION",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ForestPrimary,
                                        letterSpacing = 1.sp
                                    )

                                    OutlinedTextField(
                                        value = studentName,
                                        onValueChange = {
                                            if (it.length <= 100) {
                                                studentName = it
                                                validationError = null
                                            }
                                        },
                                        label = { Text("Student Full Name *") },
                                        placeholder = { Text("e.g., Aarav Sharma") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Person, contentDescription = null)
                                        },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            capitalization = KeyboardCapitalization.Words,
                                            imeAction = ImeAction.Next
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("student_name_input")
                                    )

                                    OutlinedTextField(
                                        value = rollNumber,
                                        onValueChange = {
                                            if (it.length <= 40) {
                                                rollNumber = it
                                                validationError = null
                                            }
                                        },
                                        label = { Text("Roll Number *") },
                                        placeholder = { Text("e.g., 10A-14 or 204") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Badge, contentDescription = null)
                                        },
                                        supportingText = {
                                            if (normalizedRollPreview.isNotEmpty()) {
                                                Text(
                                                    text = "Normalized ID: $normalizedRollPreview (unique per ${settings.registrationScope})",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            } else {
                                                Text("Letters, digits, and hyphens only. Duplicate roll numbers are prevented.")
                                            }
                                        },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            capitalization = KeyboardCapitalization.Characters,
                                            imeAction = ImeAction.Done
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("roll_number_input")
                                    )
                                }
                            }

                            // SPORTS EVENTS Section
                            EventCategorySection(
                                title = "SPORTS EVENTS",
                                icon = Icons.Filled.DirectionsRun,
                                accentColor = ForestPrimary,
                                events = sportsEvents,
                                selectedIds = selectedSportsIds,
                                maxSelectable = maxSports,
                                onToggle = { eventId ->
                                    validationError = null
                                    selectedSportsIds = if (eventId in selectedSportsIds) {
                                        selectedSportsIds - eventId
                                    } else if (selectedSportsIds.size < maxSports) {
                                        selectedSportsIds + eventId
                                    } else {
                                        validationError = "You can select up to $maxSports sports events."
                                        selectedSportsIds
                                    }
                                }
                            )

                            // CULTURAL EVENTS Section
                            EventCategorySection(
                                title = "CULTURAL EVENTS",
                                icon = Icons.Filled.MusicNote,
                                accentColor = CrestGoldDeep,
                                events = culturalEvents,
                                selectedIds = selectedCulturalIds,
                                maxSelectable = maxCultural,
                                onToggle = { eventId ->
                                    validationError = null
                                    selectedCulturalIds = if (eventId in selectedCulturalIds) {
                                        selectedCulturalIds - eventId
                                    } else if (selectedCulturalIds.size < maxCultural) {
                                        selectedCulturalIds + eventId
                                    } else {
                                        validationError = "You can select up to $maxCultural cultural events."
                                        selectedCulturalIds
                                    }
                                }
                            )

                            if (validationError != null) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = CrimsonWash,
                                    border = BorderStroke(1.dp, CrimsonError.copy(alpha = 0.35f))
                                ) {
                                    Text(
                                        text = validationError!!,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = CrimsonError,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp)
                                    )
                                }
                            }

                            // Submit Participation CTA
                            Button(
                                onClick = {
                                    val cleanName = studentName.trim().replace(Regex("\\s+"), " ")
                                    val normalized = SecurityUtils.normalizeRollNumber(rollNumber)
                                    val chosenSports = sportsEvents.filter { it.id in selectedSportsIds }
                                    val chosenCultural = culturalEvents.filter { it.id in selectedCulturalIds }

                                    when {
                                        cleanName.length < 2 ->
                                            validationError = "Please enter the student's full name (at least 2 characters)."
                                        !SecurityUtils.isValidNormalizedRoll(normalized) ->
                                            validationError = "Please enter a valid roll number."
                                        chosenSports.isEmpty() && chosenCultural.isEmpty() ->
                                            validationError = "Please select at least one Sports or Cultural event."
                                        else -> {
                                            validationError = null
                                            onSubmit(cleanName, normalized, chosenSports, chosenCultural)
                                        }
                                    }
                                },
                                enabled = !isSubmitting,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .testTag("submit_participation_button"),
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ForestPrimary,
                                    contentColor = PureWhite
                                )
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = PureWhite,
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("RECORDING PARTICIPATION...")
                                } else {
                                    Text(
                                        text = stringResource(id = R.string.cta_submit_participation),
                                        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.8.sp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventCategorySection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: androidx.compose.ui.graphics.Color,
    events: List<PortalEvent>,
    selectedIds: Set<String>,
    maxSelectable: Int,
    onToggle: (String) -> Unit
) {
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        letterSpacing = 0.8.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = accentColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "${selectedIds.size} / $maxSelectable",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            if (events.isEmpty()) {
                Text(
                    text = "No active events available in this category.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // 2-column responsive grid built cleanly inside Column
                events.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { event ->
                            val isSelected = event.id in selectedIds
                            EventSelectionCard(
                                event = event,
                                selected = isSelected,
                                enabled = selectedIds.size < maxSelectable,
                                onToggle = { onToggle(event.id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("event_card_${event.id}")
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegistrationClosedView(
    settings: PortalSettings,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(CrimsonWash),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = CrimsonError,
                modifier = Modifier.size(32.dp)
            )
        }
        PortalStatusPill(isOpen = false)
        Text(
            text = "Registrations Currently Closed",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = settings.closedMessage.ifBlank { stringResource(id = R.string.registration_closed_default) },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onBack) {
            Text("Return to Home")
        }
    }
}

@Composable
private fun RegistrationSuccessScreen(
    participant: Participant,
    onRegisterAnother: () -> Unit,
    onReturnHome: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        GreenHouseCrest(size = 104.dp, framed = true)
        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(EmeraldSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Success",
                tint = ForestPrimary,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = stringResource(id = R.string.registration_success_title),
            style = MaterialTheme.typography.headlineLarge.copy(letterSpacing = 1.sp),
            color = ForestDeep,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = stringResource(id = R.string.registration_success_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 460.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        Surface(
            shape = RoundedCornerShape(50),
            color = CrestGoldWash,
            border = BorderStroke(1.dp, CrestGoldBorder)
        ) {
            Text(
                text = stringResource(id = R.string.house_motto),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp),
                color = CrestGoldDeep,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "PARTICIPATION RECEIPT",
                    style = MaterialTheme.typography.labelSmall,
                    color = ForestPrimary
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ReceiptRow("Student", participant.studentName)
                ReceiptRow("Roll Number", participant.rollNumber)
                ReceiptRow("Session", participant.registrationScope)
                if (participant.sportsEventNames.isNotEmpty()) {
                    ReceiptRow("Sports", participant.sportsEventNames.joinToString(", "))
                }
                if (participant.culturalEventNames.isNotEmpty()) {
                    ReceiptRow("Cultural", participant.culturalEventNames.joinToString(", "))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onReturnHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("success_return_home_button"),
                colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary)
            ) {
                Text("RETURN TO HOME")
            }
            OutlinedButton(
                onClick = onRegisterAnother,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("success_register_another_button")
            ) {
                Text("REGISTER ANOTHER STUDENT")
            }
        }
    }
}

@Composable
private fun ReceiptRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(104.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

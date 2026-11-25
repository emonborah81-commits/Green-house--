package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.PortalRepository
import com.example.data.firebase.friendlyFirestoreMessage
import com.example.data.firebase.unwrapFirestoreThrowable
import com.example.data.model.AuditLog
import com.example.data.model.EventCategory
import com.example.data.model.ManagementInvite
import com.example.data.model.ManagementPermissions
import com.example.data.model.ManagementProfile
import com.example.data.model.ManagementRole
import com.example.data.model.Participant
import com.example.data.model.PortalEvent
import com.example.data.model.PortalSettings
import com.example.util.ActionRateLimiter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class ParticipantSortOption(val label: String) {
    LATEST("Latest Submission"),
    NAME_ASC("Name (A–Z)"),
    ROLL_ASC("Roll Number")
}

data class EventStatisticRow(
    val event: PortalEvent,
    val participantCount: Int
)

data class DashboardSummary(
    val totalParticipants: Int = 0,
    val totalSportsEntries: Int = 0,
    val totalCulturalEntries: Int = 0,
    val totalActiveEvents: Int = 0,
    val totalAllEvents: Int = 0,
    val latestParticipant: Participant? = null
)

data class PortalBannerMessage(
    val text: String,
    val isError: Boolean = false,
    val id: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalCoroutinesApi::class)
class PortalViewModel(
    private val repository: PortalRepository,
    val currentUserId: String,
    val currentUserEmail: String,
    val currentUserDisplayName: String
) : ViewModel() {

    private val registrationRateLimiter = ActionRateLimiter(maxAttempts = 5, windowMillis = 60_000L)
    private val inviteRedeemLimiter = ActionRateLimiter(maxAttempts = 5, windowMillis = 120_000L)

    private val _bannerMessage = MutableStateFlow<PortalBannerMessage?>(null)
    val bannerMessage: StateFlow<PortalBannerMessage?> = _bannerMessage.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _lastSubmittedParticipant = MutableStateFlow<Participant?>(null)
    val lastSubmittedParticipant: StateFlow<Participant?> = _lastSubmittedParticipant.asStateFlow()

    private val _generatedSetupCode = MutableStateFlow<String?>(null)
    val generatedSetupCode: StateFlow<String?> = _generatedSetupCode.asStateFlow()

    val isBootstrappedOwner: Boolean = repository.isBootstrappedAdminCurrent()

    // --- PORTAL SETTINGS & STUDENT ACTIVE EVENTS ---

    val settingsState: StateFlow<UiState<PortalSettings?>> = repository.observeSettings()
        .map<PortalSettings?, UiState<PortalSettings?>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreThrowable(error)
            Log.w(TAG, "Error observing portal settings", root)
            emit(UiState.Error(friendlyFirestoreMessage(root, "Failed to load portal settings.")))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val activeEventsState: StateFlow<UiState<List<PortalEvent>>> = repository.observeActiveEvents()
        .map<List<PortalEvent>, UiState<List<PortalEvent>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreThrowable(error)
            Log.w(TAG, "Error observing active events", root)
            emit(UiState.Error(friendlyFirestoreMessage(root, "Failed to load active events.")))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    // --- MANAGEMENT PROFILE & ROLE-GATED REALTIME STREAMS ---

    val managementProfileState: StateFlow<UiState<ManagementProfile?>> =
        repository.observeMyManagementProfile(currentUserId)
            .map<ManagementProfile?, UiState<ManagementProfile?>> { profile ->
                val effective = profile?.takeIf { it.active } ?: if (isBootstrappedOwner) {
                    ManagementProfile(
                        id = currentUserId,
                        userId = currentUserId,
                        email = currentUserEmail,
                        displayName = currentUserDisplayName.ifBlank { "Green House Admin" },
                        role = ManagementRole.TEACHER.name,
                        active = true
                    )
                } else {
                    null
                }
                UiState.Success(effective)
            }
            .catch { error ->
                val root = unwrapFirestoreThrowable(error)
                Log.w(TAG, "Error observing management profile", root)
                emit(UiState.Error(friendlyFirestoreMessage(root, "Failed to load management profile.")))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    private val activeManagerProfile: StateFlow<ManagementProfile?> = managementProfileState
        .map { state -> (state as? UiState.Success)?.data?.takeIf { it.active } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val allEventsState: StateFlow<UiState<List<PortalEvent>>> = activeManagerProfile
        .flatMapLatest { manager ->
            if (manager == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeAllEvents()
                    .map<List<PortalEvent>, UiState<List<PortalEvent>>> { UiState.Success(it) }
                    .catch { error ->
                        val root = unwrapFirestoreThrowable(error)
                        Log.w(TAG, "Error observing all events", root)
                        emit(UiState.Error(friendlyFirestoreMessage(root, "Failed to load events.")))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val participantsState: StateFlow<UiState<List<Participant>>> = activeManagerProfile
        .flatMapLatest { manager ->
            if (manager == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeParticipants()
                    .map<List<Participant>, UiState<List<Participant>>> { UiState.Success(it) }
                    .catch { error ->
                        val root = unwrapFirestoreThrowable(error)
                        Log.w(TAG, "Error observing participants", root)
                        emit(UiState.Error(friendlyFirestoreMessage(root, "Failed to load participants.")))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val auditLogsState: StateFlow<UiState<List<AuditLog>>> = activeManagerProfile
        .flatMapLatest { manager ->
            if (manager == null || !ManagementPermissions.canViewAuditLogs(manager.roleEnum)) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeAuditLogs()
                    .map<List<AuditLog>, UiState<List<AuditLog>>> { UiState.Success(it) }
                    .catch { error ->
                        val root = unwrapFirestoreThrowable(error)
                        Log.w(TAG, "Error observing audit logs", root)
                        emit(UiState.Error(friendlyFirestoreMessage(root, "Failed to load audit logs.")))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val teamState: StateFlow<UiState<List<ManagementProfile>>> = activeManagerProfile
        .flatMapLatest { manager ->
            if (manager == null || !ManagementPermissions.canManageTeam(manager.roleEnum)) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeManagementTeam()
                    .map<List<ManagementProfile>, UiState<List<ManagementProfile>>> { UiState.Success(it) }
                    .catch { error ->
                        val root = unwrapFirestoreThrowable(error)
                        Log.w(TAG, "Error observing management team", root)
                        emit(UiState.Error(friendlyFirestoreMessage(root, "Failed to load team accounts.")))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val invitesState: StateFlow<UiState<List<ManagementInvite>>> = activeManagerProfile
        .flatMapLatest { manager ->
            if (manager == null || !ManagementPermissions.canManageTeam(manager.roleEnum)) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeManagementInvites()
                    .map<List<ManagementInvite>, UiState<List<ManagementInvite>>> { UiState.Success(it) }
                    .catch { error ->
                        val root = unwrapFirestoreThrowable(error)
                        Log.w(TAG, "Error observing invites", root)
                        emit(UiState.Error(friendlyFirestoreMessage(root, "Failed to load setup codes.")))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    // --- DERIVED LIVE STATISTICS ---

    val dashboardSummary: StateFlow<DashboardSummary> = combine(
        participantsState,
        allEventsState
    ) { participantsUi, eventsUi ->
        val participants = (participantsUi as? UiState.Success)?.data.orEmpty()
        val events = (eventsUi as? UiState.Success)?.data.orEmpty()
        DashboardSummary(
            totalParticipants = participants.size,
            totalSportsEntries = participants.sumOf { it.sportsEventIds.size },
            totalCulturalEntries = participants.sumOf { it.culturalEventIds.size },
            totalActiveEvents = events.count { it.enabled && !it.archived },
            totalAllEvents = events.count { !it.archived },
            latestParticipant = participants.maxByOrNull { it.submittedAt?.seconds ?: 0L }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), DashboardSummary())

    val eventStatistics: StateFlow<List<EventStatisticRow>> = combine(
        participantsState,
        allEventsState
    ) { participantsUi, eventsUi ->
        val participants = (participantsUi as? UiState.Success)?.data.orEmpty()
        val events = (eventsUi as? UiState.Success)?.data.orEmpty()
        val countsByEventId = mutableMapOf<String, Int>()
        participants.forEach { p ->
            p.sportsEventIds.forEach { id -> countsByEventId[id] = (countsByEventId[id] ?: 0) + 1 }
            p.culturalEventIds.forEach { id -> countsByEventId[id] = (countsByEventId[id] ?: 0) + 1 }
        }
        events.map { event ->
            EventStatisticRow(
                event = event,
                participantCount = countsByEventId[event.id] ?: 0
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    init {
        if (isBootstrappedOwner) {
            bootstrapOwnerIfNeeded()
        }
    }

    fun bootstrapOwnerIfNeeded(preferredRole: ManagementRole = ManagementRole.TEACHER) {
        viewModelScope.launch {
            repository.ensurePortalSeeded(preferredRole)
        }
    }

    fun showBanner(text: String, isError: Boolean = false) {
        _bannerMessage.value = PortalBannerMessage(text = text, isError = isError)
    }

    fun dismissBanner() {
        _bannerMessage.value = null
    }

    fun clearRegistrationSuccess() {
        _lastSubmittedParticipant.value = null
    }

    fun clearGeneratedSetupCode() {
        _generatedSetupCode.value = null
    }

    // --- STUDENT REGISTRATION ACTION ---

    fun submitRegistration(
        studentName: String,
        rollNumber: String,
        selectedSports: List<PortalEvent>,
        selectedCultural: List<PortalEvent>
    ) {
        val settings = (settingsState.value as? UiState.Success)?.data
        if (settings == null) {
            showBanner("Portal settings are still initializing. Please try again in a moment.", isError = true)
            return
        }
        if (!registrationRateLimiter.tryAcquire()) {
            val waitSec = registrationRateLimiter.remainingCooldownSeconds()
            showBanner("Too many attempts. Please wait ${waitSec}s before trying again.", isError = true)
            return
        }
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.submitStudentParticipation(
                studentName = studentName,
                rawRollNumber = rollNumber,
                selectedSports = selectedSports,
                selectedCultural = selectedCultural,
                settings = settings
            )
            _isSubmitting.value = false
            result.onSuccess { participant ->
                _lastSubmittedParticipant.value = participant
            }.onFailure { error ->
                showBanner(
                    friendlyFirestoreMessage(error, "Registration failed. Please verify your details."),
                    isError = true
                )
            }
        }
    }

    // --- MANAGEMENT ACTIONS ---

    fun redeemSetupCode(code: String, displayName: String, onSuccess: () -> Unit = {}) {
        if (!inviteRedeemLimiter.tryAcquire()) {
            val waitSec = inviteRedeemLimiter.remainingCooldownSeconds()
            showBanner("Too many code attempts. Please wait ${waitSec}s.", isError = true)
            return
        }
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.redeemOneTimeSetupCode(code, displayName)
            _isSubmitting.value = false
            result.onSuccess { role ->
                showBanner("Management access activated as ${role.displayName}.")
                onSuccess()
            }.onFailure { error ->
                showBanner(error.localizedMessage ?: "Invalid or expired setup code.", isError = true)
            }
        }
    }

    fun updateParticipant(
        participant: Participant,
        newName: String,
        newRoll: String,
        newSports: List<PortalEvent>,
        newCultural: List<PortalEvent>,
        onDone: () -> Unit = {}
    ) {
        val actor = activeManagerProfile.value ?: return
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.updateParticipant(actor, participant, newName, newRoll, newSports, newCultural)
            _isSubmitting.value = false
            result.onSuccess {
                showBanner("Participant record updated.")
                onDone()
            }.onFailure { error ->
                showBanner(friendlyFirestoreMessage(error, "Could not update participant."), isError = true)
            }
        }
    }

    fun deleteParticipant(participant: Participant, onDone: () -> Unit = {}) {
        val actor = activeManagerProfile.value ?: return
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.deleteParticipant(actor, participant)
            _isSubmitting.value = false
            result.onSuccess {
                showBanner("Removed ${participant.studentName} (${participant.rollNumber}).")
                onDone()
            }.onFailure { error ->
                showBanner(friendlyFirestoreMessage(error, "Could not delete participant."), isError = true)
            }
        }
    }

    fun addEvent(name: String, category: EventCategory, onDone: () -> Unit = {}) {
        val actor = activeManagerProfile.value ?: return
        val existing = (allEventsState.value as? UiState.Success)?.data.orEmpty()
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.addEvent(actor, name, category, existing)
            _isSubmitting.value = false
            result.onSuccess {
                showBanner("Added \"$name\" to ${category.label}.")
                onDone()
            }.onFailure { error ->
                showBanner(friendlyFirestoreMessage(error, "Could not add event."), isError = true)
            }
        }
    }

    fun renameEvent(event: PortalEvent, newName: String, onDone: () -> Unit = {}) {
        val actor = activeManagerProfile.value ?: return
        val existing = (allEventsState.value as? UiState.Success)?.data.orEmpty()
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.renameEvent(actor, event, newName, existing)
            _isSubmitting.value = false
            result.onSuccess {
                showBanner("Event renamed to \"$newName\".")
                onDone()
            }.onFailure { error ->
                showBanner(friendlyFirestoreMessage(error, "Could not rename event."), isError = true)
            }
        }
    }

    fun toggleEventEnabled(event: PortalEvent, enabled: Boolean) {
        val actor = activeManagerProfile.value ?: return
        viewModelScope.launch {
            val result = repository.setEventEnabled(actor, event, enabled)
            result.onSuccess {
                showBanner(
                    if (enabled) "\"${event.name}\" is now active for registration."
                    else "\"${event.name}\" disabled from student registration."
                )
            }.onFailure { error ->
                showBanner(friendlyFirestoreMessage(error, "Failed to update event status."), isError = true)
            }
        }
    }

    fun archiveOrDeleteEvent(event: PortalEvent, participantCount: Int, forceArchive: Boolean, onDone: () -> Unit = {}) {
        val actor = activeManagerProfile.value ?: return
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = if (forceArchive || participantCount > 0) {
                repository.archiveEvent(actor, event)
            } else {
                repository.deleteEventPermanently(actor, event, participantCount)
            }
            _isSubmitting.value = false
            result.onSuccess {
                showBanner(
                    if (forceArchive || participantCount > 0) {
                        "Archived \"${event.name}\" (historical student records preserved)."
                    } else {
                        "Deleted unused event \"${event.name}\"."
                    }
                )
                onDone()
            }.onFailure { error ->
                showBanner(friendlyFirestoreMessage(error, "Could not remove event."), isError = true)
            }
        }
    }

    fun saveRegistrationSettings(
        registrationOpen: Boolean,
        registrationScope: String,
        maxSportsEvents: Long,
        maxCulturalEvents: Long,
        closedMessage: String
    ) {
        val actor = activeManagerProfile.value ?: return
        val current = (settingsState.value as? UiState.Success)?.data
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.updateRegistrationSettings(
                actor = actor,
                registrationOpen = registrationOpen,
                registrationScope = registrationScope,
                maxSportsEvents = maxSportsEvents,
                maxCulturalEvents = maxCulturalEvents,
                closedMessage = closedMessage,
                wasPreviouslyOpen = current?.registrationOpen ?: true
            )
            _isSubmitting.value = false
            result.onSuccess {
                showBanner(
                    if (registrationOpen) "Registration is OPEN for scope $registrationScope."
                    else "Registration is CLOSED. Existing records remain preserved."
                )
            }.onFailure { error ->
                showBanner(friendlyFirestoreMessage(error, "Could not update registration settings."), isError = true)
            }
        }
    }

    fun issueSetupCode(role: ManagementRole, label: String, targetEmail: String) {
        val actor = activeManagerProfile.value ?: return
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.createOneTimeSetupInvite(actor, role, label, targetEmail)
            _isSubmitting.value = false
            result.onSuccess { rawCode ->
                _generatedSetupCode.value = rawCode
                showBanner("One-time ${role.badgeLabel} setup code generated.")
            }.onFailure { error ->
                showBanner(friendlyFirestoreMessage(error, "Could not generate setup code."), isError = true)
            }
        }
    }

    fun updateTeamMember(target: ManagementProfile, role: ManagementRole, active: Boolean) {
        val actor = activeManagerProfile.value ?: return
        viewModelScope.launch {
            val result = repository.updateManagementMember(actor, target, role, active)
            result.onSuccess {
                showBanner("Updated ${target.displayName} (${role.badgeLabel}).")
            }.onFailure { error ->
                showBanner(friendlyFirestoreMessage(error, "Could not update team member."), isError = true)
            }
        }
    }

    fun revokeInvite(invite: ManagementInvite) {
        val actor = activeManagerProfile.value ?: return
        viewModelScope.launch {
            val result = repository.revokeInvite(actor, invite)
            result.onSuccess {
                showBanner("Setup pass revoked.")
            }.onFailure { error ->
                showBanner(friendlyFirestoreMessage(error, "Could not revoke setup pass."), isError = true)
            }
        }
    }

    private companion object {
        const val TAG = "PortalViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}

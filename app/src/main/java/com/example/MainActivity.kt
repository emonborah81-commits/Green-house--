package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.firebase.PortalRepository
import com.example.data.firebase.attemptAutoSignIn
import com.example.data.firebase.onGoogleSignInClicked
import com.example.data.firebase.signOutUser
import com.example.ui.components.PortalBannerHost
import com.example.ui.screens.LandingScreen
import com.example.ui.screens.ManagementAuthScreen
import com.example.ui.screens.ManagementShellScreen
import com.example.ui.screens.RegistrationScreen
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.GreenHouseTheme
import com.example.ui.viewmodel.PortalViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private enum class PortalDestination {
    LANDING,
    STUDENT_REGISTRATION,
    MANAGEMENT
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GreenHouseTheme {
                GreenHouseAppRoot()
            }
        }
    }
}

@Composable
fun GreenHouseAppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf<FirebaseUser?>(Firebase.auth.currentUser) }
    var isAuthLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var pendingDestination by rememberSaveable { mutableStateOf<PortalDestination?>(null) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    // Attempt silent Google auto sign-in on startup if not signed in
    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = { currentUser = Firebase.auth.currentUser },
                onUnauthenticated = { /* Wait for interactive Google Sign-In */ },
                scope = scope
            )
        }
    }

    val triggerInteractiveSignIn: (PortalDestination?) -> Unit = { target ->
        pendingDestination = target
        isAuthLoading = true
        authError = null
        onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isAuthLoading = false
                currentUser = Firebase.auth.currentUser
            },
            onAuthError = { msg ->
                isAuthLoading = false
                authError = msg
                pendingDestination = null
            },
            scope = scope,
            onAuthCancelled = {
                isAuthLoading = false
                pendingDestination = null
            }
        )
    }

    val user = currentUser
    if (user == null) {
        // Unauthenticated gate: ZERO Firestore queries or ViewModels instantiated
        LandingScreen(
            signedInEmail = null,
            isAuthLoading = isAuthLoading,
            authError = authError,
            registrationOpen = null,
            onRegisterClick = { triggerInteractiveSignIn(PortalDestination.STUDENT_REGISTRATION) },
            onManagementLoginClick = { triggerInteractiveSignIn(PortalDestination.MANAGEMENT) },
            onSignInClick = { triggerInteractiveSignIn(null) },
            onSignOutClick = {}
        )
    } else {
        // Authenticated session keyed strictly by UID so account switches reset ViewModel & listeners cleanly
        val uid = user.uid
        key(uid) {
            AuthenticatedPortalFlow(
                currentUserId = uid,
                currentUserEmail = user.email.orEmpty(),
                currentUserDisplayName = user.displayName.orEmpty(),
                initialDestination = pendingDestination ?: PortalDestination.LANDING,
                onConsumeInitialDestination = { pendingDestination = null },
                onSignOut = {
                    pendingDestination = null
                    signOutUser(
                        credentialManager = credentialManager,
                        scope = scope,
                        onSignOutComplete = { currentUser = null }
                    )
                },
                onSwitchAccount = {
                    pendingDestination = PortalDestination.MANAGEMENT
                    signOutUser(
                        credentialManager = credentialManager,
                        scope = scope,
                        onSignOutComplete = {
                            currentUser = null
                            triggerInteractiveSignIn(PortalDestination.MANAGEMENT)
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedPortalFlow(
    currentUserId: String,
    currentUserEmail: String,
    currentUserDisplayName: String,
    initialDestination: PortalDestination,
    onConsumeInitialDestination: () -> Unit,
    onSignOut: () -> Unit,
    onSwitchAccount: () -> Unit
) {
    val viewModel: PortalViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                PortalViewModel(
                    repository = PortalRepository(db),
                    currentUserId = currentUserId,
                    currentUserEmail = currentUserEmail,
                    currentUserDisplayName = currentUserDisplayName
                )
            }
        }
    )

    var destination by rememberSaveable(currentUserId) { mutableStateOf(initialDestination) }
    LaunchedEffect(initialDestination) {
        if (initialDestination != PortalDestination.LANDING) {
            destination = initialDestination
            onConsumeInitialDestination()
        }
    }

    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val activeEventsState by viewModel.activeEventsState.collectAsStateWithLifecycle()
    val managementProfileState by viewModel.managementProfileState.collectAsStateWithLifecycle()
    val allEventsState by viewModel.allEventsState.collectAsStateWithLifecycle()
    val participantsState by viewModel.participantsState.collectAsStateWithLifecycle()
    val auditLogsState by viewModel.auditLogsState.collectAsStateWithLifecycle()
    val teamState by viewModel.teamState.collectAsStateWithLifecycle()
    val invitesState by viewModel.invitesState.collectAsStateWithLifecycle()
    val dashboardSummary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
    val eventStats by viewModel.eventStatistics.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val submittedParticipant by viewModel.lastSubmittedParticipant.collectAsStateWithLifecycle()
    val generatedSetupCode by viewModel.generatedSetupCode.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()

    val settings = (settingsState as? UiState.Success)?.data

    Box(modifier = Modifier.fillMaxSize()) {
        when (destination) {
            PortalDestination.LANDING -> {
                LandingScreen(
                    signedInEmail = currentUserEmail.ifBlank { currentUserDisplayName },
                    isAuthLoading = false,
                    authError = null,
                    registrationOpen = settings?.registrationOpen,
                    onRegisterClick = { destination = PortalDestination.STUDENT_REGISTRATION },
                    onManagementLoginClick = { destination = PortalDestination.MANAGEMENT },
                    onSignInClick = {},
                    onSignOutClick = onSignOut
                )
            }

            PortalDestination.STUDENT_REGISTRATION -> {
                RegistrationScreen(
                    settingsState = settingsState,
                    activeEventsState = activeEventsState,
                    isSubmitting = isSubmitting,
                    submittedParticipant = submittedParticipant,
                    isBootstrappedOwner = viewModel.isBootstrappedOwner,
                    onInitializePortal = { viewModel.bootstrapOwnerIfNeeded() },
                    onSubmit = { name, roll, sports, cultural ->
                        viewModel.submitRegistration(name, roll, sports, cultural)
                    },
                    onRegisterAnother = { viewModel.clearRegistrationSuccess() },
                    onBack = {
                        viewModel.clearRegistrationSuccess()
                        destination = PortalDestination.LANDING
                    }
                )
            }

            PortalDestination.MANAGEMENT -> {
                when (val profileUi = managementProfileState) {
                    is UiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ForestPrimary)
                        }
                    }

                    is UiState.Error, is UiState.Success -> {
                        val activeProfile = (profileUi as? UiState.Success)?.data
                        if (activeProfile == null || !activeProfile.active) {
                            ManagementAuthScreen(
                                signedInEmail = currentUserEmail,
                                defaultDisplayName = currentUserDisplayName,
                                isSubmitting = isSubmitting,
                                onRedeemSetupCode = { code, name ->
                                    viewModel.redeemSetupCode(code, name)
                                },
                                onSwitchAccount = onSwitchAccount,
                                onBack = { destination = PortalDestination.LANDING }
                            )
                        } else {
                            ManagementShellScreen(
                                managerProfile = activeProfile,
                                settings = settings,
                                summary = dashboardSummary,
                                participantsState = participantsState,
                                eventsState = allEventsState,
                                eventStats = eventStats,
                                teamState = teamState,
                                invitesState = invitesState,
                                auditLogsState = auditLogsState,
                                generatedSetupCode = generatedSetupCode,
                                isSubmitting = isSubmitting,
                                onUpdateParticipant = { p, n, r, s, c, done ->
                                    viewModel.updateParticipant(p, n, r, s, c, done)
                                },
                                onDeleteParticipant = { p, done ->
                                    viewModel.deleteParticipant(p, done)
                                },
                                onAddEvent = { name, cat, done ->
                                    viewModel.addEvent(name, cat, done)
                                },
                                onRenameEvent = { evt, newName, done ->
                                    viewModel.renameEvent(evt, newName, done)
                                },
                                onToggleEventEnabled = { evt, enabled ->
                                    viewModel.toggleEventEnabled(evt, enabled)
                                },
                                onArchiveOrDeleteEvent = { evt, count, forceArchive, done ->
                                    viewModel.archiveOrDeleteEvent(evt, count, forceArchive, done)
                                },
                                onSaveSettings = { open, scopeStr, maxS, maxC, msg ->
                                    viewModel.saveRegistrationSettings(open, scopeStr, maxS, maxC, msg)
                                },
                                onIssueSetupCode = { role, label, targetEmail ->
                                    viewModel.issueSetupCode(role, label, targetEmail)
                                },
                                onDismissGeneratedCode = { viewModel.clearGeneratedSetupCode() },
                                onUpdateTeamMember = { target, newRole, active ->
                                    viewModel.updateTeamMember(target, newRole, active)
                                },
                                onRevokeInvite = { invite -> viewModel.revokeInvite(invite) },
                                onNotify = { text, isErr -> viewModel.showBanner(text, isErr) },
                                onGoHome = { destination = PortalDestination.LANDING },
                                onSignOut = onSignOut
                            )
                        }
                    }
                }
            }
        }

        PortalBannerHost(
            banner = bannerMessage,
            onDismiss = { viewModel.dismissBanner() },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        )
    }
}

package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.AuditLog
import com.example.data.model.DefaultSeedEvents
import com.example.data.model.EventCategory
import com.example.data.model.ManagementInvite
import com.example.data.model.ManagementPermissions
import com.example.data.model.ManagementProfile
import com.example.data.model.ManagementRole
import com.example.data.model.Participant
import com.example.data.model.PortalEvent
import com.example.data.model.PortalSettings
import com.example.util.SecurityUtils
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PortalRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    companion object {
        const val BOOTSTRAPPED_ADMIN_EMAIL = "emonborah81@gmail.com"
        private const val COL_SETTINGS = "settings"
        private const val DOC_PORTAL = "portal"
        private const val COL_EVENTS = "events"
        private const val COL_PARTICIPANTS = "participants"
        private const val COL_ROLL_LOCKS = "roll_locks"
        private const val COL_SELECTIONS = "participant_event_selections"
        private const val COL_MANAGEMENT_PROFILES = "management_profiles"
        private const val COL_AUDIT_LOGS = "audit_logs"
        private const val COL_MANAGEMENT_INVITES = "management_invites"
        private const val COL_INVITE_CLAIMS = "management_invite_claims"
    }

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    fun isBootstrappedAdminCurrent(): Boolean {
        val user = auth.currentUser ?: return false
        return user.isEmailVerified && user.email.equals(BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true)
    }

    // --- REALTIME OBSERVATION STREAMS ---

    fun observeSettings(): Flow<PortalSettings?> = flow {
        val uid = requireUserId()
        val path = "$COL_SETTINGS/$DOC_PORTAL"
        emitAll(
            db.collection(COL_SETTINGS).document(DOC_PORTAL)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            PortalSettings::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val root = unwrapFirestoreThrowable(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(root, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    /**
     * Student-scoped realtime stream matching firestore.rules allow list filter:
     * enabled == true && archived == false
     */
    fun observeActiveEvents(): Flow<List<PortalEvent>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection(COL_EVENTS)
                .whereEqualTo("enabled", true)
                .whereEqualTo("archived", false)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        PortalEvent::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(compareBy<PortalEvent> { it.category }.thenBy { it.sortOrder }.thenBy { it.name })
                }
                .catch { error ->
                    val root = unwrapFirestoreThrowable(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(root, OperationType.LIST, COL_EVENTS)
                    }
                    throw error
                }
        )
    }

    /**
     * Management-only realtime stream of all events (enabled, disabled, archived).
     */
    fun observeAllEvents(): Flow<List<PortalEvent>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection(COL_EVENTS)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        PortalEvent::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(compareBy<PortalEvent> { it.category }.thenBy { it.archived }.thenBy { it.sortOrder }.thenBy { it.name })
                }
                .catch { error ->
                    val root = unwrapFirestoreThrowable(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(root, OperationType.LIST, COL_EVENTS)
                    }
                    throw error
                }
        )
    }

    /**
     * Management-only realtime stream of all participants.
     */
    fun observeParticipants(): Flow<List<Participant>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection(COL_PARTICIPANTS)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        Participant::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.submittedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val root = unwrapFirestoreThrowable(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(root, OperationType.LIST, COL_PARTICIPANTS)
                    }
                    throw error
                }
        )
    }

    fun observeMyManagementProfile(userId: String): Flow<ManagementProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Refusing cross-user profile stream." }
        val path = "$COL_MANAGEMENT_PROFILES/$userId"
        emitAll(
            db.collection(COL_MANAGEMENT_PROFILES).document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            ManagementProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val root = unwrapFirestoreThrowable(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(root, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeManagementTeam(): Flow<List<ManagementProfile>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection(COL_MANAGEMENT_PROFILES)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        ManagementProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.role }
                }
                .catch { error ->
                    val root = unwrapFirestoreThrowable(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(root, OperationType.LIST, COL_MANAGEMENT_PROFILES)
                    }
                    throw error
                }
        )
    }

    fun observeAuditLogs(): Flow<List<AuditLog>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection(COL_AUDIT_LOGS)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        AuditLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val root = unwrapFirestoreThrowable(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(root, OperationType.LIST, COL_AUDIT_LOGS)
                    }
                    throw error
                }
        )
    }

    fun observeManagementInvites(): Flow<List<ManagementInvite>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection(COL_MANAGEMENT_INVITES)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        ManagementInvite::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val root = unwrapFirestoreThrowable(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(root, OperationType.LIST, COL_MANAGEMENT_INVITES)
                    }
                    throw error
                }
        )
    }

    // --- BOOTSTRAP & SEEDING ---

    /**
     * Initializes portal settings, default 20 Green House events, and the bootstrapped admin profile
     * idempotently when an authorized Captain/Teacher/Owner enters the portal.
     */
    suspend fun ensurePortalSeeded(initialRoleForOwner: ManagementRole = ManagementRole.TEACHER): Result<Unit> {
        return try {
            val uid = requireUserId()
            val user = auth.currentUser ?: error("Not signed in")

            // 1. Ensure bootstrapped owner has a ManagementProfile doc if none exists
            if (isBootstrappedAdminCurrent()) {
                val profileRef = db.collection(COL_MANAGEMENT_PROFILES).document(uid)
                val existingProfile = profileRef.get().await()
                if (!existingProfile.exists()) {
                    val profilePayload = mapOf(
                        "userId" to uid,
                        "email" to (user.email ?: BOOTSTRAPPED_ADMIN_EMAIL),
                        "displayName" to (user.displayName?.takeIf { it.isNotBlank() } ?: "Green House Admin"),
                        "role" to initialRoleForOwner.name,
                        "active" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    profileRef.set(profilePayload).await()
                }
            }

            // 2. Ensure settings/portal exists
            val settingsRef = db.collection(COL_SETTINGS).document(DOC_PORTAL)
            val existingSettings = settingsRef.get().await()
            if (!existingSettings.exists()) {
                val settingsPayload = mapOf(
                    "registrationOpen" to true,
                    "registrationScope" to "2026",
                    "maxSportsEvents" to 3L,
                    "maxCulturalEvents" to 3L,
                    "closedMessage" to "Student registrations are currently closed by Green House Management. Please contact your House Captain or Teacher-in-Charge.",
                    "updatedByUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                settingsRef.set(settingsPayload).await()
            }

            // 3. Seed default 11 Sports + 9 Cultural events if events collection is empty
            val existingEvents = db.collection(COL_EVENTS).limit(1).get().await()
            if (existingEvents.isEmpty) {
                val batch = db.batch()
                DefaultSeedEvents.all.forEach { spec ->
                    val docRef = db.collection(COL_EVENTS).document(spec.id)
                    batch.set(
                        docRef,
                        mapOf(
                            "name" to spec.name,
                            "normalizedName" to SecurityUtils.normalizeEventName(spec.name),
                            "category" to spec.category.name,
                            "enabled" to true,
                            "archived" to false,
                            "sortOrder" to spec.sortOrder.toLong(),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "$COL_SETTINGS/$DOC_PORTAL")
            Result.failure(e)
        }
    }

    // --- STUDENT REGISTRATION (ATOMIC BATCH WITH ROLL LOCK & RELATIONAL SELECTIONS) ---

    suspend fun submitStudentParticipation(
        studentName: String,
        rawRollNumber: String,
        selectedSports: List<PortalEvent>,
        selectedCultural: List<PortalEvent>,
        settings: PortalSettings
    ): Result<Participant> {
        var logPath = COL_PARTICIPANTS
        return try {
            val uid = requireUserId()
            val cleanName = studentName.trim().replace(Regex("\\s+"), " ")
            val normalizedRoll = SecurityUtils.normalizeRollNumber(rawRollNumber)

            require(cleanName.length in 2..100) { "Student full name must be between 2 and 100 characters." }
            require(SecurityUtils.isValidNormalizedRoll(normalizedRoll)) {
                "Enter a valid roll number using letters, numbers, or hyphens."
            }
            require(settings.registrationOpen) {
                settings.closedMessage.ifBlank { "Registration is currently closed." }
            }
            require(selectedSports.isNotEmpty() || selectedCultural.isNotEmpty()) {
                "Please select at least one Sports or Cultural event."
            }
            require(selectedSports.size <= settings.maxSportsEvents) {
                "You can select at most ${settings.maxSportsEvents} sports events."
            }
            require(selectedCultural.size <= settings.maxCulturalEvents) {
                "You can select at most ${settings.maxCulturalEvents} cultural events."
            }

            val scope = settings.registrationScope
            val lockId = "${scope}__${normalizedRoll}"
            val participantRef = db.collection(COL_PARTICIPANTS).document()
            val participantId = participantRef.id
            logPath = participantRef.path

            val batch = db.batch()
            val participantPayload = mapOf(
                "studentName" to cleanName,
                "rollNumber" to normalizedRoll,
                "normalizedRollNumber" to normalizedRoll,
                "registrationScope" to scope,
                "sportsEventIds" to selectedSports.map { it.id },
                "culturalEventIds" to selectedCultural.map { it.id },
                "sportsEventNames" to selectedSports.map { it.name },
                "culturalEventNames" to selectedCultural.map { it.name },
                "submittedByUid" to uid,
                "submittedAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(participantRef, participantPayload)

            val lockRef = db.collection(COL_ROLL_LOCKS).document(lockId)
            batch.set(
                lockRef,
                mapOf(
                    "participantId" to participantId,
                    "registrationScope" to scope,
                    "normalizedRollNumber" to normalizedRoll,
                    "submittedByUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            (selectedSports + selectedCultural).forEach { event ->
                val selRef = db.collection(COL_SELECTIONS).document("${participantId}__${event.id}")
                batch.set(
                    selRef,
                    mapOf(
                        "participantId" to participantId,
                        "eventId" to event.id,
                        "eventName" to event.name,
                        "category" to event.category,
                        "registrationScope" to scope,
                        "submittedByUid" to uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()

            Result.success(
                Participant(
                    id = participantId,
                    studentName = cleanName,
                    rollNumber = normalizedRoll,
                    normalizedRollNumber = normalizedRoll,
                    registrationScope = scope,
                    sportsEventIds = selectedSports.map { it.id },
                    culturalEventIds = selectedCultural.map { it.id },
                    sportsEventNames = selectedSports.map { it.name },
                    culturalEventNames = selectedCultural.map { it.name },
                    submittedByUid = uid
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            handleFirestoreError(e, OperationType.CREATE, logPath)
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Result.failure(
                    IllegalStateException(
                        "Registration could not be completed. This roll number may already be registered for ${settings.registrationScope}, or registration is closed."
                    )
                )
            } else {
                Result.failure(e)
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, logPath)
            Result.failure(e)
        }
    }

    // --- PARTICIPANT MANAGEMENT ---

    suspend fun updateParticipant(
        actor: ManagementProfile,
        existing: Participant,
        newName: String,
        newRawRoll: String,
        newSports: List<PortalEvent>,
        newCultural: List<PortalEvent>
    ): Result<Unit> {
        val path = "$COL_PARTICIPANTS/${existing.id}"
        return try {
            val uid = requireUserId()
            require(ManagementPermissions.canEditParticipants(actor.roleEnum)) {
                "Your role does not permit editing participants."
            }
            val cleanName = newName.trim().replace(Regex("\\s+"), " ")
            val normalizedRoll = SecurityUtils.normalizeRollNumber(newRawRoll)
            require(cleanName.length in 2..100) { "Student full name must be between 2 and 100 characters." }
            require(SecurityUtils.isValidNormalizedRoll(normalizedRoll)) { "Invalid roll number format." }
            require(newSports.isNotEmpty() || newCultural.isNotEmpty()) {
                "Participant must have at least one event selected."
            }

            val scope = existing.registrationScope
            val oldLockId = "${scope}__${existing.normalizedRollNumber}"
            val newLockId = "${scope}__${normalizedRoll}"
            val rollChanged = oldLockId != newLockId

            if (rollChanged) {
                val conflict = db.collection(COL_ROLL_LOCKS).document(newLockId).get().await()
                if (conflict.exists()) {
                    return Result.failure(
                        IllegalStateException("Roll number $normalizedRoll is already registered in scope $scope.")
                    )
                }
            }

            val batch = db.batch()
            val participantRef = db.collection(COL_PARTICIPANTS).document(existing.id)
            batch.update(
                participantRef,
                mapOf(
                    "studentName" to cleanName,
                    "rollNumber" to normalizedRoll,
                    "normalizedRollNumber" to normalizedRoll,
                    "sportsEventIds" to newSports.map { it.id },
                    "culturalEventIds" to newCultural.map { it.id },
                    "sportsEventNames" to newSports.map { it.name },
                    "culturalEventNames" to newCultural.map { it.name },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            if (rollChanged) {
                batch.delete(db.collection(COL_ROLL_LOCKS).document(oldLockId))
                batch.set(
                    db.collection(COL_ROLL_LOCKS).document(newLockId),
                    mapOf(
                        "participantId" to existing.id,
                        "registrationScope" to scope,
                        "normalizedRollNumber" to normalizedRoll,
                        "submittedByUid" to existing.submittedByUid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            // Sync relational participant_event_selections
            val oldEventIds = (existing.sportsEventIds + existing.culturalEventIds).toSet()
            val newEvents = (newSports + newCultural)
            val newEventIds = newEvents.map { it.id }.toSet()

            (oldEventIds - newEventIds).forEach { removedId ->
                batch.delete(db.collection(COL_SELECTIONS).document("${existing.id}__${removedId}"))
            }
            newEvents.filter { it.id !in oldEventIds }.forEach { added ->
                batch.set(
                    db.collection(COL_SELECTIONS).document("${existing.id}__${added.id}"),
                    mapOf(
                        "participantId" to existing.id,
                        "eventId" to added.id,
                        "eventName" to added.name,
                        "category" to added.category,
                        "registrationScope" to scope,
                        "submittedByUid" to uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            appendAuditLogToBatch(
                batch = batch,
                actor = actor,
                action = "PARTICIPANT_EDITED",
                recordType = "PARTICIPANT",
                recordId = existing.id,
                details = "Updated ${existing.studentName} (${existing.rollNumber}) -> $cleanName ($normalizedRoll); ${newSports.size} sports, ${newCultural.size} cultural."
            )

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteParticipant(actor: ManagementProfile, participant: Participant): Result<Unit> {
        val path = "$COL_PARTICIPANTS/${participant.id}"
        return try {
            requireUserId()
            require(ManagementPermissions.canDeleteParticipants(actor.roleEnum)) {
                "Only House Captain or Teacher-in-Charge can delete participant records."
            }

            val batch = db.batch()
            batch.delete(db.collection(COL_PARTICIPANTS).document(participant.id))
            val lockId = "${participant.registrationScope}__${participant.normalizedRollNumber}"
            batch.delete(db.collection(COL_ROLL_LOCKS).document(lockId))

            (participant.sportsEventIds + participant.culturalEventIds).forEach { eventId ->
                batch.delete(db.collection(COL_SELECTIONS).document("${participant.id}__${eventId}"))
            }

            appendAuditLogToBatch(
                batch = batch,
                actor = actor,
                action = "PARTICIPANT_DELETED",
                recordType = "PARTICIPANT",
                recordId = participant.id,
                details = "Deleted participant ${participant.studentName} (${participant.rollNumber})."
            )

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- EVENT MANAGEMENT ---

    suspend fun addEvent(
        actor: ManagementProfile,
        name: String,
        category: EventCategory,
        existingEvents: List<PortalEvent>
    ): Result<Unit> {
        var path = COL_EVENTS
        return try {
            requireUserId()
            require(ManagementPermissions.canManageEvents(actor.roleEnum)) {
                "Your role does not permit managing events."
            }
            val clean = SecurityUtils.cleanEventName(name)
            val normalized = SecurityUtils.normalizeEventName(clean)
            require(clean.length in 2..80) { "Event name must be between 2 and 80 characters." }

            val duplicate = existingEvents.any {
                !it.archived && it.categoryEnum == category && it.normalizedName == normalized
            }
            if (duplicate) {
                return Result.failure(
                    IllegalArgumentException("An active ${category.label} entry named \"$clean\" already exists.")
                )
            }

            val nextOrder = (existingEvents.filter { it.categoryEnum == category }
                .maxOfOrNull { it.sortOrder } ?: 0L) + 1L
            val prefix = if (category == EventCategory.SPORTS) "sports" else "cultural"
            val docId = SecurityUtils.slugifyEventId(prefix, clean)
            val docRef = db.collection(COL_EVENTS).document(docId)
            path = docRef.path

            val batch = db.batch()
            batch.set(
                docRef,
                mapOf(
                    "name" to clean,
                    "normalizedName" to normalized,
                    "category" to category.name,
                    "enabled" to true,
                    "archived" to false,
                    "sortOrder" to nextOrder,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            appendAuditLogToBatch(
                batch = batch,
                actor = actor,
                action = "EVENT_ADDED",
                recordType = "EVENT",
                recordId = docId,
                details = "Added ${category.name} event \"$clean\"."
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun renameEvent(
        actor: ManagementProfile,
        event: PortalEvent,
        newName: String,
        existingEvents: List<PortalEvent>
    ): Result<Unit> {
        val path = "$COL_EVENTS/${event.id}"
        return try {
            requireUserId()
            require(ManagementPermissions.canManageEvents(actor.roleEnum)) {
                "Your role does not permit renaming events."
            }
            val clean = SecurityUtils.cleanEventName(newName)
            val normalized = SecurityUtils.normalizeEventName(clean)
            require(clean.length in 2..80) { "Event name must be between 2 and 80 characters." }

            val duplicate = existingEvents.any {
                it.id != event.id && !it.archived &&
                    it.categoryEnum == event.categoryEnum &&
                    it.normalizedName == normalized
            }
            if (duplicate) {
                return Result.failure(
                    IllegalArgumentException("Another ${event.categoryEnum.label} entry is already named \"$clean\".")
                )
            }

            val batch = db.batch()
            batch.update(
                db.collection(COL_EVENTS).document(event.id),
                mapOf(
                    "name" to clean,
                    "normalizedName" to normalized,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            appendAuditLogToBatch(
                batch = batch,
                actor = actor,
                action = "EVENT_RENAMED",
                recordType = "EVENT",
                recordId = event.id,
                details = "Renamed event \"${event.name}\" to \"$clean\"."
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setEventEnabled(
        actor: ManagementProfile,
        event: PortalEvent,
        enabled: Boolean
    ): Result<Unit> {
        val path = "$COL_EVENTS/${event.id}"
        return try {
            requireUserId()
            require(ManagementPermissions.canManageEvents(actor.roleEnum)) {
                "Your role does not permit changing event status."
            }
            val batch = db.batch()
            batch.update(
                db.collection(COL_EVENTS).document(event.id),
                mapOf(
                    "enabled" to enabled,
                    "archived" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            appendAuditLogToBatch(
                batch = batch,
                actor = actor,
                action = if (enabled) "EVENT_ENABLED" else "EVENT_DISABLED",
                recordType = "EVENT",
                recordId = event.id,
                details = "${if (enabled) "Re-enabled" else "Disabled"} event \"${event.name}\"."
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun archiveEvent(actor: ManagementProfile, event: PortalEvent): Result<Unit> {
        val path = "$COL_EVENTS/${event.id}"
        return try {
            requireUserId()
            require(ManagementPermissions.canManageEvents(actor.roleEnum)) {
                "Your role does not permit archiving events."
            }
            val batch = db.batch()
            batch.update(
                db.collection(COL_EVENTS).document(event.id),
                mapOf(
                    "enabled" to false,
                    "archived" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            appendAuditLogToBatch(
                batch = batch,
                actor = actor,
                action = "EVENT_ARCHIVED",
                recordType = "EVENT",
                recordId = event.id,
                details = "Archived event \"${event.name}\" (historical registrations preserved)."
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteEventPermanently(
        actor: ManagementProfile,
        event: PortalEvent,
        participantCount: Int
    ): Result<Unit> {
        val path = "$COL_EVENTS/${event.id}"
        return try {
            requireUserId()
            require(ManagementPermissions.canDeleteEvents(actor.roleEnum)) {
                "Only House Captain or Teacher-in-Charge can permanently delete events."
            }
            // Prefer archival automatically if historical records exist
            if (participantCount > 0) {
                return archiveEvent(actor, event)
            }
            val batch = db.batch()
            batch.delete(db.collection(COL_EVENTS).document(event.id))
            appendAuditLogToBatch(
                batch = batch,
                actor = actor,
                action = "EVENT_DELETED",
                recordType = "EVENT",
                recordId = event.id,
                details = "Permanently deleted unused event \"${event.name}\"."
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- REGISTRATION CONTROL & SETTINGS ---

    suspend fun updateRegistrationSettings(
        actor: ManagementProfile,
        registrationOpen: Boolean,
        registrationScope: String,
        maxSportsEvents: Long,
        maxCulturalEvents: Long,
        closedMessage: String,
        wasPreviouslyOpen: Boolean
    ): Result<Unit> {
        val path = "$COL_SETTINGS/$DOC_PORTAL"
        return try {
            val uid = requireUserId()
            require(ManagementPermissions.canControlRegistration(actor.roleEnum)) {
                "Only House Captain or Teacher-in-Charge can modify registration controls."
            }
            val cleanScope = registrationScope.trim()
            require(SecurityUtils.isValidScope(cleanScope)) {
                "Registration scope must be 2-32 alphanumeric characters (e.g., 2026)."
            }
            require(maxSportsEvents in 1L..20L && maxCulturalEvents in 1L..20L) {
                "Event limits must be between 1 and 20."
            }
            val cleanMessage = closedMessage.trim().take(300)

            val batch = db.batch()
            batch.update(
                db.collection(COL_SETTINGS).document(DOC_PORTAL),
                mapOf(
                    "registrationOpen" to registrationOpen,
                    "registrationScope" to cleanScope,
                    "maxSportsEvents" to maxSportsEvents,
                    "maxCulturalEvents" to maxCulturalEvents,
                    "closedMessage" to cleanMessage,
                    "updatedByUid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val action = when {
                registrationOpen && !wasPreviouslyOpen -> "REGISTRATION_OPENED"
                !registrationOpen && wasPreviouslyOpen -> "REGISTRATION_CLOSED"
                else -> "SETTINGS_UPDATED"
            }
            appendAuditLogToBatch(
                batch = batch,
                actor = actor,
                action = action,
                recordType = "SETTINGS",
                recordId = DOC_PORTAL,
                details = "Status=${if (registrationOpen) "OPEN" else "CLOSED"}, Scope=$cleanScope, MaxSports=$maxSportsEvents, MaxCultural=$maxCulturalEvents."
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- MANAGEMENT TEAM & ONE-TIME SETUP PASSES ---

    /**
     * Generates a cryptographically strong one-time setup code, stores ONLY its SHA-256 hash
     * in management_invites, and returns the raw code once to display to the issuing admin.
     */
    suspend fun createOneTimeSetupInvite(
        actor: ManagementProfile,
        role: ManagementRole,
        label: String,
        targetEmail: String
    ): Result<String> {
        var path = COL_MANAGEMENT_INVITES
        return try {
            val uid = requireUserId()
            require(ManagementPermissions.canManageTeam(actor.roleEnum)) {
                "Only House Captain or Teacher-in-Charge can issue management setup codes."
            }
            val cleanLabel = label.trim().ifBlank { "${role.displayName} Setup Pass" }.take(100)
            val cleanEmail = targetEmail.trim().lowercase()
            val rawCode = SecurityUtils.generateOneTimeSetupCode()
            val codeHash = SecurityUtils.sha256Hex(rawCode)
            val docRef = db.collection(COL_MANAGEMENT_INVITES).document(codeHash)
            path = docRef.path

            val batch = db.batch()
            batch.set(
                docRef,
                mapOf(
                    "role" to role.name,
                    "label" to cleanLabel,
                    "targetEmail" to cleanEmail,
                    "redeemed" to false,
                    "redeemedByUid" to "",
                    "createdByUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            appendAuditLogToBatch(
                batch = batch,
                actor = actor,
                action = "SETUP_CODE_ISSUED",
                recordType = "INVITE",
                recordId = codeHash.take(12),
                details = "Issued single-use ${role.badgeLabel} setup code \"$cleanLabel\"${if (cleanEmail.isNotBlank()) " for $cleanEmail" else ""}."
            )
            batch.commit().await()
            Result.success(rawCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    /**
     * Redeems a single-use setup code atomically into a role-bound management_profiles/{uid} doc.
     */
    suspend fun redeemOneTimeSetupCode(rawCode: String, displayNameInput: String): Result<ManagementRole> {
        val codeHash = SecurityUtils.sha256Hex(rawCode)
        val path = "$COL_MANAGEMENT_INVITES/$codeHash"
        return try {
            val uid = requireUserId()
            val user = auth.currentUser ?: error("Not signed in")
            require(user.isEmailVerified && !user.email.isNullOrBlank()) {
                "A verified Google account email is required for management access."
            }
            require(rawCode.trim().length >= 6) { "Enter a valid setup code." }

            val inviteSnap = db.collection(COL_MANAGEMENT_INVITES).document(codeHash).get().await()
            if (!inviteSnap.exists()) {
                return Result.failure(IllegalArgumentException("Invalid or already used setup code."))
            }
            val invite = inviteSnap.toObject(
                ManagementInvite::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalArgumentException("Invalid setup code."))

            if (invite.redeemed) {
                return Result.failure(IllegalStateException("This one-time setup code has already been used."))
            }
            val role = invite.roleEnum ?: return Result.failure(IllegalStateException("Invalid invite role."))
            val cleanDisplay = displayNameInput.trim()
                .ifBlank { user.displayName?.takeIf { it.isNotBlank() } ?: user.email!!.substringBefore("@") }
                .take(100)

            val batch = db.batch()
            batch.update(
                db.collection(COL_MANAGEMENT_INVITES).document(codeHash),
                mapOf(
                    "redeemed" to true,
                    "redeemedByUid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection(COL_INVITE_CLAIMS).document(uid),
                mapOf(
                    "inviteId" to codeHash,
                    "role" to role.name,
                    "claimedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection(COL_MANAGEMENT_PROFILES).document(uid),
                mapOf(
                    "userId" to uid,
                    "email" to user.email!!.lowercase(),
                    "displayName" to cleanDisplay,
                    "role" to role.name,
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(role)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(
                IllegalArgumentException("Invalid, expired, or unauthorized setup code.")
            )
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateManagementMember(
        actor: ManagementProfile,
        target: ManagementProfile,
        newRole: ManagementRole,
        active: Boolean
    ): Result<Unit> {
        val path = "$COL_MANAGEMENT_PROFILES/${target.userId}"
        return try {
            requireUserId()
            require(ManagementPermissions.canManageTeam(actor.roleEnum)) {
                "Only House Captain or Teacher-in-Charge can modify team roles."
            }
            val batch = db.batch()
            batch.update(
                db.collection(COL_MANAGEMENT_PROFILES).document(target.userId),
                mapOf(
                    "role" to newRole.name,
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            appendAuditLogToBatch(
                batch = batch,
                actor = actor,
                action = if (active) "MEMBER_ROLE_UPDATED" else "MEMBER_REVOKED",
                recordType = "MANAGEMENT_PROFILE",
                recordId = target.userId,
                details = "Updated ${target.displayName} (${target.email}) -> role=${newRole.badgeLabel}, active=$active."
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun revokeInvite(actor: ManagementProfile, invite: ManagementInvite): Result<Unit> {
        val path = "$COL_MANAGEMENT_INVITES/${invite.id}"
        return try {
            requireUserId()
            require(ManagementPermissions.canManageTeam(actor.roleEnum)) {
                "Only House Captain or Teacher-in-Charge can revoke setup passes."
            }
            db.collection(COL_MANAGEMENT_INVITES).document(invite.id).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun appendAuditLogToBatch(
        batch: com.google.firebase.firestore.WriteBatch,
        actor: ManagementProfile,
        action: String,
        recordType: String,
        recordId: String,
        details: String
    ) {
        val uid = auth.currentUser?.uid ?: return
        val logRef = db.collection(COL_AUDIT_LOGS).document()
        batch.set(
            logRef,
            mapOf(
                "actorUid" to uid,
                "actorName" to actor.displayName.ifBlank { actor.email.ifBlank { "Manager" } }.take(100),
                "actorRole" to (actor.roleEnum?.name ?: ManagementRole.TEACHER.name),
                "action" to action.take(64),
                "recordType" to recordType.take(40),
                "recordId" to recordId.take(128),
                "details" to details.take(500),
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
    }
}

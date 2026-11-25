package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class EventCategory(val label: String) {
    SPORTS("Sports Events"),
    CULTURAL("Cultural Events");

    companion object {
        fun fromRaw(raw: String?): EventCategory =
            entries.firstOrNull { it.name == raw } ?: SPORTS
    }
}

enum class ManagementRole(val displayName: String, val badgeLabel: String) {
    CAPTAIN("House Captain", "CAPTAIN"),
    VICE_CAPTAIN("Vice Captain", "VICE CAPTAIN"),
    VOLUNTEER("House Volunteer", "VOLUNTEER"),
    TEACHER("Teacher-in-Charge", "TEACHER");

    companion object {
        fun fromRaw(raw: String?): ManagementRole? =
            entries.firstOrNull { it.name == raw }
    }
}

/**
 * Centralized role-based permission matrix mirrored 1:1 in firestore.rules.
 */
object ManagementPermissions {
    fun canViewParticipants(role: ManagementRole?): Boolean = role != null

    fun canEditParticipants(role: ManagementRole?): Boolean =
        role == ManagementRole.CAPTAIN ||
            role == ManagementRole.TEACHER ||
            role == ManagementRole.VICE_CAPTAIN

    fun canDeleteParticipants(role: ManagementRole?): Boolean =
        role == ManagementRole.CAPTAIN || role == ManagementRole.TEACHER

    fun canManageEvents(role: ManagementRole?): Boolean =
        role == ManagementRole.CAPTAIN ||
            role == ManagementRole.TEACHER ||
            role == ManagementRole.VICE_CAPTAIN

    fun canDeleteEvents(role: ManagementRole?): Boolean =
        role == ManagementRole.CAPTAIN || role == ManagementRole.TEACHER

    fun canViewStatistics(role: ManagementRole?): Boolean =
        role == ManagementRole.CAPTAIN ||
            role == ManagementRole.TEACHER ||
            role == ManagementRole.VICE_CAPTAIN

    fun canExportCsv(role: ManagementRole?): Boolean =
        role == ManagementRole.CAPTAIN ||
            role == ManagementRole.TEACHER ||
            role == ManagementRole.VICE_CAPTAIN

    fun canControlRegistration(role: ManagementRole?): Boolean =
        role == ManagementRole.CAPTAIN || role == ManagementRole.TEACHER

    fun canViewAuditLogs(role: ManagementRole?): Boolean =
        role == ManagementRole.CAPTAIN || role == ManagementRole.TEACHER

    fun canManageTeam(role: ManagementRole?): Boolean =
        role == ManagementRole.CAPTAIN || role == ManagementRole.TEACHER
}

@Keep
@IgnoreExtraProperties
data class PortalEvent(
    @DocumentId val id: String = "",
    val name: String = "",
    val normalizedName: String = "",
    val category: String = EventCategory.SPORTS.name,
    val enabled: Boolean = true,
    val archived: Boolean = false,
    val sortOrder: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val categoryEnum: EventCategory get() = EventCategory.fromRaw(category)
}

@Keep
@IgnoreExtraProperties
data class Participant(
    @DocumentId val id: String = "",
    val studentName: String = "",
    val rollNumber: String = "",
    val normalizedRollNumber: String = "",
    val registrationScope: String = "2026",
    val sportsEventIds: List<String> = emptyList(),
    val culturalEventIds: List<String> = emptyList(),
    val sportsEventNames: List<String> = emptyList(),
    val culturalEventNames: List<String> = emptyList(),
    val submittedByUid: String = "",
    @ServerTimestamp val submittedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ManagementProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = ManagementRole.VOLUNTEER.name,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val roleEnum: ManagementRole? get() = ManagementRole.fromRaw(role)
}

@Keep
@IgnoreExtraProperties
data class PortalSettings(
    @DocumentId val id: String = "portal",
    val registrationOpen: Boolean = true,
    val registrationScope: String = "2026",
    val maxSportsEvents: Long = 3L,
    val maxCulturalEvents: Long = 3L,
    val closedMessage: String = "Student registrations are currently closed by Green House Management. Please contact your House Captain or Teacher-in-Charge.",
    val updatedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val actorUid: String = "",
    val actorName: String = "",
    val actorRole: String = ManagementRole.TEACHER.name,
    val action: String = "",
    val recordType: String = "",
    val recordId: String = "",
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val actorRoleEnum: ManagementRole? get() = ManagementRole.fromRaw(actorRole)
}

@Keep
@IgnoreExtraProperties
data class ManagementInvite(
    @DocumentId val id: String = "",
    val role: String = ManagementRole.VOLUNTEER.name,
    val label: String = "",
    val targetEmail: String = "",
    val redeemed: Boolean = false,
    val redeemedByUid: String = "",
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val roleEnum: ManagementRole? get() = ManagementRole.fromRaw(role)
}

data class SeedEventSpec(
    val id: String,
    val name: String,
    val category: EventCategory,
    val sortOrder: Int
)

object DefaultSeedEvents {
    val all: List<SeedEventSpec> = listOf(
        // Sports Events (11)
        SeedEventSpec("sports_100m_race", "100m Race", EventCategory.SPORTS, 1),
        SeedEventSpec("sports_200m_race", "200m Race", EventCategory.SPORTS, 2),
        SeedEventSpec("sports_400m_race", "400m Race", EventCategory.SPORTS, 3),
        SeedEventSpec("sports_long_jump", "Long Jump", EventCategory.SPORTS, 4),
        SeedEventSpec("sports_high_jump", "High Jump", EventCategory.SPORTS, 5),
        SeedEventSpec("sports_football", "Football", EventCategory.SPORTS, 6),
        SeedEventSpec("sports_cricket", "Cricket", EventCategory.SPORTS, 7),
        SeedEventSpec("sports_volleyball", "Volleyball", EventCategory.SPORTS, 8),
        SeedEventSpec("sports_badminton", "Badminton", EventCategory.SPORTS, 9),
        SeedEventSpec("sports_shot_put", "Shot Put", EventCategory.SPORTS, 10),
        SeedEventSpec("sports_discus_throw", "Discus Throw", EventCategory.SPORTS, 11),
        // Cultural Events (9)
        SeedEventSpec("cultural_dance", "Dance", EventCategory.CULTURAL, 1),
        SeedEventSpec("cultural_group_dance", "Group Dance", EventCategory.CULTURAL, 2),
        SeedEventSpec("cultural_solo_singing", "Solo Singing", EventCategory.CULTURAL, 3),
        SeedEventSpec("cultural_group_singing", "Group Singing", EventCategory.CULTURAL, 4),
        SeedEventSpec("cultural_drama", "Drama", EventCategory.CULTURAL, 5),
        SeedEventSpec("cultural_recitation", "Recitation", EventCategory.CULTURAL, 6),
        SeedEventSpec("cultural_drawing", "Drawing", EventCategory.CULTURAL, 7),
        SeedEventSpec("cultural_quiz", "Quiz", EventCategory.CULTURAL, 8),
        SeedEventSpec("cultural_instrumental_music", "Instrumental Music", EventCategory.CULTURAL, 9)
    )
}

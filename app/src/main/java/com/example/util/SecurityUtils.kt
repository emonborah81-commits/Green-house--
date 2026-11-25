package com.example.util

import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.google.firebase.Timestamp

object SecurityUtils {
    private val validRollRegex = Regex("^[A-Z0-9_-]{1,40}$")
    private val validScopeRegex = Regex("^[a-zA-Z0-9_-]{2,32}$")
    private val safeRandom = SecureRandom()
    private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /**
     * Normalizes roll numbers consistently: trims, uppercases, collapses spaces/slashes to hyphens,
     * and strips disallowed punctuation.
     */
    fun normalizeRollNumber(raw: String): String {
        return raw.trim()
            .uppercase(Locale.ROOT)
            .replace(Regex("[\\s/]+"), "-")
            .replace(Regex("[^A-Z0-9_-]"), "")
            .replace(Regex("-{2,}"), "-")
            .trim('-')
    }

    fun isValidNormalizedRoll(normalized: String): Boolean =
        normalized.isNotEmpty() && validRollRegex.matches(normalized)

    fun normalizeEventName(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

    fun cleanEventName(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ")

    fun isValidScope(scope: String): Boolean = validScopeRegex.matches(scope.trim())

    fun slugifyEventId(categoryPrefix: String, name: String): String {
        val slug = name.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(40)
        val suffix = (System.currentTimeMillis() % 100000).toString()
        return "${categoryPrefix}_${slug.ifEmpty { "event" }}_$suffix"
    }

    /**
     * Computes SHA-256 hex digest of a one-time setup code so raw passcodes are never
     * stored or exposed in Firestore document IDs or logs.
     */
    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.trim().uppercase(Locale.ROOT).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generates a cryptographically strong 10-character one-time setup code (e.g., GH4K-9M2P-7X).
     */
    fun generateOneTimeSetupCode(): String {
        val chars = CharArray(10) { CODE_ALPHABET[safeRandom.nextInt(CODE_ALPHABET.length)] }
        return "${chars.sliceArray(0..3).concatToString()}-${chars.sliceArray(4..7).concatToString()}-${chars.sliceArray(8..9).concatToString()}"
    }

    fun formatTimestamp(timestamp: Timestamp?): String {
        val date = timestamp?.toDate() ?: return "Just now"
        val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        return formatter.format(date)
    }

    fun formatShortTime(timestamp: Timestamp?): String {
        val date = timestamp?.toDate() ?: return "Pending"
        val formatter = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
        return formatter.format(date)
    }
}

/**
 * Sliding-window client-side rate limiter to throttle burst submissions and failed code attempts.
 */
class ActionRateLimiter(
    private val maxAttempts: Int,
    private val windowMillis: Long
) {
    private val timestamps = ArrayDeque<Long>()

    @Synchronized
    fun tryAcquire(nowMillis: Long = System.currentTimeMillis()): Boolean {
        while (timestamps.isNotEmpty() && nowMillis - timestamps.first() > windowMillis) {
            timestamps.removeFirst()
        }
        if (timestamps.size >= maxAttempts) return false
        timestamps.addLast(nowMillis)
        return true
    }

    @Synchronized
    fun remainingCooldownSeconds(nowMillis: Long = System.currentTimeMillis()): Int {
        if (timestamps.isEmpty()) return 0
        val oldest = timestamps.first()
        val remaining = (windowMillis - (nowMillis - oldest)).coerceAtLeast(0L)
        return ((remaining + 999L) / 1000L).toInt()
    }
}

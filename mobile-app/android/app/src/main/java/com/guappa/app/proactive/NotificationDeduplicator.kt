package com.guappa.app.proactive

import android.util.Log
import java.security.MessageDigest

/**
 * Prevents duplicate notifications within a configurable time window.
 *
 * Uses a ring buffer of the last [maxEntries] event fingerprints (default 100).
 * An event fingerprint is a SHA-256 hash of the concatenation of event type + content.
 *
 * Thread-safe: all operations are synchronized on the internal buffer.
 *
 * Usage:
 *   val dedup = NotificationDeduplicator()
 *   val fingerprint = dedup.fingerprint("sms_received", "Hello from 555-1234")
 *   if (dedup.isDuplicate(fingerprint)) {
 *       // skip notification
 *   } else {
 *       dedup.record(fingerprint)
 *       // send notification
 *   }
 */
class NotificationDeduplicator(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES
) {
    companion object {
        private const val TAG = "NotifDeduplicator"
        private const val DEFAULT_WINDOW_MS = 5 * 60 * 1000L  // 5 minutes
        private const val DEFAULT_MAX_ENTRIES = 100
    }

    private data class FingerprintEntry(
        val fingerprint: String,
        val timestamp: Long
    )

    private val buffer = ArrayDeque<FingerprintEntry>(maxEntries)
    private val lock = Any()

    /**
     * Computes a fingerprint for the given event type and content.
     * Returns a hex-encoded SHA-256 hash.
     */
    fun fingerprint(type: String, content: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val input = "$type|$content"
            val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // Fallback: use hashCode if SHA-256 is unavailable (should not happen)
            Log.w(TAG, "SHA-256 unavailable, using hashCode fallback")
            "$type|$content".hashCode().toString()
        }
    }

    /**
     * Checks whether this fingerprint was already recorded within [windowMs].
     * Does NOT record the fingerprint — call [record] explicitly after delivery.
     */
    fun isDuplicate(eventFingerprint: String): Boolean {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            purgeExpired(now)
            return buffer.any { it.fingerprint == eventFingerprint }
        }
    }

    /**
     * Records the fingerprint. Must be called after successful notification delivery.
     * If the buffer is full, the oldest entry is evicted.
     */
    fun record(eventFingerprint: String) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            purgeExpired(now)
            if (buffer.size >= maxEntries) {
                buffer.removeFirst()
            }
            buffer.addLast(FingerprintEntry(eventFingerprint, now))
        }
    }

    /**
     * Convenience: checks duplicate status and records in a single call.
     * Returns true if the event IS a duplicate (notification should be skipped).
     * Returns false if the event is new (notification should proceed); the fingerprint
     * is automatically recorded.
     */
    fun checkAndRecord(eventFingerprint: String): Boolean {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            purgeExpired(now)
            if (buffer.any { it.fingerprint == eventFingerprint }) {
                return true
            }
            if (buffer.size >= maxEntries) {
                buffer.removeFirst()
            }
            buffer.addLast(FingerprintEntry(eventFingerprint, now))
            return false
        }
    }

    /**
     * Clears all recorded fingerprints.
     */
    fun clear() {
        synchronized(lock) {
            buffer.clear()
        }
    }

    /**
     * Returns the current number of tracked fingerprints.
     */
    fun size(): Int {
        synchronized(lock) {
            purgeExpired(System.currentTimeMillis())
            return buffer.size
        }
    }

    /** Remove entries older than [windowMs]. */
    private fun purgeExpired(now: Long) {
        val cutoff = now - windowMs
        while (buffer.isNotEmpty() && buffer.first().timestamp < cutoff) {
            buffer.removeFirst()
        }
    }
}

package com.elyedali.makhzan.security

import java.security.MessageDigest
import java.security.SecureRandom

/** الرموز السرية: لا يُحفظ الرمز نفسه، بل بصمة مملّحة منه */
object Pins {
    fun isValid(pin: String) = pin.length in 4..8 && pin.all { it.isDigit() }

    fun hash(pin: String): String {
        val salt = ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        return "$salt:${digest(salt, pin)}"
    }

    fun matches(pin: String, stored: String?): Boolean {
        if (stored.isNullOrBlank()) return false
        val parts = stored.split(":")
        if (parts.size != 2) return false
        return MessageDigest.isEqual(digest(parts[0], pin).toByteArray(), parts[1].toByteArray())
    }

    private fun digest(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("makhzan:$salt:$pin".toByteArray())
            .joinToString("") { "%02x".format(it) }
}

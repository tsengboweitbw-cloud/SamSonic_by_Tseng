package com.example.samsonic.data.remote

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Builds Subsonic's salted-token auth params (token = md5(password + salt)) so the plaintext
 * password never goes over the wire, only a fresh, unguessable-per-request digest.
 */
object SubsonicAuth {
    private const val CLIENT_ID = "SamSonic"
    private const val API_VERSION = "1.16.1"

    fun params(username: String, password: String): Map<String, String> {
        val salt = randomSalt()
        val token = md5Hex(password + salt)
        return mapOf(
            "u" to username,
            "t" to token,
            "s" to salt,
            "v" to API_VERSION,
            "c" to CLIENT_ID,
            "f" to "json",
        )
    }

    private fun randomSalt(): String {
        val bytes = ByteArray(12)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

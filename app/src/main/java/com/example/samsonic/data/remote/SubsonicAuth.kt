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

    private val random = SecureRandom()

    private fun randomSalt(): String {
        val bytes = ByteArray(12)
        random.nextBytes(bytes)
        return bytes.toHex()
    }

    private fun md5Hex(input: String): String =
        MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8)).toHex()

    private const val HEX = "0123456789abcdef"

    // Straight to characters: String.format per byte is slow for something done per request.
    private fun ByteArray.toHex(): String {
        val chars = CharArray(size * 2)
        forEachIndexed { i, b ->
            val v = b.toInt() and 0xff
            chars[i * 2] = HEX[v ushr 4]
            chars[i * 2 + 1] = HEX[v and 0x0f]
        }
        return String(chars)
    }
}

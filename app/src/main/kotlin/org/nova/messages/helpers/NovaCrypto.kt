package org.nova.messages.helpers

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object NovaCrypto {
    private val encodingMap = mapOf(
        'A' to Pair("4427", "[3]"), 'B' to Pair("9910", "<4>"), 'C' to Pair("1182", "{19}"),
        'D' to Pair("5501", "[9]"), 'E' to Pair("7740", "<2>"), 'F' to Pair("3308", "{7}"),
        'G' to Pair("9021", "[5]"), 'H' to Pair("6614", "<3>"), 'I' to Pair("1209", "{11}"),
        'J' to Pair("8842", "[6]"), 'K' to Pair("4410", "<8>"), 'L' to Pair("5599", "{13}"),
        'M' to Pair("7721", "[4]"), 'N' to Pair("9933", "<5>"), 'O' to Pair("2288", "{17}"),
        'P' to Pair("6401", "[7]"), 'Q' to Pair("3109", "<6>"), 'R' to Pair("5577", "{3}"),
        'S' to Pair("9012", "[8]"), 'T' to Pair("4420", "<9>"), 'U' to Pair("7711", "{5}"),
        'V' to Pair("1199", "[4]"), 'W' to Pair("3302", "<7>"), 'X' to Pair("8840", "{2}"),
        'Y' to Pair("5511", "[3]"), 'Z' to Pair("9931", "<4>")
    )

    private val decodingMap = encodingMap.entries.associate { it.value.first to it.key }

    const val NOVA_PREFIX = "\u200B\u200CNOVA:"
    private const val DEFAULT_SECRET = "NOVA_CORE_SECRET_2026_EVOLVE_INIT"

    // GCM IV is 12 bytes; prepended to ciphertext so receiver can recover it.
    private const val IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    /**
     * Encode letters to their 4-digit representation.
     * Preserves case: only uppercase A-Z are encoded; lowercase and other chars pass through.
     */
    fun encodeLetters(text: String): String {
        return text.map { char ->
            encodingMap[char]?.first ?: char.toString()
        }.joinToString("")
    }

    /**
     * Decode 4-digit sequences back to letters.
     * Only decodes when the 4-digit group is a known encoded letter (in decodingMap).
     * Leaves all other characters (including real numbers) untouched.
     */
    fun decodeNumbers(encoded: String): String {
        val result = StringBuilder()
        var i = 0
        while (i < encoded.length) {
            if (i + 4 <= encoded.length) {
                val part = encoded.substring(i, i + 4)
                val letter = decodingMap[part]
                if (letter != null) {
                    result.append(letter)
                    i += 4
                    continue
                }
            }
            result.append(encoded[i])
            i++
        }
        return result.toString()
    }

    fun evolveKey(currentSecret: String?, token: String): String {
        val base = if (currentSecret.isNullOrEmpty()) DEFAULT_SECRET else currentSecret
        val input = base + token
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun getAESKey(secret: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256").digest(secret.toByteArray())
        return SecretKeySpec(digest.copyOfRange(0, 16), "AES")
    }

    /**
     * Encrypt text using AES-GCM with a random 12-byte IV.
     * The IV is prepended to the ciphertext and the whole thing is Base64-encoded.
     */
    fun encrypt(text: String, secret: String): String {
        val key = getAESKey(secret)
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val encrypted = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        // Prepend IV to ciphertext
        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypt Base64-encoded ciphertext.
     * Extracts the 12-byte IV from the first 12 bytes, then decrypts the rest.
     */
    fun decrypt(encryptedBase64: String, secret: String): String {
        val key = getAESKey(secret)
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        if (combined.size < IV_LENGTH) throw IllegalArgumentException("Ciphertext too short")
        val iv = combined.copyOfRange(0, IV_LENGTH)
        val ciphertext = combined.copyOfRange(IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val decoded = cipher.doFinal(ciphertext)
        return String(decoded, Charsets.UTF_8)
    }

    fun getTokenForFirstChar(text: String): String {
        val firstChar = text.firstOrNull()?.uppercaseChar() ?: 'A'
        return encodingMap[firstChar]?.second ?: "[3]"
    }

    fun wrapMessage(encrypted: String, token: String): String {
        val noiseSymbols = listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")", "_", "+", "|", "=", "?", "/")
        val noise1 = noiseSymbols.random()
        val noise2 = noiseSymbols.random()
        return "$NOVA_PREFIX\u200D$token\u200E$noise1$encrypted$noise2"
    }

    fun unwrapMessage(fullText: String): Pair<String, String>? {
        if (!fullText.startsWith(NOVA_PREFIX)) return null
        val tokenRegex = Regex("\u200D(.*?)\u200E")
        val match = tokenRegex.find(fullText) ?: return null
        val token = match.groupValues[1]

        var encryptedPart = fullText.substringAfter("\u200E")
        if (encryptedPart.length > 2) {
            encryptedPart = encryptedPart.substring(1, encryptedPart.length - 1)
        }
        return Pair(token, encryptedPart)
    }
}
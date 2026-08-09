package org.nova.messages.helpers

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec

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

    fun encodeLetters(text: String): String {
        return text.uppercase().map { char ->
            encodingMap[char]?.first ?: char.toString()
        }.joinToString("")
    }

    fun decodeNumbers(encoded: String): String {
        var result = ""
        var i = 0
        while (i < encoded.length) {
            var found = false
            for (len in 4 downTo 1) {
                if (i + len <= encoded.length) {
                    val part = encoded.substring(i, i + len)
                    val letter = decodingMap[part]
                    if (letter != null) {
                        result += letter
                        i += len
                        found = true
                        break
                    }
                }
            }
            if (!found) {
                result += encoded[i]
                i++
            }
        }
        return result
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

    fun encrypt(text: String, secret: String): String {
        val key = getAESKey(secret)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val iv = IvParameterSpec(ByteArray(16))
        cipher.init(Cipher.ENCRYPT_MODE, key, iv)
        val encrypted = cipher.doFinal(text.toByteArray())
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    fun decrypt(encryptedBase64: String, secret: String): String {
        val key = getAESKey(secret)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val iv = IvParameterSpec(ByteArray(16))
        cipher.init(Cipher.DECRYPT_MODE, key, iv)
        val decoded = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        return String(cipher.doFinal(decoded))
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

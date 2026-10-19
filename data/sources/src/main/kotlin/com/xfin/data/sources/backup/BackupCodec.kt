package com.xfin.data.sources.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Everything a backup holds. Sources carry their passwords/tokens in plain text, so the file is encrypted. */
@Serializable
internal data class BackupData(
    val createdAt: Long,
    val sources: List<BackupSource> = emptyList(),
    val favorites: List<BackupChannelRef> = emptyList(),
    val history: List<BackupChannelRef> = emptyList(),
    val hiddenGroups: List<String> = emptyList(),
    val vodProgress: List<BackupVodProgress> = emptyList(),
    val vodFavorites: List<BackupVodFavorite> = emptyList(),
    /** SharedPreferences files by name; only string values are kept. */
    val preferences: Map<String, Map<String, String>> = emptyMap(),
)

@Serializable
internal data class BackupSource(
    /** The source's id on the device that made the backup; rows below refer to it. */
    val id: Long,
    val name: String,
    val type: String,
    val url: String,
    val username: String? = null,
    /** Xtream password or Jellyfin access token. */
    val secret: String? = null,
    val epgUrl: String? = null,
    val userAgent: String? = null,
    val xtreamOutput: String = "TS",
    val createdAt: Long = 0,
    val userId: String? = null,
    val serverId: String? = null,
)

/** A favorite (at = added) or a history entry (at = watched). */
@Serializable
internal data class BackupChannelRef(val sourceId: Long, val channelKey: String, val at: Long)

@Serializable
internal data class BackupVodProgress(
    val sourceId: Long,
    val itemId: String,
    val positionMs: Long,
    val durationMs: Long,
    val played: Boolean,
    val updatedAt: Long,
    val kind: String,
    val name: String,
    val seriesId: String? = null,
    val seriesName: String? = null,
    val posterUrl: String? = null,
    val thumbUrl: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
)

@Serializable
internal data class BackupVodFavorite(val sourceId: Long, val kind: String, val providerId: Long, val addedAt: Long)

/** The file on disk: a small readable header around the encrypted [BackupData]. */
@Serializable
private data class Envelope(
    val format: String,
    val version: Int,
    val kdf: String,
    val iterations: Int,
    val salt: String,
    val iv: String,
    @SerialName("data") val ciphertext: String,
)

internal class BackupException(message: String) : Exception(message)

/** Encodes backups as password-encrypted JSON (PBKDF2-SHA256 key, AES-256-GCM). */
internal object BackupCodec {
    private const val FORMAT = "xfin-backup"
    private const val VERSION = 1
    private const val KDF = "PBKDF2WithHmacSHA256"
    const val DEFAULT_ITERATIONS = 210_000

    /** Refuses files that would make key derivation run for minutes. */
    private const val MAX_ITERATIONS = 5_000_000

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val random = SecureRandom()

    fun encode(data: BackupData, password: String, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(json.encodeToString(BackupData.serializer(), data).toByteArray(Charsets.UTF_8))
        val b64 = Base64.getEncoder()
        val envelope = Envelope(FORMAT, VERSION, KDF, iterations, b64.encodeToString(salt), b64.encodeToString(iv), b64.encodeToString(ciphertext))
        return json.encodeToString(Envelope.serializer(), envelope).toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray, password: String): BackupData {
        val envelope = try {
            json.decodeFromString(Envelope.serializer(), bytes.toString(Charsets.UTF_8))
        } catch (e: SerializationException) {
            throw BackupException("This isn't an xFIN backup file")
        } catch (e: IllegalArgumentException) {
            throw BackupException("This isn't an xFIN backup file")
        }
        if (envelope.format != FORMAT || envelope.kdf != KDF) throw BackupException("This isn't an xFIN backup file")
        if (envelope.version > VERSION) throw BackupException("This backup was made by a newer version of xFIN")
        if (envelope.iterations !in 1..MAX_ITERATIONS) throw BackupException("The backup file is damaged")
        val b64 = Base64.getDecoder()
        val plain = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(password, b64.decode(envelope.salt), envelope.iterations),
                GCMParameterSpec(128, b64.decode(envelope.iv)),
            )
            cipher.doFinal(b64.decode(envelope.ciphertext))
        } catch (e: AEADBadTagException) {
            throw BackupException("Wrong password, or the file is damaged")
        } catch (e: GeneralSecurityException) {
            throw BackupException("The backup file is damaged")
        } catch (e: IllegalArgumentException) {
            throw BackupException("The backup file is damaged")
        }
        return json.decodeFromString(BackupData.serializer(), plain.toString(Charsets.UTF_8))
    }

    private fun key(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}

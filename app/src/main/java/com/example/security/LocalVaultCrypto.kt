package com.example.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class VaultState {
    HEALTHY,
    KEY_UNAVAILABLE,
    KEY_CORRUPTED,
    TEST_EPHEMERAL
}

class LocalVaultUnavailableException(
    val vaultState: VaultState,
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

/**
 * Hardware-backed Android Keystore AES-256-GCM Envelope Encryption Engine (KEK / DEK).
 *
 * Security invariants:
 * - Fail-Closed: On Android devices, if the AndroidKeyStore KEK cannot be loaded or the wrapped
 *   DEK file exists on disk but fails authenticated unwrapping, the vault NEVER overwrites the
 *   wrapped DEK file and NEVER silently falls back to an ephemeral in-memory key. Instead it
 *   transitions [vaultState] to [VaultState.KEY_CORRUPTED] or [VaultState.KEY_UNAVAILABLE] and
 *   throws [LocalVaultUnavailableException] so existing ciphertext remains untouched until the
 *   user explicitly retries unlock or confirms cryptographic vault reset.
 * - Backward Compatibility: Decrypts V2 envelope (`ENC:AKSV2:`), V1 direct Keystore (`ENC:AKSV1:`),
 *   legacy static (`ENC:AES256GCM:`), and pre-encryption plaintext rows.
 * - Cryptographic Erasure: [cryptographicEraseAndResetVault] destroys both AndroidKeyStore KEK
 *   aliases and the wrapped DEK file, rendering residual SQLite pages unrecoverable.
 */
object LocalVaultCrypto {
    const val VAULT_PREFIX_V2 = "ENC:AKSV2:"
    const val VAULT_PREFIX_V1 = "ENC:AKSV1:"
    private const val LEGACY_STATIC_PREFIX = "ENC:AES256GCM:"

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val LEGACY_V1_KEY_ALIAS = "krimoon_local_vault_aes256_v1"
    private const val KEK_V2_KEY_ALIAS = "krimoon_local_vault_kek_aes256_v2"
    private const val WRAPPED_DEK_FILENAME = "krimoon_vault_dek_v2.bin"
    private const val DEK_WRAP_AAD = "KRIMOON_DEK_WRAP_V2"

    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    private val secureRandom = SecureRandom()
    private val keyLock = Any()

    private val _vaultState = MutableStateFlow(VaultState.HEALTHY)
    val vaultState: StateFlow<VaultState> = _vaultState.asStateFlow()

    @Volatile
    private var appNoBackupDir: File? = null

    @Volatile
    private var cachedDek: SecretKey? = null

    @Volatile
    private var cachedRawDekBytes: ByteArray? = null

    @Volatile
    private var cachedLegacyV1Key: SecretKey? = null

    @Volatile
    var isHardwareKeystoreBacked: Boolean = false
        private set

    @Volatile
    private var testModeEnabled: Boolean = false

    @Volatile
    private var forcedFailureStateForTest: VaultState? = null

    private fun isJvmOrRobolectricTestEnv(): Boolean {
        if (testModeEnabled) return true
        val fp = runCatching { Build.FINGERPRINT }.getOrNull().orEmpty()
        if (fp.equals("robolectric", ignoreCase = true)) return true
        val runtime = System.getProperty("java.runtime.name").orEmpty()
        return !runtime.contains("Android", ignoreCase = true)
    }

    /**
     * Initializes persistent hardware-wrapped DEK storage using the app's `noBackupFilesDir`
     * (excluded from cloud and ADB backups). Safe to call multiple times.
     */
    fun init(context: Context) {
        synchronized(keyLock) {
            if (appNoBackupDir == null) {
                val appCtx = context.applicationContext
                appNoBackupDir = runCatching { appCtx.noBackupFilesDir }.getOrNull() ?: appCtx.filesDir
            }
        }
    }

    /**
     * Initializes storage and eagerly verifies KEK/DEK health during Application startup
     * or user-triggered recovery retry. Never overwrites an existing unreadable DEK file.
     */
    fun initializeAndVerify(context: Context): VaultState {
        init(context)
        return try {
            getOrCreateDek()
            _vaultState.value
        } catch (e: LocalVaultUnavailableException) {
            _vaultState.value = e.vaultState
            e.vaultState
        } catch (_: Throwable) {
            _vaultState.value = VaultState.KEY_UNAVAILABLE
            VaultState.KEY_UNAVAILABLE
        }
    }

    /**
     * Retries unwrapping the existing KEK/DEK without modifying disk state.
     */
    fun retryUnlock(): VaultState {
        synchronized(keyLock) {
            if (forcedFailureStateForTest != null) {
                _vaultState.value = forcedFailureStateForTest!!
                return _vaultState.value
            }
            cachedDek = null
            cachedRawDekBytes = null
            return try {
                getOrCreateDek()
                _vaultState.value
            } catch (e: LocalVaultUnavailableException) {
                _vaultState.value = e.vaultState
                e.vaultState
            }
        }
    }

    /**
     * Returns true only when the vault key is actively loaded and writes will not risk
     * encrypting with an unintended key or overwriting recoverable ciphertext.
     */
    fun canSafelyWrite(): Boolean {
        if (forcedFailureStateForTest != null) return false
        val state = _vaultState.value
        return state == VaultState.HEALTHY || state == VaultState.TEST_EPHEMERAL
    }

    fun getWrappedDekFileForDiagnostics(): File? {
        return appNoBackupDir?.let { File(it, WRAPPED_DEK_FILENAME) }
    }

    private fun getOrCreateDek(): SecretKey {
        forcedFailureStateForTest?.let { forced ->
            _vaultState.value = forced
            isHardwareKeystoreBacked = false
            throw LocalVaultUnavailableException(
                vaultState = forced,
                message = "Local vault locked ($forced): refusing to overwrite ciphertext or use an ephemeral key."
            )
        }

        cachedDek?.let { return it }
        synchronized(keyLock) {
            forcedFailureStateForTest?.let { forced ->
                _vaultState.value = forced
                isHardwareKeystoreBacked = false
                throw LocalVaultUnavailableException(
                    vaultState = forced,
                    message = "Local vault locked ($forced): refusing to overwrite ciphertext or use an ephemeral key."
                )
            }
            cachedDek?.let { return it }

            val dir = appNoBackupDir
            val dekFile = dir?.let { File(it, WRAPPED_DEK_FILENAME) }
            val kek = tryLoadOrCreateKeystoreKey(KEK_V2_KEY_ALIAS)

            if (kek != null && dekFile != null) {
                // 1. Existing wrapped DEK on disk: MUST unwrap cleanly. NEVER overwrite on failure.
                if (dekFile.exists()) {
                    val rawDek = tryUnwrapDekBytesFromDisk(dekFile, kek)
                    if (rawDek != null) {
                        val dek = SecretKeySpec(rawDek, "AES")
                        isHardwareKeystoreBacked = true
                        cachedRawDekBytes = rawDek
                        cachedDek = dek
                        _vaultState.value = VaultState.HEALTHY
                        return dek
                    } else {
                        isHardwareKeystoreBacked = false
                        _vaultState.value = VaultState.KEY_CORRUPTED
                        throw LocalVaultUnavailableException(
                            vaultState = VaultState.KEY_CORRUPTED,
                            message = "Wrapped DEK file exists but could not be unwrapped by AndroidKeyStore KEK. Preserving existing ciphertext."
                        )
                    }
                }

                // 2. Fresh installation (no wrapped DEK yet): generate 256-bit DEK and wrap with hardware KEK
                val rawDek = ByteArray(32).also { secureRandom.nextBytes(it) }
                if (tryWrapDekToDisk(dekFile, rawDek, kek)) {
                    val dek = SecretKeySpec(rawDek, "AES")
                    isHardwareKeystoreBacked = true
                    cachedRawDekBytes = rawDek
                    cachedDek = dek
                    _vaultState.value = VaultState.HEALTHY
                    return dek
                } else {
                    isHardwareKeystoreBacked = false
                    _vaultState.value = VaultState.KEY_UNAVAILABLE
                    throw LocalVaultUnavailableException(
                        vaultState = VaultState.KEY_UNAVAILABLE,
                        message = "Failed to persist hardware-wrapped DEK to noBackupFilesDir."
                    )
                }
            }

            // Fail-closed if a wrapped DEK already exists on disk even in test/non-Keystore mode
            if (dekFile != null && dekFile.exists() && !testModeEnabled) {
                isHardwareKeystoreBacked = false
                _vaultState.value = VaultState.KEY_CORRUPTED
                throw LocalVaultUnavailableException(
                    vaultState = VaultState.KEY_CORRUPTED,
                    message = "AndroidKeyStore KEK is unavailable while wrapped DEK exists on disk."
                )
            }

            // Ephemeral key is ONLY permitted under JVM/Robolectric unit tests (never on production Android)
            if (isJvmOrRobolectricTestEnv()) {
                isHardwareKeystoreBacked = false
                val randomBytes = ByteArray(32).also { secureRandom.nextBytes(it) }
                val fallback = SecretKeySpec(randomBytes, "AES")
                cachedRawDekBytes = randomBytes
                cachedDek = fallback
                _vaultState.value = VaultState.TEST_EPHEMERAL
                return fallback
            }

            // Production Android device where AndroidKeyStore or noBackupFilesDir is unavailable -> FAIL CLOSED
            isHardwareKeystoreBacked = false
            _vaultState.value = VaultState.KEY_UNAVAILABLE
            throw LocalVaultUnavailableException(
                vaultState = VaultState.KEY_UNAVAILABLE,
                message = "AndroidKeyStore master key is unavailable. Refusing insecure ephemeral fallback."
            )
        }
    }

    /**
     * Non-destructive KEK re-wrap: re-wraps the SAME in-memory 256-bit DEK with a fresh
     * AndroidKeyStore KEK so all existing encrypted database rows remain readable without
     * losing data. Returns false if the DEK is not currently unlocked in memory.
     */
    fun rewrapDekWithFreshKek(): Boolean {
        synchronized(keyLock) {
            if (!canSafelyWrite()) return false
            val rawDek = cachedRawDekBytes ?: return false
            val dir = appNoBackupDir ?: return false
            val dekFile = File(dir, WRAPPED_DEK_FILENAME)
            return try {
                val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                if (ks.containsAlias(KEK_V2_KEY_ALIAS)) {
                    ks.deleteEntry(KEK_V2_KEY_ALIAS)
                }
                val freshKek = tryLoadOrCreateKeystoreKey(KEK_V2_KEY_ALIAS) ?: return false
                tryWrapDekToDisk(dekFile, rawDek, freshKek)
            } catch (_: Throwable) {
                // In JVM/Robolectric test environments without AndroidKeyStore, keep the same DEK intact
                isJvmOrRobolectricTestEnv()
            }
        }
    }

    private fun getLegacyV1KeyOrNull(): SecretKey? {
        cachedLegacyV1Key?.let { return it }
        synchronized(keyLock) {
            cachedLegacyV1Key?.let { return it }
            val key = tryLoadExistingKeystoreKey(LEGACY_V1_KEY_ALIAS)
            cachedLegacyV1Key = key
            return key
        }
    }

    private fun tryLoadExistingKeystoreKey(alias: String): SecretKey? {
        return try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            ks.getKey(alias, null) as? SecretKey
        } catch (_: Throwable) {
            null
        }
    }

    private fun tryLoadOrCreateKeystoreKey(alias: String): SecretKey? {
        return try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = ks.getKey(alias, null) as? SecretKey
            if (existing != null) return existing

            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGen.init(spec)
            keyGen.generateKey()
        } catch (_: Throwable) {
            null
        }
    }

    private fun tryWrapDekToDisk(dekFile: File, rawDek: ByteArray, kek: SecretKey): Boolean {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, kek)
            cipher.updateAAD(DEK_WRAP_AAD.toByteArray(StandardCharsets.UTF_8))
            val wrapped = cipher.doFinal(rawDek)
            val iv = cipher.iv
            if (iv.size != GCM_IV_BYTES) return false
            val blob = ByteArray(GCM_IV_BYTES + wrapped.size)
            System.arraycopy(iv, 0, blob, 0, GCM_IV_BYTES)
            System.arraycopy(wrapped, 0, blob, GCM_IV_BYTES, wrapped.size)
            dekFile.parentFile?.mkdirs()
            val tmpFile = File(dekFile.parentFile, "${dekFile.name}.tmp")
            tmpFile.writeBytes(blob)
            if (!tmpFile.renameTo(dekFile)) {
                dekFile.writeBytes(blob)
                tmpFile.delete()
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun tryUnwrapDekBytesFromDisk(dekFile: File, kek: SecretKey): ByteArray? {
        return try {
            val blob = dekFile.readBytes()
            if (blob.size <= GCM_IV_BYTES + 16) return null
            val iv = blob.copyOfRange(0, GCM_IV_BYTES)
            val wrapped = blob.copyOfRange(GCM_IV_BYTES, blob.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(DEK_WRAP_AAD.toByteArray(StandardCharsets.UTF_8))
            val rawDek = cipher.doFinal(wrapped)
            if (rawDek.size != 32) return null
            rawDek
        } catch (_: Throwable) {
            null
        }
    }

    fun isEncrypted(value: String): Boolean {
        return value.startsWith(VAULT_PREFIX_V2) ||
            value.startsWith(VAULT_PREFIX_V1) ||
            value.startsWith(LEGACY_STATIC_PREFIX)
    }

    fun isLatestFormat(value: String): Boolean {
        return value.startsWith(VAULT_PREFIX_V2)
    }

    /**
     * Encrypts a plaintext field with AES-256-GCM using the hardware-wrapped DEK,
     * bound to [aadContext] (table:column). Already-V2 values are returned unchanged.
     * Throws [LocalVaultUnavailableException] if the vault is locked/corrupted.
     */
    fun encryptField(plainText: String, aadContext: String): String {
        if (plainText.isEmpty()) return ""
        if (plainText.startsWith(VAULT_PREFIX_V2)) return plainText

        val normalizedPlain = when {
            plainText.startsWith(VAULT_PREFIX_V1) -> decryptV1DirectResult(plainText, aadContext)
                .getOrElse { throw LocalVaultUnavailableException(_vaultState.value, "Failed to re-encrypt V1 vault field.", it) }
            plainText.startsWith(LEGACY_STATIC_PREFIX) -> decryptLegacyStaticResult(plainText, aadContext)
                .getOrElse { throw LocalVaultUnavailableException(_vaultState.value, "Failed to re-encrypt legacy vault field.", it) }
            else -> plainText
        }

        val dek = getOrCreateDek()
        val iv = ByteArray(GCM_IV_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, dek, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD("KRIMOON_VAULT_V2:$aadContext".toByteArray(StandardCharsets.UTF_8))

        val cipherBytes = cipher.doFinal(normalizedPlain.toByteArray(StandardCharsets.UTF_8))
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val ctB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        return "$VAULT_PREFIX_V2$ivB64:$ctB64"
    }

    /**
     * Explicit Result-returning decryption API. Callers performing database updates or migrations
     * MUST use this or check [canSafelyWrite] so failed decryption is never confused with empty text.
     */
    fun decryptFieldResult(storedText: String, aadContext: String): Result<String> {
        if (storedText.isEmpty()) return Result.success("")
        return when {
            storedText.startsWith(VAULT_PREFIX_V2) -> decryptV2EnvelopeResult(storedText, aadContext)
            storedText.startsWith(VAULT_PREFIX_V1) -> decryptV1DirectResult(storedText, aadContext)
            storedText.startsWith(LEGACY_STATIC_PREFIX) -> decryptLegacyStaticResult(storedText, aadContext)
            else -> Result.success(storedText) // Seamless read of legacy pre-encryption plaintext rows
        }
    }

    /**
     * Decrypts a vault-encrypted field for read-only UI display.
     * On tampering or unavailable key, records a neutralized threat and returns empty string
     * without mutating stored database records.
     */
    fun decryptField(storedText: String, aadContext: String): String {
        return decryptFieldResult(storedText, aadContext).getOrElse { "" }
    }

    private fun decryptV2EnvelopeResult(storedText: String, aadContext: String): Result<String> {
        return try {
            val payload = storedText.removePrefix(VAULT_PREFIX_V2)
            val colonIdx = payload.indexOf(':')
            if (colonIdx <= 0) {
                BackendSecurityFirewall.recordNeutralizedThreat()
                return Result.failure(SecurityException("Malformed V2 vault ciphertext."))
            }
            val iv = Base64.decode(payload.substring(0, colonIdx), Base64.NO_WRAP)
            val ct = Base64.decode(payload.substring(colonIdx + 1), Base64.NO_WRAP)
            if (iv.size != GCM_IV_BYTES) {
                BackendSecurityFirewall.recordNeutralizedThreat()
                return Result.failure(SecurityException("Invalid V2 AES-GCM IV length."))
            }

            val dek = getOrCreateDek()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, dek, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD("KRIMOON_VAULT_V2:$aadContext".toByteArray(StandardCharsets.UTF_8))
            Result.success(String(cipher.doFinal(ct), StandardCharsets.UTF_8))
        } catch (e: LocalVaultUnavailableException) {
            Result.failure(e)
        } catch (e: Throwable) {
            BackendSecurityFirewall.recordNeutralizedThreat()
            Result.failure(SecurityException("Vault V2 GCM authentication failed.", e))
        }
    }

    private fun decryptV1DirectResult(storedText: String, aadContext: String): Result<String> {
        return try {
            val payload = storedText.removePrefix(VAULT_PREFIX_V1)
            val colonIdx = payload.indexOf(':')
            if (colonIdx <= 0) {
                BackendSecurityFirewall.recordNeutralizedThreat()
                return Result.failure(SecurityException("Malformed V1 vault ciphertext."))
            }
            val iv = Base64.decode(payload.substring(0, colonIdx), Base64.NO_WRAP)
            val ct = Base64.decode(payload.substring(colonIdx + 1), Base64.NO_WRAP)
            if (iv.size != GCM_IV_BYTES) {
                BackendSecurityFirewall.recordNeutralizedThreat()
                return Result.failure(SecurityException("Invalid V1 AES-GCM IV length."))
            }

            val key = getLegacyV1KeyOrNull()
                ?: return Result.failure(LocalVaultUnavailableException(VaultState.KEY_UNAVAILABLE, "Legacy V1 Keystore key unavailable."))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD("KRIMOON_VAULT_V1:$aadContext".toByteArray(StandardCharsets.UTF_8))
            Result.success(String(cipher.doFinal(ct), StandardCharsets.UTF_8))
        } catch (e: Throwable) {
            BackendSecurityFirewall.recordNeutralizedThreat()
            Result.failure(SecurityException("Vault V1 GCM authentication failed.", e))
        }
    }

    /**
     * Explicit destructive cryptographic erasure and reset.
     * ONLY call after explicit user confirmation in the Vault Recovery Modal or Full Data Wipe!
     * Deletes the wrapped DEK file and AndroidKeyStore KEK aliases, then generates a brand-new
     * 256-bit DEK so residual SQLite pages become permanently unrecoverable.
     */
    fun cryptographicEraseAndResetVault() {
        synchronized(keyLock) {
            forcedFailureStateForTest = null
            cachedDek = null
            cachedRawDekBytes?.fill(0)
            cachedRawDekBytes = null
            cachedLegacyV1Key = null
            try {
                appNoBackupDir?.let { dir ->
                    File(dir, WRAPPED_DEK_FILENAME).delete()
                    File(dir, "$WRAPPED_DEK_FILENAME.tmp").delete()
                }
            } catch (_: Throwable) {}
            try {
                val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                if (ks.containsAlias(KEK_V2_KEY_ALIAS)) ks.deleteEntry(KEK_V2_KEY_ALIAS)
                if (ks.containsAlias(LEGACY_V1_KEY_ALIAS)) ks.deleteEntry(LEGACY_V1_KEY_ALIAS)
            } catch (_: Throwable) {}
            runCatching { getOrCreateDek() }
        }
    }

    /**
     * Backward-compatible alias for cryptographic erasure during full local wipe.
     */
    fun rotateVaultKey() {
        cryptographicEraseAndResetVault()
    }

    // --- Deterministic JVM / Robolectric Test Hooks ---

    internal fun resetForTest(noBackupDir: File? = null, enableTestMode: Boolean = true) {
        synchronized(keyLock) {
            forcedFailureStateForTest = null
            testModeEnabled = enableTestMode
            cachedDek = null
            cachedRawDekBytes?.fill(0)
            cachedRawDekBytes = null
            cachedLegacyV1Key = null
            isHardwareKeystoreBacked = false
            if (noBackupDir != null) {
                appNoBackupDir = noBackupDir
            }
            _vaultState.value = if (enableTestMode) VaultState.TEST_EPHEMERAL else VaultState.HEALTHY
        }
    }

    internal fun simulateKeystoreFailureForTest(state: VaultState?) {
        synchronized(keyLock) {
            forcedFailureStateForTest = state
            if (state != null) {
                cachedDek = null
                cachedRawDekBytes?.fill(0)
                cachedRawDekBytes = null
                isHardwareKeystoreBacked = false
                _vaultState.value = state
            } else {
                _vaultState.value = if (isJvmOrRobolectricTestEnv()) VaultState.TEST_EPHEMERAL else VaultState.HEALTHY
            }
        }
    }

    private fun decryptLegacyStaticResult(storedText: String, aadContext: String): Result<String> {
        return try {
            val payload = storedText.removePrefix(LEGACY_STATIC_PREFIX)
            val parts = payload.split(":")
            if (parts.size != 2) {
                BackendSecurityFirewall.recordNeutralizedThreat()
                return Result.failure(SecurityException("Malformed legacy vault ciphertext."))
            }
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ct = Base64.decode(parts[1], Base64.NO_WRAP)
            val salt = "KriMoon_AtRest_Salt_v2_KriMoonLocalVault_v2".toByteArray(StandardCharsets.UTF_8)
            val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec = javax.crypto.spec.PBEKeySpec("KriMoonLocalVault_v2".toCharArray(), salt, 4096, 256)
            val legacyKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, legacyKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(aadContext.toByteArray(StandardCharsets.UTF_8))
            Result.success(String(cipher.doFinal(ct), StandardCharsets.UTF_8))
        } catch (e: Throwable) {
            BackendSecurityFirewall.recordNeutralizedThreat()
            Result.failure(SecurityException("Legacy static vault decryption failed.", e))
        }
    }
}

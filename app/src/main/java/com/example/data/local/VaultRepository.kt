package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest

class VaultSecurityManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("gs_vault_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PIN_HASH = "vault_pin_hash"
        private const val KEY_IS_SETUP = "vault_is_setup"
        private const val KEY_SECURITY_ANSWER_HASH = "vault_sec_answer_hash"
        private const val KEY_SECURITY_QUESTION = "vault_sec_question"
    }

    fun isVaultConfigured(): Boolean {
        return prefs.getBoolean(KEY_IS_SETUP, false) && prefs.getString(KEY_PIN_HASH, null) != null
    }

    fun setupPin(pin: String, securityQuestion: String = "Favorite Color", securityAnswer: String = "Cyan"): Boolean {
        if (pin.length < 4) return false
        val hashedPin = hashString(pin)
        val hashedAnswer = hashString(securityAnswer.trim().lowercase())
        prefs.edit()
            .putString(KEY_PIN_HASH, hashedPin)
            .putBoolean(KEY_IS_SETUP, true)
            .putString(KEY_SECURITY_QUESTION, securityQuestion)
            .putString(KEY_SECURITY_ANSWER_HASH, hashedAnswer)
            .apply()
        return true
    }

    fun verifyPin(pin: String): Boolean {
        val savedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return savedHash == hashString(pin)
    }

    fun resetPinWithAnswer(answer: String, newPin: String): Boolean {
        val savedAnswerHash = prefs.getString(KEY_SECURITY_ANSWER_HASH, null) ?: return false
        if (savedAnswerHash == hashString(answer.trim().lowercase())) {
            return setupPin(newPin)
        }
        return false
    }

    fun getSecurityQuestion(): String {
        return prefs.getString(KEY_SECURITY_QUESTION, "What is your secret word?") ?: "What is your secret word?"
    }

    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

class VaultRepository(
    private val vaultDao: VaultDao,
    private val streamHistoryDao: StreamHistoryDao
) {
    val allVaultItems: Flow<List<VaultItemEntity>> = vaultDao.getAllVaultItems()
    val allStreamHistory: Flow<List<StreamHistoryEntity>> = streamHistoryDao.getAllStreamHistory()
    val bookmarkedStreams: Flow<List<StreamHistoryEntity>> = streamHistoryDao.getBookmarkedStreams()

    suspend fun addVaultItem(item: VaultItemEntity): Long = vaultDao.insertVaultItem(item)
    suspend fun updateVaultItem(item: VaultItemEntity) = vaultDao.updateVaultItem(item)
    suspend fun deleteVaultItem(item: VaultItemEntity) = vaultDao.deleteVaultItem(item)
    suspend fun deleteVaultItemById(id: Long) = vaultDao.deleteVaultItemById(id)

    suspend fun recordStreamPlay(title: String, url: String, type: String, thumbnail: String? = null) {
        val entity = StreamHistoryEntity(
            title = title,
            streamUrl = url,
            mediaType = type,
            thumbnail = thumbnail,
            lastPlayedAt = System.currentTimeMillis()
        )
        streamHistoryDao.insertOrUpdateStream(entity)
    }

    suspend fun toggleBookmarkStream(id: Long, current: Boolean) {
        streamHistoryDao.setBookmark(id, !current)
    }

    suspend fun clearStreamHistory() {
        streamHistoryDao.clearHistory()
    }
}

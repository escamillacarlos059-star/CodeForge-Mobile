package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ideDataStore: DataStore<Preferences> by preferencesDataStore(name = "codeforge_prefs")

data class IdeSettings(
    val githubToken: String = "",
    val activeProjectId: String = "",
    val editorFontSizeSp: Int = 13,
    val wordWrap: Boolean = false
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val GITHUB_TOKEN = stringPreferencesKey("github_token")
        val ACTIVE_PROJECT_ID = stringPreferencesKey("active_project_id")
        val EDITOR_FONT_SIZE = intPreferencesKey("editor_font_size")
        val WORD_WRAP = booleanPreferencesKey("word_wrap")
    }

    val settingsFlow: Flow<IdeSettings> = context.ideDataStore.data.map { prefs ->
        IdeSettings(
            githubToken = prefs[Keys.GITHUB_TOKEN] ?: "",
            activeProjectId = prefs[Keys.ACTIVE_PROJECT_ID] ?: "",
            editorFontSizeSp = prefs[Keys.EDITOR_FONT_SIZE] ?: 13,
            wordWrap = prefs[Keys.WORD_WRAP] ?: false
        )
    }

    suspend fun saveGithubToken(token: String) {
        context.ideDataStore.edit { it[Keys.GITHUB_TOKEN] = token.trim() }
    }

    suspend fun saveActiveProjectId(projectId: String) {
        context.ideDataStore.edit { it[Keys.ACTIVE_PROJECT_ID] = projectId }
    }

    suspend fun saveEditorFontSize(sizeSp: Int) {
        context.ideDataStore.edit { it[Keys.EDITOR_FONT_SIZE] = sizeSp.coerceIn(10, 22) }
    }

    suspend fun saveWordWrap(enabled: Boolean) {
        context.ideDataStore.edit { it[Keys.WORD_WRAP] = enabled }
    }
}

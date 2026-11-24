// PillReadings.kt

package com.fizy.music.floating

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import com.fizy.music.constants.LyricsRomanizeAsMainKey
import com.fizy.music.constants.TranslateLanguageKey
import com.fizy.music.constants.TranslateModeKey
import com.fizy.music.constants.TranslateSourceLanguageKey
import com.fizy.music.db.entities.LyricsEntity
import com.fizy.music.lyrics.GoogleTranslateEngine
import com.fizy.music.lyrics.LyricsEntry
import com.fizy.music.lyrics.LyricsTranslationHelper
import com.fizy.music.lyrics.YouTubeCaptionTranslation
import com.fizy.music.ui.player.LineReading
import com.fizy.music.ui.player.RomanizeLanguageKeys
import com.fizy.music.ui.player.readSong
import com.fizy.music.utils.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

// the reading and translation settings the lyrics page uses, read straight from the store since the pill has no screen of its own
internal data class ReadingSettings(
    val romanize: Map<Preferences.Key<Boolean>, Boolean>,
    val romanizedAsMain: Boolean,
    val language: String,
    val sourceLanguage: String,
    val mode: String,
)

internal fun readingSettings(context: Context): Flow<ReadingSettings> = context.dataStore.data
    .map { prefs ->
        ReadingSettings(
            romanize = RomanizeLanguageKeys.associateWith { prefs[it] ?: true },
            romanizedAsMain = prefs[LyricsRomanizeAsMainKey] ?: false,
            language = prefs[TranslateLanguageKey]?.takeIf { it.isNotBlank() } ?: "en",
            sourceLanguage = prefs[TranslateSourceLanguageKey]?.takeIf { it.isNotBlank() } ?: "auto",
            mode = prefs[TranslateModeKey] ?: "line",
        )
    }
    .distinctUntilChanged()

// every line's romanisation, worked out the way the lyrics page does it; null when the song is in nothing fizy romanises
internal suspend fun romanizedLines(lines: List<LyricsEntry>, settings: ReadingSettings): List<LineReading?>? {
    val out = arrayOfNulls<LineReading>(lines.size)
    readSong(lines, allowed = { settings.romanize[it] != false }) { index, reading -> out[index] = reading }
    return out.takeIf { found -> found.any { !it?.text.isNullOrBlank() } }?.toList()
}

// the translation the lyrics page would show: the saved one when it's in the chosen language, otherwise google's, kept in memory so it never replaces one the page saved
internal suspend fun translatedLines(lines: List<LyricsEntry>, saved: LyricsEntity?, settings: ReadingSettings): List<String?>? {
    val sung = lines.withIndex().filter { it.value.text.isNotBlank() }
    if (sung.isEmpty()) return null
    val target = settings.language
    val tags = setOf(
        target,
        LyricsTranslationHelper.languageTag(settings.sourceLanguage, target),
        YouTubeCaptionTranslation.storedLanguageTag(target),
    )
    val stored = saved?.takeIf { it.translatedLyrics.isNotBlank() && it.translationMode == settings.mode && it.translationLanguage in tags }
    val texts = stored?.translatedLyrics?.lines()
        ?: GoogleTranslateEngine.translate(sung.map { it.value.text }, target, settings.sourceLanguage)
        ?: return null
    val out = arrayOfNulls<String>(lines.size)
    sung.forEachIndexed { k, (index, entry) ->
        // a line already in the target language comes back the same, and showing it twice only doubles it
        out[index] = texts.getOrNull(k)?.takeIf { it.isNotBlank() && !it.trim().equals(entry.text.trim(), ignoreCase = true) }
    }
    return out.toList().takeIf { found -> found.any { it != null } }
}

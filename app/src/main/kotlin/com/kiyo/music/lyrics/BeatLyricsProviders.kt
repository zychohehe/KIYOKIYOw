// BeatLyricsProviders.kt
//
// Connects the lyrics sources in :providers:beatlyrics (taken from Beatcord, see that module's NOTICE.md)
// to the app's LyricsProvider interface. Each source is one provider; the heavy lifting (matching the
// song, picking the right recording, parsing) all happens in the module.

package com.kiyo.music.lyrics

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import com.kiyo.music.constants.EnableBetterLyricsKey
import com.kiyo.music.constants.EnableBetterLyricsPortatoKey
import com.kiyo.music.constants.EnableBiniLyricsKey
import com.kiyo.music.constants.EnableGeniusLyricsKey
import com.kiyo.music.constants.EnableKugouKey
import com.kiyo.music.constants.EnableLrcLibKey
import com.kiyo.music.constants.EnableLrcRedKey
import com.kiyo.music.constants.EnableMusixmatchLyricsKey
import com.kiyo.music.constants.EnablePaxsenixKey
import com.kiyo.music.constants.EnableUnisonKey
import com.kiyo.music.constants.EnableYouLyPlusKey
import com.kiyo.music.constants.PaxsenixApiKey
import com.kiyo.music.utils.dataStore
import com.kiyo.music.utils.get
import com.music.beatlyrics.LyricLine
import com.music.beatlyrics.LyricsRepository
import com.music.beatlyrics.LyricsSource
import com.music.beatlyrics.PaxSenix
import java.util.Locale

/** One lyrics source from the beatlyrics module, shown to the rest of the app as a LyricsProvider. */
class BeatLyricsProvider(
    override val name: String,
    private val source: LyricsSource,
    private val enableKey: Preferences.Key<Boolean>? = null,
    /** needs a PaxSenix API key to answer, so it stays off until one is saved */
    private val needsPaxsenixKey: Boolean = false,
) : LyricsProvider {

    override fun isEnabled(context: Context): Boolean {
        if (needsPaxsenixKey) {
            val key = context.dataStore[PaxsenixApiKey].orEmpty()
            if (key.isBlank()) return false
            PaxSenix.setApiKey(key)
        }
        return enableKey?.let { context.dataStore[it] } ?: true
    }

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = runCatching {
        val found = LyricsRepository.lyrics(
            videoId = id,
            title = title,
            artist = artist,
            durationMs = if (duration > 0) duration * 1000L else 0L,
            album = album,
            // only this one source: the app's own LyricsHelper does the ordering and picking between sources
            sources = setOf(source),
            order = listOf(source),
        )
        found?.lines?.toKiyoLrc()?.takeIf { it.isNotBlank() }
            ?: throw NoSuchElementException("$name has no lyrics for this song")
    }
}

/**
 * Lines as the text format the rest of the app reads: `[mm:ss.xxx]` per line and, when a source
 * times single words or syllables, `<mm:ss.xxx>` in front of each one. A backing vocal becomes its own
 * `{bg}` line, the same way the app already stores them.
 */
internal fun List<LyricLine>.toKiyoLrc(): String {
    if (isEmpty()) return ""
    // unsynced text has no timestamps to write
    if (none { it.timeMs > 0L }) return filter { it.text.isNotBlank() }.joinToString("\n") { it.text }
    val out = ArrayList<String>()
    sortedBy { it.timeMs }.forEach { line ->
        if (line.text.isNotBlank()) out += lrcStamp(line.timeMs, '[', ']') + line.toBody()
        line.background?.takeIf { it.text.isNotBlank() }?.let { bg ->
            out += lrcStamp(bg.timeMs, '[', ']') + "{bg}" + bg.toBody()
        }
    }
    return out.joinToString("\n")
}

private fun LyricLine.toBody(): String {
    if (words.isEmpty()) return text
    val sb = StringBuilder()
    words.forEachIndexed { index, word ->
        if (word.syllables.isEmpty()) {
            sb.append(lrcStamp(word.startMs, '<', '>')).append(word.text)
        } else {
            // a word timed by syllable: one stamp per piece, no space between them
            word.syllables.forEach { syllable ->
                val from = syllable.startChar.coerceIn(0, word.text.length)
                val to = syllable.endChar.coerceIn(from, word.text.length)
                sb.append(lrcStamp(syllable.startMs, '<', '>')).append(word.text.substring(from, to))
            }
        }
        if (index < words.lastIndex) sb.append(' ')
    }
    return sb.toString().trim()
}

private fun lrcStamp(timeMs: Long, open: Char, close: Char): String {
    val t = timeMs.coerceAtLeast(0L)
    return String.format(Locale.ROOT, "%c%02d:%02d.%03d%c", open, t / 60_000, t / 1000 % 60, t % 1000, close)
}

/** The sources, keyed by the names the registry and saved settings use. */
object BeatLyricsProviders {
    val LrcRed = BeatLyricsProvider("LrcRed", LyricsSource.LRC_RED, EnableLrcRedKey)
    val BiniLyrics = BeatLyricsProvider("BiniLyrics", LyricsSource.BINI_LYRICS, EnableBiniLyricsKey)
    val BetterLyrics = BeatLyricsProvider("BetterLyrics", LyricsSource.BETTER_LYRICS, EnableBetterLyricsKey)
    val BetterLyricsPortato =
        BeatLyricsProvider("BetterLyricsPortato", LyricsSource.BETTER_LYRICS_PORTATO, EnableBetterLyricsPortatoKey)
    val Paxsenix = BeatLyricsProvider("Paxsenix", LyricsSource.PAXSENIX, EnablePaxsenixKey)
    val PaxsenixSpotify =
        BeatLyricsProvider("PaxsenixSpotify", LyricsSource.PAXSENIX_SPOTIFY, needsPaxsenixKey = true)
    val PaxsenixMusixmatch =
        BeatLyricsProvider("PaxsenixMusixmatch", LyricsSource.PAXSENIX_MUSIXMATCH, needsPaxsenixKey = true)
    val YouLyPlus = BeatLyricsProvider("YouLyPlus", LyricsSource.LYRICS_PLUS, EnableYouLyPlusKey)
    val Unison = BeatLyricsProvider("Unison", LyricsSource.UNISON, EnableUnisonKey)
    val Kugou = BeatLyricsProvider("Kugou", LyricsSource.KUGOU, EnableKugouKey)
    val LrcLib = BeatLyricsProvider("LrcLib", LyricsSource.LRCLIB, EnableLrcLibKey)
    val Musixmatch = BeatLyricsProvider("Musixmatch", LyricsSource.MUSIXMATCH, EnableMusixmatchLyricsKey)
    val Genius = BeatLyricsProvider("Genius", LyricsSource.GENIUS, EnableGeniusLyricsKey)
}

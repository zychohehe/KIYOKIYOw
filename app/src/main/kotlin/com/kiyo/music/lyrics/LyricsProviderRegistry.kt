// LyricsProviderRegistry.kt

package com.kiyo.music.lyrics

import com.kiyo.music.constants.PreferredLyricsProvider

object LyricsProviderRegistry {
    // the lyrics sources come from :providers:beatlyrics (taken from Beatcord); the three YouTube ones
    // stay on the app's own YouTube code
    private val providerMap = mapOf(
        "LrcRed"             to BeatLyricsProviders.LrcRed,
        "BiniLyrics"         to BeatLyricsProviders.BiniLyrics,
        "BetterLyrics"       to BeatLyricsProviders.BetterLyrics,
        "BetterLyricsPortato" to BeatLyricsProviders.BetterLyricsPortato,
        "Paxsenix"           to BeatLyricsProviders.Paxsenix,
        "PaxsenixSpotify"    to BeatLyricsProviders.PaxsenixSpotify,
        "PaxsenixMusixmatch" to BeatLyricsProviders.PaxsenixMusixmatch,
        "YouLyPlus"          to BeatLyricsProviders.YouLyPlus,
        "Unison"             to BeatLyricsProviders.Unison,
        "Kugou"              to BeatLyricsProviders.Kugou,
        "LrcLib"             to BeatLyricsProviders.LrcLib,
        "Musixmatch"         to BeatLyricsProviders.Musixmatch,
        "Genius"             to BeatLyricsProviders.Genius,
        "YouTubeSubtitle"    to YouTubeSubtitleLyricsProvider,
        "YouTubeMusic"       to YouTubeLyricsProvider,
        "YouTubeCaptions"    to YouTubeCaptionsLyricsProvider,
    )

    // lyrics from modules that are on, set by the module registry; a module switched off simply isn't in here
    @Volatile
    private var moduleProviders: Map<String, ModuleLyricsProvider> = emptyMap()

    fun setModuleProviders(providers: List<ModuleLyricsProvider>) {
        moduleProviders = providers.associateBy { it.name }
    }

    val providerNames: List<String> get() = moduleProviders.keys.toList() + providerMap.keys

    fun getProviderByName(name: String): LyricsProvider? = providerMap[name] ?: moduleProviders[name]

    /** A saved order, with any source added since it was saved at the end, in default order. */
    fun deserializeProviderOrder(orderString: String): List<String> {
        if (orderString.isBlank()) return getDefaultProviderOrder()
        val saved = orderString.split(",").map { it.trim() }.filter { it in providerNames }.distinct()
        // a lyrics module someone just turned on goes first, they added it to be asked
        val newModules = moduleProviders.keys.filter { it !in saved }
        return newModules + saved + getDefaultProviderOrder().filter { it !in saved && it !in newModules }
    }

    fun serializeProviderOrder(providers: List<String>): String =
        providers.filter { it in providerNames }.joinToString(",")

    // Beatcord's order: the word-timed Apple sources first, then the community ones, the whole-line
    // databases after, plain text (Genius) next to last. KuGou is a Chinese service, so LyricsHelper only
    // believes it first-hand for Chinese songs. YouTube's auto captions last of all - they are speech recognition.
    fun getDefaultProviderOrder(): List<String> = moduleProviders.keys.toList() + listOf(
        "LrcRed",
        "BiniLyrics",
        "BetterLyrics",
        "BetterLyricsPortato",
        "Paxsenix",
        "PaxsenixSpotify",
        "PaxsenixMusixmatch",
        "YouLyPlus",
        "Unison",
        "YouTubeSubtitle",
        "YouTubeMusic",
        "Kugou",
        "LrcLib",
        "Musixmatch",
        "Genius",
        "YouTubeCaptions",
    )

    fun getOrderedProviders(orderString: String): List<LyricsProvider> =
        deserializeProviderOrder(orderString).mapNotNull { getProviderByName(it) }

    fun getProviderNameForEnum(enum: PreferredLyricsProvider): String = when (enum) {
        PreferredLyricsProvider.LRCLIB        -> "LrcLib"
        PreferredLyricsProvider.KUGOU         -> "Kugou"
        PreferredLyricsProvider.BETTER_LYRICS -> "BetterLyrics"
        // SimpMusic is switched off in the new lyrics set; Unison is the nearest community database
        PreferredLyricsProvider.SIMPMUSIC     -> "Unison"
        PreferredLyricsProvider.YOULYPLUS     -> "YouLyPlus"
        PreferredLyricsProvider.PAXSENIX      -> "Paxsenix"
    }

    fun getDisplayName(name: String): String = when (name) {
        "LrcRed"              -> "lrc.red"
        "BiniLyrics"          -> "BiniLyrics"
        "BetterLyrics"        -> "Apple Music"
        "BetterLyricsPortato" -> "QQ Music (Portato)"
        "Paxsenix"            -> "PaxSenix"
        "PaxsenixSpotify"     -> "PaxSenix: Spotify"
        "PaxsenixMusixmatch"  -> "PaxSenix: Musixmatch"
        "YouLyPlus"           -> "LyricsPlus"
        "Unison"              -> "Unison"
        "Kugou"               -> "KuGou"
        "LrcLib"              -> "LrcLib"
        "Musixmatch"          -> "Musixmatch"
        "Genius"              -> "Genius"
        "YouTubeSubtitle"     -> "YouTube Subtitle"
        "YouTubeMusic"        -> "YouTube Music"
        "YouTubeCaptions"     -> "YouTube Auto Captions"
        else                  -> moduleProviders[name]?.displayName ?: name
    }

    /**
     * The open-source project behind a source, for the lyrics page's "Star on GitHub" button.
     * Only sources whose code is public: KuGou, NetEase, QQ Music, Deezer and YouTube are the
     * companies' own services, and Paxsenix hasn't published its server. SimpMusic's lyrics
     * server has no public repo of its own, so its star goes to the SimpMusic app.
     */
    fun getProject(name: String): LyricsProject? = when (name) {
        "YouLyPlus"    -> LyricsProject("LyricsPlus", "https://github.com/ibratabian17/LyricsPlus")
        "BetterLyrics" -> LyricsProject("Better Lyrics", "https://github.com/better-lyrics/better-lyrics")
        "LrcLib"       -> LyricsProject("LRCLIB", "https://github.com/tranxuanthang/lrclib")
        else           -> null
    }

    // lrc.red lets anyone use its lyrics as long as the app links back to it wherever they show
    fun getWebsite(name: String): String? = when (name) {
        "LrcRed" -> "https://lrc.red/"
        else     -> null
    }
}

data class LyricsProject(val name: String, val repoUrl: String)

package com.github.libretube.helpers

import com.github.libretube.api.obj.Streams
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale

object DiscoveryLanguageHelper {
    data class Signals(
        val language: String,
        val seedStreams: List<Streams>,
        val searchQueries: List<String>
    )

    suspend fun buildSignals(
        recentVideoIds: List<String>,
        region: String
    ): Signals {
        val details = recentVideoIds
            .take(4)
            .mapNotNull { id ->
                runCatching { StreamPrefetchCache.getOrFetch(id) }.getOrNull()
            }

        val originalLanguages = details
            .flatMap { streams ->
                streams.audioStreams
                    .filter {
                        it.audioTrackType?.contains("ORIGINAL", ignoreCase = true) == true ||
                            it.audioTrackType?.contains("MAIN", ignoreCase = true) == true
                    }
                    .mapNotNull { it.audioTrackLocale?.substringBefore('-') }
            }
            .filter(::validLanguage)

        val anyAudioLanguages = details
            .flatMap { streams ->
                streams.audioStreams
                    .mapNotNull { it.audioTrackLocale?.substringBefore('-') }
            }
            .filter(::validLanguage)

        val captionLanguages = details
            .flatMap { streams ->
                streams.subtitles.mapNotNull { it.code?.substringBefore('-') }
            }
            .filter(::validLanguage)

        val language = dominantLanguage(
            when {
                originalLanguages.isNotEmpty() -> originalLanguages
                anyAudioLanguages.isNotEmpty() -> anyAudioLanguages
                captionLanguages.isNotEmpty() -> captionLanguages
                else -> emptyList()
            }
        ) ?: LocaleHelper.getAppLocale().language
            .takeIf(::validLanguage)
            ?: Locale.getDefault().language
            .takeIf(::validLanguage)
            ?: "pt"

        // NewPipe/YouTube can localize search/extraction separately from app UI.
        NewPipe.setPreferredLocalization(Localization(language, region))
        NewPipe.setPreferredContentCountry(ContentCountry(region))

        val queries = buildQueries(details)

        return Signals(
            language = language,
            seedStreams = details,
            searchQueries = queries
        )
    }

    private fun dominantLanguage(languages: List<String>): String? =
        languages
            .groupingBy { it.lowercase() }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

    private fun validLanguage(code: String): Boolean {
        val normalized = code.trim().lowercase()
        return normalized.length in 2..3 &&
            normalized != "und" &&
            normalized != "zxx"
    }

    private fun buildQueries(streams: List<Streams>): List<String> {
        val tags = streams
            .flatMap { it.tags }
            .map { it.trim() }
            .filter { it.length in 3..36 }
            .filterNot { it.contains("http", ignoreCase = true) }

        val categories = streams
            .map { it.category.trim() }
            .filter { it.length in 3..36 }

        // Tags are much better than raw titles for finding genuinely new channels
        // about the same subjects instead of near-duplicates of the watched video.
        return (tags + categories)
            .distinctBy { it.lowercase() }
            .shuffled()
            .take(3)
    }
}

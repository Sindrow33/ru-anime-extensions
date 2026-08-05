package eu.kanade.tachiyomi.animeextension.ru.animego

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLEncoder

class CvhExtractor(
    private val client: OkHttpClient,
    private val baseUrl: String,
) {

    fun videosFromUrl(
        url: String,
        prefix: String = "",
    ): List<Video> {
        val iframeHeaders = Headers.headersOf(
            "Accept",
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Referer",
            "$baseUrl/",
        )

        val iframeBody = get(url, iframeHeaders) ?: return emptyList()
        val player = Jsoup.parse(iframeBody, url)
            .selectFirst("video-player")
            ?: return emptyList()

        val titleId = player.attr("data-title-id").ifBlank {
            url.substringBefore("?")
                .trimEnd('/')
                .split('/')
                .getOrNull(
                    url.substringBefore("?")
                        .trimEnd('/')
                        .split('/')
                        .indexOfLast { it == "cdn-iframe" } + 1,
                )
                .orEmpty()
        }

        val publisherId = player.attr("data-publisher-id")
            .ifBlank { "747" }

        val aggregator = player.attr("data-aggregator")
            .ifBlank { "mali" }

        val priorityVoice = player.attr("priority-voice")
            .ifBlank { player.attr("data-priority-voice") }

        val episode = url.substringBefore("?")
            .trimEnd('/')
            .substringAfterLast('/')
            .toIntOrNull()

        if (titleId.isBlank()) return emptyList()

        val apiHeaders = Headers.headersOf(
            "Accept",
            "application/json, text/plain, */*",
            "Referer",
            url,
            "Origin",
            playerOrigin(url),
        )

        val playlistUrl = buildString {
            append(API_BASE)
            append("/player/sv/playlist?pub=")
            append(encode(publisherId))
            append("&aggr=")
            append(encode(aggregator))
            append("&id=")
            append(encode(titleId))
        }

        val playlistBody = get(playlistUrl, apiHeaders)
            ?: return emptyList()

        val items = runCatching {
            JSONObject(playlistBody).optJSONArray("items")
        }.getOrNull() ?: return emptyList()

        val entries = (0 until items.length()).mapNotNull {
            items.optJSONObject(it)
        }

        val selected = entries.firstOrNull { item ->
            episodeMatches(item, episode) &&
                priorityVoice.isNotBlank() &&
                item.optString("voiceStudio")
                    .equals(priorityVoice, ignoreCase = true)
        } ?: entries.firstOrNull { item ->
            episodeMatches(item, episode)
        } ?: entries.firstOrNull()
            ?: return emptyList()

        val vkId = selected.optString("vkId").ifBlank {
            selected.optString("videoId")
        }

        if (vkId.isBlank()) return emptyList()

        val videoBody = get(
            "$API_BASE/player/sv/video/${encode(vkId)}",
            apiHeaders,
        ) ?: return emptyList()

        val sources = runCatching {
            JSONObject(videoBody).optJSONObject("sources")
        }.getOrNull() ?: return emptyList()

        val videoHeaders = Headers.headersOf(
            "Referer",
            "https://player.cdnvideohub.com/",
            "Origin",
            "https://player.cdnvideohub.com",
        )

        return QUALITIES.mapNotNull { (field, quality) ->
            val source = sources.optString(field)
                .trim()
                .fixProtocol()

            if (source.isBlank()) return@mapNotNull null

            Video(
                source,
                "${prefix}CVH $quality",
                source,
                headers = videoHeaders,
            )
        }.distinctBy { it.videoUrl }
    }

    private fun episodeMatches(
        item: JSONObject,
        episode: Int?,
    ): Boolean {
        if (episode == null) return true

        val itemEpisode = item.optString("episode")
            .filter { it.isDigit() }
            .toIntOrNull()

        return itemEpisode == episode
    }

    private fun get(
        url: String,
        requestHeaders: Headers,
    ): String? = runCatching {
        client.newCall(GET(url, requestHeaders))
            .execute()
            .use { response ->
                if (response.isSuccessful) {
                    response.body.string()
                } else {
                    null
                }
            }
    }.getOrNull()

    private fun playerOrigin(url: String): String = runCatching {
        val uri = URI(url)
        "${uri.scheme}://${uri.host}"
    }.getOrDefault("https://player.cdnvideohub.com")

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun String.fixProtocol(): String = when {
        startsWith("//") -> "https:$this"
        startsWith("http://") -> replaceFirst("http://", "https://")
        else -> this
    }

    companion object {
        private const val API_BASE =
            "https://plapi.cdnvideohub.com/api/v1"

        private val QUALITIES = listOf(
            "mpeg4kUrl" to "2160p",
            "mpeg2kUrl" to "1440p",
            "mpegQhdUrl" to "QHD",
            "mpegFullHdUrl" to "1080p",
            "mpegHighUrl" to "720p",
            "mpegMediumUrl" to "480p",
            "mpegLowUrl" to "360p",
        )
    }
}

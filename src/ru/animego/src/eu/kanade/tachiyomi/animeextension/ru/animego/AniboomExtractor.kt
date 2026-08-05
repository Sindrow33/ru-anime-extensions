package eu.kanade.tachiyomi.animeextension.ru.animego

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.jsoup.Jsoup

class AniboomExtractor(
    private val client: OkHttpClient,
    private val baseUrl: String,
) {

    fun videosFromUrl(
        url: String,
        prefix: String = "",
    ): List<Video> {
        val pageHeaders = Headers.headersOf(
            "Accept",
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Referer",
            "$baseUrl/",
        )

        val body = runCatching {
            client.newCall(GET(url, pageHeaders))
                .execute()
                .use { response ->
                    if (response.isSuccessful) {
                        response.body.string()
                    } else {
                        ""
                    }
                }
        }.getOrNull().orEmpty()

        if (body.isBlank()) return emptyList()

        val page = Jsoup.parse(body, url)

        val rawParameters = page.selectFirst(
            "#video[data-parameters], [data-parameters][data-player]",
        )?.attr("data-parameters")
            ?.ifBlank {
                page.selectFirst("[data-parameters]")
                    ?.attr("data-parameters")
                    .orEmpty()
            }
            .orEmpty()

        if (rawParameters.isBlank()) return emptyList()

        val parameters = runCatching {
            JSONObject(rawParameters)
        }.getOrNull() ?: return emptyList()

        val hls = sourceFrom(parameters, "hls").fixProtocol()
        val dash = sourceFrom(parameters, "dash").fixProtocol()

        if (hls.isBlank() && dash.isBlank()) return emptyList()

        val host = runCatching { url.toHttpUrl().host }
            .getOrDefault("aniboom.one")

        val videoHeaders = Headers.headersOf(
            "Referer",
            "https://$host/",
            "Accept-Language",
            "ru-RU,ru;q=0.9,en;q=0.8",
            "Origin",
            "https://$host",
        )

        return buildList {
            if (dash.isNotBlank() && !dash.contains(".m3u8")) {
                add(
                    Video(
                        dash,
                        "${prefix}AniBoom DASH",
                        dash,
                        headers = videoHeaders,
                    ),
                )
            }

            if (hls.isNotBlank()) {
                add(
                    Video(
                        hls,
                        "${prefix}AniBoom HLS",
                        hls,
                        headers = videoHeaders,
                    ),
                )
            }

            if (hls.isBlank() && dash.isNotBlank()) {
                add(
                    Video(
                        dash,
                        "${prefix}AniBoom",
                        dash,
                        headers = videoHeaders,
                    ),
                )
            }
        }.distinctBy { it.videoUrl }
    }

    /*
     * AniBoom встречается в двух форматах:
     *
     * "hls": {"src": "..."}
     *
     * и:
     *
     * "hls": "{\"src\":\"...\"}"
     */
    private fun sourceFrom(
        json: JSONObject,
        key: String,
    ): String {
        val value = json.opt(key)

        return when (value) {
            is JSONObject -> value.optString("src")
            is String -> {
                val trimmed = value.trim()

                runCatching {
                    JSONObject(trimmed).optString("src")
                }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: trimmed.takeIf {
                        it.startsWith("http") ||
                            it.startsWith("//")
                    }.orEmpty()
            }
            else -> ""
        }
    }

    private fun String.fixProtocol(): String = when {
        startsWith("//") -> "https:$this"
        startsWith("http://") -> replaceFirst("http://", "https://")
        else -> this
    }
}

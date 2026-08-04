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

    fun videosFromUrl(url: String, prefix: String = ""): List<Video> {
        val pageHeaders = Headers.headersOf(
            "Accept",
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Referer",
            "$baseUrl/",
        )

        val body = runCatching {
            client.newCall(GET(url, pageHeaders)).execute().use { it.body.string() }
        }.getOrNull() ?: return emptyList()

        val data = Jsoup.parse(body, url)
            .selectFirst("#video[data-parameters]")
            ?.attr("data-parameters")
            ?: return emptyList()

        val json = runCatching { JSONObject(data) }.getOrNull() ?: return emptyList()
        val hls = json.optJSONObject("hls")?.optString("src").orEmpty().fixProtocol()
        val dash = json.optJSONObject("dash")?.optString("src").orEmpty().fixProtocol()
        if (hls.isBlank()) return emptyList()

        val host = url.toHttpUrl().host
        // Заголовки обязательны в Title Case, иначе ссылки отдают 403
        val videoHeaders = Headers.headersOf(
            "Referer",
            "https://$host/",
            "Accept-Language",
            "ru-RU",
            "Origin",
            "https://$host",
        )

        // Иногда бэкенд кладёт m3u8 в ключ dash — тогда отдаём только HLS
        return if (dash.isBlank() || dash.endsWith(".m3u8")) {
            listOf(Video(hls, "${prefix}Aniboom HLS", hls, headers = videoHeaders))
        } else {
            listOf(
                Video(dash, "${prefix}Aniboom DASH", dash, headers = videoHeaders),
                Video(hls, "${prefix}Aniboom HLS", hls, headers = videoHeaders),
            )
        }
    }

    private fun String.fixProtocol(): String = if (startsWith("//")) "https:$this" else this
}

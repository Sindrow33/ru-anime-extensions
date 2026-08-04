package eu.kanade.tachiyomi.animeextension.ru.animego

import android.util.Base64
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.json.JSONObject

class KodikExtractor(
    private val client: OkHttpClient,
    private val baseUrl: String,
) {

    private var cachedApiPath: String? = null

    fun videosFromUrl(url: String, prefix: String = ""): List<Video> {
        val host = url.toHttpUrl().host
        val pageHeaders = Headers.headersOf("Referer", "$baseUrl/")

        val pageBody = runCatching {
            client.newCall(GET(url, pageHeaders)).execute().use { it.body.string() }
        }.getOrNull() ?: return emptyList()

        if (pageBody.contains("Видео не найдено")) return emptyList()

        val playerJsPath = PLAYER_JS_REGEX.find(pageBody)?.groupValues?.get(1)
            ?: return emptyList()

        val payload = linkedMapOf(
            "d" to findVar(pageBody, "domain"),
            "d_sign" to findVar(pageBody, "d_sign"),
            "pd" to findVar(pageBody, "pd"),
            "pd_sign" to findVar(pageBody, "pd_sign"),
            "ref" to findVar(pageBody, "ref"),
            "ref_sign" to findVar(pageBody, "ref_sign"),
            "type" to findVInfo(pageBody, "type"),
            "hash" to findVInfo(pageBody, "hash"),
            "id" to findVInfo(pageBody, "id"),
            "bad_user" to "false",
            "info" to "{}",
            "cdn_is_working" to "true",
        )
        if (payload["hash"].isNullOrBlank() || payload["id"].isNullOrBlank()) return emptyList()

        val apiHeaders = Headers.headersOf(
            "Origin",
            "https://$host",
            "Referer",
            url,
            "Accept",
            "application/json, text/javascript, */*; q=0.01",
        )

        var apiPath = cachedApiPath ?: fetchApiPath(host, playerJsPath, pageHeaders)
            ?: return emptyList()
        var apiBody = postPayload("https://$host$apiPath", apiHeaders, payload)
        if (apiBody == null) {
            // API-эндпоинт ротируется — перечитываем JS плеера и пробуем ещё раз
            apiPath = fetchApiPath(host, playerJsPath, pageHeaders) ?: return emptyList()
            cachedApiPath = apiPath
            apiBody = postPayload("https://$host$apiPath", apiHeaders, payload) ?: return emptyList()
        }

        val links = runCatching { JSONObject(apiBody).optJSONObject("links") }.getOrNull()
            ?: return emptyList()

        val videoHeaders = Headers.headersOf("Referer", "https://$host/")

        return listOf("360", "480", "720").mapNotNull { quality ->
            val src = links.optJSONArray(quality)
                ?.optJSONObject(0)
                ?.optString("src")
                .orEmpty()
            if (src.isBlank()) return@mapNotNull null

            var videoUrl = decodeUrl(src)
            if (quality == "720") {
                videoUrl = videoUrl.replace("/480.mp4:", "/720.mp4:")
            }
            Video(videoUrl, "${prefix}Kodik ${quality}p", videoUrl, headers = videoHeaders)
        }
    }

    private fun postPayload(url: String, headers: Headers, payload: Map<String, String>): String? {
        val form = FormBody.Builder()
        payload.forEach { (key, value) -> form.add(key, value) }
        return runCatching {
            client.newCall(POST(url, headers, form.build())).execute().use { resp ->
                if (resp.isSuccessful) resp.body.string() else null
            }
        }.getOrNull()
    }

    private fun fetchApiPath(host: String, playerJsPath: String, headers: Headers): String? {
        val js = runCatching {
            client.newCall(GET("https://$host$playerJsPath", headers)).execute()
                .use { it.body.string() }
        }.getOrNull() ?: return null

        val encoded = API_PATH_REGEX.find(js)?.groupValues?.get(1) ?: return null
        return runCatching { String(Base64.decode(encoded, Base64.DEFAULT)) }.getOrNull()
    }

    private fun findVar(html: String, name: String): String = Regex("""var\s+$name\s*=\s*['"](.*?)['"];""")
        .find(html)?.groupValues?.get(1).orEmpty()

    private fun findVInfo(html: String, name: String): String = Regex("""vInfo\.$name\s*=\s*['"](.*?)['"];""")
        .find(html)?.groupValues?.get(1).orEmpty()

    private fun decodeUrl(src: String): String {
        // С 03.2025 kodik может отдавать прямые m3u8 без кодирования
        if (src.endsWith(".m3u8")) {
            return if (src.startsWith("https")) src else "https:$src"
        }
        val rot = buildString {
            for (c in src) {
                append(
                    when {
                        c in 'A'..'Z' -> 'A' + (c - 'A' + 18) % 26
                        c in 'a'..'z' -> 'a' + (c - 'a' + 18) % 26
                        else -> c
                    },
                )
            }
        }
        val padded = if (rot.endsWith("==")) rot else rot + "=="
        val decoded = runCatching {
            String(Base64.decode(padded, Base64.DEFAULT))
        }.getOrNull() ?: return src
        return if (decoded.startsWith("https")) decoded else "https:$decoded"
    }

    companion object {
        private val PLAYER_JS_REGEX = Regex("""<script[^>]+src="([^"]*assets/js[^"]*)"""")
        private val API_PATH_REGEX = Regex("""\$\.ajax[^)]+atob\(["'](\w+=)["']\)""")
    }
}

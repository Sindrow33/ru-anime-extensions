package aniyomi.kodikextractor

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient

/**
 * Extractor for Kodik / kodikplayer embeds.
 * Usage: KodikExtractor(client).videosFromUrl(embedUrl)
 */
class KodikExtractor(private val client: OkHttpClient) {

    fun videosFromUrl(url: String, prefix: String = "Kodik"): List<Video> {
        return try {
            val headers = Headers.headersOf("Referer", url)
            val body = client.newCall(GET(url, headers)).execute().body.string()

            val found = mutableListOf<Video>()

            // 1) Direct m3u8/mp4 links inside the player page
            Regex("""(https?:\\?/\\?/[^\s"'\\]+\.(?:m3u8|mp4)[^\s"'\\]*)""")
                .findAll(body)
                .forEach { m ->
                    val direct = m.groupValues[1].replace("\\/", "/")
                    found.add(Video(direct, prefix, direct))
                }
            if (found.isNotEmpty()) return found.distinctBy { it.videoUrl }

            // 2) Kodik /gvi API by hash + id
            val m = Regex("""/(?:season|serial|video|serial)\??/?(\d+)?/?([a-f0-9]{16,})?""").find(url)
            val hash = Regex("""[?&/]hash=([a-f0-9]{16,})|/([a-f0-9]{32,})""")
                .find(url)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }
            val id = Regex("""[?&/]id=(\d+)|/(?:serial|video)/(\d+)""")
                .find(url)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }

            if (!hash.isNullOrEmpty() && !id.isNullOrEmpty()) {
                found += fromApi(id, hash, prefix)
            }
            found.distinctBy { it.videoUrl }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun fromApi(id: String, hash: String, prefix: String): List<Video> = try {
        val formBody = FormBody.Builder()
            .add("hash", hash)
            .add("id", id)
            .build()
        val resp = client.newCall(
            POST("https://kodikplayer.com/gvi", body = formBody),
        ).execute().body.string()

        // links: {"360":[{"src":"..."}],"480":[...],"720":[...]}
        val result = mutableListOf<Video>()
        Regex(""""(\d{3,4})"\s*:\s*\[\s*\{[^}]*"src"\s*:\s*"([^"]+)"""")
            .findAll(resp)
            .forEach { m ->
                val quality = m.groupValues[1]
                var src = m.groupValues[2].replace("\\/", "/")
                if (src.startsWith("//")) src = "https:$src"
                result.add(Video(src, "$prefix ${quality}p", src))
            }
        result
    } catch (e: Exception) {
        emptyList()
    }
}

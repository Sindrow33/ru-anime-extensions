package eu.kanade.tachiyomi.animeextension.ru.dubclub

import android.util.Base64
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import keiyoushi.utils.useAsJsoup
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class DubClubSource(
    override val name: String,
    override val baseUrl: String,
) : ParsedAnimeHttpSource() {

    override val lang = "ru"
    override val supportsLatest = true

    // ============================ Popular / Latest ============================

    override fun popularAnimeRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularAnimeSelector(): String = ".popular article.p-item"

    override fun popularAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        val link = element.selectFirst(
            "a.short-poster, a.gallery_item, .sh-desc > a[href]",
        )!!

        anime.setUrlWithoutDomain(link.absUrl("href"))

        anime.title = link.attr("title")
            .trim()
            .ifEmpty {
                element.selectFirst(".sh-title, .p-title")
                    ?.text()
                    ?.trim()
                    ?.substringBeforeLast("...")
                    .orEmpty()
            }

        val image = element.selectFirst(
            "a.short-poster img, a.gallery_item img, img",
        )

        if (image != null) {
            anime.thumbnail_url = image.attr("data-src")
                .trim()
                .ifEmpty { image.absUrl("src") }
                .ifEmpty { image.attr("src").trim() }
        } else {
            anime.thumbnail_url = link.ownText()
                .trim()
                .takeIf {
                    it.startsWith("https://") || it.startsWith("http://")
                }
        }

        return anime
    }

    // У сайта нет отдельной страницы рейтинга, поэтому используем
    // самостоятельный боковой блок .popular без пагинации.
    override fun popularAnimeNextPageSelector(): String = ".popular .pnext a"

    override fun latestUpdatesRequest(page: Int): Request = if (page == 1) {
        GET(baseUrl, headers)
    } else {
        GET("$baseUrl/page/$page/", headers)
    }

    override fun latestUpdatesSelector(): String = "main .floats article.short"

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = "main .floats .pnext a"

    // ============================ Search ============================

    override fun searchAnimeRequest(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): Request {
        val searchStart = if (page <= 1) 0 else page

        val url = baseUrl.toHttpUrl()
            .newBuilder()
            .addPathSegment("index.php")
            .addQueryParameter("do", "search")
            .addQueryParameter("subaction", "search")
            .addQueryParameter("story", query)
            .addQueryParameter("search_start", searchStart.toString())
            .addQueryParameter("result_from", "1")
            .build()

        return GET(url.toString(), headers)
    }

    override fun searchAnimeSelector(): String = "main .floats article.short"

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String = "main .floats .pnext a"

    // ============================ Details ============================

    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()

        anime.title = document.selectFirst(".fheader h1")?.text()
            ?: document.title().substringBefore(" »")

        document.selectFirst(".fposter img")?.let { poster ->
            var thumbnail = poster.attr("data-src")

            if (thumbnail.isEmpty()) {
                thumbnail = poster.absUrl("src")
            }

            if (thumbnail.isEmpty()) {
                thumbnail = poster.attr("src")
            }

            anime.thumbnail_url = thumbnail
        }

        anime.description = document.selectFirst("#fdesc")
            ?.ownText()
            ?.trim()
            ?.ifEmpty {
                document.selectFirst("#fdesc")?.text()?.trim()
            }

        anime.genre = document.select("#flistd a[href]")
            .eachText()
            .joinToString(", ")

        anime.status = SAnime.UNKNOWN

        return anime
    }

    // ============================ Episodes ============================

    override fun episodeListParse(response: Response): List<SEpisode> {
        val titleDocument = response.useAsJsoup()
        val iframeUrl = findKodikIframe(titleDocument) ?: return emptyList()

        val playerRequest = Request.Builder()
            .url(iframeUrl)
            .header("Referer", response.request.url.toString())
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        val playerHtml = client
            .newCall(playerRequest)
            .execute()
            .use { playerResponse ->
                if (!playerResponse.isSuccessful) {
                    return emptyList()
                }

                playerResponse.body.string()
            }

        val playerDocument = Jsoup.parse(playerHtml, iframeUrl)

        val episodeElements = playerDocument.select(
            ".serial-series-box [data-id][data-hash]",
        )

        if (episodeElements.isEmpty()) {
            return emptyList()
        }

        val episodeNumberRegex = Regex("""\d+(?:[.,]\d+)?""")

        return episodeElements
            .mapNotNull { episodeElement ->
                val id = episodeElement.attr("data-id").trim()
                val hash = episodeElement.attr("data-hash").trim()

                if (id.isEmpty() || hash.isEmpty()) {
                    return@mapNotNull null
                }

                val episodeNumber = listOf(
                    episodeElement.attr("value"),
                    episodeElement.attr("data-episode"),
                    episodeElement.attr("data-title"),
                    episodeElement.text(),
                )
                    .asSequence()
                    .mapNotNull { value ->
                        episodeNumberRegex.find(value)
                            ?.value
                            ?.replace(',', '.')
                            ?.toFloatOrNull()
                    }
                    .firstOrNull()
                    ?: return@mapNotNull null

                val originalTitle = episodeElement.attr("data-title")
                    .trim()
                    .ifEmpty { episodeElement.text().trim() }

                val episodeUrl = response.request.url
                    .newBuilder()
                    .removeAllQueryParameters(EPISODE_ID_PARAMETER)
                    .removeAllQueryParameters(EPISODE_HASH_PARAMETER)
                    .addQueryParameter(EPISODE_ID_PARAMETER, id)
                    .addQueryParameter(EPISODE_HASH_PARAMETER, hash)
                    .build()
                    .toString()

                SEpisode.create().apply {
                    name = originalTitle.ifEmpty {
                        "Серия ${formatEpisodeNumber(episodeNumber)}"
                    }
                    episode_number = episodeNumber
                    setUrlWithoutDomain(episodeUrl)
                }
            }
            .distinctBy { it.episode_number }
            .sortedByDescending { it.episode_number }
    }

    override fun episodeListSelector(): String = throw UnsupportedOperationException()

    override fun episodeFromElement(element: Element): SEpisode = throw UnsupportedOperationException()

    // ============================ Videos ============================

    override fun videoListParse(response: Response): List<Video> {
        val episodeId = response.request.url
            .queryParameter(EPISODE_ID_PARAMETER)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return emptyList()

        val episodeHash = response.request.url
            .queryParameter(EPISODE_HASH_PARAMETER)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return emptyList()

        val titleDocument = response.useAsJsoup()
        val iframeUrl = findKodikIframe(titleDocument) ?: return emptyList()

        val playerRoot = Regex("""^https?://[^/]+""")
            .find(iframeUrl)
            ?.value
            ?: return emptyList()

        val episodePlayerUrl =
            "$playerRoot/seria/$episodeId/$episodeHash/720p"

        try {
            val playerRequest = Request.Builder()
                .url(episodePlayerUrl)
                .header("Referer", response.request.url.toString())
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            client
                .newCall(playerRequest)
                .execute()
                .close()
        } catch (_: Exception) {
            // /ftor иногда работает и без предварительного запроса.
        }

        val formBody = FormBody.Builder()
            .add("type", "seria")
            .add("id", episodeId)
            .add("hash", episodeHash)
            .add("bad_user", "false")
            .add("cdn_is_working", "true")
            .add("info", "{}")
            .build()

        val request = Request.Builder()
            .url("$playerRoot/ftor")
            .header("Referer", episodePlayerUrl)
            .header("Origin", playerRoot)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .post(formBody)
            .build()

        val json = client
            .newCall(request)
            .execute()
            .use { apiResponse ->
                if (!apiResponse.isSuccessful) {
                    return emptyList()
                }

                apiResponse.body.string()
            }

        return parseKodikVideos(json)
    }

    private fun parseKodikVideos(json: String): List<Video> {
        val result = mutableListOf<Video>()

        val linkRegex = Regex(
            """"(\d{3,4})"\s*:\s*\[\s*\{[^}]*?"src"\s*:\s*"([^"]+)"""",
        )

        linkRegex.findAll(json).forEach { match ->
            val quality = match.groupValues[1]
            val encryptedSource = match.groupValues[2]
                .replace("\\/", "/")
                .replace("\\u0026", "&")

            val videoUrl = decodeKodikSource(encryptedSource)
                ?: return@forEach

            result += Video(
                videoUrl,
                "Kodik ${quality}p",
                videoUrl,
            )
        }

        return result
            .distinctBy { it.videoUrl }
            .sortedByDescending {
                Regex("""(\d{3,4})p""")
                    .find(it.quality)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
                    ?: 0
            }
    }

    /**
     * Новые ответы Kodik /ftor используют ROT18, после чего Base64.
     */
    private fun decodeKodikSource(source: String): String? {
        if (source.startsWith("https://") || source.startsWith("http://")) {
            return source
        }

        if (source.startsWith("//")) {
            return "https:$source"
        }

        return try {
            val rotated = source.map { character ->
                when (character) {
                    in 'A'..'Z' -> {
                        ('A'.code + (character.code - 'A'.code + 18) % 26).toChar()
                    }

                    in 'a'..'z' -> {
                        ('a'.code + (character.code - 'a'.code + 18) % 26).toChar()
                    }

                    else -> character
                }
            }.joinToString("")

            val decoded = String(
                Base64.decode(rotated, Base64.DEFAULT),
                Charsets.UTF_8,
            ).trim()

            when {
                decoded.startsWith("//") -> "https:$decoded"
                decoded.startsWith("https://") -> decoded
                decoded.startsWith("http://") -> decoded
                decoded.isNotEmpty() -> "https://$decoded"
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun findKodikIframe(document: Document): String? {
        document.select("iframe").forEach { iframe ->
            var source = iframe.attr("src").trim()

            if (source.isEmpty()) {
                source = iframe.attr("data-src").trim()
            }

            if (source.isEmpty()) {
                return@forEach
            }

            val absoluteUrl = when {
                source.startsWith("//") -> "https:$source"
                source.startsWith("http://") || source.startsWith("https://") -> source
                source.startsWith("/") -> "$baseUrl$source"
                else -> iframe.absUrl("src").ifEmpty { source }
            }

            if (absoluteUrl.contains("kodik", ignoreCase = true)) {
                return absoluteUrl
            }
        }

        return null
    }

    private fun formatEpisodeNumber(number: Float): String = if (number % 1f == 0f) {
        number.toInt().toString()
    } else {
        number.toString()
    }

    override fun videoListSelector(): String = throw UnsupportedOperationException()

    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()

    override fun videoUrlParse(document: Document): String = throw UnsupportedOperationException()

    private companion object {
        const val EPISODE_ID_PARAMETER = "dubclub_ep_id"
        const val EPISODE_HASH_PARAMETER = "dubclub_ep_hash"

        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}

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
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
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

        val thumbnail = if (image != null) {
            image.attr("data-src")
                .trim()
                .ifEmpty { image.absUrl("src") }
                .ifEmpty { image.attr("src").trim() }
        } else {
            link.ownText().trim()
        }

        anime.thumbnail_url = thumbnail
            .takeIf { it.isNotEmpty() }
            ?.let(::absoluteMediaUrl)

        return anime
    }

    // Боковой блок популярных не имеет отдельной пагинации.
    override fun popularAnimeNextPageSelector(): String = "#dubclub-popular-next-page"

    override fun latestUpdatesRequest(page: Int): Request = if (page == 1) {
        GET(baseUrl, headers)
    } else {
        GET("$baseUrl/page/$page/", headers)
    }

    override fun latestUpdatesSelector(): String = "main .floats article.short"

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = "#bottom-nav .pnext a, main .pnext a"

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

    override fun searchAnimeNextPageSelector(): String = "#bottom-nav .pnext a, main .pnext a"

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

        val seasonPlayerHtml = fetchPlayerHtml(
            url = iframeUrl,
            referer = response.request.url.toString(),
        ) ?: return emptyList()

        val episodePlayerUrl = "$playerRoot/seria/$episodeId/$episodeHash/720p"
            .toHttpUrl()
            .newBuilder()
            .encodedQuery(extractPlayerQuery(seasonPlayerHtml))
            .build()
            .toString()

        val episodePlayerHtml = fetchPlayerHtml(
            url = episodePlayerUrl,
            referer = iframeUrl,
        ) ?: return emptyList()

        val formBody = FormBody.Builder()
            .addPlayerAuthorization(episodePlayerHtml)
            .add("type", "seria")
            .add("id", episodeId)
            .add("hash", episodeHash)
            .add("bad_user", "false")
            .add("cdn_is_working", "true")
            .add("info", "{}")
            .build()

        val videoHeaders = Headers.Builder()
            .add("Referer", episodePlayerUrl)
            .add("Origin", playerRoot)
            .add("User-Agent", USER_AGENT)
            .build()

        val apiEndpoints = mutableListOf("$playerRoot/ftor")

        extractKodikApiEndpoint(
            playerHtml = episodePlayerHtml,
            playerUrl = episodePlayerUrl,
            playerRoot = playerRoot,
        )?.let(apiEndpoints::add)

        for (endpoint in apiEndpoints.distinct()) {
            val apiJson = postKodikApi(
                endpoint = endpoint,
                referer = episodePlayerUrl,
                origin = playerRoot,
                formBody = formBody,
            ) ?: continue

            val videos = parseKodikVideos(apiJson, videoHeaders)

            if (videos.isNotEmpty()) {
                return videos
            }
        }

        return emptyList()
    }

    private fun fetchPlayerHtml(
        url: String,
        referer: String,
    ): String? = try {
        val request = Request.Builder()
            .url(url)
            .header("Referer", referer)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        client.newCall(request).execute().use { playerResponse ->
            if (playerResponse.isSuccessful) {
                playerResponse.body.string()
            } else {
                null
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun extractKodikApiEndpoint(
        playerHtml: String,
        playerUrl: String,
        playerRoot: String,
    ): String? {
        PLAYER_JS_REGEX.findAll(playerHtml).forEach { match ->
            val scriptPath = match.groupValues[1].trim()
            if (scriptPath.isEmpty()) return@forEach

            val scriptUrl = when {
                scriptPath.startsWith("//") -> "https:$scriptPath"
                scriptPath.startsWith("https://") || scriptPath.startsWith("http://") -> scriptPath
                else -> playerUrl.toHttpUrl()
                    .resolve(scriptPath)
                    ?.toString()
                    ?: return@forEach
            }

            val scriptBody = fetchPlayerHtml(
                url = scriptUrl,
                referer = playerUrl,
            ) ?: return@forEach

            val encodedPath = API_PATH_REGEX
                .find(scriptBody)
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return@forEach

            val paddedPath = encodedPath.padEnd(
                encodedPath.length + (4 - encodedPath.length % 4) % 4,
                '=',
            )

            val decodedPath = runCatching {
                String(
                    Base64.decode(paddedPath, Base64.DEFAULT),
                    Charsets.UTF_8,
                ).trim()
            }.getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?: return@forEach

            return when {
                decodedPath.startsWith("https://") ||
                    decodedPath.startsWith("http://") -> decodedPath
                decodedPath.startsWith("/") -> "$playerRoot$decodedPath"
                else -> "$playerRoot/${decodedPath.trimStart('/')}"
            }
        }

        return null
    }

    private fun postKodikApi(
        endpoint: String,
        referer: String,
        origin: String,
        formBody: FormBody,
    ): String? = runCatching {
        val request = Request.Builder()
            .url(endpoint)
            .header("Referer", referer)
            .header("Origin", origin)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .post(formBody)
            .build()

        client.newCall(request).execute().use { apiResponse ->
            if (apiResponse.isSuccessful) {
                apiResponse.body.string()
            } else {
                null
            }
        }
    }.getOrNull()

    private fun extractPlayerQuery(playerHtml: String): String? {
        val rawParams = Regex("""var\s+urlParams\s*=\s*'([^']+)'""")
            .find(playerHtml)
            ?.groupValues
            ?.get(1)
            ?: return null

        return runCatching {
            val json = JSONObject(rawParams)
            val queryParameters = mutableListOf<String>()
            val keys = json.keys()

            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.opt(key)

                if (value != null && value != JSONObject.NULL) {
                    // Kodik already percent-encodes values such as `ref`.
                    // Encoding them again changes `%3A` to `%253A` and
                    // invalidates the matching signature.
                    queryParameters +=
                        "$key=${value.toString().lowercaseIfBoolean()}"
                }
            }

            queryParameters.joinToString("&")
        }.getOrNull()
    }

    private fun FormBody.Builder.addPlayerAuthorization(playerHtml: String): FormBody.Builder = apply {
        PLAYER_AUTH_FIELDS.forEach { (variable, field) ->
            Regex("""var\s+$variable\s*=\s*[\"']([^\"']+)[\"']""")
                .find(playerHtml)
                ?.groupValues
                ?.get(1)
                ?.let { add(field, it) }
        }
    }

    private fun String.lowercaseIfBoolean(): String = when (this) {
        "true", "false" -> lowercase()
        else -> this
    }

    private fun parseKodikVideos(
        json: String,
        videoHeaders: Headers,
    ): List<Video> {
        val links = runCatching {
            JSONObject(json).optJSONObject("links")
        }.getOrNull() ?: return emptyList()

        val qualities = mutableListOf<String>()
        val keys = links.keys()

        while (keys.hasNext()) {
            qualities += keys.next()
        }

        return qualities.mapNotNull { quality ->
            val sources = links.optJSONArray(quality)
                ?: return@mapNotNull null

            var videoUrl: String? = null

            for (index in 0 until sources.length()) {
                val encodedSource = sources
                    .optJSONObject(index)
                    ?.optString("src")
                    ?.trim()
                    ?.replace("\\/", "/")
                    ?.replace("\\u0026", "&")
                    .orEmpty()

                if (encodedSource.isEmpty()) continue

                val decodedSource = decodeKodikSource(encodedSource)
                    ?: continue

                if (
                    decodedSource.startsWith("https://") ||
                    decodedSource.startsWith("http://")
                ) {
                    videoUrl = decodedSource
                    break
                }
            }

            val resolvedVideoUrl = videoUrl ?: return@mapNotNull null
            val qualityLabel = if (quality.endsWith("p")) quality else "${quality}p"

            Video(
                resolvedVideoUrl,
                "Kodik $qualityLabel",
                resolvedVideoUrl,
                headers = videoHeaders,
            )
        }
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
     * Kodik может вернуть прямую ссылку или ROT18 + Base64.
     */
    private fun decodeKodikSource(source: String): String? {
        val normalizedSource = source.trim()

        if (
            normalizedSource.startsWith("https://") ||
            normalizedSource.startsWith("http://")
        ) {
            return normalizedSource
        }

        if (normalizedSource.startsWith("//")) {
            return "https:$normalizedSource"
        }

        return runCatching {
            val rotated = normalizedSource.map { character ->
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

            val padded = rotated.padEnd(
                rotated.length + (4 - rotated.length % 4) % 4,
                '=',
            )

            val decoded = String(
                Base64.decode(padded, Base64.DEFAULT),
                Charsets.UTF_8,
            ).trim()

            when {
                decoded.startsWith("//") -> "https:$decoded"
                decoded.startsWith("https://") -> decoded
                decoded.startsWith("http://") -> decoded
                else -> null
            }
        }.getOrNull()
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

    private fun absoluteMediaUrl(url: String): String = when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("https://") || url.startsWith("http://") -> url
        url.startsWith("/") -> "$baseUrl$url"
        else -> "$baseUrl/${url.trimStart('/')}"
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
        val PLAYER_JS_REGEX =
            Regex("""<script[^>]+src=["']([^"']*assets/js[^"']+)["']""")
        val API_PATH_REGEX =
            Regex("""\$\.ajax[^)]+atob\(["']([^"']+)["']\)""")

        const val EPISODE_ID_PARAMETER = "dubclub_ep_id"
        const val EPISODE_HASH_PARAMETER = "dubclub_ep_hash"

        val PLAYER_AUTH_FIELDS = listOf(
            "domain" to "d",
            "d_sign" to "d_sign",
            "pd" to "pd",
            "pd_sign" to "pd_sign",
            "ref" to "ref",
            "ref_sign" to "ref_sign",
        )

        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}

package eu.kanade.tachiyomi.animeextension.ru.dubclub

import aniyomi.kodikextractor.KodikExtractor
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import keiyoushi.utils.useAsJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class DubClubSource(override val name: String, override val baseUrl: String) : ParsedAnimeHttpSource() {

    override val lang = "ru"
    override val supportsLatest = true

    private val kodik by lazy { KodikExtractor(network.client) }

    // ============================ Popular / Latest ============================
    override fun popularAnimeRequest(page: Int): Request = if (page == 1) GET(baseUrl, headers) else GET("$baseUrl/page/$page/", headers)

    override fun popularAnimeSelector(): String = "article.short"

    override fun popularAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        val link = element.selectFirst("a.short-poster")!!
        anime.setUrlWithoutDomain(link.absUrl("href"))
        anime.title = element.selectFirst(".sh-desc a .sh-title")?.text()?.substringBeforeLast("...")
            ?: element.selectFirst(".sh-desc a")?.attr("title")
            ?: link.attr("title")
        element.selectFirst("a.short-poster img")?.let { img ->
            var thumb = img.attr("data-src")
            if (thumb.isEmpty()) thumb = img.absUrl("src")
            if (thumb.isEmpty()) thumb = img.attr("src")
            anime.thumbnail_url = thumb
        }
        return anime
    }

    override fun popularAnimeNextPageSelector(): String = ".pnext a, .navigation a:last-of-type"

    override fun latestUpdatesRequest(page: Int): Request = popularAnimeRequest(page)
    override fun latestUpdatesSelector(): String = popularAnimeSelector()
    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // ============================ Search ============================
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request = GET("$baseUrl/index.php?do=search&subaction=search&story=$query&search_start=$page", headers)

    override fun searchAnimeSelector(): String = popularAnimeSelector()
    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    // ============================ Details ============================
    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()
        anime.title = document.selectFirst(".fheader h1")?.text() ?: document.title().substringBefore(" »")
        val poster = document.selectFirst(".fposter img")
        if (poster != null) {
            var thumb = poster.attr("data-src")
            if (thumb.isEmpty()) thumb = poster.absUrl("src")
            if (thumb.isEmpty()) thumb = poster.attr("src")
            anime.thumbnail_url = thumb
        }
        anime.description = document.selectFirst("#fdesc")?.ownText()?.trim()
            ?.ifEmpty { document.selectFirst("#fdesc")?.text()?.trim() }
        anime.genre = document.select("#flistd a[href]").eachText().joinToString(", ")
        anime.status = SAnime.UNKNOWN
        return anime
    }

    // ============================ Episodes ============================
    override fun episodeListParse(response: Response): List<SEpisode> {
        val ep = SEpisode.create().apply {
            name = "Смотреть онлайн"
            episode_number = 1f
            setUrlWithoutDomain(response.request.url.toString())
        }
        return listOf(ep)
    }

    override fun episodeListSelector(): String = throw UnsupportedOperationException()
    override fun episodeFromElement(element: Element): SEpisode = throw UnsupportedOperationException()

    // ============================ Videos ============================
    override fun videoListParse(response: Response): List<Video> {
        val document = response.useAsJsoup()
        val videos = mutableListOf<Video>()

        document.select("iframe[src]").forEach { iframe ->
            var src = iframe.attr("src")
            if (src.startsWith("//")) src = "https:$src"
            when {
                src.contains("kodik") -> videos += kodik.videosFromUrl(src, "Kodik")
                src.isNotEmpty() -> videos += Video(src, "Плеер", src)
            }
        }
        return videos.distinctBy { it.videoUrl }
    }

    override fun videoListSelector(): String = throw UnsupportedOperationException()
    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document): String = throw UnsupportedOperationException()
}

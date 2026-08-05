package eu.kanade.tachiyomi.animeextension.ru.anistar

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class AnistarSource(override val name: String, override val baseUrl: String) : ParsedAnimeHttpSource() {

    override val lang = "ru"
    override val supportsLatest = true

    // ============================== Popular ==============================
    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/page/$page/", headers)

    override fun popularAnimeSelector(): String = "div.shortstory, .shortstory, article, .th-item"

    override fun popularAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        val a = element.selectFirst("a[href]")!!
        anime.setUrlWithoutDomain(a.attr("href"))
        anime.title = element.selectFirst("img")?.attr("alt")
            ?: a.attr("title").ifEmpty { a.text() }
        anime.thumbnail_url = element.selectFirst("img")?.let {
            it.attr("data-src").ifEmpty { it.attr("src") }
        }
        return anime
    }

    override fun popularAnimeNextPageSelector(): String = "span.navigation a.next, .nav_ext + a, a.next"

    // ============================== Latest ==============================
    override fun latestUpdatesRequest(page: Int): Request = popularAnimeRequest(page)
    override fun latestUpdatesSelector(): String = popularAnimeSelector()
    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // ============================== Search ==============================
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request = GET("$baseUrl/index.php?do=search&subaction=search&story=$query&search_start=$page", headers)

    override fun searchAnimeSelector(): String = popularAnimeSelector()
    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    // ============================== Details ==============================
    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()
        anime.title = document.selectFirst("h1, .shortstoryHead h1, .fullstory h1")?.text() ?: document.title()
        anime.thumbnail_url = document.selectFirst("img[src*='/uploads/'], .fposter img, .fullstory img")?.let {
            it.attr("data-src").ifEmpty { it.attr("src") }
        }
        anime.description = document.selectFirst(".fullstory, .shortstory, #dle-content, .fstory")?.text()
        return anime
    }

    // ============================== Episodes ==============================
    override fun episodeListSelector(): String = "div.video, .player"

    override fun episodeFromElement(element: Element): SEpisode {
        val ep = SEpisode.create()
        ep.name = "Серия"
        ep.episode_number = 1f
        ep.url = ""
        return ep
    }

    // ============================== Videos ==============================
    override fun videoListSelector(): String = "iframe[src]"

    override fun videoFromElement(element: Element): Video = Video(element.attr("src"), "Плеер", element.attr("src"))

    override fun videoUrlParse(document: Document): String = ""
}

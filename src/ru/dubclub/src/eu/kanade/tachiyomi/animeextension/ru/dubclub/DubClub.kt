package eu.kanade.tachiyomi.animeextension.ru.dubclub

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory

class DubClub : AnimeSourceFactory {
    override fun createSources(): List<AnimeSource> = listOf(
        DubClubSource("DubClub", "https://dubclub.online"),
    )
}

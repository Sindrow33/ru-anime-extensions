package eu.kanade.tachiyomi.animeextension.ru.jamclub

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory

class JamClub : AnimeSourceFactory {
    override fun createSources(): List<AnimeSource> = listOf(
        JamClubSource("JamClub", "https://jam-club.tv"),
    )
}

package eu.kanade.tachiyomi.animeextension.ru.aniliberty

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory

class Aniliberty : AnimeSourceFactory {
    override fun createSources(): List<AnimeSource> = listOf<AnimeSource>(
        AnilibertySource(),
    )
}

package eu.kanade.tachiyomi.animeextension.ru.ruchime

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory

class Ruchime : AnimeSourceFactory {
    override fun createSources(): List<AnimeSource> = listOf(
        RuchimeSource("Ruchime", "https://1.ruchime.org"),
    )
}

package eu.kanade.tachiyomi.animeextension.ru.jutsu

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory

class Jutsu : AnimeSourceFactory {
    override fun createSources(): List<AnimeSource> = listOf(
        JutsuSource("Jutsu", "https://jut.su"),
    )
}

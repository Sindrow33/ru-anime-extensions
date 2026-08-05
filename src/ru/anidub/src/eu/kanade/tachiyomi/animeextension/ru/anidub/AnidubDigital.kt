package eu.kanade.tachiyomi.animeextension.ru.anidub

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory

class AnidubDigital : AnimeSourceFactory {
    override fun createSources(): List<AnimeSource> = listOf(
        AnidubDigitalSource("AnidubDigital", "https://v14.anidub.digital"),
    )
}

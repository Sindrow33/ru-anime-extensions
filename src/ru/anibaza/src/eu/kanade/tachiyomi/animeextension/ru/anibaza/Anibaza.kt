package eu.kanade.tachiyomi.animeextension.ru.anibaza

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory

class Anibaza : AnimeSourceFactory {
    override fun createSources(): List<AnimeSource> = listOf(
        AnibazaSource("Anibaza", "https://anibaza.ru"),
    )
}

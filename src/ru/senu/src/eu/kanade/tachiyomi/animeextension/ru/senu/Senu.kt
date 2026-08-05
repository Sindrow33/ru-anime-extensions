package eu.kanade.tachiyomi.animeextension.ru.senu

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory

class Senu : AnimeSourceFactory {
    override fun createSources(): List<AnimeSource> = listOf(
        SenuSource("Senu", "https://senu.pro"),
    )
}

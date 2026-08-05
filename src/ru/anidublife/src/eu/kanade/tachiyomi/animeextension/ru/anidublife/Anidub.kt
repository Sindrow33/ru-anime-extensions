package eu.kanade.tachiyomi.animeextension.ru.anidublife

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory

class Anidub : AnimeSourceFactory {
    override fun createSources(): List<AnimeSource> = listOf(
        AnidubSource("Anidub", "https://anidub.life"),
    )
}

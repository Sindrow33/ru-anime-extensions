package eu.kanade.tachiyomi.animeextension.ru.anistar

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory

class Anistar : AnimeSourceFactory {
    override fun createSources(): List<AnimeSource> = listOf(
        AnistarSource("Anistar", "https://anistar.ru"),
    )
}

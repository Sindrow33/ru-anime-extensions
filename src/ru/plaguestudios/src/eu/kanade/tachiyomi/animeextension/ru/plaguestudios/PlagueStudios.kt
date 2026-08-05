package eu.kanade.tachiyomi.animeextension.ru.plaguestudios

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory

class PlagueStudios : AnimeSourceFactory {
    override fun createSources(): List<AnimeSource> = listOf(
        PlagueStudiosSource("PlagueStudios", "https://plaguestudios.tv"),
    )
}

# T35: AniList + Bangumi Scraper Decisions

## Fix applied: ScraperSource.BANGUMI_ARCHIVE → ScraperSource.BANGUMI
The user-provided code referenced `ScraperSource.BANGUMI_ARCHIVE` which does not exist in the codebase. The actual enum value is `ScraperSource.BANGUMI` (defined in `core/model/../ScraperResult.kt`).

## Enhancements beyond spec
- AniListScraper now populates `anime.anilistId` from the parsed integer ID
- BangumiScraper now populates `anime.bangumiId` from the parsed integer ID
- BangumiScraper populates `ScraperResult.titleCn` from `name_cn` field
- Both scrapers catch `NumberFormatException` when parsing `animeId.toInt()` and return `AppError.ScrapingError.NoMatchFound`

## Verified against codebase
- `MetadataScraper` interface: `searchAnime`, `getAnimeDetails`, `getEpisodes`, `searchByAlias` all match
- `Anime` data class: 14 constructor params (id, title, titleCn, summary, genres, studio, director, episodeCount, airDate, rating, bangumiId, anilistId, tmdbId, posterUrl, fanartUrl)
- `ScraperResult` data class: 6 params (animeId, title, titleCn, matchedTitle, confidence, source)
- `AppError.ScrapingError`: NoMatchFound, ApiError, ParseError
- `Result`: Success/Error sealed class with `success()`/`failure()` companions

## Build verification
Gradle build fails at project-level due to missing `miruplay.android.application` convention plugin (from build-logic/). This is a pre-existing project configuration issue unrelated to these files. Files were manually verified for import resolution and type compatibility.

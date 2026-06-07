# Metadata Search Aggregation Refactor

## Status

This document records the staged redesign of MiruPlay metadata search/match into **local multi-source recall + local normalization + local clustering + local rerank**.

Current implementation status:
- **Phase 1:** complete and validated
- **Phase 2:** substantially completed for drama provider-neutral compatibility
- **Phase 3:** started via `TVMaze` drama provider, with recall plus minimal provider-ref detail hydration

A focused Room schema migration is now included for provider-neutral drama series cache persistence.

---

## Current Structural Problems

### 1. Drama search and apply were TMDB-centric

Before this refactor:
- `DramaMetadataRepository` exposed TMDB-shaped detail APIs
- `DramaDetailViewModel` manual match flow, copy, and selection behavior assumed `TMDB`
- `LibraryDramaResolver` preferred explicit `metadataSource == "TMDB"` style semantics
- `DramaMetadataSearchResult` and `DramaSeries` leaked `tmdbId` into the domain/UI path

This caused drama search candidates to be effectively single-source, even if the app later needed `TVMaze`, `TheTVDB`, or `MyDramaList`.

### 2. Anime had multiple scrapers but no shared aggregation layer

Before this refactor:
- `BangumiScraper` and `AniListScraper` existed independently
- `AnimeDetailViewModel` did some local multi-query dedupe, but not true cross-provider aggregation/rerank
- `MetadataBatchPlanner` allowed custom search callbacks, but had no built-in multi-source aggregate default
- upstream ordering still influenced final ranking too directly

This meant MiruPlay still lacked a single local decision layer that could compare Bangumi/AniList candidates using local title/path/season evidence.

### 3. Result shapes were not provider-neutral enough

Before this refactor:
- `ScraperResult` was anime-oriented
- `DramaMetadataSearchResult` was TMDB-oriented
- provider-specific IDs leaked too early into the UI/domain path

This made it difficult to:
- merge cross-provider candidates
- preserve provider-local rank/score while still applying local rerank
- carry evidence and recommendation level through UI/batch flows

---

## Target Architecture

### Shared local search pipeline

MiruPlay should decide metadata candidates locally using this pipeline:

1. **Query planning** from local context
2. **Multi-provider recall**
3. **Provider-local normalization**
4. **Within-provider dedupe**
5. **Cross-provider clustering**
6. **Local rerank**
7. **Decision output** for auto-accept / review / low-confidence
8. **Provider-specific detail fetch/apply** only after a local candidate is chosen

### Core abstractions

Implemented/shared abstractions:
- `MetadataProviderRef`
- `MetadataSearchContext`
- `MetadataSearchQuery`
- `MetadataQueryPlan`
- `MetadataSearchProviderCandidate`
- `AggregatedMetadataCandidate`
- `AggregatedMetadataSearchResult`
- `MatchEvidence`
- `MatchRecommendation`
- `MetadataQueryPlanner`
- `MetadataSearchAggregationSupport`
- `MetadataCandidateClusterer`
- `MetadataCandidateReranker`
- `AnimeMetadataSearchProvider` / `DramaMetadataSearchProvider`
- `AnimeMetadataSearchAggregator` / `DramaMetadataSearchAggregator`

### Local evidence used by rerank

Current rerank uses:
- normalized title similarity
- exact/contains match
- CJK overlap
- token overlap
- season match / mismatch
- year proximity
- local episode/season structure consistency
- prior bound provider hit
- provider original score/rank
- provider completeness
- provider prior weight
- multi-source convergence bonus

### Query planner responsibilities

Current planner derives local search signals from:
- manual query
- title / localized title / original title
- seasonless variants
- aliases
- cached metadata title
- file path derived titles
- existing bound provider ref
- provider-ref hint text such as `Bangumi:431767`, `TMDB:321`, `TVMaze:maze-321`

Provider-ref hints are not treated as plain text queries.
They are surfaced separately via `MetadataQueryPlan.providerRefHints` so providers can inject exact candidates directly.

---

## Phase Plan

### Phase 1 — Shared aggregation skeleton, no schema changes

Goals:
- introduce provider-neutral aggregation/rerank models
- add shared local query planner / clustering / rerank support
- route anime manual match through local multi-source aggregation
- provide batch anime matching with an aggregated default search callback
- route drama manual match through aggregated search candidates
- keep final drama detail apply compatible with the current TMDB-first path
- avoid Room migration

### Phase 2 — Drama provider-neutral compatibility

Goals:
- move drama repository/UI/resolver semantics to `providerRef`
- stop assuming TMDB is the only durable external identity
- keep compatibility with existing cached/indexed TMDB data
- reduce TMDB-specific copy and selection assumptions in UI

### Phase 3 — Add a second real drama provider

Goals:
- keep TMDB as the current structured detail source if needed
- add a second recall source into drama aggregation
- first practical target: `TVMaze`
- future candidates: `TheTVDB`, `MyDramaList`

---

## What Is Implemented Now

### Shared provider-neutral models and aggregation support

New files:
- `core/model/src/main/kotlin/com/miruplay/tv/model/MetadataSearchModels.kt`
- `repository-api/src/main/kotlin/com/miruplay/tv/repository/MetadataSearchAggregation.kt`
- `repository-api/src/main/kotlin/com/miruplay/tv/repository/MetadataSearchText.kt`

These files introduce:
- provider-neutral candidate models
- query planning
- provider-ref hint parsing
- title normalization helpers
- within-provider dedupe
- cross-provider clustering
- local rerank + evidence generation

### Anime manual match now uses shared aggregation

Updated file:
- `ui-tv/src/main/kotlin/com/miruplay/tv/ui/detail/AnimeDetailViewModel.kt`

Behavior:
- builds `MetadataSearchContext` from current anime + local episodes
- calls the shared anime aggregator
- reranks Bangumi + AniList locally
- keeps Bangumi as an allowed preferred apply result, without making Bangumi the only recall source

### Batch anime matching now has an aggregated default

Updated file:
- `repository-api/src/main/kotlin/com/miruplay/tv/repository/MetadataBatchPlanner.kt`

Behavior:
- `aggregatedSearchCandidates(...)` now provides a shared default aggregate callback
- batch preview and detail manual search can reuse the same local aggregation primitives
- existing metadata bindings now feed candidate aliases, including provider-ref hints like `Bangumi:431767`

### Drama manual match now uses shared aggregation

Updated files:
- `ui-tv/src/main/kotlin/com/miruplay/tv/ui/detail/DramaDetailViewModel.kt`
- `ui-tv/src/main/kotlin/com/miruplay/tv/ui/detail/DramaDetailScreen.kt`
- `ui-tv/src/main/kotlin/com/miruplay/tv/ui/mode/DramaLibraryScreen.kt`

Behavior:
- drama manual match uses `DramaMetadataSearchAggregator`
- aggregated drama UI candidates now choose a provider-neutral representative from the local cluster instead of hard-preferring `TMDB` at projection time
- search messaging distinguishes between:
  - multi-source online candidate search
  - whether a direct online detail source is currently available
- UI copy is less misleading about TMDB being the only online concept
- bound non-TMDB providers such as `TVMaze` are no longer blocked by a missing `TMDB` token when direct provider-ref detail refresh is available

### Drama compatibility slice toward provider-neutral apply

Updated files:
- `core/model/src/main/kotlin/com/miruplay/tv/model/DramaModels.kt`
- `repository-api/src/main/kotlin/com/miruplay/tv/repository/DramaMetadataRepository.kt`
- `repository-api/src/main/kotlin/com/miruplay/tv/repository/LibraryDramaResolver.kt`

Behavior:
- `DramaSeries` can expose `boundMetadataProviderRef()`
- `DramaMetadataSearchResult` carries `providerRef`
- resolver and detail apply can persist/load `metadataSource + metadataId` generically
- repository apply path now uses `fetchSeriesMetadataByProviderRef(...)`
- stale cached `TMDB` compatibility ids no longer override an explicit stored provider binding such as `TVMaze:maze-321`
- `DramaMetadataRepository` now exposes capability-style checks so UI/detail refresh logic can reason about provider-ref detail support instead of only checking TMDB token state
- TMDB compatibility still remains where needed

### Second real drama recall provider

New file:
- `scraper/src/main/kotlin/com/miruplay/tv/scraper/TvMazeDramaMetadataSearchProvider.kt`

Updated files:
- `scraper/src/main/kotlin/com/miruplay/tv/scraper/MetadataSearchAggregators.kt`
- `scraper/src/main/kotlin/com/miruplay/tv/scraper/di/ScraperModule.kt`

Behavior:
- drama candidate recall is now genuinely multi-source: `TMDB` + `TVMaze`
- `TVMaze` now participates in recall/aggregation and supports minimal provider-ref detail + episode hydration
- title-based drama detail refresh now routes as `TMDB`-first with `TVMaze` fallback when TMDB title fetch is unavailable or returns no usable result

---

## Reused Logic vs New Logic

### Reused and elevated

Reused/adapted from existing behavior:
- `BangumiSubjectMatcher` matching philosophy remains relevant for local evidence weighting
- `MetadataScraper` / `MetadataScraperSearch` provider-specific search capabilities remain intact
- Bangumi and AniList scraper implementations are reused as provider adapters
- existing local title/path/season knowledge from detail/batch flows is reused as search context
- existing TMDB drama repository remains the current structured detail provider

### New abstractions

New abstractions introduced by this refactor:
- provider-neutral search models
- shared query planner
- shared dedupe/cluster/rerank support
- provider adapter layer for anime/drama search
- drama `TVMaze` provider adapter with search and minimal detail hydration
- provider-ref hint parsing and injection path

---

## Files Added or Modified

### Added
- `core/model/src/main/kotlin/com/miruplay/tv/model/MetadataSearchModels.kt`
- `repository-api/src/main/kotlin/com/miruplay/tv/repository/MetadataSearchAggregation.kt`
- `repository-api/src/main/kotlin/com/miruplay/tv/repository/MetadataSearchText.kt`
- `scraper/src/main/kotlin/com/miruplay/tv/scraper/MetadataSearchAggregators.kt`
- `scraper/src/main/kotlin/com/miruplay/tv/scraper/RoutingDramaMetadataRepository.kt`
- `scraper/src/main/kotlin/com/miruplay/tv/scraper/TvMazeDramaMetadataRepository.kt`
- `scraper/src/main/kotlin/com/miruplay/tv/scraper/TvMazeDramaMetadataSearchProvider.kt`
- `scraper/src/test/kotlin/com/miruplay/tv/scraper/TvMazeDramaMetadataRepositoryTest.kt`
- `scraper/src/test/kotlin/com/miruplay/tv/scraper/TvMazeDramaMetadataSearchProviderTest.kt`
- `ui-tv/src/test/kotlin/com/miruplay/tv/ui/detail/AnimeDetailViewModelTest.kt`
- `repository-api/src/test/kotlin/com/miruplay/tv/repository/MetadataSearchAggregationTest.kt`
- `docs/metadata-search-aggregation-refactor.md`

### Modified
- `repository-api/src/main/kotlin/com/miruplay/tv/repository/MetadataBatchPlanner.kt`
- `repository-api/src/main/kotlin/com/miruplay/tv/repository/DramaMetadataRepository.kt`
- `repository-api/src/main/kotlin/com/miruplay/tv/repository/LibraryDramaResolver.kt`
- `scraper/src/main/kotlin/com/miruplay/tv/scraper/TmdbDramaMetadataRepository.kt`
- `scraper/src/main/kotlin/com/miruplay/tv/scraper/di/ScraperModule.kt`
- `ui-tv/src/main/kotlin/com/miruplay/tv/ui/detail/AnimeDetailViewModel.kt`
- `ui-tv/src/main/kotlin/com/miruplay/tv/ui/detail/DramaDetailViewModel.kt`
- `ui-tv/src/main/kotlin/com/miruplay/tv/ui/detail/DramaDetailScreen.kt`
- `ui-tv/src/main/kotlin/com/miruplay/tv/ui/mode/DramaLibraryScreen.kt`
- `ui-tv/src/main/kotlin/com/miruplay/tv/ui/mode/DramaLibraryViewModel.kt`
- `core/model/src/main/kotlin/com/miruplay/tv/model/DramaModels.kt`
- related tests in `core/model`, `repository-api`, `ui-tv`, and `scraper`

---

## Validation

Targeted validation completed successfully for the current staged landing:

- `./gradlew :core:model:test --tests com.miruplay.tv.model.DramaModelsTest --tests com.miruplay.tv.model.MetadataUiConventionsTest --no-build-cache -Dorg.gradle.caching=false`
- `./gradlew :repository-api:test --tests com.miruplay.tv.repository.MetadataSearchAggregationTest --tests com.miruplay.tv.repository.MetadataBatchPlannerTest --tests com.miruplay.tv.repository.LibraryDramaResolverTest --no-build-cache -Dorg.gradle.caching=false`
- `./gradlew :ui-tv:testDebugUnitTest --tests com.miruplay.tv.ui.detail.AnimeDetailViewModelTest --tests com.miruplay.tv.ui.detail.DramaDetailViewModelTest --tests com.miruplay.tv.ui.mode.DramaLibraryViewModelTest --no-build-cache -Dorg.gradle.caching=false`
- `./gradlew :scraper:testDebugUnitTest --tests com.miruplay.tv.scraper.TmdbDramaMetadataRepositoryTest --tests com.miruplay.tv.scraper.TvMazeDramaMetadataSearchProviderTest --tests com.miruplay.tv.scraper.TvMazeDramaMetadataRepositoryTest --no-build-cache -Dorg.gradle.caching=false`

Covered test themes:
- query planner season/path/provider-hint extraction
- within-provider dedupe
- cross-provider clustering
- year/season/structure-sensitive rerank
- anime manual aggregate match behavior
- drama manual aggregate match behavior
- drama provider-neutral binding persistence behavior
- drama manual-match representative projection no longer hard-preferring `TMDB`
- stale TMDB cache compatibility no longer overriding explicit provider bindings
- bound `TVMaze` detail refresh without TMDB token
- provider-neutral drama cache persistence across app restarts via `drama_series_cache`
- second drama provider (`TVMaze`) mapping and hint-based candidate injection

---

## Remaining Work

### Still intentionally incomplete after this landing

1. **Drama domain is not fully provider-neutral yet**
- `tmdbId` still exists in `DramaSeries` and `DramaMetadataSearchResult` for compatibility
- some cache compatibility paths still serialize through legacy anime metadata cache fields
- TMDB still remains the default fully integrated drama detail provider for title-based lookup and compatibility behavior

2. **Non-TMDB drama provider support is still partial**
- `TVMaze` now supports search plus minimal provider-ref detail/episode hydration, and bound `TVMaze` entries can refresh without TMDB token gating
- broader non-TMDB parity is still incomplete for richer metadata fidelity, fallback strategy, and long-term cache/schema handling across all drama providers

3. **Drama schema remains partially compatibility-shaped**
- a focused `drama_series_cache` Room table now persists provider-neutral drama binding/header data across app restarts
- legacy anime-shaped cache compatibility still remains as a fallback bridge for older `drama-series:*` rows and TMDB compatibility ids

4. **Settings copy still exposes TMDB as the current tokenized detail source**
- this is currently accurate for detail refresh capability
- broader multi-source settings UX can be improved later

---

## Recommended Next Steps

### Next for Phase 2

1. Continue shrinking remaining drama `tmdbId` compatibility fields until they are no longer needed by public domain/UI shapes
2. Reduce remaining legacy drama cache compatibility fallback through anime-shaped storage once migration confidence is sufficient
3. Keep tightening `LibraryDramaResolver` / detail UI copy toward provider-neutral wording where it still implicitly frames TMDB as the only title-refresh path
4. Improve title-based drama detail routing quality and richer provider fallback selection beyond the current `TMDB`-first / `TVMaze`-fallback strategy

### Next for Phase 3

1. Keep `TVMaze` as the first non-TMDB secondary provider and improve its detail fidelity
2. Decide whether the next structured detail provider should be:
   - deeper `TVMaze` parity work, or
   - `TheTVDB` if richer structure is needed and API constraints are acceptable
3. Treat `MyDramaList` primarily as a recall/alias enrichment source unless a reliable structured detail path is added

---

## Merge Guidance

The current landing is designed to be:
- incremental
- rollback-friendly
- schema-safe
- testable
- compatible with existing Bangumi sync, playback, and scan pipelines

Recommended merge interpretation:
- treat this as the **Phase 1 complete landing**
- treat the current provider-neutral drama compatibility and `TVMaze` provider work as **a strong Phase 2 landing with safe forward progress into Phase 3**, not as proof that the full long-range migration is done

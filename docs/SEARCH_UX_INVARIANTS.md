# Search UX invariants

These rules protect the desktop search experience from regressions.

## 1. Background search must never steal keyboard focus

Typing in the top search field starts a debounced asynchronous request.
Starting, cancelling or completing that request must not disable the search field or move keyboard focus away from it.

A user must be able to type a long title continuously even when intermediate queries are already loading.

Current debounce: 450 ms.

## 2. Search preserves the section where it started

- Search from Movies -> movie results only; Movies stays selected.
- Search from Series -> TV-series results only; Series stays selected.
- Search from TV -> live-channel search; TV stays selected.
- Search from Home -> global movie + series search.

Subsequent debounced queries keep the original search scope until the user leaves search.

## 3. Clearing or escaping returns to the source section

Clearing the search field or pressing Escape while showing search results returns to the section where the search started instead of forcing Home or Movies.

## 4. TMDb search is bilingual but canonically deduplicated

TMDb searches merge preferred-language and alternate ES/EN responses by canonical TMDb ID.
The preferred catalog language wins when the same title appears in both responses.

Regression coverage:
- MainFrameSearchTest
- TmdbFixtureTest cross-language fallback

## 5. Series search must tolerate TMDb prefix gaps

TMDb's text search is not an autocomplete API and may return zero or irrelevant
results for a valid title prefix (for example, `ginn` may miss `Ginny & Georgia`,
and `sui` may miss `Suits`).

For Series searches with at least three normalized characters, Streamflix uses
TMDb's official daily TV-series ID export as a local prefix index. The compressed
export is downloaded once per UTC day, cached under the local Streamflix cache,
and scanned off the Swing UI thread. Only the best few canonical TMDb IDs are
resolved through the normal localized API.

Rules:
- direct title prefixes outrank word-internal prefixes;
- popularity breaks ties among equivalent prefixes;
- preferred-language API metadata is still used for the displayed card;
- the index is a fallback/ranking aid, never a replacement for canonical TMDb IDs;
- index download/search failure must degrade to normal TMDb search rather than
  break the search UI;
- incremental empty results are presented as a non-final typing state; pressing
  Enter confirms an exact search.

Regression coverage:
- TmdbTitleIndexTest
- MainFrameSearchTest

## 6. Concurrent multi-provider VOD search with bounded timeouts and canonical deduplication

When searching in Home, Movies, or Series scope, Streamflix fans out concurrent asynchronous queries across all compatible registered VOD providers with a bounded per-provider timeout (4.5s).

Rules:
- Slow or unresponsive providers are timed out without blocking responsive providers;
- Results are deduplicated by canonical TMDb ID or normalized semantic key (title + year + type);
- Richer metadata (TMDb / official posters / ratings) is preserved for the primary display card;
- Alternative candidate sources are recorded to enable instant sequential playback failover;
- TV/Live/Sports search remains strictly scoped to its domain;
- Unconfigured TMDb allows other VOD providers to return results rather than blocking search entirely.

Regression coverage:
- VodSearchCoordinatorTest
- ProviderFallbackCoordinatorTest

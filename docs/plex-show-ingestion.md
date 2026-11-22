# Plex show scrobble ingestion

Após a ingestão local, séries podem entrar no fluxo de enriquecimento automático descrito em [`features/automatic-media-enrichment.md`](features/automatic-media-enrichment.md). Este documento trata do mapeamento e da persistência específicos do Plex.

## Scope

This feature ingests Plex `media.scrobble` events for `Metadata.type=episode`.

- `media.play` and `media.stop` are ignored for episode completion.
- Domain persistence is decoupled from `event_sources`.

It also supports full show library import during pipeline startup and manual watch creation through the API.

## Mapping

Plex payload -> Media Pulse domain:

- `Metadata.originalTitle` / `Metadata.grandparentTitle` -> `tv_shows.original_title`
- `Metadata.grandparentSlug` -> `tv_shows.slug`; when absent, a local slug is generated from the show title
- `Metadata.parentYear` / `Metadata.year` -> `tv_shows.year`, except for unmatched `tv.plex.agents.none` episode metadata, whose `year` describes the episode and is not used as the show year
- `Metadata.summary` -> `tv_episodes.summary`
- `Metadata.title` -> `tv_episodes.title`
- `Metadata.parentIndex` -> `tv_episodes.season_number`
- `Metadata.index` -> `tv_episodes.episode_number`
- `Metadata.lastViewedAt` -> `tv_episode_watches.watched_at`
- GUIDs de série `tmdb://`, `tvdb://` e `imdb://` -> `tv_shows.tmdb_id`, `tv_shows.tvdb_id` e `tv_shows.imdb_id`
- GUIDs de episódio `tmdb://`, `tvdb://` e `imdb://` -> `tv_episodes.tmdb_id`, `tv_episodes.tvdb_id` e `tv_episodes.imdb_id`
- `Image[]` + `thumb` do show -> baixados do Plex e armazenados localmente

## Canonical identity

Show identity prefers canonical third-party ids when available.

- Resolution priority during Plex library import is:
  1. `TMDB`
  2. `TVDB`
  3. show fingerprint by `original_title + year`
- Plex `ratingKey` and `plex://...` GUIDs are never persisted or used for reconciliation.
- `ratingKey` is used only in memory while navigating the current Plex API import.
- Plex episode scrobbles first reuse the canonical show of an episode identified by unambiguous third-party IDs. When no episode ID resolves, they try the show fingerprint and then an exact `slug + year` match accepted only when it returns one candidate. When the show year is absent, they may reuse a unique exact known title (including localized titles) with the same nonblank slug supplied by Plex. Generated fallback slugs do not qualify. They do not resolve the show by Plex GUID or slug alone; known years still require an exact year match.
- For unmatched `tv.plex.agents.none` libraries without canonical IDs, a scrobble may reuse an exact title match only when it resolves to one local show. Ambiguous title matches are ignored and a separate show is created.
- Show third-party IDs (TMDB/TVDB/IMDB) are stored directly in `tv_shows` when available.
- Episode third-party IDs (TMDB/TVDB/IMDB) are stored directly in `tv_episodes` when available.
- Episode resolution prefers third-party IDs and falls back to the fingerprint based on show, season, episode number, and title.
- Plex identifier conflicts never block a scrobble or library import: the current canonical ID is preserved and a warning records the episode, show, provider, and conflicting IDs.
- When Plex returns multiple distinct episode IDs for the same provider, that provider is ignored for both reconciliation and persistence instead of choosing an arbitrary candidate.

## Tables

- `tv_shows`
- `tv_show_titles`
- `tv_show_images`
- `tv_episodes`
- `tv_episode_watches`

## Show images

- The service downloads all relevant show images available in Plex payload (`Image[]`).
- One image is chosen as primary with deterministic rule:
  1. First image marked as `coverPoster`.
  2. Fallback to first valid image.
  3. Fallback to `thumb` if list is empty.
- Primary image path is stored in `tv_shows.cover_url`.

## Startup full import

Show library import runs in the startup pipeline when enabled.

- It imports from Plex `show` sections using paginated reads.
- It persists canonical shows, episodes, localized titles, show IDs in `tv_shows`, and episode IDs in `tv_episodes` when available.
- It generates a local show slug from the Plex title when the provider does not supply one, keeping imported shows navigable.
- Show reconciliation during import prefers TMDB/TVDB identifiers over fingerprint fallback. If neither resolves, an import with third-party IDs may adopt a single shell with an exact known title and the same supplied slug, only if that shell has no year or external IDs. This preserves watches received before the first library import. Ambiguous matches or shells with known years/IDs are not adopted.
- It does not create rows in `tv_episode_watches`.

## Manual watch ingestion

`POST /api/shows/watches` processes a single manual episode watch.

Resolution order:

1. `tmdbId` -> existing show by `tv_shows.tmdb_id`.
2. `tvdbId` -> existing show by `tv_shows.tvdb_id`.
3. Show fingerprint by `showTitle + year`.
4. Episode by fingerprint or `(show_id, season_number, episode_number)`.

When `tmdbId` is present:

- TMDb `/tv/{id}` fills missing show metadata (`description`, `slug`, `year`).
- TMDb poster/backdrop are downloaded and stored under `/covers/tmdb/tv-shows/{showId}/...` when no primary image exists.

Manual sources:

- `tv_show_titles.source` supports `MANUAL`.
- `tv_episode_watches.source` supports `MANUAL`.

## Woody Woodpecker: cadastro duplicado antes da importação

Em 22/11/2026, eventos Plex de `O Show do Pica-Pau` chegaram sem ano e sem IDs externos dos episódios, mas com slug `woody-woodpecker-and-friends`. O webhook criou a série `129`; a biblioteca criou depois `130`, com título original `Woody Woodpecker and Friends`, ano 1929 e TVDB `236591`. Os quatro episódios assistidos de `129` também existiam em `130`, por temporada, número e título. O mesmo slug abria um registro enquanto `unwatched=true` listava o outro; o filtro estava correto por ID local.

A versão 1.43.2 cobre ambas as ordens de chegada: webhook sem ano reconhece título conhecido + slug fornecido, desde que únicos; importação pode adotar o cadastro incompleto anterior ao catálogo. IDs continuam tendo prioridade e não se faz reconciliação por slug isolado. Testes cobrem reconhecimento, ambiguidade, slugs diferentes e preservação de identidades/anos conhecidos.

O [reparo específico 129 → 130](sql/merge-show-129-into-130.sql) preserva os IDs/fontes/datas das quatro visualizações, apontando-as aos episódios do catálogo, e mantém os 25 episódios e a capa de `130`. Exige backup, valida as identidades e interrompe diante de novos vínculos, comentários/avaliações ou alterações no histórico. Não é migration. Execute com `psql -X -d mediapulse` para simular com rollback e `-v apply=true` para aplicar; valide o detalhe por slug com 4/25 episódios vistos e a ausência na biblioteca `unwatched=true`.

## Comparar organizações diferentes da mesma coleção no Plex

No teste com os mesmos curtas em `Woody Woodpecker and Friends` (TVDB `236591`) e `The Woody Woodpecker Show` (TVDB `76964`), hard links dentro da mesma biblioteca Plex fizeram episódios alternarem entre os cadastros a cada varredura. A detecção de movimentação também deslocou marcações/histórico para a identificação de destino. Não usar hard links nessa configuração de comparação. Neste servidor, `cp --reflink=always` retornou `Operation not supported`; foram usadas cópias independentes, com inodes distintos, após autorização do owner.

Antes de copiar, mapear cada curta por título e data, exigindo correspondência única. As duas organizações têm numeração diferente: por exemplo, `Woody Woodpecker` é S1941E07 em `and Friends` e S1941E01 em `Show`. As datas também podem divergir entre os catálogos. Guardar o mapeamento para uma eventual migração posterior, manter a série de teste sem monitoramento/downloads automáticos no Sonarr e preservar o histórico na identificação original até a escolha do owner.

A pasta de teste pode usar `.plexmatch` com `tvdbid: 76964` para orientar a identidade. Isso não define a ordem dos episódios: configurar somente essa série no Plex para `TheTVDB (Aired)` (`showOrdering=tvdbAiring`) quando os arquivos seguem o Sonarr/TVDB. A biblioteca deste teste tinha `The Movie Database` como ordem padrão, incompatível com as temporadas por ano dos arquivos. Depois da varredura, conferir contagem, IDs externos e títulos dos episódios antes de importar para o Media Pulse. Não tratar um vínculo TMDb no nível da série como garantia de compatibilidade da numeração dos episódios.

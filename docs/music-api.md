# Music API

A Music API expõe exploração read-only da biblioteca, escuta recente, rankings, cobertura e páginas de detalhe.

## Endpoints

| Path | Params | Retorna |
| --- | --- | --- |
| `GET /api/music/summary` | `range=week|month|custom`, `start?`, `end?` | `MusicSummaryResponse` |
| `GET /api/music/recent-albums` | `limit=20`, `cursor?` | `RecentAlbumsPageResponse` |
| `GET /api/music/library/artists` | `limit=20`, `cursor?` | `ArtistLibraryPageResponse` |
| `GET /api/music/library/albums` | `limit=20`, `cursor?` | `AlbumLibraryPageResponse` |
| `GET /api/music/library/tracks` | `limit=20`, `cursor?` | `TrackLibraryPageResponse` |
| `GET /api/music/search` | `q`, `limit=10` | `SearchResponse` |
| `GET /api/music/albums/{albumId}` | `albumId` | `AlbumPageResponse` |
| `GET /api/music/artists/{artistId}` | `artistId` | `ArtistPageResponse` |
| `GET /api/music/tracks/{trackId}` | `trackId` | `TrackPageResponse` |
| `GET /api/music/tops/artists` | `start`, `end`, `limit=20` | lista de artistas |
| `GET /api/music/tops/albums` | `start`, `end`, `limit=20` | lista de álbuns |
| `GET /api/music/tops/tracks` | `start`, `end`, `limit=20` | lista de faixas |
| `GET /api/music/tops/genres` | `start`, `end`, `limit=20` | lista de gêneros |
| `GET /api/music/coverage/artists` | `limit=50` | cobertura por artista |
| `GET /api/music/coverage/albums` | `limit=50` | cobertura por álbum |
| `GET /api/music/albums/never-played` | `limit=50` | álbuns nunca tocados |
| `GET /api/music/genres/trending` | `start`, `end`, `compareStart`, `compareEnd`, `limit=20` | gêneros em alta |
| `GET /api/music/genres/recent` | `limit=50` | gêneros recentes |
| `GET /api/music/genres/underplayed` | `start`, `end`, `minLibraryAlbums=3`, `limit=20` | gêneros subexplorados |
| `GET /api/music/genres/top-by-source` | `start`, `end`, `limit=10` | top gêneros por source |

## Paginação

Os endpoints abaixo usam paginação por cursor retornado no payload:

- `GET /api/music/recent-albums`
- `GET /api/music/library/artists`
- `GET /api/music/library/albums`
- `GET /api/music/library/tracks`

## Range temporal

`GET /api/music/summary` resolve o range assim:

- `week`: últimos 7 dias a partir de `now`
- `month`: últimos 30 dias a partir de `now`
- `custom`: exige `start` e `end`

Os endpoints de ranking e análise por período exigem `start` e `end` explícitos em ISO-8601 UTC.

## Observações de contrato

- páginas de artista, álbum e faixa retornam visão agregada do histórico, não eventos crus
- endpoints de coverage comparam catálogo conhecido com o que já foi ouvido
- `genres/recent` usa os últimos plays, não uma janela por data

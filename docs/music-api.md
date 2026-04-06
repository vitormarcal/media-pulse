# Music API

A Music API fornece visão read-only da escuta recente, rankings, cobertura de biblioteca e páginas de detalhe para álbum, artista e faixa.

## Endpoints

| Path | Params | Retorna |
| --- | --- | --- |
| `GET /api/music/summary` | `range=week|month|custom`, `start?`, `end?` | `MusicSummaryResponse` |
| `GET /api/music/recent-albums` | `limit=20` | `RecentAlbumResponse[]` |
| `GET /api/music/search` | `q`, `limit=10` | `SearchResponse` |
| `GET /api/music/albums/{albumId}` | `albumId` (path) | `AlbumPageResponse` |
| `GET /api/music/artists/{artistId}` | `artistId` (path) | `ArtistPageResponse` |
| `GET /api/music/tracks/{trackId}` | `trackId` (path) | `TrackPageResponse` |
| `GET /api/music/tops/artists` | `start`, `end`, `limit=20` | `TopArtistResponse[]` |
| `GET /api/music/tops/albums` | `start`, `end`, `limit=20` | `TopAlbumResponse[]` |
| `GET /api/music/tops/tracks` | `start`, `end`, `limit=20` | `TopTrackResponse[]` |
| `GET /api/music/tops/genres` | `start`, `end`, `limit=20` | `TopGenreResponse[]` |
| `GET /api/music/coverage/artists` | `limit=50` | `ArtistCoverageResponse[]` |
| `GET /api/music/coverage/albums` | `limit=50` | `AlbumCoverageResponse[]` |
| `GET /api/music/albums/never-played` | `limit=50` | `TopAlbumResponse[]` |
| `GET /api/music/genres/trending` | `start`, `end`, `compareStart`, `compareEnd`, `limit=20` | `TrendingGenreResponse[]` |
| `GET /api/music/genres/recent` | `limit=50` | `RecentGenreResponse[]` |
| `GET /api/music/genres/underplayed` | `start`, `end`, `minLibraryAlbums=3`, `limit=20` | `UnderplayedGenreResponse[]` |
| `GET /api/music/genres/top-by-source` | `start`, `end`, `limit=10` | `TopGenreBySourceResponse[]` |

## Contratos principais

### ArtistPageResponse

```json
{
  "artistId": 12,
  "artistName": "The Cure",
  "totalPlays": 182,
  "uniqueTracksPlayed": 47,
  "uniqueAlbumsPlayed": 9,
  "libraryAlbumsCount": 14,
  "libraryTracksCount": 121,
  "lastPlayed": "2026-04-05T22:11:00Z",
  "albums": [
    {
      "albumId": 81,
      "albumTitle": "Disintegration",
      "year": 1989,
      "coverUrl": "/covers/plex/music/albums/81/poster.jpg",
      "totalTracks": 12,
      "playedTracks": 10,
      "playCount": 44,
      "lastPlayed": "2026-04-05T22:11:00Z"
    }
  ],
  "topTracks": [
    {
      "trackId": 301,
      "title": "Pictures of You",
      "albumId": 81,
      "albumTitle": "Disintegration",
      "playCount": 19,
      "lastPlayed": "2026-04-05T22:11:00Z"
    }
  ],
  "playsByDay": [
    {
      "day": "2026-04-01",
      "plays": 7
    }
  ]
}
```

### TrackPageResponse

```json
{
  "trackId": 301,
  "title": "Pictures of You",
  "artistId": 12,
  "artistName": "The Cure",
  "totalPlays": 19,
  "lastPlayed": "2026-04-05T22:11:00Z",
  "albums": [
    {
      "albumId": 81,
      "albumTitle": "Disintegration",
      "year": 1989,
      "coverUrl": "/covers/plex/music/albums/81/poster.jpg",
      "discNumber": 1,
      "trackNumber": 2,
      "playCount": 19,
      "lastPlayed": "2026-04-05T22:11:00Z"
    }
  ],
  "recentPlays": [
    {
      "playedAt": "2026-04-05T22:11:00Z",
      "source": "SPOTIFY",
      "albumId": 81,
      "albumTitle": "Disintegration"
    }
  ]
}
```

### AlbumPageResponse

```json
{
  "albumId": 81,
  "albumTitle": "Disintegration",
  "artistId": 12,
  "artistName": "The Cure",
  "year": 1989,
  "coverUrl": "/covers/plex/music/albums/81/poster.jpg",
  "lastPlayed": "2026-04-05T22:11:00Z",
  "totalPlays": 44,
  "tracks": [
    {
      "trackId": 301,
      "title": "Pictures of You",
      "discNumber": 1,
      "trackNumber": 2,
      "playCount": 19,
      "lastPlayed": "2026-04-05T22:11:00Z"
    }
  ],
  "playsByDay": [
    {
      "day": "2026-04-01",
      "plays": 7
    }
  ]
}
```

## Exemplos

### Artista

```bash
curl "{{host}}/api/music/artists/12"
```

### Faixa

```bash
curl "{{host}}/api/music/tracks/301"
```

### Álbum

```bash
curl "{{host}}/api/music/albums/81"
```

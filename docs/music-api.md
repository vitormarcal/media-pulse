# Music API

## Listas de álbuns

Listas são recortes pessoais e mutáveis formados por álbuns já presentes no acervo local. A ordem editorial fica armazenada em `album_list_items.position`; ordenar a interface por marcação ou avaliação não altera essa sequência.

| Método | Endpoint | Comportamento |
|---|---|---|
| `GET` | `/api/music/lists` | Lista os recortes com progresso e até três capas. |
| `GET` | `/api/music/lists/{slug}` | Retorna detalhes, itens, posição, `listenedAt` e avaliação global. |
| `POST` | `/api/music/lists` | Cria com body `{ "name": "...", "description": "..." }`. |
| `PUT` | `/api/music/lists/{listId}` | Atualiza nome e descrição. |
| `DELETE` | `/api/music/lists/{listId}` | Exclui o recorte e seus itens, sem excluir álbuns. |
| `POST` | `/api/music/lists/{listId}/albums/{albumId}` | Adiciona um álbum local ao fim da lista. |
| `DELETE` | `/api/music/lists/{listId}/albums/{albumId}` | Remove o item e normaliza posições. |
| `PUT` | `/api/music/lists/{listId}/order` | Salva a ordem completa com body `{ "albumIds": [3, 1, 2] }`. |
| `PATCH` | `/api/music/lists/{listId}/albums/{albumId}/listened` | Marca ou desmarca com body `{ "listened": true }`. |

`listenedAt` representa apenas o estado atual e é apagado ao desmarcar; não existe histórico de marcações. `rating` vem da avaliação global `ALBUM` em `media_ratings`.

A Music API expõe exploração read-only da biblioteca, escuta recente, rankings, cobertura e páginas de detalhe.

No frontend atual, `/music` concentra tanto o recorte editorial quanto o arquivo completo:

- `/music` mantém a capa editorial recente
- `/music?view=archive&kind=albums|artists|tracks` abre o arquivo principal
- `/music?q=...&kind=...` e `/music?year=...` reutilizam os mesmos endpoints read-only abaixo

## Endpoints

| Path | Params | Retorna |
| --- | --- | --- |
| `GET /api/music/summary` | `range=week|month|custom`, `start?`, `end?` | `MusicSummaryResponse` |
| `GET /api/music/stats` | - | `MusicStatsResponse` |
| `GET /api/music/year/{year}` | `limitAlbums=80`, `limitArtists=12`, `limitTracks=12` | `MusicByYearResponse` |
| `GET /api/music/recent-albums` | `limit=20`, `cursor?` | `RecentAlbumsPageResponse` |
| `GET /api/music/albums/rediscovered` | `limit=8` | `RediscoveredAlbumResponse[]` |
| `GET /api/music/library/artists` | `limit=20`, `cursor?` | `ArtistLibraryPageResponse` |
| `GET /api/music/library/albums` | `limit=20`, `cursor?` | `AlbumLibraryPageResponse` |
| `GET /api/music/library/tracks` | `limit=20`, `cursor?` | `TrackLibraryPageResponse` |
| `GET /api/music/search` | `q`, `limit=10` | `SearchResponse` |
| `GET /api/music/albums/{albumId}` | `albumId` | `AlbumPageResponse` |
| `GET /api/music/albums/{albumId}/musicbrainz/candidates` | `albumId` | Candidatos de release group sem alterar dados locais. |
| `GET /api/music/albums/{albumId}/musicbrainz/preview` | `albumId`, `releaseGroupMbid` | Prévia das mudanças e campos preservados. |
| `POST /api/music/albums/{albumId}/musicbrainz` | body com `releaseGroupMbid` | Confirma o vínculo e aplica ano ausente e termos. |
| `GET /api/music/artists/{artistId}` | `artistId` | `ArtistPageResponse` |
| `GET /api/music/artists/{artistId}/musicbrainz/candidates` | `artistId` | Candidatos de artista sem alterar dados locais. |
| `POST /api/music/artists/{artistId}/musicbrainz` | body com `artistMbid` | Confirma ou troca o vínculo do artista. |
| `GET /api/music/tracks/{trackId}` | `trackId` | `TrackPageResponse` |
| `GET /api/music/terms/{kind}/{slug}` | `kind=GENRE|TAG`, `slug` | `AlbumTermDetailsResponse` |
| `GET /api/music/terms/search` | `q`, `kind=GENRE|TAG`, `limit=10` | `AlbumTermSuggestionDto[]` |
| `POST /api/music/albums/{albumId}/terms` | body com `name`, `kind=GENRE|TAG` | `AlbumTermDto` |
| `POST /api/music/albums/{albumId}/terms/{termId}/visibility` | body com `hidden` | `AlbumTermDto` |
| `POST /api/music/terms/{termId}/visibility` | body com `hidden` | `AlbumTermDto` |
| `GET /api/music/admin/track-duplicates` | `limit=20`, `cursor?`, `includeIgnored=false`, `artist?`, `album?` | `DuplicateTrackReviewPageResponse` |
| `POST /api/music/admin/track-duplicates/ignore` | body com `albumId`, `groupKey`, `ignored=true` | vazio |
| `POST /api/music/admin/track-duplicates/merge` | body com `albumId`, `groupKey`, `targetTrackId`, `sourceTrackIds[]` | `DuplicateTrackMergeResponse` |
| `POST /api/music/admin/track-duplicates/merge-batch` | body com `merges[]` | `DuplicateTrackBatchMergeResponse` |
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
- `GET /api/music/admin/track-duplicates`

Trate `cursor` como opaco e envie exatamente o `nextCursor` retornado.

## Range temporal

`GET /api/music/summary` resolve o range assim:

- `week`: últimos 7 dias a partir de `now`
- `month`: últimos 30 dias a partir de `now`
- `custom`: exige `start` e `end`

Os endpoints de ranking e análise por período exigem `start` e `end` explícitos em ISO-8601 UTC.

`GET /api/music/year/{year}` resolve automaticamente a janela inteira do ano em UTC:

- início: `YYYY-01-01T00:00:00Z`
- fim: `YYYY-12-31T23:59:59Z`

`GET /api/music/albums/rediscovered` calcula redescobertas em relação ao momento da requisição. A primeira heurística é interna ao backend:

- janela recente: últimos 30 dias
- mínimo histórico: 5 plays antes da janela recente
- mínimo recente: 2 plays dentro da janela recente
- gap mínimo: 90 dias entre o último play histórico e o primeiro play recente

Os resultados são ordenados por força de redescoberta, usando um score interno simples: `quietGapDays * ln(historicalPlayCount + 1) * ln(recentPlayCount + 1)`. Esse score não é exposto no contrato; ele só prioriza retornos com gap longo, histórico relevante e atividade recente real.

Cada item retorna álbum, artista, capa, ano, contagens histórica/recente, último play histórico, primeiro play recente, play mais recente e `quietGapDays`.

## Termos de álbuns

Álbuns podem receber termos locais editáveis.

- `kind` aceita `GENRE` ou `TAG`
- `source` exposto atualmente é `USER`
- `hidden` em termo global oculta o termo em todos os álbuns
- `hidden` no vínculo álbum-termo oculta apenas naquele álbum
- termos ocultos continuam persistidos e podem ser reativados

## Observações de contrato

- páginas de artista, álbum e faixa retornam visão agregada do histórico, não eventos crus
- endpoints de coverage comparam catálogo conhecido com o que já foi ouvido
- `albums/rediscovered` opera só sobre histórico local já importado, sem chamadas a provedores externos
- `genres/recent` usa os últimos plays, não uma janela por data
- `stats` existe para sustentar navegação anual e visão consolidada da library
- `year/{year}` é centrado em álbuns; artistas e faixas entram como contexto editorial do mesmo período
- `AlbumPageResponse.rating` e `AlbumPageResponse.comments` podem incluir dados cross-domain de Ratings e Comments

## Invariantes

- playbacks são agregados para as respostas públicas
- rankings e análises por período exigem `start` e `end` explícitos quando o endpoint não tem `range`
- endpoints de termos de álbum operam sobre catálogo local
- artistas admitem no máximo um ID Spotify e um MBID de `artist`, armazenados diretamente em `artists`
- a identidade MusicBrainz canônica de um álbum é um único `release-group`, armazenado em `albums.musicbrainz_release_group_id`
- MBIDs de `release` e IDs de álbum Spotify são aliases técnicos `0..N`, armazenados em `album_musicbrainz_release_ids` e `album_spotify_ids`
- cada alias externo de álbum ou faixa pertence a uma única entidade local; as tabelas específicas garantem essa unicidade
- aliases de álbum servem para reconhecer ingestões e deduplicar edições que convergem no mesmo trabalho; eles não significam que o produto acompanhe uma edição comercial específica
- faixas admitem aliases Spotify e MBIDs de `recording` `0..N`, armazenados em `track_spotify_ids` e `track_musicbrainz_recording_ids`; nenhum alias válido deve ser descartado por haver mais de um
- ao consolidar faixas duplicadas, todos os aliases externos das faixas de origem são transferidos para a faixa vencedora
- busca e prévia MusicBrainz são read-only e toda correspondência exige confirmação do owner
- o enriquecimento não sobrescreve título, artista, capa, tracklist ou ano já preenchido
- `POST /api/music/musicbrainz/artists` cria ou reutiliza um artista somente após validar o MBID no MusicBrainz
- `GET /api/music/artists/{artistId}/musicbrainz/discography` é uma prévia read-only que classifica release groups
- `POST /api/music/artists/{artistId}/musicbrainz/discography/import` aceita até 50 MBIDs pertencentes ao artista e cria somente itens classificados como ausentes
- possíveis correspondências locais nunca são mescladas automaticamente durante a importação de discografia

## Non-goals

- este contrato não cobre importação Spotify/Plex; endpoints operacionais ficam em `operations-api.md`
- termos de álbum não documentam enriquecimento externo automático
- importação de discografia não escolhe release, capa ou tracklist
- o modelo atual não escolhe qual alias Spotify fornece a tracklist principal nem mescla automaticamente tracklists de edições distintas
- associação manual de aliases Spotify, revisão de convergência entre edições e escolha da tracklist principal pertencem a uma evolução futura própria

## Critérios de aceite

- endpoints documentados existem em `MusicSummaryController` ou `MusicDuplicateReviewController`
- DTOs citados existem em `api/music`
- cursor é tratado como contrato opaco

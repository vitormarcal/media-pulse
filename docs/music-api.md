# Music API

## Artistas na ingestão Spotify

A ingestão de reproduções recentes resolve o álbum pelo primeiro artista com nome não vazio em `album.artists` e a faixa pelo primeiro artista com nome não vazio em `track.artists`. Se uma lista não tiver artista válido, usa o artista da outra; se ambas estiverem ausentes ou inválidas, usa `Unknown`, sem associar IDs de entradas sem nome. A capa usa o artista resolvido para o álbum.

Antes dessa separação, o artista da faixa também era usado para resolver o álbum, podendo atribuir compilações ou colaborações ao artista errado. A mudança vale para novas resoluções: IDs externos existentes continuam prioritários, sem reatribuir automaticamente artistas de registros históricos. Não há migração nem mudança de contrato HTTP. O vínculo legado ainda persiste um artista por álbum e por faixa; os créditos adicionais de álbum estão descritos abaixo.

Para validar em produção, importar uma faixa cujo artista difere do artista do álbum e conferir ambos no catálogo, preferindo um álbum ainda não importado. Reprocessar o mesmo evento deve manter IDs e quantidade de reproduções. Álbuns já existentes com atribuição incorreta precisam de revisão separada; reimportar não os corrige. Conferir também um álbum solo. Plano das próximas versões: [múltiplos artistas por álbum](features/album-artist-credits.md).

## Créditos de artistas do álbum (versão 2)

A migração `V68__add_album_artist_credits.sql` cria `album_artists` e preenche cada álbum existente com seu artista atual, posição zero e origem `LEGACY`. Novos álbuns criados pela ingestão canônica ou importação de discografia recebem o mesmo vínculo inicial. Uma leitura sem créditos armazenados retorna o artista legado como fallback.

`GET /api/music/albums/{albumId}` acrescenta `artists[]`, ordenado por `position`, com `artistId`, `artistName` (nome canônico), `creditedName`, `joinPhrase` e `source` (`LEGACY`, `SPOTIFY` ou `MUSICBRAINZ_CONFIRMED`). `artistId` e `artistName` no nível do álbum permanecem com o significado anterior, assim como IDs, fingerprints, histórico e rankings. A página do álbum apresenta os créditos com links individuais. Biblioteca, busca e discografias por todos os artistas estão disponíveis a partir da etapa 4, descrita abaixo.

Candidatos e prévias MusicBrainz acrescentam `candidate.artists[]` (na busca, cada item tem `artists[]`), com `artistMbid`, `artistName`, `creditedName`, `joinPhrase` e `position`. Os campos singulares dos candidatos continuam representando o primeiro crédito por compatibilidade. `preservedFields` da prévia passa a ser `title`, `artistId`, `coverUrl`, `tracklist`: o vínculo legado é preservado, mas os créditos ordenados são substituídos após confirmação. A prévia mostra todos os nomes creditados e separadores antes de salvar.

Antes, a confirmação vinculava o primeiro MBID ao artista legado, mesmo quando representavam pessoas diferentes. Agora, cada crédito resolve o artista pelo MBID; sem vínculo, reutiliza o nome canônico local se não houver conflito de MBID ou cria o artista. Homônimos com outra identidade retornam `409` sem aplicar parcialmente o enriquecimento. Não são baixados perfis completos dos novos artistas nesse fluxo. Uma lista vazia não apaga créditos existentes; créditos incompletos retornam `409`. A consulta externa ocorre antes da transação; vínculo do release group, artistas, créditos, ano e termos são aplicados juntos. Reaplicação é idempotente.

Mesclagens de artistas transferem as referências dos créditos. Nas etapas 2 e 3, mesclar álbuns retorna `409` se um álbum de origem tiver créditos de provedor; mantenha como principal o único álbum com esses créditos. Isso evita apagar seus créditos enquanto a conciliação não está implementada.

Validação em produção: abrir um álbum conhecido de dois artistas, acessar **Enriquecer dados**, selecionar o release group correto, conferir os dois créditos na prévia e confirmar. Ambos devem aparecer no cabeçalho com links próprios e na ordem do MusicBrainz. Repetir a confirmação não deve criar artistas ou posições extras. Conferir que IDs, reproduções, título, capa e tracklist permanecem, assim como o funcionamento de um álbum antigo sem enriquecimento. A migração V68 é aditiva; rollback para a versão 1 mantém a tabela e os créditos, mas aquela versão não os exibe. Não remover a tabela ao voltar o código. Para a nova origem introduzida pela V69, consultar a limitação de rollback da versão 3 abaixo.

Testes: `JAVA_TOOL_OPTIONS=-Dapi.version=1.44 ./server/gradlew -p server test --tests '*AlbumArtistCreditsIntegrationTest' --tests '*MusicBrainzPageEnrichmentServiceTest' --tests '*CanonicalizationServiceTest' --tests '*MusicQueryRepositoryTest' --tests '*AlbumMerge*' --tests '*ArtistMerge*'`. A opção de API é necessária neste ambiente com Docker recente, conforme o padrão já documentado nos outros domínios; conferir zero testes ignorados no relatório de integração.

## Créditos automáticos Spotify e preservação no Plex (versão 3)

A migração `V69__allow_spotify_album_artist_credits.sql` amplia a restrição de origem em `album_artists` para aceitar `SPOTIFY`, preservando os dados existentes. A ingestão recente grava todos os artistas de `track.album.artists` na ordem recebida. IDs externos continuam reconhecendo o álbum canônico; não há reatribuição do vínculo legado ou alteração de fingerprints. Como o Spotify não fornece separadores editoriais, a exibição usa `, ` entre os artistas e uma string vazia após o último.

A atualização automática exige ID e nome de álbum e uma lista de artistas não vazia, com ID e nome válidos em todas as entradas. Ausência ou entrada inválida preserva os créditos atuais; não se usa a lista de artistas da faixa como lista do álbum. A primeira lista válida substitui `LEGACY`. Uma lista automática posterior pode reordenar e acrescentar artistas, mas é ignorada se omitir qualquer artista anteriormente creditado pelo Spotify. Listas confirmadas no MusicBrainz têm prioridade: o Spotify não as altera nem cria artistas adicionais para elas.

A resolução usa ID Spotify e depois nome ou alias local sem conflito de identidade. Se um nome local estiver vinculado a outro ID Spotify, se dois IDs diferentes tiverem o mesmo nome normalizado ou se resolverem para a mesma entidade local, a atualização dos créditos é ignorada antes das gravações; a ingestão da reprodução segue seu fluxo normal. Artistas já resolvidos mantêm nome canônico e MBID; nomes do payload são preservados nos créditos.

Plex (biblioteca e webhook), arquivos de histórico estendido do Spotify e preenchimento de tracklist não oferecem a mesma lista de artistas do álbum nos fluxos atuais. Continuam apenas inicializando o vínculo `LEGACY` quando faltam créditos, sem substituir listas completas. A importação de biblioteca Plex pode reconhecer o mesmo álbum por release/release group MusicBrainz conhecido. O webhook atual não fornece identidade de álbum compartilhada nesse fluxo: reconhece por metadados legados; sem correspondência suficiente pode gerar outro registro, que exige revisão. Não se promete mesclagem automática por semelhança.

Na etapa 3, a proteção da mesclagem de álbuns também abrange origens `SPOTIFY`: um álbum de origem com créditos de provedor não pode ser eliminado pela mesclagem. Pode-se manter como principal o único álbum que tenha esses créditos.

Validação em produção:

1. Reproduzir uma faixa de álbum colaborativo no Spotify e aguardar a ingestão recente. O cabeçalho do álbum deve mostrar todos os artistas de `album.artists`, e `GET /api/music/albums/{id}` deve retornar `source: SPOTIFY` em seus créditos.
2. Reimportar o mesmo evento: IDs, créditos e quantidade de reproduções devem permanecer.
3. Importar a biblioteca Plex com a correspondência MusicBrainz já estabelecida para o mesmo álbum: não deve criar outro álbum nem remover créditos. Conferir também um webhook cuja combinação de artista legado e álbum corresponda ao registro existente.
4. Confirmar os créditos pelo MusicBrainz, reproduzir novamente pelo Spotify e verificar que nomes, separadores e ordem confirmados são preservados.

Os testes automatizados cobrem listas parciais, conflitos e a prioridade da confirmação manual, sem depender de alterar payloads reais. Executar os testes de créditos junto de `*SpotifyPlaybackServiceTest`, `*SpotifyExtendedPlaybackServiceTest`, `*PlexMusic*Test` e `*AlbumMergeRepositoryTest`.

Rollback: a beta.2 não reconhece o novo valor `SPOTIFY` no enum. Depois de gravar essa origem, voltar apenas seu binário pode falhar ao ler os álbuns. Usar uma build de compatibilidade que reconheça a origem ou restaurar o backup anterior à implantação; preservar a migração e os créditos se optar pela build de compatibilidade. Não renomear origens para `LEGACY` como atalho, pois isso perde a procedência e suas proteções.

## Navegação por créditos e mesclagem (etapa 4)

Sem nova migração. Discografias locais e suas contagens de álbuns usam `album_artists`: um álbum aparece uma vez para cada artista creditado, mesmo se esse artista estiver repetido em posições editoriais diferentes. O vínculo legado é usado apenas se não houver créditos armazenados. A seleção de discos no MusicBrainz também considera esses álbuns locais ao classificar possíveis correspondências, evitando sugerir sua criação apenas por pertencerem originalmente a outro artista legado.

Os contratos acrescentam `artists[]` aos itens de biblioteca de álbuns, busca de álbuns, discografia do artista, listas e candidatos de mesclagem. O formato é o mesmo do detalhe (`AlbumArtistCredit` no OpenAPI); campos singulares continuam representando o vínculo legado. Os créditos são lidos em lote. A biblioteca e a busca exibem os nomes completos; listas oferecem links individuais para artistas. A busca e os filtros administrativos consideram nomes canônicos e nomes creditados. O catálogo administrativo continua agrupado pelo artista legado, mas permite selecionar álbuns entre grupos.

Consultas de associação usam `EXISTS`, sem multiplicar paginação ou contagens por crédito. A discografia contabiliza reproduções pelas respectivas faixas do álbum, evitando multiplicá-las pelo número de faixas da tracklist. Estatísticas pessoais e rankings de artistas mantêm suas regras anteriores; o crédito de álbum não atribui automaticamente todas as reproduções a cada colaborador. Os demais painéis editoriais e rankings mantêm sua apresentação atual.

Na prévia e na confirmação de mesclagem, `artistCreditsFromAlbumId` escolhe de qual álbum preservar a lista completa, incluindo ordem, nomes, separadores e origem. A prévia retorna esse ID e `artists[]`. Sem escolha, a recomendação prioriza `MUSICBRAINZ_CONFIRMED`, depois `SPOTIFY`, depois `LEGACY`; entre prioridades iguais, prefere mais artistas distintos e depois o principal. Listas diferentes geram aviso na prévia e exigem escolha explícita na confirmação (`409` se omitida). Uma seleção fora dos candidatos retorna `400`. A interface preenche a recomendação, permite revisá-la e exige a confirmação já existente da mesclagem definitiva. As demais listas de créditos são substituídas pela escolhida, sem união automática que altere seus créditos editoriais.

Álbuns com artistas legados diferentes podem ser mesclados quando todos compartilham ao menos um artista creditado. Se o artista legado é o mesmo, a regra anterior continua válida. O principal mantém seu vínculo legado; a operação transfere créditos escolhidos antes de eliminar as origens, além de preservar histórico, listas, marcações e identificadores conforme as regras existentes. A leitura final e a gravação compartilham locks de álbum com o enriquecimento.

Validação em produção: abrir *Elis & Tom* pelas páginas de Elis Regina e Antônio Carlos Jobim; o mesmo ID deve aparecer em ambas. Buscar por qualquer artista deve retornar o álbum uma única vez. Conferir os dois créditos na biblioteca e em uma lista. Na revisão de duplicados, verificar candidatos e créditos finais na prévia e escolher sua origem antes de confirmar uma mesclagem desejada. Os testes em PostgreSQL cobrem essa mesclagem e verificam que histórico e lista permanecem sem duplicação. Não é necessário mesclar registros reais apenas para validar a navegação.

Rollback para a beta.3 preserva o banco e os créditos escolhidos; a interface e a navegação voltam às limitações daquela versão. Não há rollback automático de mesclagens definitivas.

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
| `POST /api/music/albums/{albumId}/musicbrainz/candidates` | `albumId` | Candidatos de release group sem alterar dados locais. |
| `POST /api/music/albums/{albumId}/musicbrainz/preview` | `albumId`, `releaseGroupMbid` | Prévia das mudanças e campos preservados. |
| `POST /api/music/albums/{albumId}/musicbrainz` | body com `releaseGroupMbid` | Confirma o vínculo e aplica ano ausente e termos. |
| `GET /api/music/artists/{artistId}` | `artistId` | `ArtistPageResponse` |
| `POST /api/music/artists/{artistId}/musicbrainz/candidates` | `artistId` | Candidatos de artista sem alterar dados locais. |
| `POST /api/music/artists/{artistId}/musicbrainz` | body com `artistMbid` | Confirma ou troca o vínculo do artista. |
| `POST /api/music/artists/{artistId}/musicbrainz/refresh` | `artistId` | Atualiza individualmente o snapshot MusicBrainz do artista. |
| `POST /api/music/artists/{artistId}/genres` | body com `name`, `kind=GENRE` | Adiciona um gênero manual ao artista. |
| `POST /api/music/artists/{artistId}/genres/{termId}/visibility` | body com `hidden` | Oculta ou restaura o gênero naquele artista. |
| `GET /api/music/tracks/{trackId}` | `trackId` | `TrackPageResponse` |
| `GET /api/music/terms/{kind}/{slug}` | `kind=GENRE|TAG`, `slug` | `AlbumTermDetailsResponse` |
| `GET /api/music/terms/search` | `q`, `kind=GENRE|TAG`, `limit=10` | `AlbumTermSuggestionDto[]` |
| `POST /api/music/albums/{albumId}/terms` | body com `name`, `kind=GENRE|TAG` | `AlbumTermDto` |
| `POST /api/music/albums/{albumId}/terms/{termId}/visibility` | body com `hidden` | `AlbumTermDto` |
| `POST /api/music/terms/{termId}/visibility` | body com `hidden` | `AlbumTermDto` |
| `GET /api/admin/music/track-duplicates` | `limit=20`, `cursor?`, `includeIgnored=false`, `artist?`, `album?` | `DuplicateTrackReviewPageResponse` |
| `POST /api/admin/music/track-duplicates/ignore` | body com `albumId`, `groupKey`, `ignored=true` | vazio |
| `POST /api/admin/music/track-duplicates/merge` | body com `albumId`, `groupKey`, `targetTrackId`, `sourceTrackIds[]` | `DuplicateTrackMergeResponse` |
| `POST /api/admin/music/track-duplicates/manual-merge` | body com `albumId`, `targetTrackId`, `sourceTrackIds[]` | Mescla faixas escolhidas manualmente após validar que todas pertencem ao álbum. |
| `POST /api/admin/music/track-duplicates/merge-batch` | body com `merges[]` | `DuplicateTrackBatchMergeResponse` |
| `GET /api/admin/music/album-duplicates` | `limit=50`, `artist?`, `album?` | Sugestões de álbuns com artista legado ou crédito compartilhado, por título ou sobreposição de faixas. |
| `GET /api/admin/music/album-duplicates/catalog` | `q`, `limit=100` | Catálogo agrupado por artista para mesclagem manual. |
| `POST /api/admin/music/album-duplicates/preview` | body com `targetAlbumId`, `sourceAlbumIds[]`, `trackOrderFromAlbumId?`, `artistCreditsFromAlbumId?` | Prévia, posições preservadas, conflitos e avisos da mesclagem definitiva. |
| `POST /api/admin/music/album-duplicates/merge` | body com principal, fontes e origem de título/capa/ano/avaliação/ordem das faixas | Consolida os álbuns no principal. |
| `GET /api/admin/music/artist-duplicates` | `limit=50`, `artist?` | Sugere artistas por nome normalizado. |
| `GET /api/admin/music/artist-duplicates/catalog` | `q`, `limit=100` | Busca artistas para uma seleção manual. |
| `POST /api/admin/music/artist-duplicates/preview` | body com `targetArtistId` e `sourceArtistIds[]` | Mostra candidatos, totais e avisos antes do merge definitivo. |
| `POST /api/admin/music/artist-duplicates/merge` | body com principal, fontes, origem de nome/foto/MusicBrainz/avaliação e aliases preservados | Consolida artistas no principal. |
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
- `GET /api/admin/music/track-duplicates`

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

Os resultados são ordenados por força de redescoberta, usando um score interno simples: `quietGapDays * ln(historicalPlayCount + 1) * ln(recentPlayCount + 1)`. Esse score não é exposto no contrato; ele só prioriza retornos com gap longo, histórico relevante e atividade recente real. Empates são resolvidos por gap, plays históricos, plays recentes, play mais recente e ID do álbum.

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
- a mesclagem manual de álbuns é definitiva, exige o mesmo artista legado ou ao menos um artista creditado em comum a todos, preserva a lista de créditos escolhida e nunca mescla faixas homônimas automaticamente
- a mesclagem manual de artistas é definitiva; transfere álbuns, faixas e comentários, une gêneros e não mescla álbuns ou faixas automaticamente
- nome, foto, perfil MusicBrainz e avaliação são escolhidos independentemente; cada nome não canônico, inclusive o nome anterior do artista principal, só vira alias quando explicitamente marcado
- aliases de nome preservados em mesclagens anteriores sempre acompanham o artista absorvido
- aliases de nome de artista participam da canonicalização depois dos identificadores externos e do nome canônico
- identificadores externos divergentes impedem o merge de artistas e devem ser resolvidos antes da confirmação
- a tracklist escolhida na mesclagem de álbuns tem prioridade; posições completas das demais edições são preservadas quando livres e ficam nulas quando conflitam
- títulos absorvidos são preservados em `album_title_aliases`; Spotify IDs, releases e release groups MusicBrainz também continuam resolvendo para o álbum principal
- a canonicalização de álbuns prioriza release group, release e Spotify ID; aliases de título são usados depois dos identificadores externos
- busca e prévia MusicBrainz usam `POST` por consultarem um provedor externo, permanecem sem escrita local e toda correspondência exige confirmação do owner
- o enriquecimento não sobrescreve título, artista, capa, tracklist ou ano já preenchido
- `POST /api/music/musicbrainz/artists` cria ou reutiliza um artista somente após validar o MBID no MusicBrainz
- `POST /api/music/artists/{artistId}/musicbrainz/discography` é uma prévia sem escrita local que classifica release groups
- `POST /api/music/artists/{artistId}/musicbrainz/discography/import` aceita até 50 MBIDs pertencentes ao artista e cria somente itens classificados como ausentes
- possíveis correspondências locais nunca são mescladas automaticamente durante a importação de discografia
- confirmar ou trocar o vínculo de um artista importa automaticamente tipo, país/áreas, período, desambiguação, aliases, gêneros e links curados do MusicBrainz
- o snapshot de identidade do artista é somente leitura e pode ser atualizado individualmente; a página nunca consulta o provedor durante leitura
- aliases e links são substituídos pelo snapshot mais recente; gêneros são cumulativos e preservam inclusões manuais e preferências de ocultação
- links externos de artista são limitados a site oficial, Wikipedia, Discogs e Bandcamp, com preferência por Wikipedia em português e depois inglês
- artistas sem foto tentam resolver automaticamente MusicBrainz → Wikidata → imagem principal `P18` → Wikimedia Commons durante vínculo ou atualização individual
- a foto é validada, baixada sob `/covers/wikimedia/artists/{artistId}/...` e nunca substituída automaticamente; a capa de álbum permanece como fallback
- falhas da imagem são persistidas separadamente e não impedem a atualização dos demais dados MusicBrainz; autoria, licença e URLs disponíveis ficam no snapshot local

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

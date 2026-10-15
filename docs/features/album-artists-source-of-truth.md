# Créditos do álbum como fonte única

Status: proposta documentada a pedido do owner; não implementada. Continuação das [cinco etapas entregues na 1.4.0](album-artist-credits.md). As etapas abaixo não são versões já publicadas e ainda não têm numeração de release ou de migração.

## Problema

Na 1.4.0, `album_artists` armazena os créditos ordenados, mas `albums.artist_id` continua representando o artista único legado. A migração V68 copiou esse artista sem validar sua correção. Confirmar créditos no MusicBrainz ou recebê-los do Spotify não reatribui o campo antigo.

Por exemplo, Elis & Tom pode ter Elis Regina em `albums.artist_id` e Elis Regina e Antônio Carlos Jobim em `album_artists`. Isso é esperado durante a transição. Um artista legado ausente dos créditos novos pode indicar uma associação histórica incorreta, mas a divergência sozinha não comprova erro.

O campo legado ainda participa de caminhos de leitura/escrita e reconhecimento do álbum. O fingerprint atual depende de título normalizado e artista único; aliases de título também possuem escopo de artista. Alterar os créditos visíveis não corrige essas estruturas, duplicatas ou associações históricas. IDs externos conhecidos têm prioridade no reconhecimento; sem eles, os metadados legados continuam relevantes.

## Objetivo

Usar `album_artists` como única fonte dos créditos do álbum e remover a dependência de `albums.artist_id`, mantendo o ID local estável, a procedência e o histórico. Créditos editoriais, reconhecimento do álbum e atribuição de reproduções devem ter regras explícitas e independentes.

## Escopo

Quatro entregas incrementais, cada uma validável em produção antes da seguinte:

| Etapa | Entrega | Validação em produção |
| --- | --- | --- |
| 1 — Leituras | Inventariar consumidores do campo antigo e migrar leituras de créditos para `album_artists`, incluindo agrupamentos administrativos. Identificar separadamente consumidores de identidade e estatísticas. | Conferir álbuns solo, colaborativos, clássicos e legados. Mesmo ID em todos os artistas creditados; busca, paginação e contagens sem duplicação. |
| 2 — Identidade | Desacoplar reconhecimento e unicidade do artista único. Manter prioridade dos IDs externos; definir e testar o tratamento de álbuns sem IDs, fingerprints antigos e aliases de título. | Reimportar Spotify e Plex; reordenar ou confirmar créditos pelo MusicBrainz sem mudar o ID local nem criar outro álbum. Casos ambíguos precisam de revisão. |
| 3 — Escritas e legado | Migrar criação, ingestão, enriquecimento e mesclagem para a relação. Expor prévia das lacunas, divergências e colisões do acervo; corrigir somente casos verificáveis. Manter a coluna antiga apenas enquanto necessária à compatibilidade da transição. | Criar e reimportar álbuns, mesclar candidatos revisados e conferir histórico, listas, IDs externos e créditos. Repetir operações sem duplicação. |
| 4 — Remoção | Após eliminar dependências, remover `albums.artist_id` e adaptar ou retirar fingerprints, restrições, índices e consultas que dependam dele. | Validar restauração de backup, implantação com Flyway e ingestões em base migrada; conferir invariantes e amostras do catálogo antes/depois. |

As etapas 2 a 4 podem exigir migrações Flyway. Números e SQL serão definidos na implementação, após inventário das dependências. Preferir mudanças aditivas e compatíveis antes da remoção final. Nenhuma migração é criada por esta proposta.

Principais áreas a revisar: modelo `Album`, `CanonicalizationService`, `FingerprintUtil`, repositórios de álbuns/artistas e consultas musicais, `album_title_aliases`, serviços de créditos, ingestão Spotify/Plex/histórico estendido, discografia MusicBrainz, mesclagens, backfill e desfazimento, DTOs e consumidores frontend. Documentação: spec existente, `docs/music-api.md` e OpenAPI se o contrato mudar.

## API/UI

- `artists[]` deve se tornar a referência dos créditos em todos os consumidores.
- Os campos singulares `artistId` e `artistName` hoje representam o vínculo legado. Não passar silenciosamente a representar o primeiro crédito: decidir depreciação, remoção ou projeção documentada, com migração coordenada dos consumidores.
- A revisão administrativa deve explicar divergências e mostrar antes/depois e colisões antes de qualquer correção. Não pressupor que o primeiro crédito é o artista correto para identidade ou estatísticas.
- Reutilizar os padrões administrativos existentes e seguir `DESIGN.md` quando houver implementação visual. Rotas e contratos novos serão definidos na descoberta da etapa correspondente.

## Non-goals

- Múltiplos artistas por faixa e funções como compositor, regente ou intérprete.
- Distribuir automaticamente uma reprodução para todos os artistas do álbum.
- Corrigir associações MusicBrainz antigas somente por haver um ID armazenado.
- Mesclar álbuns automaticamente por título ou por semelhança de créditos.
- Criar novos provedores, dependências, filas ou infraestrutura.

## Heurísticas/regras

- O ID local do álbum deve permanecer estável após alterações de nomes, ordem, quantidade ou origem dos créditos.
- Não substituir o fingerprint atual por um hash do primeiro crédito ou da lista completa: reordenação e divergências entre provedores não podem, por si, criar outra identidade de álbum.
- IDs Spotify, releases e release groups MusicBrainz são evidências de reconhecimento. Vínculos históricos incorretos precisam de revisão; esta migração não os torna automaticamente confiáveis.
- Sem identidade externa suficiente, uma possível correspondência não autoriza mesclagem automática. A regra concreta de reconhecimento permanece uma decisão da etapa 2.
- Preservar nomes creditados, separadores, ordem e origem, inclusive posições repetidas para o mesmo artista. Manter a proteção das confirmações MusicBrainz e dos créditos completos contra payloads parciais.
- Créditos `LEGACY` continuam válidos como informação histórica disponível. Não exigir consulta externa de todo o acervo para concluir a migração e não dividir nomes por vírgula ou `&`.
- Lacunas, artistas legados ausentes dos créditos e possíveis colisões devem ser relatados separadamente. Correções e mesclagens exigem revisão, sem reescrever reproduções silenciosamente.
- Estatísticas precisam ser inventariadas por consulta: manter a atribuição existente baseada nas reproduções/faixas quando aplicável. Não fazer joins que multipliquem escutas pelo número de créditos. Mudança de métrica exige decisão explícita e documentação.
- A nova estrutura deve preservar as garantias de revisão, locks, auditoria e desfazimento dos lotes existentes. Mudanças que invalidem lotes antigos precisam de tratamento documentado antes da implantação.
- Antes da remoção da coluna, verificar todos os caminhos de escrita e a inexistência de dependências executáveis. Não manter uma segunda fonte permanente de créditos sob outro nome.
- Rollback de código só é possível enquanto o schema e as escritas forem compatíveis. Após remover a coluna, o binário 1.4.0 não é compatível; preparar backup e restauração testada ou uma estratégia explícita de compatibilidade, sem prometer reversão automática.

## Acceptance criteria mínimos

- Cada etapa tem testes proporcionais, procedimento de piloto, critérios de avanço e rollback documentados antes da implantação.
- Álbum solo, Elis & Tom, álbum clássico com vários créditos, compilação, álbum sem IDs externos e registro com legado divergente estão cobertos.
- Alterar créditos ou alternar ingestões Spotify/Plex e confirmação MusicBrainz preserva o ID local e não duplica álbuns ou reproduções nos casos com identidade suficiente.
- Busca, biblioteca, discografias, listas e agrupamentos administrativos usam os créditos sem duplicar itens ou totais.
- A reconciliação demonstra preservação de IDs, reproduções, listas, avaliações, comentários, capas e vínculos externos, exceto correções explicitamente revisadas.
- A etapa final não possui dependência de `albums.artist_id`; contratos singulares têm destino explícito e consumidores migrados.
- Para estatísticas, totais e atribuições anteriores são comparados antes/depois; diferenças precisam de explicação e aprovação da regra correspondente.
- Permanecem pendentes antes de implementar as respectivas etapas: regra de identidade sem IDs externos, destino dos campos singulares da API e eventual mudança de atribuição estatística. Esta spec não escolhe essas regras implicitamente.

# Múltiplos artistas por álbum

Status: spec aprovada. Beta.3 validada pelo owner com Elis & Tom. Beta.4 validada pelo owner. Etapa 5 implementada, com validação em produção pendente.

## Problema

O catálogo representa apenas um artista por álbum. Spotify e MusicBrainz fornecem vários créditos, mas a ingestão não os preserva integralmente. A ingestão Spotify também usa o primeiro artista da faixa para resolver o álbum, confundindo créditos de faixa e de álbum.

## Objetivo

Preservar localmente os artistas creditados no álbum e permitir sua exibição e navegação, com versões independentes e verificáveis em produção, preservando IDs e histórico.

## Escopo

1. **Versão 1 — separar artista de álbum e faixa:** resolver o álbum pelo primeiro artista válido de `album.artists` e a faixa pelo primeiro artista válido de `track.artists`. Definir fallback para payloads incompletos preservando a ingestão. Não corrigir automaticamente registros antigos. Sem migração.
2. **Versão 2 — armazenar e mostrar créditos:** criar `album_artists` com artista, posição, nome creditado, separador e origem; preencher os vínculos existentes a partir de `albums.artist_id`. Preservar essa coluna e os contratos atuais. Permitir aplicar todos os créditos pela prévia e confirmação do enriquecimento MusicBrainz. Migração Flyway aditiva.
3. **Versão 3 — automatizar Spotify e preservar no Plex:** persistir todos os artistas do álbum recebidos do Spotify, mantendo ordem e idempotência. Importações parciais do Plex não removem créditos completos. A migração `V69` amplia a restrição de origem para aceitar `SPOTIFY`; a necessidade foi identificada ao implementar esta versão.
4. **Versão 4 — navegar por todos os artistas:** atualizar discografias, busca, filtros, biblioteca, listas e mesclagem de álbuns. Preservar créditos na mesclagem, sem duplicar resultados. Índices adicionais somente se necessários. Rankings mantêm a regra atual.
5. **Versão 5 — completar o acervo existente:** processar lotes pequenos com prévia, usando identificadores externos armazenados. Registrar alterações por álbum para retomada, auditoria e reversão. Conflitos exigem revisão. Migração aditiva `V70`: auditoria dos lotes e revisão dos créditos para proteger aplicação e desfazimento.

Cada versão é implantada e validada em produção antes da seguinte. A versão 3 introduz o valor de origem `SPOTIFY`; após gravá-lo, rollback para a beta.2 exige uma build de compatibilidade que reconheça esse valor ou restauração do backup anterior. Não basta voltar o binário da beta.2, cujo enum não reconhece essa origem. Voltar a aplicação deve preservar as estruturas aditivas e os dados coletados; isso não desfaz alterações já persistidas. Correções da versão 1 podem exigir ajuste pontual, e os lotes da versão 5 devem permitir desfazer seus próprios vínculos adicionados.

## API/UI

- Versão 1: sem mudança de contrato ou interface.
- Versão 2: adicionar `artists[]` aos contratos relevantes sem remover os campos atuais; mostrar créditos ordenados e links no detalhe do álbum e na prévia MusicBrainz.
- Versão 3: reutilizar os fluxos de importação existentes.
- Versão 4: propagar créditos para a navegação e revisão de duplicados existentes.
- Versão 5: página `/music/admin/album-artist-backfills` para seleção automática ou busca de álbuns, prévia, aplicação, resultados por álbum, recuperação de lotes e desfazimento; usa as APIs administrativas existentes.
- Seguir `DESIGN.md` e os componentes existentes em qualquer alteração visual.

Principais arquivos e áreas: `SpotifyPlaybackService`, `CanonicalizationService`, `MusicBrainzPageEnrichmentService`, DTOs dos provedores, modelos e repositórios musicais, consultas e serviços de duplicados, contratos da API, páginas/componentes de música, migrações Flyway e documentação de música/OpenAPI.

## Non-goals

- Múltiplos artistas por faixa nesta sequência.
- Atribuir todas as reproduções de um álbum a cada artista creditado.
- Remover `albums.artist_id` ou redefinir globalmente fingerprints nesta sequência.
- Novos provedores, dependências ou frameworks.
- Mesclar automaticamente álbuns históricos por semelhança de nome.
- Inferir os artistas do álbum pela união dos artistas das faixas.

## Heurísticas/regras

- Créditos do álbum e da faixa são conceitos distintos; compilações não tornam todos os participantes das faixas autores do álbum.
- Preservar identidade e ordem dos artistas; não separar nomes por vírgula, `&` ou outros caracteres para inferir entidades.
- Reutilizar identificadores externos para reconhecer álbuns. Ausência de identidade suficiente não autoriza mesclagem automática.
- Créditos confirmados pelo usuário no MusicBrainz não são substituídos por importações automáticas.
- Ausência ou parcialidade de dados de um provedor não autoriza apagar créditos completos.
- Spotify só atualiza créditos quando o payload contém ID e nome do álbum e uma lista não vazia de artistas com IDs e nomes válidos. Usa apenas `album.artists`, nunca artistas da tracklist ou do histórico estendido.
- A primeira lista válida do Spotify pode substituir créditos `LEGACY`. Listas seguintes podem reordenar, atualizar nomes e acrescentar artistas, mas não omitir artistas locais já creditados pelo Spotify. A confirmação MusicBrainz tem prioridade e pode substituir a lista automática.
- Identidades Spotify conflitantes ou homônimos incompatíveis com a unicidade de nomes do catálogo preservam os créditos atuais; a atualização automática é ignorada sem criar artistas parcialmente. Separadores de exibição são `, ` entre créditos do Spotify, que não fornece join phrases.
- `albums.artist_id` permanece como vínculo legado de compatibilidade e pode diferir do primeiro crédito confirmado. Créditos não reatribuem esse campo ou fingerprints. Rankings e identidade mantêm suas regras anteriores; a navegação passa a usar os créditos na etapa 4.
- A mesclagem de artistas transfere os créditos preservando nomes e posições. Na etapa 4, a mesclagem de álbuns permite escolher uma lista editorial completa de um dos álbuns. A recomendação prioriza MusicBrainz confirmado, depois Spotify, depois legado; entre listas de mesma origem, prefere mais artistas distintos e, no empate, o principal.
- Créditos divergentes exigem seleção explícita no pedido de mesclagem. A prévia mostra os créditos finais e avisa que as outras listas serão substituídas; não se concatenam listas editoriais automaticamente. A origem da lista escolhida é preservada.
- Álbuns com artistas legados diferentes podem ser mesclados se compartilharem ao menos um artista creditado. O principal mantém seu artista legado e sua identidade.
- Discografias, contagens de álbuns na biblioteca e busca usam os créditos com fallback legado apenas quando não houver lista armazenada. A busca considera nomes canônicos e creditados. Leitura em lote e `EXISTS` evitam resultados repetidos; estatísticas de reprodução e rankings de artistas não recebem todas as escutas dos álbuns colaborativos.
- Não há restrição de unicidade por artista no álbum: o mesmo artista pode aparecer em mais de uma posição de um crédito editorial.
- A origem deve distinguir o vínculo migrado dos créditos recebidos dos provedores e confirmados pelo usuário.
- Atualizações devem ser transacionais e idempotentes.
- A etapa 5 tem página administrativa em `/music/admin/album-artist-backfills`, usando as APIs de lote. Não há agendamento ou fila novos. Aceita até 10 IDs locais ou seleciona até 10 álbuns legados com ID externo conhecido; o padrão da API é 5 e o da página é 1. O lote usa release group canônico MusicBrainz quando disponível, senão um alias Spotify conhecido. Não faz busca por nome nem adivinha edições.
- Prévia persiste somente auditoria; aplicação exige um segundo pedido e usa a proposta armazenada, sem nova consulta externa. Álbuns com créditos de provedor já preenchidos são ignorados. Uma confirmação de lote MusicBrainz registra créditos `MUSICBRAINZ_CONFIRMED`.
- Cada álbum tem transação e status próprios. Repetir aplicação não reaplica sucessos e pode retomar erros de gravação com proposta válida; erros de consulta, conflitos ou propostas pendentes exigem nova prévia.
- Desfazer restaura apenas a lista anterior de créditos quando a lista, revisão e vínculo externo ainda coincidem com o resultado do lote. Não apaga artistas criados nem desfaz IDs externos associados a eles. Confirmações manuais posteriores, mesmo com créditos iguais, incrementam a revisão e impedem o desfazimento antigo.
- Mesclagens incrementam revisões de créditos; auditoria não possui FK para álbuns, preservando seus registros após exclusão por mesclagem. Sem atualização em massa de IDs, histórico, título, capa, ano, termos ou tracklist.
- Atualizar a spec existente de enriquecimento MusicBrainz quando a versão 2 alterar sua regra atual de preservar o artista.

## Acceptance criteria mínimos

- **Versão 1:** uma faixa com participação ou de compilação usa o artista do álbum informado pelo Spotify para resolver o álbum e o artista da faixa para resolver a faixa. Payload incompleto tem fallback testado. Reprocessamento não duplica reprodução ou álbum.
- **Versão 2:** álbum de dois artistas mostra ambos na ordem correta após confirmação MusicBrainz; reaplicar é idempotente; álbuns antigos e consumidores dos campos atuais continuam funcionando.
- **Versão 3:** importar e reimportar álbum colaborativo pelo Spotify preserva todos os créditos. Evento posterior do Plex não os remove e reconhece o álbum quando houver identidade externa comum.
- **Versão 4:** o mesmo álbum aparece nas páginas dos artistas creditados e na busca, sem duplicar paginação ou totais. Mesclagem preserva créditos, listas e histórico.
- **Versão 5:** lote piloto permite conferir alterações antes da aplicação, mantém IDs e reproduções, pode ser retomado sem duplicação e registra o necessário para desfazer seus vínculos adicionados.
- Cada versão recebe testes proporcionais ao risco, documentação atualizada e execução de `ktlintFormat` quando houver alterações Kotlin.

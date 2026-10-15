# Múltiplos artistas por álbum

Status: spec aprovada. Versões 1 a 3 implementadas, com validação em produção pendente; versões 4 e 5 planejadas.

## Problema

O catálogo representa apenas um artista por álbum. Spotify e MusicBrainz fornecem vários créditos, mas a ingestão não os preserva integralmente. A ingestão Spotify também usa o primeiro artista da faixa para resolver o álbum, confundindo créditos de faixa e de álbum.

## Objetivo

Preservar localmente os artistas creditados no álbum e permitir sua exibição e navegação, com versões independentes e verificáveis em produção, preservando IDs e histórico.

## Escopo

1. **Versão 1 — separar artista de álbum e faixa:** resolver o álbum pelo primeiro artista válido de `album.artists` e a faixa pelo primeiro artista válido de `track.artists`. Definir fallback para payloads incompletos preservando a ingestão. Não corrigir automaticamente registros antigos. Sem migração.
2. **Versão 2 — armazenar e mostrar créditos:** criar `album_artists` com artista, posição, nome creditado, separador e origem; preencher os vínculos existentes a partir de `albums.artist_id`. Preservar essa coluna e os contratos atuais. Permitir aplicar todos os créditos pela prévia e confirmação do enriquecimento MusicBrainz. Migração Flyway aditiva.
3. **Versão 3 — automatizar Spotify e preservar no Plex:** persistir todos os artistas do álbum recebidos do Spotify, mantendo ordem e idempotência. Importações parciais do Plex não removem créditos completos. A migração `V69` amplia a restrição de origem para aceitar `SPOTIFY`; a necessidade foi identificada ao implementar esta versão.
4. **Versão 4 — navegar por todos os artistas:** atualizar discografias, busca, filtros, biblioteca, listas e mesclagem de álbuns. Preservar créditos na mesclagem, sem duplicar resultados. Índices adicionais somente se necessários. Rankings mantêm a regra atual.
5. **Versão 5 — completar o acervo existente:** processar lotes pequenos com prévia, usando identificadores externos armazenados. Registrar alterações por álbum para retomada, auditoria e reversão. Conflitos exigem revisão. Migração aditiva se necessária para registrar execuções.

Cada versão é implantada e validada em produção antes da seguinte. A versão 3 introduz o valor de origem `SPOTIFY`; após gravá-lo, rollback para a beta.2 exige uma build de compatibilidade que reconheça esse valor ou restauração do backup anterior. Não basta voltar o binário da beta.2, cujo enum não reconhece essa origem. Voltar a aplicação deve preservar as estruturas aditivas e os dados coletados; isso não desfaz alterações já persistidas. Correções da versão 1 podem exigir ajuste pontual, e os lotes da versão 5 devem permitir desfazer seus próprios vínculos adicionados.

## API/UI

- Versão 1: sem mudança de contrato ou interface.
- Versão 2: adicionar `artists[]` aos contratos relevantes sem remover os campos atuais; mostrar créditos ordenados e links no detalhe do álbum e na prévia MusicBrainz.
- Versão 3: reutilizar os fluxos de importação existentes.
- Versão 4: propagar créditos para a navegação e revisão de duplicados existentes.
- Versão 5: disponibilizar operação administrativa com prévia, execução e resultado por álbum; definir o contrato ao detalhar essa versão.
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
- `albums.artist_id` permanece como vínculo legado de compatibilidade e pode diferir do primeiro crédito confirmado. Créditos não reatribuem esse campo ou fingerprints. Consultas e rankings legados continuam usando esse vínculo até suas evoluções previstas.
- Na versão 2, mesclar artistas transfere as referências dos créditos e preserva nomes creditados e posições. Mesclar álbuns bloqueia a operação se algum álbum de origem tiver créditos confirmados, para não perdê-los. É possível preservar como principal o único álbum com créditos confirmados. Conciliação completa fica para a versão 4.
- Não há restrição de unicidade por artista no álbum: o mesmo artista pode aparecer em mais de uma posição de um crédito editorial.
- A origem deve distinguir o vínculo migrado dos créditos recebidos dos provedores e confirmados pelo usuário.
- Atualizações devem ser transacionais e idempotentes.
- Atualizar a spec existente de enriquecimento MusicBrainz quando a versão 2 alterar sua regra atual de preservar o artista.

## Acceptance criteria mínimos

- **Versão 1:** uma faixa com participação ou de compilação usa o artista do álbum informado pelo Spotify para resolver o álbum e o artista da faixa para resolver a faixa. Payload incompleto tem fallback testado. Reprocessamento não duplica reprodução ou álbum.
- **Versão 2:** álbum de dois artistas mostra ambos na ordem correta após confirmação MusicBrainz; reaplicar é idempotente; álbuns antigos e consumidores dos campos atuais continuam funcionando.
- **Versão 3:** importar e reimportar álbum colaborativo pelo Spotify preserva todos os créditos. Evento posterior do Plex não os remove e reconhece o álbum quando houver identidade externa comum.
- **Versão 4:** o mesmo álbum aparece nas páginas dos artistas creditados e na busca, sem duplicar paginação ou totais. Mesclagem preserva créditos, listas e histórico.
- **Versão 5:** lote piloto permite conferir alterações antes da aplicação, mantém IDs e reproduções, pode ser retomado sem duplicação e registra o necessário para desfazer seus vínculos adicionados.
- Cada versão recebe testes proporcionais ao risco, documentação atualizada e execução de `ktlintFormat` quando houver alterações Kotlin.

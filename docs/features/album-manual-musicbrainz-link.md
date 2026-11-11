# Vínculo manual MusicBrainz no álbum

## Problema

A busca de correspondência pode não encontrar um álbum existente no MusicBrainz. O painel de atualização também aparece distante do botão que o revela.

Escopo solicitado pelo owner; implementado e validado localmente.

## Objetivo

Permitir indicar a correspondência conhecida e manter a atualização junto de sua ação, preservando prévia e confirmação.

## Escopo e UI/API

- Mostrar o painel imediatamente depois do cabeçalho Sobre o álbum, antes de gêneros, listas e links; manter Atualizar dados/Fechar atualização.
- Oferecer no painel um campo Link ou identificador do MusicBrainz e a ação Consultar, disponível mesmo quando a busca não retorna candidatos.
- Aceitar URLs canônicas musicbrainz.org/release-group/{UUID}, musicbrainz.org/release/{UUID} e UUID de release group. Não aceitar URL de artista ou gravação. Para uma edição, resolver o release group usando o cliente já existente; o vínculo local continua no nível do álbum, sem substituir sua edição/tracklist.
- Validar formato e host, extrair somente o tipo/UUID e consultar os endpoints fixos do cliente. Mostrar a mesma prévia de título/artistas/ano e alterações utilizada pelos candidatos automáticos, com link para a correspondência.
- Confirmar enriquecimento aplica o vínculo pelo fluxo existente; colar ou consultar não grava. Manter o texto informado e feedback em caso de erro. Bloquear ações concorrentes durante a consulta/aplicação e invalidar prévia ao alterar a entrada.
- Reaproveitar a operação de prévia com parâmetro opcional de tipo release-group/release (padrão release-group), preservando compatibilidade. Aplicação continua recebendo o release group resolvido na prévia.

Arquivos principais: pages/music/albums/[id].vue, AlbumMusicBrainzPanel.vue, utilitário de interpretação do identificador conforme necessário; MusicBrainzPageEnrichmentController, MusicBrainzPageEnrichmentService e testes; docs/music-api.md, docs/openapi.yaml e DESIGN.md. Sem migração ou dependência.

## Fora do escopo

Criar entradas no MusicBrainz, editar título manualmente, escolher edição canônica, substituir tracklist, ampliar imagens ou consultar contexto automaticamente após vincular.

## Regras

Reutilizar os painéis brancos, controles de 16px e foco visível definidos em DESIGN.md. Preservar a busca automática, confirmações e controles owner/CSRF. Entrada inválida, correspondência ausente, conflito de vínculo e falha externa devem ter feedback sem perder o valor informado. Não buscar a URL fornecida arbitrariamente.

## Critérios de aceite

- Painel abre junto do botão em desktop/celular, com aria-controls/aria-expanded coerentes.
- URL de álbum, URL de edição e UUID válido geram prévia; tipo/host/formato inválidos são rejeitados sem consulta remota.
- Prévia não escreve; aplicação usa o release group resolvido; erro preserva entrada e permite retry; mudanças na entrada invalidam prévia.
- Verificar busca automática, vínculo existente, falhas de rede e acesso público; testes proporcionais, ktlintFormat, checks e builds pertinentes.

## Detalhes de implementação

A prévia mantém o parâmetro histórico `releaseGroupMbid` e aceita `entityType` opcional. O campo no frontend extrai tipo e UUID; o backend valida novamente antes de consultar o provedor. A entrada fica bloqueada durante a consulta, e edição invalida a prévia anterior. O painel mantém seu estado ao recolher/reabrir, e atualizações da resposta do álbum preservam a página já carregada, incluindo feedback de sucesso.

## Validação

- Backend: ktlintFormat, bootJar e 16 testes de serviço/contrato/OpenAPI sem falhas. Inclui referência de álbum, resolução de edição, validação antes de consulta e tratamento dos erros reais do cliente MusicBrainz (ausência, transporte e falha fatal).
- Frontend: lint, formatação, tipos e build.
- Chromium em 1440px e 390px, owner/visitante, com APIs simuladas: busca sem candidatos, entradas inválidas, UUID e URLs com resolução, prévia sem gravação, invalidação ao editar, 404/502, falha na aplicação e retry, payload do release group resolvido, posicionamento do painel e preservação de estado/feedback. Regressão de galeria, introdução e contexto.
- Versão avançada posteriormente para 1.21.0 a pedido do owner, com commit e push. Release e deploy permanecem fora desta etapa.

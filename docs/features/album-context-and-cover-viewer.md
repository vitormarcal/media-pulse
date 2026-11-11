# Contexto do álbum e ampliação da capa

## Problema

O detalhe de álbum já organiza a memória pessoal e as faixas, mas ainda oferece pouco contexto sobre a obra e não permite ampliar a capa.

## Objetivo

Dar identidade ao álbum por meio da arte e de uma introdução factual, mantendo o arquivo local e a composição aprovada na seção 19 de DESIGN.md.

Escopo aprovado pelo owner, incluindo tipo, primeiro lançamento e links externos.

## Escopo

1. Clicar na capa ou em “Ver imagem” abre o visualizador compartilhado de filmes e séries, com imagem integral, Escape, foco contido e retorno ao controle de origem. Sem capa, não mostrar ação. O rótulo “Ver todas as N imagens” será usado quando houver várias imagens disponíveis; esta entrega continua usando apenas a capa existente.
2. Incluir tipo principal/secundários e primeira data de lançamento do release group, respeitando a precisão disponível, além de links MusicBrainz, Spotify, Discogs e Bandcamp quando disponíveis. Consultar relações do release group MusicBrainz para localizar o artigo do álbum na Wikipedia, diretamente ou via Wikidata. Priorizar português e usar inglês como alternativa identificada. Não pesquisar apenas pelo título nem inferir uma correspondência sem vínculo.
3. Buscar a introdução do artigo em texto simples. Mostrar prévia com texto, idioma, fonte e comparação com o texto salvo em “Atualizar dados”; permitir aplicar o texto explicitamente sem exigir nova aplicação dos créditos, gêneros e tags do álbum.
4. Guardar localmente texto, idioma, URL canônica do artigo, referência de revisão quando disponível e data de obtenção. A consulta da página usa somente o conteúdo salvo. Falha externa preserva o texto existente e permite nova tentativa.
5. Mostrar a introdução após os créditos/ano e antes da memória pessoal, sem novo card interno. Textos longos têm “Ler mais” e “Ler menos”, com fonte/atribuição acessível junto do texto. Ausência de conteúdo não reserva espaço. Não alterar cores ou adicionar fundos decorativos.

## API/UI

- Ampliar de forma aditiva GET /api/music/albums/{albumId} com contexto opcional e sua proveniência.
- Acrescentar operações owner de prévia e confirmação do contexto junto dos endpoints de enriquecimento de álbum. A confirmação salva a prévia apresentada; chamadas remotas ficam fora da transação de persistência.
- Migração Flyway para persistência do contexto opcional. Álbuns existentes continuam válidos sem preenchimento ou backfill remoto automático.
- Reutilizar WebClient e MediaImageGallery, sem nova dependência.
- Arquivos principais: AlbumPageHero.vue, pages/music/albums/[id].vue, AlbumMusicBrainzPanel.vue, types/music.ts, utils/music.ts; DTOs/controller/service de enriquecimento musical; MusicBrainzApiClient e seus DTOs; cliente Wikimedia e persistência do contexto conforme os padrões existentes; migração, testes, docs/music-api.md, docs/openapi.yaml e DESIGN.md.

## Fora do escopo

Novas imagens, seleção de capa, Visual Library para música, alterações das páginas de artistas, biografias geradas, tradução automática, texto manual, importação em lote, player e duração.

## Regras

- Reutilizar o visualizador público, sem exibir controles de curadoria sem endpoints correspondentes.
- Não substituir conteúdo salvo sem confirmação; distinguir ausência de artigo/texto de falha de consulta.
- Resolver apenas hosts e identificadores Wikimedia esperados, sem buscar URLs arbitrárias recebidas de relações externas.
- Preservar atribuição e licença aplicável ao texto importado, com link para fonte e informações de reutilização.
- Preservar tracklist, capa, créditos, avaliações, comentários, listas e o fluxo existente de enriquecimento.

## Critérios de aceite

- Abrir/fechar a capa por mouse e teclado; conferir Escape, foco, scroll, falha de imagem e desktop/celular.
- Validar vínculo direto e via Wikidata, prioridade pt/en, ausência de vínculo/artigo/introdução e falhas externas.
- Prévia não escreve; confirmação salva exatamente o conteúdo revisado; falha preserva o conteúdo atual; leitura pública não consulta provedores.
- Conferir introdução curta/longa e ausente, atribuição, controles owner e regressão dos fluxos existentes.
- Testes proporcionais de resolução, persistência e contrato; ktlintFormat, verificações e build do frontend/backend.

## Implementação e validação

Implementado sem novas dependências. Flyway V78 adiciona `album_context`, com snapshot opcional em JSONB e exclusão em cascata ao remover o álbum. Prévias usam tokens temporários na instância (15 minutos, até 100), invalidados em reinício. Confirmação verifica vínculo e snapshot atual sob bloqueio do álbum antes de salvar exatamente a prévia.

Validação: ktlintFormat, testes de resolução Wikimedia, prévia/confirmação e regressão MusicBrainz/controller; integração PostgreSQL com todas as migrações, round trip de JSON, substituição e cascata; OpenAPI; lint, formatação, tipos e build do frontend e bootJar. O Docker local requer `JAVA_TOOL_OPTIONS=-Dapi.version=1.44` para executar Testcontainers com este daemon.

Chromium em 1440px e 390px, owner e visitante: abertura da capa por imagem/botão, Escape, retorno de foco e scroll; ausência de setas para uma imagem; texto longo, atribuição e idioma; ausência de texto/capa; prévia sem escrita, falha de consulta, falha de confirmação e retry; atualização após salvar e ausência de overflow. Escritas de navegador verificadas com APIs simuladas. Fluxos anteriores de comentários, notas, listas e termos também conferidos.

Publicação solicitada posteriormente na versão 1.20.0, com tag Git v1.20.0 e imagens 1.20.0/latest. Implantação no Mordor pelo serviço Compose existente; Flyway aplica V78 na inicialização.

# Revisão do detalhe de álbum

Status: implementado e validado localmente.

## Problema

A página coloca curadoria, estatísticas e listas antes das faixas. Artistas, reproduções e última escuta aparecem repetidos. Nota e comentários ficam separados da identificação do álbum, e os comentários têm um resumo redundante. O cabeçalho usa a capa como fundo e como imagem, com altura e recorte que prejudicam sua proporção quadrada.

## Objetivo

Apresentar o álbum como uma obra musical do arquivo pessoal, com acesso rápido aos artistas, faixas e memória de escuta. Seguir os princípios de propriedade dos dados, experiência do owner, riqueza visual e simplicidade de PRODUCT_CONSTITUTION.md, adaptando a linguagem aprovada em DESIGN.md para música.

## Escopo e experiência

1. Cabeçalho branco uniforme sobre fundo quente. Mostrar capa inteira em área quadrada, título, créditos de artistas com links individuais e ano quando disponível. Nota compacta junto dos comentários. Comentários com uma data por item e composição sob demanda, usando o tratamento editorial compartilhado.
2. Faixas logo após o cabeçalho, em lista compacta com número, título, reproduções e última escuta identificadas pelos rótulos das colunas. Preservar discos e posições disponíveis, além da ordem e dos IDs retornados pela API. No celular, distribuir esses dados por linhas legíveis. A nota da faixa permanece acessível ao owner, e a nota existente aparece para visitantes.
3. Escutas com total de reproduções, última escuta em uma data identificável e os dias recentes fornecidos pela API. Usar datas legíveis e estados vazios diretos. As contagens de faixas e de reproduções terão sua apresentação principal em locais definidos para evitar repetição.
4. Sobre o álbum reúne gêneros, tags e listas com links. A edição fica junto da seção. Atualizar dados abre o fluxo MusicBrainz, com prévia e confirmação. Revisar faixas duplicadas abre a ferramenta existente junto da lista de faixas.
5. Retorno explícito para Música. Superfícies brancas, tipografia e espaçamento consistentes com filmes e séries, preservando a composição musical da capa e dos créditos.

## API e dados

Reutilizar GET /api/music/albums/{albumId}, os controles de notas/comentários e as operações de termos, listas, MusicBrainz e duplicatas. Sem migração, alteração de API ou dependência. A primeira data registrada é derivada de playsByDay; os até 12 dias com reproduções mais recentes são consultáveis sob demanda, em ordem decrescente.

Decisão de escopo: busca e filtro de faixas, duração e imagens adicionais foram discutidos e ficaram fora desta revisão. A consulta fornecida pelo owner encontrou duração válida em apenas 17% das 21.833 faixas vinculadas a álbuns; enriquecimento de duração fica para outra etapa.

A resposta atual contém contagens de reproduções de faixas e totais agregados. Não fornece sessões completas de audição, duração das faixas nem um histórico paginado de eventos. A interface deve nomear os dados conforme seu significado e o recorte disponível.

## Fora do escopo

Busca e filtro de faixas, duração, imagens adicionais, player, sessões de audição completas, edição de tracklist, novos provedores, migração de imagens de música para o Visual Library, página de artista, página de faixa e biblioteca musical.

## Regras

- Preservar nomes creditados, ordem dos artistas e joinPhrase. Álbuns legados continuam usando o artista disponível.
- Reproduções agregadas não representam necessariamente audições completas do álbum.
- Manter a avaliação de cada faixa acessível sem repetir a explicação da escala em todas as linhas.
- Preservar os fluxos sensíveis de confirmação MusicBrainz e mesclagem de duplicatas, com a origem e as consequências das alterações visíveis durante essas operações.
- Ocultar seções vazias de comentários e listas para visitantes, mantendo acesso à inclusão pelo owner.
- Preservar capa integral e fallback quando ausente. Títulos e créditos longos devem quebrar sem overflow.

## Arquivos principais

- frontend/app/pages/music/albums/[id].vue
- frontend/app/components/music/AlbumPageHero.vue
- frontend/app/components/music/AlbumTrackList.vue
- frontend/app/components/music/AlbumContextPanel.vue
- frontend/app/components/music/AlbumTermsPanel.vue
- frontend/app/components/music/AlbumListsPanel.vue
- frontend/app/utils/music.ts e frontend/app/types/music.ts, conforme a apresentação dos dados exigir.
- DESIGN.md para registrar as decisões aprovadas de música.

## Critérios de aceite

- Identificação, memória pessoal e faixas aparecem antes das ferramentas de manutenção.
- Artistas, total de reproduções e última escuta têm apresentação clara e sem blocos redundantes.
- Conferir álbum sem capa, sem escutas, sem faixas, com vários discos, créditos múltiplos e títulos longos em desktop e celular.
- Validar links dos artistas, notas de álbum/faixa, comentários, listas, edição de termos e abertura das ferramentas MusicBrainz e duplicatas.
- Rodar lint, formatação, tipos e build. Conferir os fluxos de escrita com APIs simuladas.

## Validação realizada

- `npm run check` e `npm run build` no frontend.
- Chromium em 1440px e 390px, como owner e visitante: créditos múltiplos, títulos longos, múltiplos discos, datas do histórico, ausência de capa/faixas/escutas e ausência de overflow horizontal.
- APIs simuladas para salvar comentário e alterar nota de faixa, incluindo atualização do resumo da nota; abertura da organização de listas, edição de termos e atualização MusicBrainz.
- Sem alteração de backend ou dados reais. Publicação solicitada posteriormente na versão 1.19.0, com imagem versionada e latest; implantação no Mordor pelo serviço Compose existente.

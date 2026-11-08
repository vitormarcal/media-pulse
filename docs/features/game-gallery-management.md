# Gerenciar imagens salvas do jogo

## Problema e objetivo

O owner precisa escolher uma imagem já salva como capa e remover imagens do jogo, sem novo upload.

## Escopo e API/UI

- O cabeçalho apresenta a capa maior e até três imagens complementares, com “Ver todas as N imagens”. As prévias preenchem seus cards com recorte central, sem barras; a ampliação preserva a composição integral. A galeria completa abre sob demanda em tela cheia, preservando proporções, miniaturas e teclado. Não há repetição do acervo abaixo do cabeçalho.
- O visualizador apresenta Capa atual ou Definir como capa e Remover imagem para a imagem atual, somente para o owner.
- Remover exige confirmação no visualizador; erros permitem nova tentativa. A imagem atual é preservada ao trocar capa; remoção continua na posição disponível e a última fecha o diálogo com retorno de foco.
- `POST /api/games/{gameId}/images/primary` e `DELETE /api/games/{gameId}/images` recebem `{url}` de uma referência já vinculada ao jogo e retornam `{coverUrl}` anulável.

## Regras

Escolher capa atualiza `games.cover_url` e a única principal em `game_images`, sob o lock existente do jogo e na mesma transação. Remover a capa escolhe a primeira imagem restante na ordem da galeria (principal primeiro, ID decrescente); sem imagens, limpa a capa. Capas antigas sem vínculo na galeria também são aceitas. Remover somente desfaz o vínculo, preservando arquivos locais e assets do Visual Library.

## Non-goals

Não altera provedores, importação, sessões, metadados ou arquivos armazenados. Sem migration ou dependências.

## Aceite

Escolher capa sem upload, refletir a escolha no detalhe e biblioteca após recarregar, remover imagem com confirmação, preservar capa ao remover outra imagem, substituir capa removida automaticamente e deixar sem capa ao remover a última. APIs exigem owner e CSRF e rejeitam referências não vinculadas.

## Validação da revisão de hierarquia

Lint, Prettier dos três componentes/página alterados, tipos e build estático aprovados. Smoke com Chromium e APIs simuladas em 1440×1000, 768×1000, 390×844 e 844×390 conferiu ausência de galeria repetida, abertura pela prévia, imagem atual preservada após trocar capa, confirmação/cancelamento, falha e retry de remoção, substituição automática, miniaturas, contenção de foco e fechamento da última imagem com rolagem liberada e foco no título. Visões pública e owner também foram conferidas com zero a quatro imagens. Nenhum dado real foi alterado.

Regressão do seletor de novas imagens aprovada em 1440, 768, 390 e 320px, incluindo seleção preservada, navegação entre diálogos, envio explícito e retry parcial. O carregamento de originais foi revalidado em 1440 e 390px na seleção e na galeria pública: remoção imediata dos pixels anteriores, espera explícita, resposta atrasada ignorada, falha e retorno a imagem já carregada.

Preparação da release 1.12.0: `npm run check` aprovado e suíte completa do backend com 601 testes, zero falhas/erros/ignorados, usando `JAVA_TOOL_OPTIONS=-Dapi.version=1.44`. Inclui os quatro testes PostgreSQL de `LibraryCoversIntegrationTest`, com troca de capa, substituição automática, remoção final e preservação dos arquivos armazenados.

Correção 1.12.1: prévias do cabeçalho preenchem os cards com recorte central, eliminando as barras da capa observadas em HuniePop. A imagem ampliada permanece inteira. Conferido no exemplo publicado e com Chromium em 1440, 768 e 390px; lint, formatação, tipos e build aprovados.

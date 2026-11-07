# Seleção de imagens de jogos

## Problema

A busca automática guarda até três capas sem permitir escolher as imagens ou explorar outros tipos já disponíveis nos provedores integrados.

## Objetivo

Escolher visualmente quais imagens guardar no acervo, mantendo a capa atual.

## Escopo

- Prévias de capas verticais/horizontais e fundos do SteamGridDB, além de capa, screenshots e artes do IGDB.
- Seleção múltipla e armazenamento explícito.
- Preservar capa, imagens existentes e sessões; manter falhas selecionadas para retry e salvar sucessos parciais.
- Usar o armazenamento existente: Visual Library quando habilitado, arquivos locais nos demais casos.

## API/UI

No detalhe, “Escolher imagens” busca prévias e “Salvar selecionadas” confirma a seleção. Clicar na prévia amplia a imagem original, permitindo navegar e selecionar/desmarcar na própria ampliação; abrir ou fechar não altera as escolhas. A galeria guardada usa masonry responsivo, fica disponível também sem login, mostra todas as imagens e permite ampliar cada imagem na própria página, com setas, miniaturas e Escape; o mosaico do cabeçalho mantém até quatro.

`GET /api/games/{gameId}/images/candidates` e `POST /api/games/{gameId}/images/selection`. Reutilizar `game_images` para guardar a referência retornada pelo armazenamento. Sem migration.

## Non-goals

Logos, ícones, troca de capa, exclusão, novos provedores, controle de URLs de origem, histórico de seleção, deduplicação própria e alteração da captura automática no cadastro.

## Heurística/regras

- Buscar o lote disponível de grids/fundos do SteamGridDB e as imagens associadas ao ID IGDB local; sem importar arquivos na busca.
- Reusar o ID SteamGridDB ou procurar pelo título como no fluxo anterior. Não buscar um novo vínculo IGDB por aproximação.
- Enviar IDs opacos das opções, revalidados no backend com os resultados atuais; não aceitar URL de download fornecida pelo navegador.
- Até 50 seleções por requisição. Preservar as demais seleções em caso de erro HTTP e manter falhas individuais selecionadas em sucesso parcial.
- Usar URLs dos provedores apenas transitoriamente para prévia e download. Persistir somente a referência retornada pelo armazenamento na galeria existente.
- A deduplicação e revisão de duplicadas pertencem ao Visual Library. Todas as opções permanecem selecionáveis; referência já vinculada ao jogo é tratada pela inserção existente da galeria.

## Critérios de aceite

- Explorar prévias dos dois provedores e selecionar várias imagens.
- Guardar apenas as selecionadas e refletir as novas imagens na página.
- Permitir nova seleção da mesma imagem, delegando o armazenamento ao fluxo existente e preservando um único vínculo quando a referência retornada for a mesma.
- Preservar capa e acervo em falhas, mantendo resultados parciais e retry.
- Validar persistência, limites, indisponibilidade e rejeição de URLs arbitrárias.

## Validação

Testes direcionados cobrem seleção, falhas parciais, reenvio ao armazenamento, ausência de downloads durante a busca, rejeição de URLs arbitrárias e preservação da capa. Handlers HTTP locais validam as consultas e respostas de ambos os provedores. O teste de seleção verifica que o retorno de uma referência já vinculada é tratado como sucesso sem alterar a capa.

Frontend validado com lint, Prettier dos arquivos alterados, tipos e build estático. Smoke em Chromium com APIs simuladas, nas larguras 1440 e 390px, conferiu checkbox por teclado, CSRF, preservação de seleção após HTTP 503, sucesso parcial, retry apenas dos itens que falharam e atualização da galeria sem overflow. Não dependeu de credenciais reais dos provedores.

A visualização do acervo foi conferida em Chromium em 1440, 768 e 390px: galeria sem login, proporções originais, abertura pelo cabeçalho e pela galeria, miniaturas, navegação circular com setas, foco contido no diálogo, fechamento por Escape e retorno ao botão de origem. A busca e o salvamento de novas imagens seguem separados da visualização.

A ampliação durante a seleção foi validada em Chromium em 1440 e 390px: imagem original no visualizador, prévias pequenas nas miniaturas, marcar/desmarcar durante a navegação, preservação ao fechar, teclado/foco e envio somente após confirmação. A galeria salva foi revalidada nas três larguras.

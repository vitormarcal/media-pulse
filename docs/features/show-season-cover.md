# Imagens próprias por temporada

## Problema

A capa única já permite reconhecer temporadas, mas o cabeçalho compacto perde a linguagem editorial da série e impede guardar pôsteres alternativos da própria temporada.

## Objetivo

Guardar múltiplos pôsteres por temporada e apresentá-los na mesma composição editorial da série, mantendo uma capa principal independente. Evolução aprovada pelo owner após conferir Monogatari e suas temporadas.

## Escopo

- Selecionar múltiplos pôsteres, definir capa e remover imagens no visualizador, apenas pelo owner.
- Exibir a capa integral no detalhe e uma miniatura junto ao título e progresso da temporada na página da série.
- Persistir o vínculo por série e número de temporada; baixar a imagem pelo armazenamento existente, incluindo Visual Library quando habilitado.
- V84 adiciona a galeria e incorpora as capas da V83 como imagens principais, preservando os vínculos existentes. Não adicionar dependência ou provedor.

## API/UI

- Consultar pôsteres pelo TMDb ID da série e número da temporada; reutilizar padrões de seleção e ampliação existentes, com seleção múltipla e salvamento explícito. Adições preservam a capa existente; sem capa, o primeiro sucesso passa a representar a temporada.
- Adicionar operações de candidatos, consulta de presença, seleção múltipla, escolha de capa e remoção de imagem por temporada, protegidas por owner e CSRF nas mutações.
- Retornar a capa própria nos contratos de resumo e detalhe de temporada. Não consultar TMDb para renderizar essas páginas.
- No detalhe: Escolher imagens junto da abertura, capa dominante e faixa com todas as complementares. Cada prévia abre seu índice na galeria completa; curadoria fica no visualizador. Na lista: imagem e título integram a mesma navegação à temporada.
- Reutilizar tokens e tratamento de imagens de DESIGN.md, seção 18; usar título expressivo, capa integral e fundo suave da temporada. Progresso fica junto da identificação, antes das complementares; episódios vêm logo abaixo. Sem sinopse específica persistida, não repetir a descrição geral da série.
- Principais arquivos envolvidos: TmdbApiClient.kt, ShowDtos.kt, consultas e controllers de séries/temporadas, novos model/repository/service de capa, ImageStorageService.kt, types/shows.ts, utils/shows.ts, ShowProgressPanel.vue, pages/shows/[slug]/seasons/[season].vue, MoviePageHero.vue, MediaImageSelector.vue, ManagedMediaImageGallery.vue e documentação de API/design. O seletor de capa única anterior é substituído pelos componentes compartilhados.

## Fora do escopo

Imagens de episódios, download automático em lote, importação de capas pelo Plex, upload manual e alterações no cálculo de progresso ou na organização de episódios.

## Heurística/regras

- Múltiplas imagens, com uma capa principal por série e número de temporada; especiais (temporada zero) são permitidos. Temporadas sem número não oferecem seleção.
- Usar a numeração e o vínculo TMDb já existentes, sem tentar remapear temporadas automaticamente.
- Sem vínculo TMDb, orientar a atualização dos dados da série. Sem candidatos, informar a ausência sem bloquear navegação ou progresso.
- Só substituir o vínculo após concluir o armazenamento da nova imagem. Falhas preservam a capa anterior e permitem tentar novamente.
- Remover a imagem principal escolhe outra imagem restante ou deixa a temporada sem capa se a galeria estiver vazia. Trocar capa e remover vínculos não apaga o asset do Visual Library, nem modifica a capa/galeria da série ou o histórico.
- Sem capa ou em falha de carregamento, manter apresentação textual sem coluna vazia; não apresentar a capa da série como se fosse específica da temporada.

## Critérios mínimos de aceite

- Owner consegue escolher múltiplos pôsteres, ampliar antes de salvar, definir capa e remover vínculos; visitante não recebe controles de edição.
- Capas preexistentes permanecem principais após a V84. Novas imagens e capa escolhida permanecem após recarregar; miniatura na série reflete a capa. Falha parcial mantém os sucessos e permite repetir as falhas.
- Capa geral da série, episódios, progresso e registros permanecem inalterados.
- Ausência de vínculo/candidatos e falhas de consulta, armazenamento e carregamento têm estados claros e recuperáveis.
- Conferir Monogatari, Bleach e The Flowers of Evil em desktop e celular, incluindo proporção integral, teclado, retorno do foco e ausência de overflow. Simular mutações para não alterar o acervo real durante testes.
- Validar migração, persistência única e falha na troca, autorização/CSRF, lint, formatação, tipos e build; executar ktlintFormat após alterações Kotlin.

Validação da evolução: migração PostgreSQL de V83 para V84 com preservação das capas, deduplicação, escolha e remoção de principal e cascata; seleção parcial e isolamento entre temporadas; proteção owner/CSRF. Interface conferida com dados reais das quatro temporadas de Monogatari, Bleach e The Flowers of Evil e imagens/gravações simuladas, em 1440, 768, 390 e 320px. Índices da galeria, retorno do foco, progresso e ausência de overflow verificados. Fluxo do owner cobre seleção múltipla, retry parcial, capa preservada ao adicionar, troca e substituição após remover a principal. Componentes compartilhados de filmes conferidos com fixtures. Lint, formatação, tipos e build de produção validados.

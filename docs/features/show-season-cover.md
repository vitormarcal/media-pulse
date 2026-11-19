# Capa própria por temporada

## Problema

Temporadas não têm capa própria no Media Pulse. Isso dificulta reconhecê-las visualmente na série e deixa o detalhe da temporada sem sua arte específica.

## Objetivo

Permitir escolher um pôster do TMDb e guardar uma única capa por temporada, independente da capa geral da série. Especificação aprovada pelo owner.

## Escopo

- Selecionar, substituir e remover a capa no detalhe da temporada, apenas pelo owner.
- Exibir a capa integral no detalhe e uma miniatura junto ao título e progresso da temporada na página da série.
- Persistir o vínculo por série e número de temporada; baixar a imagem pelo armazenamento existente, incluindo Visual Library quando habilitado.
- Adicionar migração Flyway para a persistência da capa. Não adicionar dependência ou provedor.

## API/UI

- Consultar pôsteres pelo TMDb ID da série e número da temporada; reutilizar padrões de seleção e ampliação existentes, com seleção única e salvamento explícito.
- Adicionar operações de candidatos, escolha e remoção de capa por temporada, protegidas por owner e CSRF nas mutações.
- Retornar a capa própria nos contratos de resumo e detalhe de temporada. Não consultar TMDb para renderizar essas páginas.
- No detalhe: Escolher capa quando ausente, Trocar capa e Remover capa quando presente. Na lista: imagem e título integram a mesma navegação à temporada.
- Reutilizar tokens e tratamento de imagens de DESIGN.md, seção 18; preservar cabeçalho compacto, legibilidade do progresso e prioridade dos episódios.
- Principais arquivos envolvidos: TmdbApiClient.kt, ShowDtos.kt, consultas e controllers de séries/temporadas, novos model/repository/service de capa, ImageStorageService.kt, types/shows.ts, utils/shows.ts, ShowProgressPanel.vue, pages/shows/[slug]/seasons/[season].vue, componente de seleção e documentação de API/design.

## Fora do escopo

Galeria por temporada, imagens de episódios, download automático em lote, importação de capas pelo Plex, upload manual e alterações no cálculo de progresso ou na organização de episódios.

## Heurística/regras

- No máximo uma capa por série e número de temporada; especiais (temporada zero) são permitidos. Temporadas sem número não oferecem seleção.
- Usar a numeração e o vínculo TMDb já existentes, sem tentar remapear temporadas automaticamente.
- Sem vínculo TMDb, orientar a atualização dos dados da série. Sem candidatos, informar a ausência sem bloquear navegação ou progresso.
- Só substituir o vínculo após concluir o armazenamento da nova imagem. Falhas preservam a capa anterior e permitem tentar novamente.
- Remover ou trocar a capa não apaga o asset do Visual Library, nem modifica a capa/galeria da série ou o histórico.
- Sem capa ou em falha de carregamento, manter apresentação textual sem coluna vazia; não apresentar a capa da série como se fosse específica da temporada.

## Critérios mínimos de aceite

- Owner consegue escolher, ampliar antes de salvar, trocar e remover uma capa; visitante não recebe controles de edição.
- Capa salva permanece após recarregar e é a mesma no detalhe e na lista de temporadas.
- Capa geral da série, episódios, progresso e registros permanecem inalterados.
- Ausência de vínculo/candidatos e falhas de consulta, armazenamento e carregamento têm estados claros e recuperáveis.
- Conferir Monogatari, Bleach e The Flowers of Evil em desktop e celular, incluindo proporção integral, teclado, retorno do foco e ausência de overflow. Simular mutações para não alterar o acervo real durante testes.
- Validar migração, persistência única e falha na troca, autorização/CSRF, lint, formatação, tipos e build; executar ktlintFormat após alterações Kotlin.

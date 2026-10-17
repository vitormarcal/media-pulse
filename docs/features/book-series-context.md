# Contexto e apresentação de séries de livros

## Problema

Séries guardam apenas nome, origem e membros. A atribuição mistura sugestões externas e organização local; listagem e detalhe têm menos contexto visual que listas e coleções existentes. Autores não apresentam as séries de seus livros.

## Objetivo

Enriquecer séries com contexto local durável, facilitar a atribuição e conectar livros, séries e autores com a linguagem visual já estabelecida.

## Escopo

- Trazer descrição, autor principal, slug externo, indicação de série concluída e contagens do Hardcover quando disponíveis.
- Permitir revisão dos metadados antes de aplicar, incluindo atualização explícita de séries já vinculadas ao Hardcover.
- Permitir editar a descrição local; preservar nome, descrição e ordem pessoais em atualizações não selecionadas.
- Derivar os autores locais da série dos créditos de autoria dos livros associados, incluindo coautores e excluindo tradução e ilustração.
- Exibir séries na página de cada autor com autoria em pelo menos um livro local associado, e links aos autores no detalhe da série.
- Revisar atribuição, listagem e detalhe de séries e os componentes compartilhados com universos para manter consistência visual.

## API/UI

Estender os contratos de séries e sugestões e acrescentar prévia/aplicação explícita de metadados de uma série vinculada. Acrescentar séries ao contexto de autor. Migration Flyway posterior à V72 armazenará os metadados locais e a identificação do autor principal externo; associações e posições existentes serão preservadas.

A atribuição distinguirá “Sugestões do Hardcover”, “Criar série” e “Adicionar a uma série existente”, mostrando capas dos livros locais, vínculos existentes e os campos selecionados para aplicação. Manter seleção e feedback em erro, sem repetir associações já salvas.

A listagem usará cards com mosaico de capas locais, nome, autores e contagem do acervo, seguindo listas de álbuns e filmes. O detalhe terá cabeçalho com capa local representativa, descrição, autores e universos, seguido dos livros na ordem de leitura. A descrição longa terá expansão. Reordenação, remoção e exclusão aparecerão durante edição. A página do autor reutilizará os mesmos cards de séries, sem substituir a biblioteca de livros.

Seguir DESIGN.md: capas em destaque, tokens existentes, superfícies quentes, tipografia e hierarquia das bibliotecas, controles com cantos de 16px, foco visível, operação por teclado e comportamento responsivo. Reutilizar os padrões de listas de álbuns, listas de filmes e coletâneas sem reproduzir regras de consumo de outros domínios.

## Non-goals

- Ingestão de livros externos ausentes ou criação automática de autores apenas por aparecerem no provedor.
- Capa própria da série importada ou novo fluxo de upload de imagens.
- Sincronização automática de metadados, alteração de ordem pessoal ou gerenciamento manual paralelo de autores de séries.
- Novas relações entre universos ou ordens de leitura alternativas.

## Heurística/regras

- Autores locais são derivados dos livros e atualizados quando seus vínculos mudam. Não inferir autoria a partir de tradução, ilustração ou outros créditos.
- O autor principal do Hardcover é contexto complementar, identificado como tal; não representa automaticamente todos os autores locais nem cria vínculos por nome aproximado.
- Descrição e demais campos opcionais ausentes não bloqueiam cadastro ou navegação. O indicador de conclusão externo descreve a publicação da série, não a leitura pessoal.
- Contagens externas são identificadas como dados do Hardcover; a contagem local considera exclusivamente livros associados no Media Pulse.
- Metadados aceitos são persistidos localmente. Navegação e páginas de autores não consultam o provedor.
- Aplicar campos selecionados altera somente esses campos; atualizar metadados não adiciona livros, não remove membros e não muda posições.
- Capas são compostas a partir dos livros locais com fallback existente; a série não depende de uma imagem externa própria.
- Preservar identidades, vínculos e ordens da entrega 1.6.0, inclusive para séries sem vínculo Hardcover.

## Acceptance criteria mínimos

- Prévia mostra os metadados disponíveis sem gravação; aplicar preserva campos não selecionados e a ordem local.
- Série existente pode receber metadados por ação explícita, sem recriar a série ou alterar seus membros.
- Séries com coautores aparecem nas páginas dos autores correspondentes; tradução e ilustração não geram autoria de série.
- Listagem, atribuição, detalhe e contexto de autor seguem os padrões existentes em desktop e celular, com capas/fallback e estados vazio, carregando e erro.
- Controles de manutenção aparecem durante edição; atribuição distingue sugestão externa de vínculo local e preserva escolhas em falha.
- Migration preserva séries, universos, livros, leituras e posições existentes; testes proporcionais validam autoria, aplicação seletiva e contratos.

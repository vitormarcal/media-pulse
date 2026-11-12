# Revisão do detalhe de autor de livros

Status: aprovado e implementado; validação local concluída.

## Problema

A página usa a capa de um livro como imagem do autor, com recorte e fundo duplicado. Contagens se repetem no cabeçalho e em dois cartões de contexto, antes dos próprios livros. A copy mistura registros, conclusões e livros, que representam coisas diferentes. O histórico repete datas e expõe a origem técnica do registro.

## Objetivo

Dar protagonismo aos livros presentes no arquivo e à relação pessoal de leitura com o autor, seguindo as superfícies e a escala dos detalhes já revisados. A personalidade vem das capas e da composição editorial, sem representar o autor com uma imagem de outra entidade.

## Escopo e UI

1. Cabeçalho compacto com nome, identificação de autor e retorno à biblioteca. Remover a capa usada como retrato, fundo duplicado e coluna reservada a imagem inexistente. Sem placeholder de pessoa.
2. Usar largura de até 1120px, fundo quente, painéis brancos, espaçamento e tipografia orientados pelas seções 17, 20 e 21 de DESIGN.md. Adaptar a composição ao domínio literário, sem copiar os controles de artista musical.
3. Reunir o contexto pessoal em uma linha discreta, sem cartões de indicadores: livros no acervo e leituras concluídas, quando pertinentes. Cada informação aparece uma vez. Preservar releituras; conclusões não equivalem a quantidade de livros distintos lidos.
4. Mostrar Livros no acervo logo após a identificação, com grade regular de capas integrais e links para os livros. Preservar créditos de coautoria. Oferecer ordenação simples por título ou ano de publicação disponível, sem chamar esse ano de primeira publicação. Sem busca ou filtros nesta etapa.
5. Mostrar Leituras recentes após os livros, com título navegável, estado e datas legíveis. Evitar datas relativas e absolutas repetidas, progresso de 100% em conclusões e rótulos técnicos sem utilidade. Identificar o recorte de até 20 registros; não apresentar essa lista como histórico completo.
6. Exibir Séries no acervo ao final, somente quando houver vínculos, preservando navegação e associações existentes, inclusive coautorias. Estados de carregamento, vazio e erro devem ter copy contextual e recuperação, sem mensagem técnica bruta.

## API e arquivos principais

Reutilizar GET /api/books/authors/{authorId}: nome, livros, séries, contagens e leituras recentes. O modelo Author armazena nome e fingerprint; o DTO HardcoverAuthor atual traz apenas nome. Não há retrato, biografia ou identidade externa do autor pronta para uso nessa página.

Arquivos previstos: frontend/app/pages/books/authors/[id].vue, components/books/AuthorPageHero.vue, AuthorContextPanel.vue, types/books.ts e utils/books.ts. Avaliar BooksLibraryGrid, BookLibraryCard e BookReadTimeline por uso opt-in ou composição específica; preservar biblioteca, revistas e detalhe do livro. Registrar a decisão final em DESIGN.md e docs/books-api.md.

Migração: não. Novas dependências ou endpoints: não previstos.

## Regras e fora do escopo

Livros no acervo não significam bibliografia completa. Contagens de sessões não significam livros distintos. A ordem atual dos livros considera data de resenha, e não última leitura; não oferecer ordenação por atividade ou popularidade sem dados correspondentes por livro.

Foto, biografia, datas de vida, vínculos externos e enriquecimento de autor ficam para uma etapa própria, com identidade verificada, dados persistidos e atribuição. Não acrescentar esses campos por dedução do nome. Não criar registro manual de leitura, ranking, importação nova ou alterações na biblioteca geral.

## Critérios de aceite

- Desktop e celular: nome longo, um ou muitos livros, capas ausentes ou com falha, sem overflow e sem imagem apresentada como retrato do autor.
- Contagens corretas com releituras e estados em andamento; datas ausentes preservadas; recorte recente explícito e ausência de conteúdo sem seções vazias desnecessárias.
- Ordenação determinística por título/ano, com anos ausentes tratados explicitamente; navegação de livros, coautores e séries preservada.
- Foco visível, teclado, carregamento e erro com recuperação; lint, formatação, tipos, build e verificação de regressão dos componentes compartilhados.


## Validação realizada

Lint, formatação, tipos e build do frontend. Chromium em 1440px e 390px com APIs simuladas: ordenação, coautoria, contagens com releituras, recorte de 20 registros, capas ausentes/com falha, estados vazios, erro/recuperação e ausência de overflow. Componentes compartilhados de livro, revista e biblioteca mantêm seus contratos; a revisão usa composição específica do autor. Sem migração ou mudança de backend.

Publicação: versão 1.24.0, tag v1.24.0 e imagens 1.24.0/latest; implantação pelo serviço Compose existente no Mordor.

# Frontend

Frontend editorial do Media Pulse em Nuxt 4.

## Setup

Instale as dependências:

```bash
# npm
npm install

# pnpm
pnpm install

# yarn
yarn install

# bun
bun install
```

## Development

Suba o backend em paralelo e inicie o Nuxt em `http://localhost:3000`.

Se a API estiver em outra origem, defina `NUXT_PUBLIC_API_BASE`.

```bash
NUXT_PUBLIC_API_BASE=http://localhost:8080 npm run dev
```

Quando frontend e backend estiverem no mesmo domínio, o valor esperado é relativo:

```bash
NUXT_PUBLIC_API_BASE=/api npm run dev
```

## Build

```bash
npm run build
```

Isso gera o frontend estático em `.output/public`.

## Qualidade

Valide lint, formatação e tipos antes de abrir PR ou publicar mudanças:

```bash
npm run check
```

Esse comando executa `npm run lint`, `npm run format:check` e `npm run typecheck`.

Para verificar somente os tipos, execute `npm run typecheck`. TypeScript e `vue-tsc` são dependências de desenvolvimento com versões fixadas no projeto e registradas no lockfile. Isso evita que o Nuxt busque versões externas incompatíveis durante a validação.

Para aplicar correções automáticas:

```bash
npm run fix
```

Esse comando executa `npm run lint:fix` e `npm run format`.

## Revistas

`/magazines` oferece biblioteca visual com busca e filtros por publicação/estado, cadastro manual e paginação. `/magazines/{id}` apresenta capa, dados do número, comentários compartilhados e histórico de jornadas, com edição, progresso e releituras. Capas e ISSN são opcionais; o registro aceita numeração e/ou mês/ano. A API está documentada em `docs/magazines-api.md`.

## Docker

Build standalone do frontend:

```bash
docker build -f ./frontend/Dockerfile -t media-pulse-frontend:test .
```

Esse caminho é útil para validar apenas a camada de UI. Para produção com frontend e backend no mesmo domínio, use o `Dockerfile` raiz do repositório.

## Listas mistas

Listas de filmes e séries permitem incluir automaticamente favoritos e/ou abandonados, preservando inclusões manuais. Em filmes, abra “Editar lista”; em séries, use “Incluir automaticamente” na página da lista. As duas regras combinam-se por OU. Ordem e capa fixa aplicam-se aos itens manuais; itens automáticos aparecem depois. Consulte [a especificação e o roadmap](../docs/features/mixed-media-lists.md).

## Página do livro

A página prioriza capa, identificação, nota e estado da leitura, seguidos de sinopse expansível, sessões de leitura e comentário pessoal. Datas de início e término aparecem quando disponíveis; a edição vinculada à leitura mais recente tem prioridade, com as demais acessíveis em uma seção expansível.

O comentário é exibido diretamente, sem contadores ou resumo de atividade. Escrever e editar abrem os respectivos formulários; novos comentários permitem registrar outras impressões ou releituras. A data pode ser ajustada em “Alterar data”. Textos importados do Hardcover mantêm a indicação de origem e não oferecem edição local.

## Treinos

Acesse “Treinos” (`/workouts`) para navegar pelo histórico e filtrar corrida, corda ou academia. “Registrar treino” abre a escolha visual da atividade, seguida de um card compacto com data/horário, duração e a métrica específica. Local e foto são opcionais; a foto escolhida tem prévia e prioridade sobre a ilustração da categoria. Fechar o diálogo preserva o rascunho enquanto a página estiver aberta.

Imagens padrão ficam em `app/assets/images/workouts/`, empacotadas pelo Nuxt. Fotos próprias são persistidas pelo backend no storage de covers. Contrato e limites: [Workouts API](../docs/workouts-api.md).

Revistas mantém `/magazines` como dashboard, `/magazines/publications` como catálogo visual e `/magazines/publications/{id}` como arquivo anual. Detalhes dos números permanecem em `/magazines/{id}`; os recortes legados por query redirecionam para as páginas próprias.

## Artes visuais

`/artworks` reúne obras locais com busca e filtros por artista, tipo, movimento, favorito e lista. Cadastro manual combina metadados, criação/seleção de artista, primeira experiência opcional e múltiplas imagens por arquivo ou URL. O detalhe permite avaliação, favorito, galeria com imagem principal, experiências e listas. Artistas e listas têm diretórios e páginas próprias. Imagens preservam suas proporções; falhas de upload preservam a obra e permitem tentar somente as imagens pendentes. Contratos: [`artworks-api.md`](../docs/artworks-api.md).

O cadastro de artes visuais usa seletor de tipo com opção Outra, exemplos de técnica/movimento e País de origem (`countryOfOrigin`). Detalhes usam cabeçalho compacto e galeria ampla com miniaturas, navegação e ampliação na página. Textos curtos aparecem completos; descrição/biografia longas começam com um trecho expansível. Artistas reúnem retrato opcional, dados e biografia, com obras locais agrupadas por ano de criação e Sem ano ao final. Controles de imagens/listas aparecem sob demanda; Gerenciar imagens permite escolher a principal, remover e adicionar.

## Imagens centralizadas

Revistas, artes visuais, livros e jogos podem usar Visual Library conforme [configuração do backend](../docs/visual-library-images.md). A interface continua usando `NUXT_PUBLIC_API_BASE`; nenhuma URL adicional precisa ser configurada no frontend. Referências `/api/image-assets/{id}/content` são resolvidas no backend. Falhas de leitura mostram placeholders e mantêm dados e ações. O formulário de revistas preserva o ID/arquivo após sucesso parcial com `coverUploadError` e tenta apenas a capa novamente, sem criar outro número.

Movimentos e estilos usa um campo múltiplo com datalist nativo, lista inicial + termos do acervo e chips removíveis. O último termo digitado é incluído ao salvar. Detalhe apresenta lista e estado de sincronização; falhas remotas não apagam a correção local e oferecem retry.

Livros e jogos oferecem “Alterar capa” no detalhe, com seleção de arquivo, prévia e envio manual. Erro mantém o arquivo para repetir somente o upload; sucesso atualiza a capa exibida. Capas do Hardcover (livros/edições), SteamGridDB e IGDB passam pelo armazenamento configurado quando ausentes. Imagens já salvas são preservadas, sem migração automática.

A página do jogo também oferece “Buscar imagens” em um card de Catálogo no padrão de filmes/séries. A ação usa os vínculos existentes, acrescenta imagens e preserva a capa. Exibe carregamento, imagens novas/ausentes e falhas com retry, atualizando a galeria após sucesso.

## Créditos de artistas em música

A biblioteca, a busca e as discografias exibem créditos completos de álbuns. Listas têm links individuais para os artistas. Na revisão de duplicados, a prévia permite escolher o álbum de origem dos créditos e mostra a lista final antes da confirmação. Contratos e validação: `docs/music-api.md`, na raiz do projeto.

A página `/music/admin/album-artist-backfills` completa créditos do acervo em lotes de até 10 álbuns. Permite busca e seleção ou seleção automática, revisão antes de aplicar, consulta dos lotes recentes, novas tentativas e desfazimento com confirmação. O padrão visual e a navegação seguem os painéis administrativos de música. A página integra a versão 1.4.0 e exige as APIs e a migração V70, introduzidas na beta.5 do backend. O controller SPA permite acesso direto e recarregamento dessa rota, com ou sem barra final.

## Séries de livros e universos

A área Livros oferece `/books/series` e `/books/universes`, com criação e páginas de detalhe por ID. Um livro pode participar de várias séries, e uma série de vários universos. Cada agrupamento mantém sua própria ordem de leitura, editável pelos controles Subir/Descer, acessíveis por teclado. Novos vínculos entram ao final.

O detalhe do livro permite gerenciar suas séries e buscar sugestões explicitamente no Hardcover, revisando os livros locais selecionados e escolhendo criar uma série ou reutilizar uma existente. A ingestão de livros continua pelo Hardcover; para livros antigos, execute uma sincronização antes de buscar sugestões. Contratos e regras em [Books API](../docs/books-api.md#séries-e-universos).

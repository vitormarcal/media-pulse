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

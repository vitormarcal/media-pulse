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

Valide lint e formatação antes de abrir PR ou publicar mudanças:

```bash
npm run check
```

Esse comando executa `npm run lint` e `npm run format:check`.

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

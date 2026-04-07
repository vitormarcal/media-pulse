# Frontend

Frontend estático em `./frontend` para consumir a API do Media Pulse.

## Estrutura

- `index.html`: shell da página
- `styles.css`: tema visual
- `app.js`: consumo das APIs de livros, música, filmes e séries

## Desenvolvimento local

Você pode servir o diretório com qualquer servidor estático simples:

```bash
cd frontend
python3 -m http.server 4173
```

Depois acesse `http://localhost:4173`.

## API base

- Por padrão o frontend usa `http://localhost:8080`
- O botão de configuração da API salva a origem escolhida em `localStorage`
- Se o frontend rodar em origem separada, o backend precisa permitir essa origem em `media-pulse.allowed-origin`

O `application.yml` versionado libera apenas `http://localhost:8080` por padrão. Para usar `4173`, `5500` ou outra porta no desenvolvimento, ajuste a configuração local do backend.

## Empacotamento com o backend

O build do backend fingerprinta os assets do frontend e os copia para recursos estáticos do Spring Boot. Isso permite servir o frontend pelo mesmo artefato do servidor quando necessário.

# Frontend

Home estática em `./frontend` para consultar a API do Media Pulse.

## Estrutura

- `index.html`: shell da página
- `styles.css`: tema visual inspirado no `DESIGN.md`
- `app.js`: consumo das APIs `movies`, `music`, `books` e `shows`

## Como abrir

Sirva o diretório com um servidor estático simples, por exemplo:

```bash
cd frontend
python3 -m http.server 4173
```

Depois acesse `http://localhost:4173`.

## API base

- Por padrão o frontend usa `http://localhost:8080`
- O botão `API base` no topo permite trocar a origem da API e salva o valor em `localStorage`
- O backend agora aceita por padrão `localhost:4173` e `localhost:5500` no CORS

# Books API

A Books API fornece uma visão read-only da biblioteca e leituras. Ela expõe cards para UI (livros, autores, edições e leituras), detalhes de um livro, listagens paginadas com cursor e um resumo agregado por período.

## Modelo de leitura

As leituras são armazenadas como **sessões/jornadas consolidadas** (não como eventos brutos de sincronização).

- Cada sessão possui um `readId` interno no Media Pulse.
- O payload da API de livros não depende de `source_event_id`.
- Transições de status da mesma jornada (`want -> reading -> read`) são refletidas na própria sessão.

## Endpoints

| Path | Params | Retorna |
| --- | --- | --- |
| `GET /api/books/year/{year}` | `year` (path) | `YearReadsResponse` |
| `GET /api/books/{bookId}` | `bookId` (path) | `BookDetailsResponse` |
| `GET /api/books/slug/{slug}` | `slug` (path) | `BookDetailsResponse` |
| `GET /api/books/list` | `status?`, `limit=20`, `cursor?` | `BooksListResponse` |
| `GET /api/books/search` | `q`, `limit=10` | `BooksSearchResponse` |
| `GET /api/books/summary` | `range=month|year|custom`, `start?`, `end?` | `BooksSummaryResponse` |

## Cursor de paginação

O cursor é simples e baseado no `readId` (id interno da sessão de leitura). Formato: `id:123`.

- O endpoint `GET /api/books/list` retorna `nextCursor` quando há mais itens.
- Para a próxima página, envie `cursor=id:123` (o último `readId` retornado) para buscar itens com `id` menor.

## Range do year

O endpoint `GET /api/books/year/{year}` usa um range UTC fixo:

- **Início:** `01/01/{year} 00:00:00 UTC`
- **Fim (exclusivo):** `01/01/{year+1} 00:00:00 UTC`

## Exemplos

### Year reads

```bash
curl "{{host}}/api/books/year/2025"
```

```json
{
  "year": 2025,
  "range": {
    "start": "2025-01-01T00:00:00Z",
    "end": "2026-01-01T00:00:00Z"
  },
  "currentlyReading": [],
  "finished": [
    {
      "readId": 481,
      "status": "READ",
      "startedAt": "2025-02-10T20:00:00Z",
      "finishedAt": "2025-03-01T22:10:00Z",
      "progressPct": 100.0,
      "progressPages": 384,
      "source": "HARDCOVER",
      "book": {
        "bookId": 52,
        "slug": "52_the_dispossessed",
        "title": "The Dispossessed",
        "coverUrl": "https://img.example/book/52.jpg",
        "releaseDate": "1974-05-01",
        "rating": 4.5,
        "reviewedAt": "2025-03-02T12:00:00Z",
        "authors": [{ "id": 9, "name": "Ursula K. Le Guin" }]
      },
      "edition": {
        "id": 112,
        "title": "Paperback",
        "isbn10": "006051275X",
        "isbn13": "9780060512750",
        "pages": 384,
        "language": "en",
        "publisher": "Harper",
        "format": "Paperback",
        "coverUrl": "https://img.example/edition/112.jpg"
      }
    }
  ],
  "paused": [],
  "didNotFinish": [],
  "wantToRead": [],
  "unknown": [],
  "stats": {
    "finishedCount": 1,
    "currentlyReadingCount": 0,
    "wantCount": 0,
    "didNotFinishCount": 0,
    "pausedCount": 0,
    "pagesFinished": 384
  }
}
```

### Book details

```bash
curl "{{host}}/api/books/52"
```

### Book details by slug

```bash
curl "{{host}}/api/books/slug/52_the_dispossessed"
```

```json
{
  "bookId": 52,
  "slug": "52_the_dispossessed",
  "title": "The Dispossessed",
  "description": "A brilliant utopian novel about two worlds.",
  "coverUrl": "https://img.example/book/52.jpg",
  "releaseDate": "1974-05-01",
  "rating": 4.5,
  "reviewRaw": "Loved the contrast between the worlds.",
  "reviewedAt": "2025-03-02T12:00:00Z",
  "authors": [{ "id": 9, "name": "Ursula K. Le Guin" }],
  "editions": [],
  "reads": []
}
```

```json
{
  "bookId": 52,
  "slug": "52_the_dispossessed",
  "title": "The Dispossessed",
  "description": "A brilliant utopian novel about two worlds.",
  "coverUrl": "https://img.example/book/52.jpg",
  "releaseDate": "1974-05-01",
  "rating": 4.5,
  "reviewRaw": "Loved the contrast between the worlds.",
  "reviewedAt": "2025-03-02T12:00:00Z",
  "authors": [{ "id": 9, "name": "Ursula K. Le Guin" }],
  "editions": [
    {
      "id": 112,
      "title": "Paperback",
      "isbn10": "006051275X",
      "isbn13": "9780060512750",
      "pages": 384,
      "language": "en",
      "publisher": "Harper",
      "format": "Paperback",
      "coverUrl": "https://img.example/edition/112.jpg"
    }
  ],
  "reads": [
    {
      "readId": 481,
      "status": "READ",
      "startedAt": "2025-02-10T20:00:00Z",
      "finishedAt": "2025-03-01T22:10:00Z",
      "progressPct": 100.0,
      "progressPages": 384,
      "source": "HARDCOVER",
      "book": {
        "bookId": 52,
        "slug": "52_the_dispossessed",
        "title": "The Dispossessed",
        "coverUrl": "https://img.example/book/52.jpg",
        "releaseDate": "1974-05-01",
        "rating": 4.5,
        "reviewedAt": "2025-03-02T12:00:00Z",
        "authors": [{ "id": 9, "name": "Ursula K. Le Guin" }]
      },
      "edition": {
        "id": 112,
        "title": "Paperback",
        "isbn10": "006051275X",
        "isbn13": "9780060512750",
        "pages": 384,
        "language": "en",
        "publisher": "Harper",
        "format": "Paperback",
        "coverUrl": "https://img.example/edition/112.jpg"
      }
    }
  ]
}
```

### List reads (paginado)

```bash
curl "{{host}}/api/books/list?status=read&limit=2"
```

```json
{
  "items": [
    {
      "readId": 481,
      "status": "READ",
      "startedAt": "2025-02-10T20:00:00Z",
      "finishedAt": "2025-03-01T22:10:00Z",
      "progressPct": 100.0,
      "progressPages": 384,
      "source": "HARDCOVER",
      "book": {
        "bookId": 52,
        "slug": "52_the_dispossessed",
        "title": "The Dispossessed",
        "coverUrl": "https://img.example/book/52.jpg",
        "releaseDate": "1974-05-01",
        "rating": 4.5,
        "reviewedAt": "2025-03-02T12:00:00Z",
        "authors": [{ "id": 9, "name": "Ursula K. Le Guin" }]
      },
      "edition": null
    },
    {
      "readId": 479,
      "status": "READ",
      "startedAt": "2025-01-02T10:00:00Z",
      "finishedAt": "2025-01-21T18:40:00Z",
      "progressPct": 100.0,
      "progressPages": 256,
      "source": "HARDCOVER",
      "book": {
        "bookId": 49,
        "slug": "49_kindred",
        "title": "Kindred",
        "coverUrl": "https://img.example/book/49.jpg",
        "releaseDate": "1979-06-01",
        "rating": 4.0,
        "reviewedAt": null,
        "authors": [{ "id": 7, "name": "Octavia E. Butler" }]
      },
      "edition": null
    }
  ],
  "nextCursor": "id:479"
}
```

### Search

```bash
curl "{{host}}/api/books/search?q=ursula&limit=5"
```

```json
{
  "books": [
    {
      "bookId": 52,
      "slug": "52_the_dispossessed",
      "title": "The Dispossessed",
      "coverUrl": "https://img.example/book/52.jpg",
      "releaseDate": "1974-05-01",
      "rating": 4.5,
      "reviewedAt": "2025-03-02T12:00:00Z",
      "authors": [{ "id": 9, "name": "Ursula K. Le Guin" }]
    }
  ],
  "authors": [{ "id": 9, "name": "Ursula K. Le Guin" }]
}
```

### Summary

```bash
curl "{{host}}/api/books/summary?range=month"
```

```json
{
  "range": {
    "start": "2025-11-01T00:00:00Z",
    "end": "2025-12-01T00:00:00Z"
  },
  "counts": {
    "finished": 2,
    "reading": 1,
    "want": 3,
    "dnf": 0,
    "paused": 1,
    "total": 7
  },
  "topAuthors": [
    { "authorId": 9, "authorName": "Ursula K. Le Guin", "finishedCount": 1 },
    { "authorId": 7, "authorName": "Octavia E. Butler", "finishedCount": 1 }
  ]
}
```

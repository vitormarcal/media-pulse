const DEFAULT_API_BASE_URL = localStorage.getItem("media-pulse-api-base") || "/";

const summaryConfigs = {
  movies: {
    endpoint: "/api/movies/summary?range=month",
    parse: (payload) => ({
      value: `${payload.uniqueMoviesCount ?? 0}`,
      detail: `${payload.watchesCount ?? 0} watches nos ultimos 30 dias`,
    }),
  },
  music: {
    endpoint: "/api/music/summary?range=month",
    parse: (payload) => ({
      value: `${payload.albumsCount ?? 0}`,
      detail: `${payload.tracksCount ?? 0} faixas e ${payload.artistsCount ?? 0} artistas`,
    }),
  },
  books: {
    endpoint: "/api/books/summary?range=month",
    parse: (payload) => ({
      value: `${payload.counts?.total ?? 0}`,
      detail: `${payload.counts?.finished ?? 0} finalizados no periodo`,
    }),
  },
  shows: {
    endpoint: "/api/shows/summary?range=month",
    parse: (payload) => ({
      value: `${payload.uniqueShowsCount ?? 0}`,
      detail: `${payload.watchesCount ?? 0} episodios vistos nos ultimos 30 dias`,
    }),
  },
};

const recentConfigs = {
  movies: {
    endpoint: "/api/movies/recent?limit=8",
    normalize: (items) =>
      items.map((item) => ({
        href: buildDetailUrl("movie", { slug: item.slug, id: item.movieId }),
        image: item.coverUrl,
        kicker: "Movie",
        title: item.title,
        meta: [item.originalTitle, item.year].filter(Boolean).join(" • "),
        date: formatDate(item.watchedAt, "Assistido"),
      })),
  },
  music: {
    endpoint: "/api/music/recent-albums?limit=8",
    normalize: (items) =>
      items.map((item) => ({
        href: buildDetailUrl("music-album", { id: item.albumId }),
        image: item.coverUrl,
        kicker: "Album",
        title: item.albumTitle,
        meta: [item.artistName, item.year].filter(Boolean).join(" • "),
        date: formatDate(item.lastPlayed, `${item.playCount} plays`),
      })),
  },
  books: {
    endpoint: "/api/books/list?limit=8",
    normalize: (payload) =>
      (payload.items || []).map((item) => ({
        href: buildDetailUrl("book", { slug: item.book?.slug, id: item.book?.bookId }),
        image: item.book?.coverUrl || item.edition?.coverUrl,
        kicker: "Book",
        title: item.book?.title || "Livro",
        meta: [
          item.book?.authors?.map((author) => author.name).join(", "),
          translateBookStatus(item.status),
        ]
          .filter(Boolean)
          .join(" • "),
        date: formatDate(item.finishedAt || item.startedAt || item.book?.reviewedAt, "Atualizado"),
      })),
  },
  shows: {
    endpoint: "/api/shows/recent?limit=8",
    normalize: (items) =>
      items.map((item) => ({
        href: buildDetailUrl("show", { slug: item.slug, id: item.showId }),
        image: item.coverUrl,
        kicker: "Show",
        title: item.title,
        meta: [item.originalTitle, item.year].filter(Boolean).join(" • "),
        date: formatDate(item.watchedAt, "Visto"),
      })),
  },
};

const searchConfigs = {
  movies: {
    endpoint: (query) => `/api/movies/search?q=${encodeURIComponent(query)}&limit=8`,
    title: "Movies",
    extract: (payload) =>
      (payload.movies || []).map((item) => ({
        href: buildDetailUrl("movie", { slug: item.slug, id: item.movieId }),
        tag: "Movie",
        title: item.title,
        meta: [item.originalTitle, item.year, formatDate(item.watchedAt, "ultimo watch")].filter(Boolean).join(" • "),
      })),
  },
  music: {
    endpoint: (query) => `/api/music/search?q=${encodeURIComponent(query)}&limit=8`,
    title: "Music",
    extract: (payload) => [
      ...(payload.artists || []).map((item) => ({
        href: buildDetailUrl("music-artist", { q: item.name, label: item.name, id: item.id }),
        tag: "Artist",
        title: item.name,
        meta: "resultado em artistas",
      })),
      ...(payload.albums || []).map((item) => ({
        href: buildDetailUrl("music-album", { id: item.id }),
        tag: "Album",
        title: item.title,
        meta: [item.artistName, item.year].filter(Boolean).join(" • "),
      })),
      ...(payload.tracks || []).map((item) => ({
        href: buildDetailUrl("music-track", { q: item.title, label: item.title, id: item.id }),
        tag: "Track",
        title: item.title,
        meta: [item.artistName, item.albumTitle].filter(Boolean).join(" • "),
      })),
    ],
  },
  books: {
    endpoint: (query) => `/api/books/search?q=${encodeURIComponent(query)}&limit=8`,
    title: "Books",
    extract: (payload) => [
      ...(payload.books || []).map((item) => ({
        href: buildDetailUrl("book", { slug: item.slug, id: item.bookId }),
        tag: "Book",
        title: item.title,
        meta: item.authors?.map((author) => author.name).join(", ") || "sem autor",
      })),
      ...(payload.authors || []).map((item) => ({
        href: buildDetailUrl("book-author", { q: item.name, label: item.name, id: item.id }),
        tag: "Author",
        title: item.name,
        meta: "resultado em autores",
      })),
    ],
  },
  shows: {
    endpoint: (query) => `/api/shows/search?q=${encodeURIComponent(query)}&limit=8`,
    title: "Shows",
    extract: (payload) =>
      (payload.shows || []).map((item) => ({
        href: buildDetailUrl("show", { slug: item.slug, id: item.showId }),
        tag: "Show",
        title: item.title,
        meta: [item.originalTitle, item.year, formatDate(item.watchedAt, "ultimo episodio")].filter(Boolean).join(" • "),
      })),
  },
};

const state = {
  apiBaseUrl: DEFAULT_API_BASE_URL.replace(/\/$/, ""),
};

const template = document.querySelector("#pin-card-template");
const searchForm = document.querySelector("#search-form");
const searchInput = document.querySelector("#search-input");
const searchStatus = document.querySelector("#search-status");
const searchResultsSection = document.querySelector("#search-results-section");
const searchResults = document.querySelector("#search-results");
const clearSearchButton = document.querySelector("#clear-search-button");
const apiBaseButton = document.querySelector("#api-base-button");

initialize();

function initialize() {
  apiBaseButton.textContent = compactApiLabel(state.apiBaseUrl);
  apiBaseButton.addEventListener("click", handleApiBaseChange);
  searchForm.addEventListener("submit", handleSearchSubmit);
  clearSearchButton.addEventListener("click", clearSearch);
  searchInput.addEventListener("input", handleSearchInput);

  loadSummaries();
  loadRecentCollections();
}

async function loadSummaries() {
  await Promise.all(
    Object.entries(summaryConfigs).map(async ([key, config]) => {
      const card = document.querySelector(`[data-summary="${key}"]`);
      try {
        const payload = await fetchJson(config.endpoint);
        const parsed = config.parse(payload);
        card.querySelector(".summary-value").textContent = parsed.value;
        card.querySelector(".summary-detail").textContent = parsed.detail;
      } catch (error) {
        card.querySelector(".summary-value").textContent = "erro";
        card.querySelector(".summary-detail").textContent = error.message;
      }
    }),
  );
}

async function loadRecentCollections() {
  await Promise.all(
    Object.entries(recentConfigs).map(async ([key, config]) => {
      const grid = document.querySelector(`#${key}-grid`);
      grid.replaceChildren(buildLoadingCard());

      try {
        const payload = await fetchJson(config.endpoint);
        const items = config.normalize(payload);
        renderPinGrid(grid, items);
      } catch (error) {
        grid.replaceChildren(buildMessageCard(`Nao foi possivel carregar ${key}: ${error.message}`));
      }
    }),
  );
}

let searchDebounce = null;

function handleSearchInput(event) {
  const query = event.currentTarget.value.trim();

  if (!query) {
    clearSearch();
    return;
  }

  window.clearTimeout(searchDebounce);
  searchDebounce = window.setTimeout(() => runSearch(query), 260);
}

function handleSearchSubmit(event) {
  event.preventDefault();
  const query = searchInput.value.trim();

  if (!query) {
    clearSearch();
    return;
  }

  runSearch(query);
}

async function runSearch(query) {
  searchResultsSection.classList.remove("hidden");
  searchResults.replaceChildren(buildMessageCard("Buscando em todas as APIs..."));
  searchStatus.textContent = `Buscando por "${query}"`;

  const groups = await Promise.all(
    Object.entries(searchConfigs).map(async ([key, config]) => {
      try {
        const payload = await fetchJson(config.endpoint(query));
        return {
          key,
          title: config.title,
          items: config.extract(payload),
        };
      } catch (error) {
        return {
          key,
          title: config.title,
          items: [],
          error: error.message,
        };
      }
    }),
  );

  searchResults.replaceChildren();
  const fragments = groups.map(buildResultGroup);
  searchResults.append(...fragments);

  const total = groups.reduce((sum, group) => sum + group.items.length, 0);
  searchStatus.textContent =
    total > 0 ? `${total} resultados agregados para "${query}"` : `Nenhum resultado para "${query}"`;
}

function buildResultGroup(group) {
  const article = document.createElement("article");
  article.className = "result-group";

  const title = document.createElement("h4");
  title.textContent = group.title;
  article.append(title);

  if (group.error) {
    article.append(buildMessageCard(`Erro: ${group.error}`));
    return article;
  }

  if (group.items.length === 0) {
    article.append(buildMessageCard("Sem resultados nesta coleção."));
    return article;
  }

  const list = document.createElement("div");
  list.className = "result-list";

  group.items.forEach((item) => {
    const entry = item.href ? document.createElement("a") : document.createElement("article");
    entry.className = "result-item";
    if (item.href) {
      entry.href = item.href;
    }
    entry.innerHTML = `
      <span class="result-item-tag">${escapeHtml(item.tag)}</span>
      <h5>${escapeHtml(item.title)}</h5>
      <p class="result-item-meta">${escapeHtml(item.meta || "")}</p>
    `;
    list.append(entry);
  });

  article.append(list);
  return article;
}

function renderPinGrid(container, items) {
  container.replaceChildren();

  if (!items.length) {
    container.append(buildMessageCard("Nenhum item encontrado."));
    return;
  }

  items.forEach((item) => {
    const node = template.content.firstElementChild.cloneNode(true);
    const link = node;
    const image = node.querySelector(".pin-image");
    const kicker = node.querySelector(".pin-kicker");
    const title = node.querySelector(".pin-title");
    const meta = node.querySelector(".pin-meta");
    const date = node.querySelector(".pin-date");

    link.href = item.href || "#";

    image.src = resolveAssetUrl(item.image);
    image.alt = item.title;
    image.onerror = () => {
      image.src = createPlaceholderDataUrl(item.title);
    };

    kicker.textContent = item.kicker;
    title.textContent = item.title;
    meta.textContent = item.meta || "Sem metadados";
    date.textContent = item.date || "Sem data";

    container.append(node);
  });
}

function buildLoadingCard() {
  return buildMessageCard("Carregando...");
}

function buildMessageCard(message) {
  const node = document.createElement("div");
  node.className = "empty-state";
  node.textContent = message;
  return node;
}

function clearSearch() {
  searchInput.value = "";
  searchResults.replaceChildren();
  searchResultsSection.classList.add("hidden");
  searchStatus.textContent = "Digite para consultar `movies`, `music`, `books` e `shows`.";
}

async function fetchJson(path) {
  const response = await fetch(`${state.apiBaseUrl}${path}`, {
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}`);
  }

  return response.json();
}

function resolveAssetUrl(value) {
  if (!value) {
    return createPlaceholderDataUrl("Media Pulse");
  }

  if (/^https?:\/\//.test(value) || value.startsWith("data:")) {
    return value;
  }

  return `${state.apiBaseUrl}${value.startsWith("/") ? value : `/${value}`}`;
}

function handleApiBaseChange() {
  const nextValue = window.prompt("Informe a base da API", state.apiBaseUrl);
  if (!nextValue) {
    return;
  }

  state.apiBaseUrl = nextValue.replace(/\/$/, "");
  localStorage.setItem("media-pulse-api-base", state.apiBaseUrl);
  apiBaseButton.textContent = compactApiLabel(state.apiBaseUrl);
  searchStatus.textContent = `API atual: ${state.apiBaseUrl}`;
  loadSummaries();
  loadRecentCollections();
}

function compactApiLabel(url) {
  return `API: ${url.replace(/^https?:\/\//, "")}`;
}

function buildDetailUrl(kind, identifiers = {}) {
  const search = new URLSearchParams({ kind });
  Object.entries(identifiers).forEach(([key, value]) => {
    if (value !== null && value !== undefined && value !== "") {
      search.set(key, value);
    }
  });
  return `./detail.html?${search.toString()}`;
}

function formatDate(value, prefix) {
  if (!value) {
    return "";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }

  const formatter = new Intl.DateTimeFormat("pt-BR", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  });

  return `${prefix} ${formatter.format(date)}`;
}

function translateBookStatus(status) {
  const map = {
    READ: "lido",
    READING: "lendo",
    WANT_TO_READ: "quero ler",
    PAUSED: "pausado",
    DID_NOT_FINISH: "abandonei",
    UNKNOWN: "desconhecido",
  };

  return map[status] || status || "";
}

function createPlaceholderDataUrl(label) {
  const safeLabel = (label || "Media Pulse").slice(0, 24);
  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" width="600" height="800" viewBox="0 0 600 800">
      <defs>
        <linearGradient id="g" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stop-color="#f2efe8" />
          <stop offset="100%" stop-color="#e0e0d9" />
        </linearGradient>
      </defs>
      <rect width="600" height="800" rx="36" fill="url(#g)" />
      <circle cx="88" cy="88" r="36" fill="#e60023" />
      <text x="88" y="100" text-anchor="middle" font-family="Arial, sans-serif" font-size="36" font-weight="700" fill="#ffffff">M</text>
      <text x="54" y="706" font-family="Arial, sans-serif" font-size="28" font-weight="700" fill="#211922">${escapeSvg(safeLabel)}</text>
      <text x="54" y="744" font-family="Arial, sans-serif" font-size="18" fill="#62625b">media pulse</text>
    </svg>
  `;

  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
}

function escapeSvg(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

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
    endpoint: "/api/movies/recent?limit=12",
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
    endpoint: "/api/music/recent-albums?limit=12",
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
    endpoint: "/api/books/list?status=read&limit=12",
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
    endpoint: "/api/shows/recent?limit=12",
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
        href: buildDetailUrl("music-artist", { id: item.id, label: item.name }),
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
        href: buildDetailUrl("music-track", { id: item.id, label: item.title }),
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
        href: buildDetailUrl("book-author", { id: item.id, label: item.name }),
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

const pinTemplate = document.querySelector("#pin-card-template");
const featureTemplate = document.querySelector("#feature-card-template");
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

  loadHome();
}

async function loadHome() {
  await Promise.all([loadSummaries(), loadEditorialHome(), loadMixedMosaic()]);
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

async function loadEditorialHome() {
  const now = new Date();
  const weekStart = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000).toISOString();
  const weekEnd = now.toISOString();

  const [currentlyWatching, readingNow, topArtists, recentAlbums, recentMovies] = await Promise.all([
    fetchSafe("/api/shows/currently-watching?limit=6&activeWithinDays=90", []),
    fetchSafe("/api/books/list?status=currently_reading&limit=6", { items: [] }),
    fetchSafe(`/api/music/tops/artists?start=${encodeURIComponent(weekStart)}&end=${encodeURIComponent(weekEnd)}&limit=6`, []),
    fetchSafe("/api/music/recent-albums?limit=6", []),
    fetchSafe("/api/movies/recent?limit=6", []),
  ]);

  renderSpotlights({
    currentlyWatching,
    readingNow: readingNow.items || [],
    topArtists,
    recentAlbums,
    recentMovies,
  });

  renderFeatureRail(
    "#continue-watching-rail",
    currentlyWatching.map((item) => ({
      href: buildDetailUrl("show", { slug: item.slug, id: item.showId }),
      image: item.coverUrl,
      badge: item.progress?.completed ? "Done" : `${computePercent(item.progress?.watchedEpisodesCount, item.progress?.episodesCount)}%`,
      kicker: "Show",
      title: item.title,
      meta: [item.originalTitle, item.year].filter(Boolean).join(" • "),
      progressCurrent: item.progress?.watchedEpisodesCount ?? 0,
      progressTotal: item.progress?.episodesCount ?? 0,
      progressLabel: `${item.progress?.watchedEpisodesCount ?? 0}/${item.progress?.episodesCount ?? 0} episódios`,
    })),
    "Nenhuma série em andamento.",
  );

  renderFeatureRail(
    "#reading-now-rail",
    (readingNow.items || []).map((item) => ({
      href: buildDetailUrl("book", { slug: item.book?.slug, id: item.book?.bookId }),
      image: item.book?.coverUrl || item.edition?.coverUrl,
      badge: translateBookStatus(item.status),
      kicker: "Book",
      title: item.book?.title || "Livro",
      meta: item.book?.authors?.map((author) => author.name).join(", ") || "sem autor",
      progressCurrent: Math.round(item.progressPct ?? 0),
      progressTotal: 100,
      progressLabel: item.progressPages ? `${item.progressPages} páginas` : `${Math.round(item.progressPct ?? 0)}%`,
    })),
    "Nenhuma leitura em andamento.",
  );

  renderFeatureRail(
    "#heavy-rotation-rail",
    topArtists.map((item, index) => ({
      href: buildDetailUrl("music-artist", { id: item.artistId, label: item.artistName }),
      image: recentAlbums[index]?.coverUrl || null,
      badge: `${item.playCount} plays`,
      kicker: "Artist",
      title: item.artistName,
      meta: recentAlbums[index] ? `ecoando com ${recentAlbums[index].albumTitle}` : "artista em alta na semana",
      progressCurrent: item.playCount,
      progressTotal: topArtists[0]?.playCount || item.playCount || 1,
      progressLabel: "na ultima semana",
    })),
    "Sem rotação pesada no período.",
  );
}

async function loadMixedMosaic() {
  const mixedGrid = document.querySelector("#mixed-grid");
  mixedGrid.replaceChildren(buildLoadingCard());

  const [moviesPayload, musicPayload, booksPayload, showsPayload] = await Promise.all([
    fetchSafe(recentConfigs.movies.endpoint, []),
    fetchSafe(recentConfigs.music.endpoint, []),
    fetchSafe(recentConfigs.books.endpoint, { items: [] }),
    fetchSafe(recentConfigs.shows.endpoint, []),
  ]);

  const pools = {
    movies: recentConfigs.movies.normalize(moviesPayload).map((item) => ({ ...item, weight: 1, type: "movies" })),
    music: recentConfigs.music.normalize(musicPayload).map((item) => ({ ...item, weight: 1, type: "music" })),
    books: recentConfigs.books.normalize(booksPayload).map((item) => ({ ...item, weight: 1, type: "books" })),
    shows: recentConfigs.shows.normalize(showsPayload).map((item) => ({ ...item, weight: 1, type: "shows" })),
  };

  const mixedItems = applyFeaturedPins(buildBalancedFeed(pools, 18));
  renderPinGrid(mixedGrid, mixedItems);
}

function renderSpotlights({ currentlyWatching, readingNow, topArtists, recentAlbums, recentMovies }) {
  const main = document.querySelector("#spotlight-feature");
  const side = document.querySelector("#spotlight-secondary");

  const primaryShow = currentlyWatching[0];
  if (primaryShow) {
    main.innerHTML = `
      <a class="spotlight-link" href="${escapeAttribute(buildDetailUrl("show", { slug: primaryShow.slug, id: primaryShow.showId }))}">
        <div class="spotlight-media">
          <img class="spotlight-image" src="${escapeAttribute(resolveAssetUrl(primaryShow.coverUrl))}" alt="${escapeAttribute(primaryShow.title)}" />
        </div>
        <div class="spotlight-copy">
          <p class="eyebrow">Continue watching</p>
          <h3>${escapeHtml(primaryShow.title)}</h3>
          <p class="spotlight-meta">${escapeHtml([primaryShow.originalTitle, primaryShow.year].filter(Boolean).join(" • "))}</p>
          <div class="spotlight-progress">
            <div class="feature-progress-bar">
              <span class="feature-progress-fill" style="width:${computePercent(primaryShow.progress?.watchedEpisodesCount, primaryShow.progress?.episodesCount)}%"></span>
            </div>
            <p>${escapeHtml(`${primaryShow.progress?.watchedEpisodesCount ?? 0}/${primaryShow.progress?.episodesCount ?? 0} episódios`)}</p>
          </div>
        </div>
      </a>
    `;
  } else {
    main.replaceChildren(buildMessageCard("Nenhuma série em andamento."));
  }

  const reading = readingNow[0];
  const artist = topArtists[0];
  const movie = recentMovies[0];
  side.innerHTML = `
    ${reading ? renderMiniSpotlight({
      href: buildDetailUrl("book", { slug: reading.book?.slug, id: reading.book?.bookId }),
      kicker: "Reading now",
      title: reading.book?.title || "Livro",
      meta: reading.book?.authors?.map((author) => author.name).join(", ") || "sem autor",
      badge: `${Math.round(reading.progressPct ?? 0)}%`,
    }) : ""}
    ${artist ? renderMiniSpotlight({
      href: buildDetailUrl("music-artist", { id: artist.artistId, label: artist.artistName }),
      kicker: "Heavy rotation",
      title: artist.artistName,
      meta: `${artist.playCount} plays na semana`,
      badge: "Artist",
    }) : ""}
    ${movie ? renderMiniSpotlight({
      href: buildDetailUrl("movie", { slug: movie.slug, id: movie.movieId }),
      kicker: "Recent watch",
      title: movie.title,
      meta: formatDate(movie.watchedAt, "visto"),
      badge: movie.year || "Movie",
    }) : ""}
    ${recentAlbums[0] ? renderMiniSpotlight({
      href: buildDetailUrl("music-album", { id: recentAlbums[0].albumId }),
      kicker: "Fresh album",
      title: recentAlbums[0].albumTitle,
      meta: recentAlbums[0].artistName,
      badge: `${recentAlbums[0].playCount} plays`,
    }) : ""}
  `;
}

function renderMiniSpotlight(item) {
  return `
    <a class="mini-spotlight" href="${escapeAttribute(item.href)}">
      <p class="mini-spotlight-kicker">${escapeHtml(item.kicker)}</p>
      <h4>${escapeHtml(item.title)}</h4>
      <p class="mini-spotlight-meta">${escapeHtml(item.meta)}</p>
      <span class="mini-spotlight-badge">${escapeHtml(String(item.badge))}</span>
    </a>
  `;
}

function renderFeatureRail(selector, items, emptyMessage) {
  const rail = document.querySelector(selector);
  rail.replaceChildren();

  if (!items.length) {
    rail.append(buildMessageCard(emptyMessage));
    return;
  }

  items.forEach((item) => {
    const node = featureTemplate.content.firstElementChild.cloneNode(true);
    const image = node.querySelector(".feature-card-image");
    const badge = node.querySelector(".feature-card-badge");
    const kicker = node.querySelector(".feature-card-kicker");
    const title = node.querySelector(".feature-card-title");
    const meta = node.querySelector(".feature-card-meta");
    const progress = node.querySelector(".feature-card-progress");
    const fill = node.querySelector(".feature-progress-fill");
    const progressLabel = node.querySelector(".feature-progress-label");

    node.href = item.href || "#";
    image.src = resolveAssetUrl(item.image);
    image.alt = item.title;
    image.onerror = () => {
      image.src = createPlaceholderDataUrl(item.title);
    };
    badge.textContent = item.badge || "";
    kicker.textContent = item.kicker || "";
    title.textContent = item.title || "";
    meta.textContent = item.meta || "";

    if (item.progressTotal && item.progressCurrent !== undefined) {
      progress.classList.remove("hidden");
      fill.style.width = `${computePercent(item.progressCurrent, item.progressTotal)}%`;
      progressLabel.textContent = item.progressLabel || "";
    }

    rail.append(node);
  });
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
    Object.entries(searchConfigs).map(async ([, config]) => {
      try {
        const payload = await fetchJson(config.endpoint(query));
        return { title: config.title, items: config.extract(payload) };
      } catch (error) {
        return { title: config.title, items: [], error: error.message };
      }
    }),
  );

  searchResults.replaceChildren();
  searchResults.append(...groups.map(buildResultGroup));
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

  if (!group.items.length) {
    article.append(buildMessageCard("Sem resultados nesta coleção."));
    return article;
  }

  const list = document.createElement("div");
  list.className = "result-list";
  group.items.forEach((item) => {
    const entry = item.href ? document.createElement("a") : document.createElement("article");
    entry.className = "result-item";
    if (item.href) entry.href = item.href;
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
    const node = pinTemplate.content.firstElementChild.cloneNode(true);
    const card = node.querySelector(".pin-card");
    const image = node.querySelector(".pin-image");
    const kicker = node.querySelector(".pin-kicker");
    const title = node.querySelector(".pin-title");
    const meta = node.querySelector(".pin-meta");
    const date = node.querySelector(".pin-date");

    node.href = item.href || "#";
    if (item.type) {
      card.classList.add(`pin-card-${item.type}`);
    }
    if (item.featured) {
      node.classList.add("pin-card-link-featured");
      card.classList.add("pin-card-featured");
    }
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

function applyFeaturedPins(items) {
  return items.map((item, index) => ({
    ...item,
    featured: shouldFeaturePin(index, items.length),
  }));
}

function shouldFeaturePin(index, total) {
  if (total < 6) return index === 0;
  return index === 0 || (index === 7 && total >= 12);
}

function buildBalancedFeed(pools, limit) {
  const entries = Object.entries(pools).map(([key, items]) => ({
    key,
    items: [...items],
    used: 0,
  }));
  const result = [];

  while (result.length < limit) {
    const available = entries.filter((entry) => entry.items.length > 0);
    if (!available.length) break;

    available.sort((left, right) => {
      const leftRatio = left.used / Math.max(1, pools[left.key].length);
      const rightRatio = right.used / Math.max(1, pools[right.key].length);
      if (leftRatio !== rightRatio) return leftRatio - rightRatio;
      if (left.used !== right.used) return left.used - right.used;
      return right.items.length - left.items.length;
    });

    const next = available[0];
    const item = next.items.shift();
    next.used += 1;
    if (item) result.push(item);
  }

  return result;
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
    headers: { Accept: "application/json" },
  });
  if (!response.ok) throw new Error(`${response.status} ${response.statusText}`);
  return response.json();
}

async function fetchSafe(path, fallback) {
  try {
    return await fetchJson(path);
  } catch {
    return fallback;
  }
}

function resolveAssetUrl(value) {
  if (!value) return createPlaceholderDataUrl("Media Pulse");
  if (/^https?:\/\//.test(value) || value.startsWith("data:")) return value;
  return `${state.apiBaseUrl}${value.startsWith("/") ? value : `/${value}`}`;
}

function handleApiBaseChange() {
  const nextValue = window.prompt("Informe a base da API", state.apiBaseUrl);
  if (!nextValue) return;
  state.apiBaseUrl = nextValue.replace(/\/$/, "");
  localStorage.setItem("media-pulse-api-base", state.apiBaseUrl);
  apiBaseButton.textContent = compactApiLabel(state.apiBaseUrl);
  searchStatus.textContent = `API atual: ${state.apiBaseUrl}`;
  loadHome();
}

function compactApiLabel(url) {
  return `API: ${url.replace(/^https?:\/\//, "")}`;
}

function buildDetailUrl(kind, identifiers = {}) {
  const search = new URLSearchParams({ kind });
  Object.entries(identifiers).forEach(([key, value]) => {
    if (value !== null && value !== undefined && value !== "") search.set(key, value);
  });
  return `./detail.html?${search.toString()}`;
}

function formatDate(value, prefix) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const formatter = new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "short", year: "numeric" });
  return `${prefix} ${formatter.format(date)}`;
}

function translateBookStatus(status) {
  const map = {
    READ: "lido",
    READING: "lendo",
    CURRENTLY_READING: "lendo",
    WANT_TO_READ: "quero ler",
    PAUSED: "pausado",
    DID_NOT_FINISH: "abandonei",
    UNKNOWN: "desconhecido",
  };
  return map[status] || status || "";
}

function computePercent(value, total) {
  if (!total || total <= 0) return 0;
  return Math.max(0, Math.min(100, Math.round((value / total) * 100)));
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
  return String(value).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function escapeAttribute(value) {
  return escapeHtml(value);
}

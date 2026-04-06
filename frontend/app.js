const MIXED_SOURCE_PAGE_LIMIT = 12;
const INITIAL_MIXED_BATCH_SIZE = 18;
const NEXT_MIXED_BATCH_SIZE = 12;
const MIXED_MIN_BUFFER_SIZE = 6;

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
    endpoint: ({ limit = MIXED_SOURCE_PAGE_LIMIT, cursor } = {}) => buildPagedPath("/api/movies/recent", { limit, cursor }),
    normalizePage: (payload) => ({
      items: (payload.items || []).map((item) => ({
        href: buildDetailUrl("movie", { slug: item.slug, id: item.movieId }),
        image: item.coverUrl,
        kicker: "Movie",
        title: item.title,
        meta: [item.originalTitle, item.year].filter(Boolean).join(" • "),
        date: formatDate(item.watchedAt, "Assistido"),
      })),
      nextCursor: payload.nextCursor || null,
    }),
    emptyPage: { items: [], nextCursor: null },
  },
  music: {
    endpoint: ({ limit = MIXED_SOURCE_PAGE_LIMIT, cursor } = {}) =>
      buildPagedPath("/api/music/recent-albums", { limit, cursor }),
    normalizePage: (payload) => ({
      items: (payload.items || []).map((item) => ({
        href: buildDetailUrl("music-album", { id: item.albumId }),
        image: item.coverUrl,
        kicker: "Album",
        title: item.albumTitle,
        meta: [item.artistName, item.year].filter(Boolean).join(" • "),
        date: formatDate(item.lastPlayed, `${item.playCount} plays`),
      })),
      nextCursor: payload.nextCursor || null,
    }),
    emptyPage: { items: [], nextCursor: null },
  },
  books: {
    endpoint: ({ limit = MIXED_SOURCE_PAGE_LIMIT, cursor } = {}) =>
      buildPagedPath("/api/books/list", { status: "read", limit, cursor }),
    normalizePage: (payload) => ({
      items: (payload.items || []).map((item) => ({
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
      nextCursor: payload.nextCursor || null,
    }),
    emptyPage: { items: [], nextCursor: null },
  },
  shows: {
    endpoint: ({ limit = MIXED_SOURCE_PAGE_LIMIT, cursor } = {}) => buildPagedPath("/api/shows/recent", { limit, cursor }),
    normalizePage: (payload) => ({
      items: (payload.items || []).map((item) => ({
        href: buildDetailUrl("show", { slug: item.slug, id: item.showId }),
        image: item.coverUrl,
        kicker: "Show",
        title: item.title,
        meta: [item.originalTitle, item.year].filter(Boolean).join(" • "),
        date: formatDate(item.watchedAt, "Visto"),
      })),
      nextCursor: payload.nextCursor || null,
    }),
    emptyPage: { items: [], nextCursor: null },
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
  mixedMosaic: createMixedMosaicState(),
};

const pinTemplate = document.querySelector("#pin-card-template");
const featureTemplate = document.querySelector("#feature-card-template");
const searchForm = document.querySelector("#search-form");
const searchInput = document.querySelector("#search-input");
const searchStatus = document.querySelector("#search-status");
const searchResultsSection = document.querySelector("#search-results-section");
const searchResults = document.querySelector("#search-results");
const clearSearchButton = document.querySelector("#clear-search-button");
const searchShortcutButtons = document.querySelectorAll("[data-search-query]");
const mixedGrid = document.querySelector("#mixed-grid");
const mixedGridStatus = document.querySelector("#mixed-grid-status");
const mixedGridSentinel = document.querySelector("#mixed-grid-sentinel");

initialize();

function initialize() {
  searchForm.addEventListener("submit", handleSearchSubmit);
  clearSearchButton.addEventListener("click", clearSearch);
  searchInput.addEventListener("input", handleSearchInput);
  searchShortcutButtons.forEach((button) => button.addEventListener("click", handleSearchShortcut));

  loadHome();
}

function createMixedSourceState() {
  return {
    items: [],
    cursor: null,
    hasMore: true,
    loading: false,
  };
}

function createMixedMosaicState() {
  return {
    loading: false,
    renderedCount: 0,
    observer: null,
    sources: Object.fromEntries(Object.keys(recentConfigs).map((key) => [key, createMixedSourceState()])),
  };
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

  const [currentlyWatching, readingNow, topArtists, recentAlbumsPayload, recentMoviesPayload] = await Promise.all([
    fetchSafe("/api/shows/currently-watching?limit=6&activeWithinDays=90", []),
    fetchSafe("/api/books/list?status=currently_reading&limit=6", { items: [] }),
    fetchSafe(`/api/music/tops/artists?start=${encodeURIComponent(weekStart)}&end=${encodeURIComponent(weekEnd)}&limit=6`, []),
    fetchSafe("/api/music/recent-albums?limit=6", { items: [] }),
    fetchSafe("/api/movies/recent?limit=6", { items: [] }),
  ]);
  const recentAlbums = recentAlbumsPayload.items || [];
  const recentMovies = recentMoviesPayload.items || [];

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
  resetMixedMosaicState();
  mixedGrid.replaceChildren(buildLoadingCard());
  setMixedLoadState("Carregando mural...", "loading");
  await Promise.all(Object.keys(recentConfigs).map((key) => fillMixedSourceBuffer(key)));
  mixedGrid.replaceChildren();
  const appended = appendNextMixedBatch(INITIAL_MIXED_BATCH_SIZE);
  if (!appended) {
    mixedGrid.append(buildMessageCard("Nenhum item encontrado."));
    setMixedLoadState("Sem itens recentes para exibir.", "done");
    return;
  }
  setupMixedMosaicObserver();
  refreshMixedLoadState();
}

function resetMixedMosaicState() {
  state.mixedMosaic.observer?.disconnect();
  state.mixedMosaic = createMixedMosaicState();
}

async function fillMixedSourceBuffer(key) {
  const source = state.mixedMosaic.sources[key];
  const config = recentConfigs[key];
  if (!source || !config || source.loading || !source.hasMore || source.items.length >= MIXED_MIN_BUFFER_SIZE) return;

  source.loading = true;
  try {
    while (source.hasMore && source.items.length < MIXED_MIN_BUFFER_SIZE) {
      const payload = await fetchSafe(config.endpoint({ limit: MIXED_SOURCE_PAGE_LIMIT, cursor: source.cursor }), config.emptyPage);
      const page = config.normalizePage(payload);
      const pageItems = page.items.map((item) => ({ ...item, weight: 1, type: key }));
      source.items.push(...pageItems);
      source.cursor = page.nextCursor || null;
      source.hasMore = Boolean(page.nextCursor);
      if (!pageItems.length) break;
    }
  } finally {
    source.loading = false;
  }
}

function appendNextMixedBatch(limit) {
  const pools = Object.fromEntries(Object.entries(state.mixedMosaic.sources).map(([key, source]) => [key, source.items]));
  const batch = buildBalancedFeed(pools, limit);
  if (!batch.length) return 0;

  const decorated = decorateMixedItems(batch, state.mixedMosaic.renderedCount);
  renderPinGrid(mixedGrid, decorated, { append: true });
  state.mixedMosaic.renderedCount += decorated.length;
  return decorated.length;
}

function decorateMixedItems(items, startIndex) {
  return items.map((item, index) => ({
    ...item,
    featured: shouldFeaturePin(startIndex + index),
  }));
}

function setupMixedMosaicObserver() {
  if (!("IntersectionObserver" in window)) return;

  state.mixedMosaic.observer?.disconnect();
  state.mixedMosaic.observer = new IntersectionObserver(handleMixedMosaicIntersection, {
    rootMargin: "1200px 0px",
  });
  state.mixedMosaic.observer.observe(mixedGridSentinel);
}

function handleMixedMosaicIntersection(entries) {
  if (!entries.some((entry) => entry.isIntersecting)) return;
  void loadMoreMixedMosaic();
}

async function loadMoreMixedMosaic() {
  if (state.mixedMosaic.loading) return;
  if (!hasMoreMixedContent()) {
    refreshMixedLoadState();
    return;
  }

  state.mixedMosaic.loading = true;
  setMixedLoadState("Carregando mais do mural...", "loading");
  try {
    await Promise.all(Object.keys(recentConfigs).map((key) => fillMixedSourceBuffer(key)));
    const appended = appendNextMixedBatch(NEXT_MIXED_BATCH_SIZE);
    if (!appended && hasMoreMixedContent()) {
      await Promise.all(Object.keys(recentConfigs).map((key) => fillMixedSourceBuffer(key)));
      appendNextMixedBatch(NEXT_MIXED_BATCH_SIZE);
    }
  } finally {
    state.mixedMosaic.loading = false;
    refreshMixedLoadState();
  }
}

function hasMoreMixedContent() {
  return Object.values(state.mixedMosaic.sources).some((source) => source.hasMore || source.items.length > 0);
}

function refreshMixedLoadState() {
  if (state.mixedMosaic.loading) {
    setMixedLoadState("Carregando mais do mural...", "loading");
    return;
  }

  if (Object.values(state.mixedMosaic.sources).every((source) => !source.hasMore && source.items.length === 0)) {
    setMixedLoadState("Fim do mural.", "done");
    state.mixedMosaic.observer?.disconnect();
    return;
  }

  setMixedLoadState("Role para carregar mais.", "idle");
}

function setMixedLoadState(message, status) {
  mixedGridStatus.textContent = message;
  mixedGridStatus.dataset.state = status;
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

function handleSearchShortcut(event) {
  const query = event.currentTarget.dataset.searchQuery?.trim();
  if (!query) {
    return;
  }

  searchInput.value = query;
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

function renderPinGrid(container, items, { append = false } = {}) {
  if (!append) {
    container.replaceChildren();
  }

  if (!items.length && !append) {
    container.append(buildMessageCard("Nenhum item encontrado."));
    return;
  }

  items.forEach((item, index) => {
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
      card.classList.add("pin-card-featured");
    } else {
      card.classList.add(`pin-card-variant-${computePinVariant(index, item.type)}`);
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

function computePinVariant(index, type) {
  const byType = {
    books: ["tall", "standard", "tall"],
    movies: ["standard", "portrait", "standard"],
    music: ["square", "standard", "portrait"],
    shows: ["portrait", "standard", "square"],
  };
  const pattern = byType[type] || ["standard", "portrait", "square"];
  return pattern[index % pattern.length];
}

function shouldFeaturePin(index) {
  return index === 0 || (index >= 7 && (index - 7) % 12 === 0);
}

function buildBalancedFeed(pools, limit) {
  const entries = Object.entries(pools).map(([key, items]) => ({
    key,
    items,
    used: 0,
    initialCount: items.length,
  }));
  const result = [];

  while (result.length < limit) {
    const available = entries.filter((entry) => entry.items.length > 0);
    if (!available.length) break;

    available.sort((left, right) => {
      const leftRatio = left.used / Math.max(1, left.initialCount);
      const rightRatio = right.used / Math.max(1, right.initialCount);
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
  const response = await fetch(path, {
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
  return value.startsWith("/") ? value : `/${value}`;
}

function buildPagedPath(path, params) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== null && value !== undefined && value !== "") {
      search.set(key, String(value));
    }
  });
  return `${path}?${search.toString()}`;
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

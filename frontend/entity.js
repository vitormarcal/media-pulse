const params = new URLSearchParams(window.location.search);
const kind = params.get("kind");
const pageTitle = document.querySelector("#entity-page-title");
const entityRoot = document.querySelector("#entity-root");
const cardTemplate = document.querySelector("#entity-card-template");

const PAGE_SIZE = 24;

const entityConfigs = {
  movies: {
    title: "Filmes",
    eyebrow: "Movies",
    description: "Um board de filmes ordenado pelo seu histórico real de watches, com revisitas e recorrência aparecendo como sinal de valor.",
    endpoint: (cursor) => buildPagedPath("/api/movies/library", { limit: PAGE_SIZE, cursor }),
    detailHref: (item) => buildDetailUrl("movie", { slug: item.slug, id: item.movieId }),
    badge: (item) => `${item.watchCount || 0} watches`,
    meta: (item) => [item.originalTitle, item.year].filter(Boolean).join(" • "),
    copy: (item) => item.watchCount > 1 ? "Revisitado no seu catálogo." : "Registro único no histórico.",
    pill: (item) => item.watchCount > 1 ? `${item.watchCount - 1} rewatches` : "primeiro watch",
    date: (item) => formatDate(item.lastWatchedAt, "último watch"),
    image: (item) => item.coverUrl,
    summary: (items) => ({
      primary: `${items.length} títulos carregados`,
      secondary: items[0]?.lastWatchedAt ? `mais recente em ${formatShortDate(items[0].lastWatchedAt)}` : "sem watches recentes",
      tertiary: `${items.filter((item) => item.watchCount > 1).length} com revisitadas na amostra`,
    }),
  },
  shows: {
    title: "Séries",
    eyebrow: "Shows",
    description: "Aqui a entidade deixa de ser só detalhe e vira um mural histórico de acompanhamento: progresso, densidade de episódios e retomadas recentes.",
    endpoint: (cursor) => buildPagedPath("/api/shows/library", { limit: PAGE_SIZE, cursor }),
    detailHref: (item) => buildDetailUrl("show", { slug: item.slug, id: item.showId }),
    badge: (item) => `${computePercent(item.watchedEpisodesCount, item.episodesCount)}%`,
    meta: (item) => [item.originalTitle, item.year].filter(Boolean).join(" • "),
    copy: (item) => `${item.watchedEpisodesCount}/${item.episodesCount} episódios vistos.`,
    pill: (item) => item.watchedEpisodesCount >= item.episodesCount && item.episodesCount > 0 ? "concluída" : "em progresso",
    date: (item) => formatDate(item.lastWatchedAt, "último episódio"),
    image: (item) => item.coverUrl,
    summary: (items) => ({
      primary: `${items.length} séries carregadas`,
      secondary: `${items.filter((item) => item.watchedEpisodesCount > 0 && item.watchedEpisodesCount < item.episodesCount).length} em andamento na amostra`,
      tertiary: items[0]?.lastWatchedAt ? `atividade mais recente em ${formatShortDate(items[0].lastWatchedAt)}` : "sem atividade recente",
    }),
  },
  books: {
    title: "Livros",
    eyebrow: "Books",
    description: "A biblioteca ganha sua própria superfície de descoberta: capas, autores, estados de leitura e histórico editorial em um grid contínuo.",
    endpoint: (cursor) => buildPagedPath("/api/books/library", { limit: PAGE_SIZE, cursor }),
    detailHref: (item) => buildDetailUrl("book", { slug: item.slug, id: item.bookId }),
    badge: (item) => translateBookStatus(item.currentStatus) || "book",
    meta: (item) => item.authors?.map((author) => author.name).join(", ") || "sem autor",
    copy: (item) => item.activeProgressPct ? `${Math.round(item.activeProgressPct)}% da leitura ativa.` : `${item.completedCount || 0} leituras concluídas.`,
    pill: (item) => `${item.readsCount || 0} jornadas`,
    date: (item) => formatDate(item.lastActivityAt, "última atividade"),
    image: (item) => item.coverUrl,
    summary: (items) => ({
      primary: `${items.length} livros carregados`,
      secondary: `${items.filter((item) => item.currentStatus === "CURRENTLY_READING").length} lendo agora na amostra`,
      tertiary: items[0]?.lastActivityAt ? `movimento mais recente em ${formatShortDate(items[0].lastActivityAt)}` : "sem atividade recente",
    }),
  },
  artists: {
    title: "Artistas",
    eyebrow: "Artists",
    description: "A escuta vira catálogo navegável por artista, com presença de capa, volume de plays e sensação de recorrência ao longo do tempo.",
    endpoint: (cursor) => buildPagedPath("/api/music/library/artists", { limit: PAGE_SIZE, cursor }),
    detailHref: (item) => buildDetailUrl("music-artist", { id: item.artistId, label: item.artistName }),
    badge: (item) => `${item.totalPlays || 0} plays`,
    meta: (item) => `${item.albumsCount || 0} álbuns • ${item.tracksCount || 0} faixas`,
    copy: (item) => item.totalPlays > 0 ? "Artista já atravessado pelo seu histórico de escuta." : "Presente no catálogo, ainda sem plays.",
    pill: () => "artist",
    date: (item) => formatDate(item.lastPlayed, "último play"),
    image: (item) => item.coverUrl,
    summary: (items) => ({
      primary: `${items.length} artistas carregados`,
      secondary: `${items.filter((item) => item.totalPlays > 0).length} já tocados na amostra`,
      tertiary: items[0]?.lastPlayed ? `mais recente em ${formatShortDate(items[0].lastPlayed)}` : "sem plays recentes",
    }),
  },
  albums: {
    title: "Álbuns",
    eyebrow: "Albums",
    description: "Um mural de discografia pessoal, sempre com a capa no centro, mas agora sustentado por plays, cobertura e continuidade histórica.",
    endpoint: (cursor) => buildPagedPath("/api/music/library/albums", { limit: PAGE_SIZE, cursor }),
    detailHref: (item) => buildDetailUrl("music-album", { id: item.albumId }),
    badge: (item) => `${item.playCount || 0} plays`,
    meta: (item) => [item.artistName, item.year].filter(Boolean).join(" • "),
    copy: (item) => `${item.playedTracks || 0}/${item.totalTracks || 0} faixas tocadas.`,
    pill: (item) => `${computePercent(item.playedTracks, item.totalTracks)}% coberto`,
    date: (item) => formatDate(item.lastPlayed, "último play"),
    image: (item) => item.coverUrl,
    summary: (items) => ({
      primary: `${items.length} álbuns carregados`,
      secondary: `${items.filter((item) => item.playCount > 0).length} com escuta registrada`,
      tertiary: items[0]?.lastPlayed ? `atividade mais recente em ${formatShortDate(items[0].lastPlayed)}` : "sem plays recentes",
    }),
  },
  tracks: {
    title: "Faixas",
    eyebrow: "Tracks",
    description: "As músicas ouvidas deixam de ficar escondidas nas páginas de álbum e passam a formar um mural próprio, denso e explorável.",
    endpoint: (cursor) => buildPagedPath("/api/music/library/tracks", { limit: PAGE_SIZE, cursor }),
    detailHref: (item) => buildDetailUrl("music-track", { id: item.trackId, label: item.title }),
    badge: (item) => `${item.totalPlays || 0} plays`,
    meta: (item) => [item.artistName, item.albumTitle].filter(Boolean).join(" • "),
    copy: (item) => item.totalPlays > 1 ? "Faixa recorrente no seu histórico." : "Play pontual no catálogo.",
    pill: () => "track",
    date: (item) => formatDate(item.lastPlayed, "último play"),
    image: (item) => item.coverUrl,
    summary: (items) => ({
      primary: `${items.length} faixas carregadas`,
      secondary: `${items.filter((item) => item.totalPlays > 1).length} repetidas na amostra`,
      tertiary: items[0]?.lastPlayed ? `mais recente em ${formatShortDate(items[0].lastPlayed)}` : "sem plays recentes",
    }),
  },
};

const state = {
  items: [],
  nextCursor: null,
  loading: false,
  done: false,
  observer: null,
  config: null,
};

initialize();

async function initialize() {
  const config = entityConfigs[kind];
  if (!config) {
    renderError("Tipo de coleção não suportado.");
    return;
  }

  state.config = config;
  document.title = `${config.title} • Media Pulse`;
  pageTitle.textContent = config.title;
  entityRoot.innerHTML = renderShell(config);
  bindLoadObserver();
  await loadNextPage();
}

function renderShell(config) {
  return `
    <section class="entity-hero">
      <div class="entity-hero-copy">
        <p class="eyebrow">${escapeHtml(config.eyebrow)}</p>
        <h2>${escapeHtml(config.title)} como mural histórico</h2>
        <p class="entity-hero-text">${escapeHtml(config.description)}</p>
        <div class="entity-hero-actions">
          <a class="primary-button" href="./index.html#search-form">Buscar no catálogo</a>
          <a class="secondary-button" href="./index.html">Voltar ao mural inicial</a>
        </div>
      </div>
      <aside class="entity-hero-panel">
        <p class="entity-panel-label">Snapshot</p>
        <div class="entity-summary-grid" id="entity-summary-grid">
          <article class="summary-card">
            <p class="summary-label">Coleção</p>
            <strong class="summary-value">...</strong>
            <span class="summary-detail">carregando amostra</span>
          </article>
          <article class="summary-card">
            <p class="summary-label">Ritmo</p>
            <strong class="summary-value">...</strong>
            <span class="summary-detail">aguardando primeira página</span>
          </article>
          <article class="summary-card">
            <p class="summary-label">Recência</p>
            <strong class="summary-value">...</strong>
            <span class="summary-detail">preenchendo contexto</span>
          </article>
        </div>
      </aside>
    </section>

    <section class="entity-board-section">
      <div class="section-heading">
        <div>
          <p class="eyebrow">Board</p>
          <h3>${escapeHtml(config.title)} em navegação contínua</h3>
        </div>
      </div>
      <div class="entity-board" id="entity-board"></div>
      <div class="mosaic-load-state" id="entity-board-status">Carregando mural...</div>
      <div class="mosaic-sentinel" id="entity-board-sentinel" aria-hidden="true"></div>
    </section>
  `;
}

async function loadNextPage() {
  if (state.loading || state.done) return;
  state.loading = true;
  setStatus(state.items.length ? "Carregando mais..." : "Carregando mural...");

  try {
    const payload = await fetchJson(state.config.endpoint(state.nextCursor));
    const items = payload.items || [];
    state.nextCursor = payload.nextCursor || null;
    state.done = !state.nextCursor;
    appendItems(items);
    refreshSummary();
    setStatus(state.done ? "Fim da coleção." : "Role para continuar explorando.");
  } catch (error) {
    setStatus(`Erro ao carregar: ${error.message}`);
  } finally {
    state.loading = false;
  }
}

function appendItems(items) {
  const board = document.querySelector("#entity-board");
  if (!board) return;

  if (!items.length && !state.items.length) {
    board.append(buildMessageCard("Nenhum registro encontrado para esta coleção."));
    return;
  }

  items.forEach((item, index) => {
    const node = cardTemplate.content.firstElementChild.cloneNode(true);
    const card = node.querySelector(".entity-card");
    const image = node.querySelector(".entity-card-image");
    const badge = node.querySelector(".entity-card-badge");
    const kicker = node.querySelector(".entity-card-kicker");
    const title = node.querySelector(".entity-card-title");
    const meta = node.querySelector(".entity-card-meta");
    const copy = node.querySelector(".entity-card-copy");
    const pill = node.querySelector(".entity-card-pill");
    const date = node.querySelector(".entity-card-date");

    node.href = state.config.detailHref(item);
    card.classList.add(`entity-card-${kind}`);
    card.classList.add(`entity-card-variant-${computeCardVariant(state.items.length + index)}`);
    image.src = resolveAssetUrl(state.config.image(item));
    image.alt = item.title || item.albumTitle || item.artistName || "Media Pulse";
    image.onerror = () => {
      image.src = createPlaceholderDataUrl(item.title || item.albumTitle || item.artistName || "Media Pulse");
    };
    badge.textContent = state.config.badge(item);
    kicker.textContent = state.config.eyebrow.slice(0, -1) || state.config.eyebrow;
    title.textContent = item.title || item.albumTitle || item.artistName || "Item";
    meta.textContent = state.config.meta(item);
    copy.textContent = state.config.copy(item);
    pill.textContent = state.config.pill(item);
    date.textContent = state.config.date(item);
    board.append(node);
  });

  state.items.push(...items);
}

function refreshSummary() {
  const summary = state.config.summary(state.items);
  const cards = [...document.querySelectorAll("#entity-summary-grid .summary-card")];
  const primary = summarizeMetric(summary.primary, "board");
  const secondary = summarizeMetric(summary.secondary, "pulse");
  const tertiary = summarizeMetric(summary.tertiary, "now");
  if (cards[0]) {
    cards[0].querySelector(".summary-value").textContent = primary.value;
    cards[0].querySelector(".summary-detail").textContent = primary.detail;
  }
  if (cards[1]) {
    cards[1].querySelector(".summary-value").textContent = secondary.value;
    cards[1].querySelector(".summary-detail").textContent = secondary.detail;
  }
  if (cards[2]) {
    cards[2].querySelector(".summary-value").textContent = tertiary.value;
    cards[2].querySelector(".summary-detail").textContent = tertiary.detail;
  }
}

function summarizeMetric(text, fallback) {
  const match = String(text || "").match(/\d+(?:[.,]\d+)?/);
  return {
    value: match?.[0] || fallback,
    detail: text || "",
  };
}

function bindLoadObserver() {
  const sentinel = document.querySelector("#entity-board-sentinel");
  if (!sentinel || !("IntersectionObserver" in window)) return;
  state.observer = new IntersectionObserver((entries) => {
    if (entries.some((entry) => entry.isIntersecting)) {
      void loadNextPage();
    }
  }, { rootMargin: "1200px 0px" });
  state.observer.observe(sentinel);
}

function renderError(message) {
  document.title = "Erro • Media Pulse";
  pageTitle.textContent = "Erro";
  entityRoot.innerHTML = `
    <section class="detail-section">
      <div class="empty-state">${escapeHtml(message)}</div>
    </section>
  `;
}

function setStatus(message) {
  const status = document.querySelector("#entity-board-status");
  if (status) status.textContent = message;
}

function computeCardVariant(index) {
  const pattern = ["portrait", "square", "tall", "portrait", "standard"];
  return pattern[index % pattern.length];
}

function buildMessageCard(message) {
  const node = document.createElement("div");
  node.className = "empty-state";
  node.textContent = message;
  return node;
}

async function fetchJson(path) {
  const response = await fetch(path, { headers: { Accept: "application/json" } });
  if (!response.ok) throw new Error(`${response.status} ${response.statusText}`);
  return response.json();
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

function buildDetailUrl(nextKind, identifiers = {}) {
  const search = new URLSearchParams({ kind: nextKind });
  Object.entries(identifiers).forEach(([key, value]) => {
    if (value !== null && value !== undefined && value !== "") {
      search.set(key, value);
    }
  });
  return `./detail.html?${search.toString()}`;
}

function resolveAssetUrl(value) {
  if (!value) return createPlaceholderDataUrl("Media Pulse");
  if (/^https?:\/\//.test(value) || value.startsWith("data:")) return value;
  return value.startsWith("/") ? value : `/${value}`;
}

function formatDate(value, prefix) {
  if (!value) return "sem data";
  return `${prefix} ${formatShortDate(value)}`;
}

function formatShortDate(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "sem data";
  return new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "short", year: "numeric" }).format(date);
}

function translateBookStatus(status) {
  const map = {
    READ: "lido",
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

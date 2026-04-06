const DEFAULT_API_BASE_URL = localStorage.getItem("media-pulse-api-base") || "https://media-pulse.marcal.dev";

const state = {
  apiBaseUrl: DEFAULT_API_BASE_URL.replace(/\/$/, ""),
};

const params = new URLSearchParams(window.location.search);
const kind = params.get("kind");

const pageTitle = document.querySelector("#detail-page-title");
const detailRoot = document.querySelector("#detail-root");
const apiBaseButton = document.querySelector("#api-base-button");

initialize();

async function initialize() {
  apiBaseButton.textContent = compactApiLabel(state.apiBaseUrl);
  apiBaseButton.addEventListener("click", handleApiBaseChange);

  if (!kind) {
    renderError("Parâmetro `kind` ausente.");
    return;
  }

  await loadDetail();
}

async function loadDetail() {
  detailRoot.innerHTML = renderLoadingState();

  try {
    switch (kind) {
      case "movie":
        await renderMovieDetail();
        return;
      case "show":
        await renderShowDetail();
        return;
      case "book":
        await renderBookDetail();
        return;
      case "music-album":
        await renderMusicAlbumDetail();
        return;
      case "book-author":
        await renderBookAuthorDetail();
        return;
      case "music-artist":
        await renderMusicArtistDetail();
        return;
      case "music-track":
        await renderMusicTrackDetail();
        return;
      default:
        renderError(`Tipo de detalhe não suportado: ${kind}`);
    }
  } catch (error) {
    renderError(error.message);
  }
}

async function renderMovieDetail() {
  const payload = await fetchJson(resolveEndpoint("/api/movies", params.get("slug"), params.get("id")));
  const watches = payload.watches || [];
  const firstWatch = watches[watches.length - 1]?.watchedAt || null;
  const lastWatch = watches[0]?.watchedAt || null;
  const rewatchCount = Math.max(watches.length - 1, 0);
  const intensity = buildIntensityPoints(watches.map((watch) => watch.watchedAt));

  renderPage({
    title: payload.title,
    html: `
      ${renderHero({
        eyebrow: "Movie",
        title: payload.title,
        subtitle: [payload.originalTitle, payload.year].filter(Boolean).join(" • "),
        description: payload.description || "Filme cadastrado no Media Pulse com histórico completo de watches e identificadores externos.",
        image: payload.coverUrl,
        chips: [
          payload.slug ? `Slug ${payload.slug}` : null,
          payload.year ? `${payload.year}` : null,
          `${watches.length} watch${watches.length === 1 ? "" : "es"}`,
          rewatchCount > 0 ? `${rewatchCount} rewatches` : "primeiro watch apenas",
        ].filter(Boolean),
        metrics: [
          metricCard("Total de watches", String(watches.length), "todas as sessões registradas"),
          metricCard("Primeiro watch", formatDate(firstWatch) || "sem dado", "início do histórico"),
          metricCard("Último watch", formatDate(lastWatch) || "sem dado", "registro mais recente"),
          metricCard("Ritmo", rewatchCount > 0 ? "Revisitado" : "One shot", rewatchCount > 0 ? "há retorno para o catálogo" : "sem revisitas até agora"),
        ].join(""),
      })}

      ${renderSection(
        "Pulse",
        "Intensidade de consumo",
        `<article class="detail-card detail-card-wide">
          <h4>Mapa de watches</h4>
          <p class="detail-card-copy">Cada ponto representa um watch. Quanto mais preenchido, maior a recorrência do filme no seu histórico.</p>
          <div class="dot-strip">${intensity.map((level) => `<span class="dot-strip-item dot-strip-item-${level}"></span>`).join("")}</div>
        </article>`,
      )}

      ${renderSection(
        "Contexto",
        "Leitura rápida",
        `
          <div class="detail-grid">
            ${buildFactCard("Linha do tempo", [
              factRow("Título original", payload.originalTitle || payload.title),
              factRow("Ano", payload.year ?? "sem ano"),
              factRow("Fontes", uniqueCountLabel(watches.map((watch) => watch.source), "fonte", "fontes")),
              factRow("IDs externos", uniqueCountLabel((payload.externalIds || []).map((item) => item.provider), "id", "ids")),
            ])}
            ${buildListCard("Histórico de watches", watches.map((watch) => ({
              title: formatDateTime(watch.watchedAt) || "Watch",
              meta: `Fonte ${watch.source}`,
            })), "Sem histórico disponível.")}
          </div>
        `,
      )}
    `,
  });
}

async function renderShowDetail() {
  const payload = await fetchJson(resolveEndpoint("/api/shows", params.get("slug"), params.get("id")));
  const progress = payload.progress || deriveFallbackShowProgress(payload.seasons || [], payload.watches || []);
  const seasons = payload.seasons || [];
  const lastWatch = payload.watches?.[0];

  renderPage({
    title: payload.title,
    html: `
      ${renderHero({
        eyebrow: "Show",
        title: payload.title,
        subtitle: [payload.originalTitle, payload.year].filter(Boolean).join(" • "),
        description: payload.description || "Série com progresso agregado por temporada e histórico de episódios assistidos.",
        image: payload.coverUrl,
        chips: [
          payload.slug ? `Slug ${payload.slug}` : null,
          `${progress.watchedEpisodesCount} de ${progress.episodesCount} episódios`,
          progress.completed ? "Série concluída" : progress.inProgress ? "Em andamento" : "Ainda não concluída",
        ].filter(Boolean),
        metrics: [
          metricCard("Concluído", `${computePercent(progress.watchedEpisodesCount, progress.episodesCount)}%`, `${progress.watchedEpisodesCount}/${progress.episodesCount} episódios`),
          metricCard("Temporadas", `${progress.completedSeasonsCount}/${progress.seasonsCount}`, progress.completed ? "todas completas" : "temporadas completas"),
          metricCard("Status", progress.completed ? "Finalizada" : progress.inProgress ? "Assistindo" : "Parada", "estado atual da série"),
          metricCard("Último episódio", lastWatch ? `${safeSeasonEpisode(lastWatch)} ` : "sem dado", lastWatch ? truncateText(lastWatch.episodeTitle, 34) : "nenhum episódio assistido"),
        ].join(""),
        extra: renderProgressPanel(progress),
      })}

      ${renderSection(
        "Seasons",
        "Progresso por temporada",
        `
          <div class="season-grid">
            ${seasons.length
              ? seasons
                  .map(
                    (season) => `
                      <article class="season-card">
                        <div class="season-card-head">
                          <div>
                            <p class="season-card-kicker">Temporada ${season.seasonNumber ?? "?"}</p>
                            <h4>${escapeHtml(season.seasonTitle || `Season ${season.seasonNumber ?? "?"}`)}</h4>
                          </div>
                          <span class="season-state ${season.completed ? "season-state-complete" : "season-state-progress"}">
                            ${season.completed ? "Completa" : `${computePercent(season.watchedEpisodesCount, season.episodesCount)}%`}
                          </span>
                        </div>
                        ${progressBar(season.watchedEpisodesCount, season.episodesCount)}
                        <div class="season-stats">
                          <strong>${season.watchedEpisodesCount}/${season.episodesCount}</strong>
                          <span>${season.lastWatchedAt ? `Último watch ${formatDate(season.lastWatchedAt)}` : "Sem watch recente"}</span>
                        </div>
                      </article>
                    `,
                  )
                  .join("")
              : renderEmptyInline("Sem temporadas retornadas pela API.")}
          </div>
        `,
      )}

      ${renderSection(
        "Episodes",
        "Linha do tempo",
        `
          <div class="detail-grid">
            ${buildFactCard("Resumo", [
              factRow("Título original", payload.originalTitle || payload.title),
              factRow("Ano", payload.year ?? "sem ano"),
              factRow("IDs externos", uniqueCountLabel((payload.externalIds || []).map((item) => item.provider), "id", "ids")),
              factRow("Watches", `${payload.watches?.length ?? 0} episódios vistos`),
            ])}
            ${buildListCard(
              "Episódios assistidos",
              (payload.watches || []).map((watch) => ({
                title: `${safeSeasonEpisode(watch)} ${watch.episodeTitle}`.trim(),
                meta: [formatDateTime(watch.watchedAt), `Fonte ${watch.source}`].filter(Boolean).join(" • "),
              })),
              "Nenhum episódio assistido.",
            )}
          </div>
        `,
      )}
    `,
  });
}

async function renderBookDetail() {
  const payload = await fetchJson(resolveEndpoint("/api/books", params.get("slug"), params.get("id")));
  const reads = payload.reads || [];
  const activeRead = reads.find((read) => read.status === "READING") || reads[0] || null;
  const completedReads = reads.filter((read) => read.status === "READ");
  const pagesRead = sum(reads.map((read) => read.progressPages || 0));

  renderPage({
    title: payload.title,
    html: `
      ${renderHero({
        eyebrow: "Book",
        title: payload.title,
        subtitle: payload.authors?.map((author) => author.name).join(", ") || "Sem autor",
        description: payload.description || payload.reviewRaw || "Livro com edições, avaliações e jornadas de leitura no Media Pulse.",
        image: payload.coverUrl || payload.editions?.[0]?.coverUrl,
        chips: [
          payload.slug ? `Slug ${payload.slug}` : null,
          payload.releaseDate ? `Lançado ${payload.releaseDate}` : null,
          payload.rating ? `Nota ${payload.rating}` : null,
          `${reads.length} jornada${reads.length === 1 ? "" : "s"} de leitura`,
        ].filter(Boolean),
        metrics: [
          metricCard("Status principal", activeRead ? translateBookStatus(activeRead.status) : "Sem leitura", activeRead?.progressPct ? `${Math.round(activeRead.progressPct)}% da jornada ativa` : "nenhuma jornada ativa"),
          metricCard("Concluído", String(completedReads.length), "leituras finalizadas"),
          metricCard("Páginas", `${pagesRead}`, "páginas registradas"),
          metricCard("Edições", `${payload.editions?.length ?? 0}`, "edições conhecidas"),
        ].join(""),
        extra: activeRead ? renderReadingProgress(activeRead) : "",
      })}

      ${renderSection(
        "Reading",
        "Jornadas de leitura",
        `
          <div class="detail-grid">
            ${buildListCard(
              "Timeline de leituras",
              reads.map((read) => ({
                title: `${translateBookStatus(read.status)}${read.progressPct ? ` • ${Math.round(read.progressPct)}%` : ""}`,
                meta: [
                  formatDateTime(read.startedAt),
                  read.finishedAt ? `até ${formatDateTime(read.finishedAt)}` : null,
                  read.progressPages ? `${read.progressPages} páginas` : null,
                  `Fonte ${read.source}`,
                ]
                  .filter(Boolean)
                  .join(" • "),
              })),
              "Nenhuma jornada registrada.",
            )}
            ${buildFactCard("Contexto editorial", [
              factRow("Autores", payload.authors?.map((author) => author.name).join(", ") || "sem autor"),
              factRow("Review", payload.reviewedAt ? formatDateTime(payload.reviewedAt) : "sem review"),
              factRow("Lançamento", payload.releaseDate || "sem data"),
              factRow("Nota", payload.rating ?? "sem nota"),
            ])}
          </div>
        `,
      )}

      ${renderSection(
        "Editions",
        "Edições disponíveis",
        `
          <div class="edition-grid">
            ${payload.editions?.length
              ? payload.editions
                  .map(
                    (edition) => `
                      <article class="edition-card">
                        <p class="edition-kicker">${escapeHtml(edition.format || "Edition")}</p>
                        <h4>${escapeHtml(edition.title || "Edição sem título")}</h4>
                        <div class="edition-meta">
                          <span>${escapeHtml(edition.publisher || "editora não informada")}</span>
                          <span>${edition.pages ? `${edition.pages} páginas` : "páginas n/d"}</span>
                          <span>${escapeHtml(edition.language || "idioma n/d")}</span>
                        </div>
                      </article>
                    `,
                  )
                  .join("")
              : renderEmptyInline("Sem edições detalhadas para este livro.")}
          </div>
        `,
      )}
    `,
  });
}

async function renderMusicAlbumDetail() {
  const id = params.get("id");
  if (!id) {
    throw new Error("Album sem `id`.");
  }

  const payload = await fetchJson(`/api/music/albums/${encodeURIComponent(id)}`);
  const topTrack = [...(payload.tracks || [])].sort((a, b) => (b.playCount || 0) - (a.playCount || 0))[0] || null;
  const uniquePlayedTracks = (payload.tracks || []).filter((track) => (track.playCount || 0) > 0).length;
  const playsBars = normalizeBars((payload.playsByDay || []).map((item) => item.plays));

  renderPage({
    title: payload.albumTitle,
    html: `
      ${renderHero({
        eyebrow: "Album",
        title: payload.albumTitle,
        subtitle: [payload.artistName, payload.year].filter(Boolean).join(" • "),
        description: payload.totalPlays
          ? `Álbum com ${payload.totalPlays} plays totais registrados e distribuição por faixa disponível.`
          : "Álbum encontrado no catálogo, ainda sem plays significativos.",
        image: payload.coverUrl,
        chips: [
          payload.lastPlayed ? `Último play ${formatDate(payload.lastPlayed)}` : "Sem play recente",
          `${payload.tracks?.length ?? 0} faixas`,
          `${payload.totalPlays ?? 0} plays`,
        ],
        metrics: [
          metricCard("Total plays", String(payload.totalPlays ?? 0), "soma completa do álbum"),
          metricCard("Faixas ouvidas", `${uniquePlayedTracks}/${payload.tracks?.length ?? 0}`, "cobertura do álbum"),
          metricCard("Top track", topTrack ? truncateText(topTrack.title, 18) : "sem dados", topTrack ? `${topTrack.playCount} plays` : "nenhum play"),
          metricCard("Último play", formatDate(payload.lastPlayed) || "sem dado", "atividade mais recente"),
        ].join(""),
        extra: `
          <article class="detail-side-panel">
            <p class="detail-side-label">Heatmap</p>
            <h3>Plays por dia</h3>
            <div class="spark-bars">
              ${playsBars.length ? playsBars.map((value) => `<span class="spark-bar" style="height:${Math.max(value, 8)}%"></span>`).join("") : renderEmptyInline("Sem histórico diário.")}
            </div>
          </article>
        `,
      })}

      ${renderSection(
        "Tracks",
        "Faixas do álbum",
        `
          <div class="track-stack">
            ${(payload.tracks || [])
              .map((track) => {
                const percent = computePercent(track.playCount || 0, topTrack?.playCount || 0);
                return `
                  <article class="track-row">
                    <div class="track-row-copy">
                      <h4>${escapeHtml(track.title)}</h4>
                      <p>${[
                        track.trackNumber ? `faixa ${track.trackNumber}` : null,
                        track.lastPlayed ? `último play ${formatDate(track.lastPlayed)}` : null,
                      ]
                        .filter(Boolean)
                        .join(" • ")}</p>
                    </div>
                    <div class="track-row-meter">
                      ${progressBar(track.playCount || 0, topTrack?.playCount || 0 || 1)}
                      <strong>${track.playCount} plays</strong>
                    </div>
                  </article>
                `;
              })
              .join("") || renderEmptyInline("Nenhuma faixa retornada.")}
          </div>
        `,
      )}
    `,
  });
}

async function renderBookAuthorDetail() {
  const id = params.get("id");
  if (!id) {
    throw new Error("Autor sem `id`.");
  }

  const payload = await fetchJson(`/api/books/authors/${encodeURIComponent(id)}`);
  renderPage({
    title: payload.name,
    html: `
      ${renderHero({
        eyebrow: "Author",
        title: payload.name,
        subtitle: `${payload.booksCount} livros no catálogo`,
        description: "Autor com resumo próprio de catálogo e jornadas de leitura registradas no Media Pulse.",
        image: null,
        chips: [
          `${payload.booksCount ?? 0} livros`,
          `${payload.finishedCount ?? 0} leituras finalizadas`,
          payload.lastFinishedAt ? `Última conclusão ${formatDate(payload.lastFinishedAt)}` : "Sem conclusão registrada",
        ],
        metrics: [
          metricCard("Livros", `${payload.booksCount ?? 0}`, "títulos associados"),
          metricCard("Leituras", `${payload.readsCount ?? 0}`, "jornadas registradas"),
          metricCard("Finalizados", `${payload.finishedCount ?? 0}`, "leituras concluídas"),
          metricCard("Lendo", `${payload.currentlyReadingCount ?? 0}`, "leituras em andamento"),
        ].join(""),
      })}
      ${renderSection(
        "Books",
        "Catálogo do autor",
        `
          <div class="detail-grid">
            ${buildCoverShelfCard("Livros", (payload.books || []).map((book) => ({
              title: book.title,
              meta: [book.releaseDate, book.rating ? `nota ${book.rating}` : null].filter(Boolean).join(" • "),
              href: buildDetailUrl("book", { slug: book.slug, id: book.bookId }),
              image: book.coverUrl,
              badge: "Book",
            })), "Nenhum livro relacionado.")}
            ${buildListCard("Leituras recentes", (payload.recentReads || []).map((read) => ({
              title: `${read.book?.title || "Livro"} • ${translateBookStatus(read.status)}`,
              meta: [formatDateTime(read.finishedAt || read.startedAt), read.progressPages ? `${read.progressPages} páginas` : null]
                .filter(Boolean)
                .join(" • "),
              href: buildDetailUrl("book", { slug: read.book?.slug, id: read.book?.bookId }),
            })), "Nenhuma leitura recente.")}
          </div>
        `,
      )}
    `,
  });
}

async function renderMusicArtistDetail() {
  const id = params.get("id");
  if (!id) {
    throw new Error("Artista sem `id`.");
  }

  const payload = await fetchJson(`/api/music/artists/${encodeURIComponent(id)}`);
  const playsBars = normalizeBars((payload.playsByDay || []).map((item) => item.plays));
  renderPage({
    title: payload.artistName,
    html: `
      ${renderHero({
        eyebrow: "Artist",
        title: payload.artistName,
        subtitle: `${payload.libraryAlbumsCount} álbuns • ${payload.libraryTracksCount} faixas`,
        description: "Artista com panorama real de catálogo, cobertura ouvida e distribuição temporal de plays.",
        image: null,
        chips: [
          `${payload.totalPlays ?? 0} plays totais`,
          `${payload.uniqueAlbumsPlayed ?? 0} álbuns ouvidos`,
          payload.lastPlayed ? `Último play ${formatDate(payload.lastPlayed)}` : "Sem play recente",
        ],
        metrics: [
          metricCard("Plays", `${payload.totalPlays ?? 0}`, "todas as execuções"),
          metricCard("Faixas ouvidas", `${payload.uniqueTracksPlayed ?? 0}/${payload.libraryTracksCount ?? 0}`, "cobertura do artista"),
          metricCard("Álbuns ouvidos", `${payload.uniqueAlbumsPlayed ?? 0}/${payload.libraryAlbumsCount ?? 0}`, "presença no catálogo"),
          metricCard("Último play", formatDate(payload.lastPlayed) || "sem dado", "atividade recente"),
        ].join(""),
        extra: `
          <article class="detail-side-panel">
            <p class="detail-side-label">Rhythm</p>
            <h3>Plays por dia</h3>
            <div class="spark-bars">
              ${playsBars.length ? playsBars.map((value) => `<span class="spark-bar" style="height:${Math.max(value, 8)}%"></span>`).join("") : renderEmptyInline("Sem histórico diário.")}
            </div>
          </article>
        `,
      })}
      ${renderSection(
        "Albums",
        "Discografia ouvida",
        `
          <div class="detail-grid">
            ${buildCoverShelfCard("Álbuns", (payload.albums || []).map((album) => ({
              title: album.albumTitle,
              meta: [album.year, `${album.playedTracks}/${album.totalTracks} faixas`, `${album.playCount} plays`].filter(Boolean).join(" • "),
              href: buildDetailUrl("music-album", { id: album.albumId }),
              image: album.coverUrl,
              badge: `${computePercent(album.playedTracks, album.totalTracks)}%`,
            })), "Nenhum álbum encontrado.")}
            ${buildListCard("Top faixas", (payload.topTracks || []).map((track) => ({
              title: track.title,
              meta: [track.albumTitle, `${track.playCount} plays`, formatDate(track.lastPlayed)].filter(Boolean).join(" • "),
              href: buildDetailUrl("music-track", { id: track.trackId, label: track.title }),
            })), "Nenhuma faixa encontrada.")}
          </div>
        `,
      )}
    `,
  });
}

async function renderMusicTrackDetail() {
  const id = params.get("id");
  if (!id) {
    throw new Error("Faixa sem `id`.");
  }

  const payload = await fetchJson(`/api/music/tracks/${encodeURIComponent(id)}`);

  renderPage({
    title: payload.title,
    html: `
      ${renderHero({
        eyebrow: "Track",
        title: payload.title,
        subtitle: payload.artistName,
        description: "Faixa com histórico real de plays, presença por álbum e timeline recente de execução.",
        image: null,
        chips: [
          `${payload.totalPlays ?? 0} plays`,
          `${payload.albums?.length ?? 0} álbuns associados`,
          payload.lastPlayed ? `Último play ${formatDate(payload.lastPlayed)}` : "Sem play recente",
        ],
        metrics: [
          metricCard("Plays", `${payload.totalPlays ?? 0}`, "execuções totais"),
          metricCard("Álbuns", `${payload.albums?.length ?? 0}`, "presença em discos"),
          metricCard("Artista", payload.artistName, "vínculo principal"),
          metricCard("Último play", formatDate(payload.lastPlayed) || "sem dado", "atividade recente"),
        ].join(""),
      })}
      ${renderSection(
        "Track",
        "Contexto da faixa",
        `
          <div class="detail-grid">
            ${buildCoverShelfCard("Álbuns", (payload.albums || []).map((album) => ({
              title: album.albumTitle,
              meta: [album.trackNumber ? `faixa ${album.trackNumber}` : null, album.year, `${album.playCount} plays`].filter(Boolean).join(" • "),
              href: buildDetailUrl("music-album", { id: album.albumId }),
              image: album.coverUrl,
              badge: album.discNumber ? `disc ${album.discNumber}` : "album",
            })), "Nenhum álbum encontrado.")}
            ${buildPlayTimelineCard("Plays recentes", payload.recentPlays || [], "Nenhum play recente.")}
          </div>
        `,
      )}
    `,
  });
}

function renderPage({ title, html }) {
  document.title = `${title} • Media Pulse`;
  pageTitle.textContent = title;
  detailRoot.innerHTML = html;
  bindResolvedImages(detailRoot);
}

function renderHero({ eyebrow, title, subtitle, description, image, chips = [], metrics = "", extra = "" }) {
  return `
    <section class="detail-hero ${extra ? "detail-hero-rich" : ""}">
      <div class="detail-poster-wrap">
        <img class="detail-poster js-resolve-image" data-src="${escapeAttribute(image || "")}" alt="${escapeAttribute(title)}" />
      </div>
      <div class="detail-copy">
        <p class="eyebrow">${escapeHtml(eyebrow)}</p>
        <h2>${escapeHtml(title)}</h2>
        <p class="detail-subtitle">${escapeHtml(subtitle || "")}</p>
        <div class="detail-meta-chips">${chips.map((chip) => `<span class="detail-chip">${escapeHtml(chip)}</span>`).join("")}</div>
        <p class="detail-description">${escapeHtml(description || "")}</p>
        <div class="detail-metrics-grid">${metrics}</div>
      </div>
      ${extra}
    </section>
  `;
}

function renderSection(eyebrow, title, content) {
  return `
    <section class="detail-section">
      <div class="section-heading">
        <div>
          <p class="eyebrow">${escapeHtml(eyebrow)}</p>
          <h3>${escapeHtml(title)}</h3>
        </div>
      </div>
      ${content}
    </section>
  `;
}

function renderProgressPanel(progress) {
  return `
    <article class="detail-side-panel">
      <p class="detail-side-label">Progress</p>
      <h3>${computePercent(progress.watchedEpisodesCount, progress.episodesCount)}% concluído</h3>
      ${progressBar(progress.watchedEpisodesCount, progress.episodesCount)}
      <div class="detail-side-pairs">
        <span>${progress.watchedEpisodesCount}/${progress.episodesCount} episódios</span>
        <span>${progress.completedSeasonsCount}/${progress.seasonsCount} temporadas completas</span>
      </div>
    </article>
  `;
}

function renderReadingProgress(read) {
  return `
    <article class="detail-side-panel">
      <p class="detail-side-label">Reading</p>
      <h3>${Math.round(read.progressPct || 0)}% da jornada</h3>
      ${progressBar(read.progressPct || 0, 100)}
      <div class="detail-side-pairs">
        <span>${read.progressPages || 0} páginas</span>
        <span>${translateBookStatus(read.status)}</span>
      </div>
    </article>
  `;
}

function metricCard(label, value, detail) {
  return `
    <article class="metric-card">
      <p>${escapeHtml(label)}</p>
      <strong>${escapeHtml(value)}</strong>
      <span>${escapeHtml(detail)}</span>
    </article>
  `;
}

function buildFactCard(title, rows) {
  return `
    <article class="detail-card">
      <h4>${escapeHtml(title)}</h4>
      <div class="detail-kv-list">
        ${rows.filter(Boolean).join("")}
      </div>
    </article>
  `;
}

function factRow(label, value) {
  if (value === null || value === undefined || value === "") {
    return "";
  }

  return `
    <div class="detail-kv-row">
      <span>${escapeHtml(label)}</span>
      <strong>${escapeHtml(String(value))}</strong>
    </div>
  `;
}

function buildListCard(title, items, emptyMessage) {
  return `
    <article class="detail-card">
      <h4>${escapeHtml(title)}</h4>
      <div class="detail-list">
        ${
          items.length
            ? items
                .map(
                  (item) => `
                    ${item.href ? `<a class="detail-list-item" href="${escapeAttribute(item.href)}">` : `<article class="detail-list-item">`}
                      <h5>${escapeHtml(item.title)}</h5>
                      <p>${escapeHtml(item.meta || "")}</p>
                    ${item.href ? "</a>" : "</article>"}
                  `,
                )
                .join("")
            : `<div class="empty-state">${escapeHtml(emptyMessage)}</div>`
        }
      </div>
    </article>
  `;
}

function buildCoverShelfCard(title, items, emptyMessage) {
  return `
    <article class="detail-card detail-card-shelf">
      <h4>${escapeHtml(title)}</h4>
      <div class="cover-shelf">
        ${
          items.length
            ? items
                .map(
                  (item) => `
                    <a class="cover-tile" href="${escapeAttribute(item.href || "#")}">
                      <div class="cover-tile-image-wrap">
                        <img class="cover-tile-image js-resolve-image" data-src="${escapeAttribute(item.image || "")}" alt="${escapeAttribute(item.title)}" />
                        <span class="cover-tile-badge">${escapeHtml(item.badge || "")}</span>
                      </div>
                      <div class="cover-tile-copy">
                        <h5>${escapeHtml(item.title)}</h5>
                        <p>${escapeHtml(item.meta || "")}</p>
                      </div>
                    </a>
                  `,
                )
                .join("")
            : `<div class="empty-state">${escapeHtml(emptyMessage)}</div>`
        }
      </div>
    </article>
  `;
}

function buildPlayTimelineCard(title, plays, emptyMessage) {
  return `
    <article class="detail-card detail-card-shelf">
      <h4>${escapeHtml(title)}</h4>
      <div class="play-timeline">
        ${
          plays.length
            ? plays
                .map(
                  (play) => `
                    <a class="play-pill" href="${escapeAttribute(buildDetailUrl("music-album", { id: play.albumId }))}">
                      <strong>${escapeHtml(formatDateTime(play.playedAt) || "Play")}</strong>
                      <span>${escapeHtml(play.albumTitle)}</span>
                      <em>${escapeHtml(play.source)}</em>
                    </a>
                  `,
                )
                .join("")
            : `<div class="empty-state">${escapeHtml(emptyMessage)}</div>`
        }
      </div>
    </article>
  `;
}

function buildSingleGrid(content) {
  return `<div class="detail-grid detail-grid-single">${content}</div>`;
}

function progressBar(value, total) {
  const percent = computePercent(value, total);
  return `
    <div class="progress-bar" aria-hidden="true">
      <span class="progress-bar-fill" style="width:${percent}%"></span>
    </div>
  `;
}

function renderLoadingState() {
  return `
    <section class="detail-section">
      <div class="empty-state">Carregando detalhe...</div>
    </section>
  `;
}

function renderError(message) {
  renderPage({
    title: "Erro no detalhe",
    html: `
      <section class="detail-section">
        <div class="section-heading">
          <div>
            <p class="eyebrow">Erro</p>
            <h3>Não foi possível abrir o detalhe</h3>
          </div>
        </div>
        <div class="empty-state">${escapeHtml(message)}</div>
      </section>
    `,
  });
}

function bindResolvedImages(root) {
  root.querySelectorAll(".js-resolve-image").forEach((image) => {
    const raw = image.dataset.src;
    image.src = resolveAssetUrl(raw);
    image.onerror = () => {
      image.src = createPlaceholderDataUrl(image.alt || "Media Pulse");
    };
  });
}

function resolveEndpoint(basePath, slug, id) {
  if (slug) {
    return `${basePath}/slug/${encodeURIComponent(slug)}`;
  }

  if (id) {
    return `${basePath}/${encodeURIComponent(id)}`;
  }

  throw new Error("Link de detalhe sem identificador.");
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

function handleApiBaseChange() {
  const nextValue = window.prompt("Informe a base da API", state.apiBaseUrl);
  if (!nextValue) {
    return;
  }

  state.apiBaseUrl = nextValue.replace(/\/$/, "");
  localStorage.setItem("media-pulse-api-base", state.apiBaseUrl);
  apiBaseButton.textContent = compactApiLabel(state.apiBaseUrl);
  loadDetail();
}

function compactApiLabel(url) {
  return `API: ${url.replace(/^https?:\/\//, "")}`;
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

function deriveFallbackShowProgress(seasons, watches) {
  const watchedEpisodesCount = sum(seasons.map((season) => season.watchedEpisodesCount || 0)) || watches.length;
  const episodesCount = sum(seasons.map((season) => season.episodesCount || 0)) || watches.length;
  const seasonsCount = seasons.length;
  const completedSeasonsCount = seasons.filter((season) => season.completed).length;
  const completed = Boolean(episodesCount && watchedEpisodesCount >= episodesCount);
  const inProgress = watchedEpisodesCount > 0 && !completed;

  return {
    episodesCount,
    watchedEpisodesCount,
    seasonsCount,
    completedSeasonsCount,
    completed,
    inProgress,
  };
}

function buildIntensityPoints(values) {
  const count = Math.max(values.length, 1);
  return Array.from({ length: Math.min(Math.max(count, 8), 24) }, (_, index) => {
    if (index >= values.length) return 0;
    if (index >= values.length - 1) return 3;
    if (index >= values.length - 3) return 2;
    return 1;
  });
}

function normalizeBars(values) {
  if (!values.length) {
    return [];
  }

  const max = Math.max(...values, 1);
  return values.map((value) => Math.round((value / max) * 100));
}

function computePercent(value, total) {
  if (!total || total <= 0) {
    return 0;
  }

  return Math.max(0, Math.min(100, Math.round((value / total) * 100)));
}

function safeSeasonEpisode(watch) {
  const season = watch.seasonNumber ?? "?";
  const episode = watch.episodeNumber ?? "?";
  return `${season}x${episode}`;
}

function uniqueCountLabel(values, singular, plural) {
  const size = new Set(values.filter(Boolean)).size;
  return `${size} ${size === 1 ? singular : plural}`;
}

function truncateText(value, max) {
  if (!value) {
    return "";
  }

  return value.length > max ? `${value.slice(0, max - 1)}…` : value;
}

function sum(values) {
  return values.reduce((acc, value) => acc + Number(value || 0), 0);
}

function formatDate(value) {
  if (!value) {
    return "";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }

  return new Intl.DateTimeFormat("pt-BR", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  }).format(date);
}

function formatDateTime(value) {
  if (!value) {
    return "";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }

  return new Intl.DateTimeFormat("pt-BR", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

function translateBookStatus(status) {
  const map = {
    READ: "Lido",
    READING: "Lendo",
    WANT_TO_READ: "Quero ler",
    PAUSED: "Pausado",
    DID_NOT_FINISH: "Abandonei",
    UNKNOWN: "Desconhecido",
  };

  return map[status] || status || "";
}

function renderEmptyInline(message) {
  return `<div class="empty-inline">${escapeHtml(message)}</div>`;
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

function escapeAttribute(value) {
  return escapeHtml(value);
}

<template>
  <header class="app-header">
    <div class="brand-lockup">
      <span class="brand-dot" />
      <div>
        <p class="eyebrow">Media Pulse</p>
        <h1>Editorial home</h1>
      </div>
    </div>

    <p class="header-copy">
      Um ponto de partida para bater o olho no que está rodando, avançando ou acabando.
    </p>
  </header>
</template>

<style scoped>
.app-header {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: end;
}

.brand-lockup {
  display: flex;
  gap: 16px;
  align-items: center;
}

.brand-dot {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--base-color-brand-red);
  box-shadow: 0 0 0 10px color-mix(in srgb, var(--base-color-brand-red) 8%, white);
}

.eyebrow {
  margin: 0 0 4px;
  color: var(--base-color-text-secondary);
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

h1 {
  margin: 0;
  font-size: clamp(2rem, 5vw, 4.8rem);
  line-height: 0.95;
  letter-spacing: -0.05em;
}

.header-copy {
  max-width: 26rem;
  margin: 0;
  color: var(--base-color-text-secondary);
  font-size: 0.95rem;
}

@media (max-width: 820px) {
  .app-header {
    flex-direction: column;
    align-items: start;
  }
}
</style>

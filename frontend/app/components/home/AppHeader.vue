<template>
  <header class="app-header">
    <div class="brand-lockup">
      <span class="brand-dot" />
      <div>
        <p class="eyebrow">Media Pulse</p>
        <h1>Agora</h1>
      </div>
    </div>

    <p class="header-copy">
      Uma primeira passada de olho no que ficou aberto, voltou a aparecer e acabou de sair da frente.
    </p>
  </header>
</template>

<style scoped>
.app-header {
  display: flex;
  justify-content: space-between;
  gap: 32px;
  align-items: end;
}

.brand-lockup {
  display: flex;
  gap: 18px;
  align-items: center;
}

.brand-dot {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--base-color-brand-red);
  box-shadow: 0 0 0 8px color-mix(in srgb, var(--base-color-brand-red) 8%, white);
}

.eyebrow {
  margin: 0 0 6px;
  color: var(--base-color-text-secondary);
  font-size: 0.75rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

h1 {
  margin: 0;
  font-size: clamp(2.6rem, 6vw, 5.2rem);
  line-height: 0.95;
  letter-spacing: -0.05em;
}

.header-copy {
  max-width: 26rem;
  margin: 0 0 6px;
  color: var(--base-color-text-secondary);
  font-size: 0.98rem;
  line-height: 1.55;
}

@media (max-width: 820px) {
  .app-header {
    flex-direction: column;
    align-items: start;
  }
}
</style>

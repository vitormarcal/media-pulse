<template>
  <article class="people-card">
    <div class="people-head">
      <div class="people-copy">
        <p class="eyebrow">Pessoas</p>
        <h2>Direção, roteiro e elenco</h2>
      </div>

      <span class="summary-pill">{{ people.visibleCount }} pessoas</span>
    </div>

    <div class="people-groups">
      <section v-for="group in people.groups" :key="group.id" class="group-row">
        <p class="group-label">{{ group.title }}</p>

        <div class="chip-list">
          <NuxtLink v-for="item in group.items" :key="item.id" :to="item.href" class="person-pill">
            <div class="person-avatar">
              <img v-if="resolveMediaUrl(item.profileUrl)" :src="resolveMediaUrl(item.profileUrl)" :alt="item.name" />
              <div v-else class="avatar-fallback">{{ item.name.slice(0, 1) }}</div>
            </div>

            <div class="person-copy">
              <span class="person-name">{{ item.name }}</span>
              <small class="person-role">{{ item.roleLabel }}</small>
            </div>
          </NuxtLink>
        </div>
      </section>

      <p v-if="!people.groups.length" class="empty-copy">Nenhum crédito disponível.</p>
    </div>
  </article>
</template>

<script setup lang="ts">
import type { ShowPageData } from '~/types/shows'

defineProps<{
  people: ShowPageData['people']
}>()
const { resolveMediaUrl } = useMediaUrl()
</script>

<style scoped>
.people-card {
  display: grid;
  gap: 18px;
  padding: 22px 24px;
  border-radius: 28px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 84%, var(--base-color-surface-soft));
}

.people-head {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: start;
}

.people-copy,
.people-groups,
.group-row,
.person-copy {
  display: grid;
  gap: 10px;
}

.eyebrow,
.group-label {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.74rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

h2,
.empty-copy {
  margin: 0;
}

.people-copy h2 {
  font-size: 1.4rem;
  line-height: 1;
  letter-spacing: -0.04em;
}

.summary-pill {
  padding: 8px 12px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 76%, white);
  color: var(--base-color-text-primary);
  font-size: 0.78rem;
}

.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.person-pill {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding: 8px 12px 8px 8px;
  border-radius: 18px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 72%, white);
}

.person-avatar {
  width: 40px;
  height: 40px;
  overflow: hidden;
  border-radius: 50%;
  background: linear-gradient(160deg, rgba(230, 0, 35, 0.1), rgba(33, 25, 34, 0.06)), var(--base-color-surface-soft);
  flex-shrink: 0;
}

.person-avatar img,
.avatar-fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-fallback {
  display: grid;
  place-items: center;
  color: var(--base-color-text-secondary);
  font-size: 1rem;
  font-weight: 700;
}

.person-copy {
  gap: 2px;
  min-width: 0;
}

.person-name {
  margin: 0;
  font-weight: 700;
  line-height: 1.1;
}

.person-role {
  margin: 0;
  color: var(--base-color-text-secondary);
  font-size: 0.78rem;
  line-height: 1.2;
}

.empty-copy {
  color: var(--base-color-text-secondary);
}

@media (max-width: 900px) {
  .people-head {
    display: grid;
  }
}
</style>

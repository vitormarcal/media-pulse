<template>
  <main class="home-page">
    <AppHeader />

    <div v-if="status === 'pending'" class="state-card">
      <p>Montando a primeira página do momento...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível montar a página inicial com os dados atuais da API.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <HomeHero
        :title="data.hero.title"
        :intro="data.hero.intro"
        :lead="data.hero.lead"
        :supporting="data.hero.supporting"
      />

      <InProgressPanel :items="data.inProgress" />
      <MomentStream :items="data.recentMoments" />

      <EditorialRail
        v-for="section in data.sections"
        :key="section.id"
        :eyebrow="section.eyebrow"
        :title="section.title"
        :description="section.description"
        :summary="section.summary"
        :items="section.items"
      />
    </template>
  </main>
</template>

<script setup lang="ts">
import AppHeader from '~/components/home/AppHeader.vue'
import EditorialRail from '~/components/home/EditorialRail.vue'
import HomeHero from '~/components/home/HomeHero.vue'
import InProgressPanel from '~/components/home/InProgressPanel.vue'
import MomentStream from '~/components/home/MomentStream.vue'
import { useHomePageData } from '~/composables/useHomePageData'

useHead({
  title: 'Media Pulse',
  meta: [
    {
      name: 'description',
      content: 'Uma home editorial para acompanhar música, filmes, séries e livros sem cara de dashboard.',
    },
  ],
})

const { data, error, status } = await useHomePageData()
</script>

<style scoped>
.home-page {
  display: grid;
  gap: var(--sema-space-section);
  width: min(1480px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
}

.state-card {
  padding: 24px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.82);
  color: var(--base-color-text-secondary);
}

.state-card.error {
  color: #7a1414;
}

pre {
  margin: 12px 0 0;
  white-space: pre-wrap;
}

@media (max-width: 720px) {
  .home-page {
    width: min(100vw - 20px, 1480px);
    padding: 20px 0 64px;
  }
}
</style>

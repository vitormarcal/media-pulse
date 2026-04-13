<template>
  <main class="music-page">
    <div v-if="status === 'pending'" class="state-card">
      <p>Montando a página de música...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível montar a página de música com os dados atuais.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <MusicCollectionHero
        :title="data.hero.title"
        :intro="data.hero.intro"
        :lead="data.hero.lead"
        :supporting="data.hero.supporting"
      />

      <section class="music-section">
        <SectionHeading
          eyebrow="Discos em rotação"
          title="Os álbuns que estão sustentando o momento"
          description="A unidade principal da seção: capas, recorrência e contexto curto para voltar ao que está realmente ocupando espaço."
          summary="Álbum é o eixo editorial principal aqui, antes de artista e antes de faixa."
        />

        <div class="masonry-grid">
          <MediaPosterCard
            v-for="(item, index) in data.featuredAlbums"
            :key="item.id"
            :item="item"
            :variant="cardVariant(index)"
          />
        </div>
      </section>

      <MusicCollectionContext
        :eyebrow="data.context.eyebrow"
        :title="data.context.title"
        :description="data.context.description"
        :summary="data.context.summary"
        :metrics="data.context.metrics"
      />

      <section class="music-section">
        <SectionHeading
          eyebrow="Artistas em primeiro plano"
          title="Quem mais puxou a escuta recente"
          description="A camada de organização da biblioteca aparece aqui como reconhecimento rápido de recorrência."
          summary="Artista estrutura a coleção; não precisa disputar com álbum como protagonista visual."
        />

        <div class="strip-grid">
          <MusicStripCard
            v-for="item in data.topArtists"
            :key="item.id"
            kicker="Artista"
            :title="item.title"
            :subtitle="item.subtitle"
            :meta="item.meta"
            :detail="item.detail"
            :image-url="item.imageUrl"
            :href="item.href"
            variant="large"
          />
        </div>
      </section>

      <section class="music-section">
        <SectionHeading
          eyebrow="Faixas que insistiram em voltar"
          title="A camada fina do replay recente"
          description="Faixa entra como detalhe útil para repetição e memória curta, sem tomar a frente da seção."
          summary="Ela apoia descoberta e replay, mas o centro da experiência continua sendo álbum + artista."
        />

        <div class="strip-grid">
          <MusicStripCard
            v-for="item in data.topTracks"
            :key="item.id"
            kicker="Faixa"
            :title="item.title"
            :subtitle="item.subtitle"
            :meta="item.meta"
            :detail="item.detail"
            :image-url="item.imageUrl"
            :href="item.href"
          />
        </div>
      </section>

      <section class="music-section">
        <SectionHeading
          eyebrow="Fronteira de descoberta"
          title="O que ainda está esperando a primeira audição"
          description="O pedaço da coleção que já entrou no arquivo, mas ainda não virou memória ativa."
          summary="Esse bloco mantém vivo o lado de descoberta sem quebrar a consistência editorial da página."
        />

        <div class="strip-grid">
          <MusicStripCard
            v-for="item in data.discoveryAlbums"
            :key="item.id"
            kicker="Descoberta"
            :title="item.title"
            :subtitle="item.subtitle"
            :meta="item.meta"
            :detail="item.detail"
            :image-url="item.imageUrl"
            :href="item.href"
          />
        </div>
      </section>
    </template>
  </main>
</template>

<script setup lang="ts">
import MediaPosterCard from '~/components/home/MediaPosterCard.vue'
import SectionHeading from '~/components/home/SectionHeading.vue'
import MusicCollectionContext from '~/components/music/MusicCollectionContext.vue'
import MusicCollectionHero from '~/components/music/MusicCollectionHero.vue'
import MusicStripCard from '~/components/music/MusicStripCard.vue'
import { useMusicCollectionData } from '~/composables/useMusicCollectionData'

const { data, error, status } = await useMusicCollectionData()

function cardVariant(index: number) {
  const pattern = ['feature', 'compact', 'tall', 'standard', 'compact', 'tall'] as const
  return pattern[index % pattern.length]
}

useHead(() => ({
  title: 'Música · Media Pulse',
  meta: [
    {
      name: 'description',
      content: 'Recorte editorial da escuta recente no Media Pulse, com foco em álbuns, artistas e faixas em rotação.',
    },
  ],
}))
</script>

<style scoped>
.music-page {
  display: grid;
  gap: var(--sema-space-section);
  width: min(1480px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
}

.music-section,
.strip-grid {
  display: grid;
  gap: 24px;
}

.strip-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.masonry-grid {
  column-count: 4;
  column-gap: 20px;
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

@media (max-width: 1280px) {
  .masonry-grid {
    column-count: 3;
  }
}

@media (max-width: 900px) {
  .strip-grid {
    grid-template-columns: 1fr;
  }

  .masonry-grid {
    column-count: 2;
  }
}

@media (max-width: 720px) {
  .music-page {
    width: min(100vw - 20px, 1480px);
    padding: 20px 0 64px;
  }
}

@media (max-width: 520px) {
  .masonry-grid {
    column-count: 1;
  }
}
</style>

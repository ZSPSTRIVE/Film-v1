<template>
  <div class="page-shell">
    <div class="page-container">
      <section class="hero-gradient relative overflow-hidden rounded-[40px] px-6 py-8 text-white shadow-[0_40px_120px_rgba(15,23,42,0.26)] sm:px-10 sm:py-10 lg:px-14 lg:py-14">
        <div class="absolute inset-y-0 right-0 w-full max-w-[46%] overflow-hidden opacity-70">
          <img
            :src="heroMedia?.backdropUrl || heroMedia?.coverUrl || fallbackPoster"
            :alt="heroMedia?.title || 'Jelly Cinema'"
            class="h-full w-full object-cover"
          />
          <div class="absolute inset-0 bg-gradient-to-l from-transparent via-[#0b1326]/30 to-[#0b1326]"></div>
        </div>

        <div class="relative z-10 max-w-3xl">
          <p class="mb-4 text-sm font-medium uppercase tracking-[0.24em] text-white/65">Apple-Inspired Discovery</p>
          <h1 class="mb-5 max-w-2xl text-[2.7rem] font-semibold leading-[0.95] tracking-[-0.06em] sm:text-[4rem] lg:text-[5rem]">
            搜片、选片、看详情
            <br />
            一次完成
          </h1>
          <p class="mb-8 max-w-2xl text-base leading-7 text-white/78 sm:text-lg">
            把影视票务、内容检索和 AI 导购放进同一条体验链路里。你可以直接搜片名，也可以说“找一部热映的高分动画”。
          </p>

          <form class="surface-panel flex flex-col gap-3 rounded-[30px] p-3 sm:flex-row sm:items-center sm:gap-4 sm:p-4" @submit.prevent="goSearch">
            <div class="input-shell flex-1 bg-white/10 text-white ring-1 ring-white/10">
              <el-icon class="text-white/70"><Search /></el-icon>
              <input
                v-model="searchQuery"
                placeholder="片名、演员、导演，或直接描述想看的内容"
                class="placeholder:text-white/55"
              />
            </div>
            <div class="flex gap-3">
              <button type="button" class="secondary-button min-w-[120px] bg-white/18 text-white" @click="goAiSearch">
                AI 搜片
              </button>
              <button type="submit" class="primary-button min-w-[120px]">立即搜索</button>
            </div>
          </form>

          <div class="mt-6 flex flex-wrap gap-3">
            <button
              v-for="tag in trendingTags"
              :key="tag.label"
              class="chip border-white/10 bg-white/12 text-white transition-colors duration-200 hover:bg-white/18"
              @click="searchByTag(tag.label, tag.ai)"
            >
              {{ tag.label }}
            </button>
          </div>
        </div>
      </section>

      <section class="mt-8 grid gap-6 lg:grid-cols-[minmax(0,1.4fr)_minmax(320px,0.8fr)]">
        <div class="surface-panel-strong rounded-[34px] p-6 sm:p-8">
          <div class="mb-6 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
            <div>
              <p class="mb-2 text-sm font-medium uppercase tracking-[0.24em] text-[#6e6e73]">Explore</p>
              <h2 class="title-section">正在推荐给你的内容</h2>
            </div>

            <div class="flex flex-wrap gap-2">
              <button
                v-for="type in types"
                :key="type.value"
                class="chip transition"
                :class="activeType === type.value ? 'chip-active' : ''"
                @click="switchType(type.value)"
              >
                {{ type.label }}
              </button>
            </div>
          </div>

          <div v-if="loading" class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            <div v-for="index in 6" :key="index" class="h-[340px] animate-pulse rounded-[28px] bg-white/60"></div>
          </div>

          <div v-else-if="mediaList.length" class="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
            <article
              v-for="item in mediaList.slice(0, 6)"
              :key="item.id"
              class="group media-card aspect-[0.76] cursor-pointer"
              @click="openDetail(item.id)"
            >
              <img :src="item.coverUrl || fallbackPoster" :alt="item.title" class="media-card-poster" />
              <div class="absolute inset-x-0 bottom-0 z-10 p-5 text-white">
                <div class="mb-3 flex items-center justify-between text-xs font-medium">
                  <span class="rounded-full bg-white/16 px-3 py-1">{{ typeLabel(item.type) }}</span>
                  <span class="rounded-full bg-white/16 px-3 py-1">★ {{ item.rating ?? '--' }}</span>
                </div>
                <h3 class="text-xl font-semibold tracking-[-0.03em]">{{ item.title }}</h3>
                <p class="mt-2 line-clamp-2 text-sm leading-6 text-white/72">
                  {{ item.summary || '暂无简介' }}
                </p>
              </div>
            </article>
          </div>

          <div v-else class="rounded-[28px] bg-white/70 p-10 text-center text-[#6e6e73]">
            当前分类还没有可展示的内容。
          </div>
        </div>

        <aside class="grid gap-6">
          <section class="surface-panel-strong rounded-[34px] p-6">
            <p class="mb-2 text-sm font-medium uppercase tracking-[0.24em] text-[#6e6e73]">Featured</p>
            <h3 class="mb-4 text-2xl font-semibold tracking-[-0.04em] text-[#1d1d1f]">
              {{ heroMedia?.title || '焦点推荐' }}
            </h3>
            <p class="mb-5 text-sm leading-7 text-[#4a4a4f]">
              {{ heroMedia?.summary || '选择一个分类，系统会自动为你加载评分和上映时间都更适合决策表达的内容。' }}
            </p>
            <div class="grid gap-3 text-sm text-[#4a4a4f]">
              <div class="chip justify-between">
                <span>类型</span>
                <strong>{{ heroMedia ? typeLabel(heroMedia.type) : '全部' }}</strong>
              </div>
              <div class="chip justify-between">
                <span>评分</span>
                <strong>{{ heroMedia?.rating ?? '--' }}</strong>
              </div>
              <div class="chip justify-between">
                <span>上映时间</span>
                <strong>{{ formatDate(heroMedia?.releaseDate) }}</strong>
              </div>
            </div>
          </section>

          <section class="surface-panel rounded-[34px] p-6">
            <p class="mb-2 text-sm font-medium uppercase tracking-[0.24em] text-[#6e6e73]">Quick Start</p>
            <h3 class="mb-4 text-2xl font-semibold tracking-[-0.04em]">试试这些搜索方式</h3>
            <div class="grid gap-3">
              <button
                v-for="tag in guidePrompts"
                :key="tag"
                class="flex items-center justify-between rounded-[24px] border border-transparent bg-white/72 px-4 py-4 text-left text-sm font-medium text-[#1d1d1f] transition-colors duration-200 hover:border-[#0071e3]/12 hover:bg-white/84"
                @click="searchByTag(tag, true)"
              >
                <span>{{ tag }}</span>
                <span class="text-[#0071e3]">AI</span>
              </button>
            </div>
          </section>
        </aside>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { mediaApi, type Media } from '@/api/media'

const router = useRouter()

const fallbackPoster = 'https://placehold.co/960x1440/e5e7eb/1f2937?text=Jelly+Cinema'

const searchQuery = ref('')
const activeType = ref(0)
const mediaList = ref<Media[]>([])
const loading = ref(false)

const types = [
  { value: 0, label: '全部' },
  { value: 1, label: '电影' },
  { value: 2, label: '电视剧' },
  { value: 3, label: '动画' }
]

const trendingTags = [
  { label: '热映高分电影', ai: false },
  { label: '适合周末放松的喜剧', ai: true },
  { label: '最近口碑不错的动画', ai: true },
  { label: '悬疑烧脑', ai: false }
]

const guidePrompts = [
  '找一部热映中的高分动画',
  '推荐一部适合情侣周末看的电影',
  '想看节奏快一点的悬疑片',
  '来点轻松治愈的电视剧'
]

const heroMedia = computed(() => mediaList.value[0])

const fetchMedia = async () => {
  loading.value = true
  try {
    const res = await mediaApi.searchMedia({
      type: activeType.value || undefined,
      page: 1,
      pageSize: 18
    })
    if (res.code === 200) {
      mediaList.value = res.data.records
    }
  } finally {
    loading.value = false
  }
}

const goSearch = () => {
  const keyword = searchQuery.value.trim()
  if (!keyword) return
  router.push({ path: '/search', query: { q: keyword } })
}

const goAiSearch = () => {
  const keyword = searchQuery.value.trim()
  if (!keyword) return
  router.push({ path: '/search', query: { q: keyword, ai: '1' } })
}

const searchByTag = (keyword: string, ai = false) => {
  router.push({ path: '/search', query: { q: keyword, ai: ai ? '1' : undefined } })
}

const switchType = (type: number) => {
  activeType.value = type
  fetchMedia()
}

const openDetail = (id: number) => {
  router.push(`/media/${id}`)
}

const typeLabel = (type?: number) => ({ 1: '电影', 2: '电视剧', 3: '动画' }[type || 0] || '内容')
const formatDate = (date?: string) => (date ? new Date(date).toLocaleDateString('zh-CN') : '待更新')

onMounted(fetchMedia)
</script>

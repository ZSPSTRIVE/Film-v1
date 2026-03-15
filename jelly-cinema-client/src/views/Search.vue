<template>
  <div class="page-shell">
    <div class="page-container">
      <section class="surface-panel-strong rounded-[40px] p-6 sm:p-8 lg:p-10">
        <div class="grid gap-8 lg:grid-cols-[300px_minmax(0,1fr)]">
          <aside class="space-y-6">
            <div>
              <p class="mb-2 text-sm font-medium uppercase tracking-[0.24em] text-[#6e6e73]">Search Controls</p>
              <h1 class="title-section">把搜索变成更像对话的体验</h1>
            </div>

            <div class="surface-panel rounded-[28px] p-5">
              <div class="mb-3 text-sm font-medium text-[#6e6e73]">搜索模式</div>
              <div class="grid grid-cols-2 gap-2">
                <button class="chip justify-center transition" :class="!useAI ? 'chip-active' : ''" @click="switchMode(false)">
                  标准搜索
                </button>
                <button class="chip justify-center transition" :class="useAI ? 'chip-active' : ''" @click="switchMode(true)">
                  AI 搜片
                </button>
              </div>
            </div>

            <div class="surface-panel rounded-[28px] p-5">
              <div class="mb-4 text-sm font-medium text-[#6e6e73]">内容类型</div>
              <div class="flex flex-wrap gap-2">
                <button
                  v-for="type in types"
                  :key="type.value"
                  class="chip transition"
                  :class="activeType === type.value ? 'chip-active' : ''"
                  @click="activeType = type.value; submitSearch()"
                >
                  {{ type.label }}
                </button>
              </div>
            </div>

            <div class="surface-panel rounded-[28px] p-5">
              <div class="mb-4 text-sm font-medium text-[#6e6e73]">上映状态</div>
              <div class="flex flex-wrap gap-2">
                <button
                  v-for="status in statuses"
                  :key="status.label"
                  class="chip transition"
                  :class="activeStatus === status.value ? 'chip-active' : ''"
                  @click="activeStatus = status.value; submitSearch()"
                >
                  {{ status.label }}
                </button>
              </div>
            </div>

            <div class="surface-panel rounded-[28px] p-5">
              <div class="mb-4 text-sm font-medium text-[#6e6e73]">快捷试试</div>
              <div class="grid gap-3">
                <button
                  v-for="prompt in quickPrompts"
                  :key="prompt"
                  class="rounded-[22px] border border-transparent bg-white/72 px-4 py-4 text-left text-sm font-medium text-[#1d1d1f] transition-colors duration-200 hover:border-[#0071e3]/12 hover:bg-white/84"
                  @click="applyPrompt(prompt)"
                >
                  {{ prompt }}
                </button>
              </div>
            </div>
          </aside>

          <main class="min-w-0">
            <form class="input-shell mb-5" @submit.prevent="submitSearch">
              <el-icon class="text-[#6e6e73]"><Search /></el-icon>
              <input v-model="searchQuery" placeholder="输入片名，或直接说“找一部热映中的高分动画”" />
              <button type="submit" class="primary-button px-5 py-2.5 text-sm">搜索</button>
            </form>

            <div class="mb-6 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
              <div>
                <p class="mb-2 text-sm font-medium uppercase tracking-[0.24em] text-[#6e6e73]">
                  {{ useAI ? 'AI Search' : 'Keyword Search' }}
                </p>
                <h2 class="title-section">{{ searchTitle }}</h2>
                <p class="mt-2 text-sm leading-6 text-[#6e6e73]">
                  {{ resultDescription }}
                </p>
              </div>

              <div class="chip self-start lg:self-auto">
                <span>结果数</span>
                <strong>{{ total }}</strong>
              </div>
            </div>

            <section v-if="useAI && aiAnswer" class="surface-panel mb-6 rounded-[30px] p-6">
              <div class="mb-4 flex flex-wrap items-center gap-3">
                <span class="chip chip-active">AI 洞察</span>
                <span v-if="aiMeta.normalizedQuery" class="chip">重写词：{{ aiMeta.normalizedQuery }}</span>
                <span v-if="aiMeta.retrievalMode" class="chip">召回：{{ aiMeta.retrievalMode }}</span>
              </div>
              <p class="text-base leading-8 text-[#1d1d1f]">{{ aiAnswer }}</p>
              <p v-if="aiMeta.intentSummary" class="mt-3 text-sm leading-6 text-[#6e6e73]">
                检索意图：{{ aiMeta.intentSummary }}
              </p>
            </section>

            <div v-if="loading" class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              <div v-for="index in 6" :key="index" class="h-[320px] animate-pulse rounded-[28px] bg-white/70"></div>
            </div>

            <div v-else-if="mediaList.length" class="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
              <article
                v-for="item in mediaList"
                :key="item.id"
                class="surface-panel rounded-[28px] border border-transparent p-4 transition-colors duration-200 hover:border-[#0071e3]/10 hover:bg-white/80"
              >
                <div class="flex gap-4">
                  <img
                    :src="item.coverUrl || fallbackPoster"
                    :alt="item.title"
                    class="h-40 w-28 rounded-[22px] object-cover shadow-[0_18px_34px_rgba(15,23,42,0.12)]"
                  />
                  <div class="min-w-0 flex-1">
                    <div class="mb-3 flex flex-wrap gap-2 text-xs font-medium">
                      <span class="chip !px-3 !py-1">{{ typeLabel(item.type) }}</span>
                      <span class="chip !px-3 !py-1">{{ statusLabel(item.status) }}</span>
                    </div>
                    <h3 class="text-xl font-semibold tracking-[-0.03em] text-[#1d1d1f]">{{ item.title }}</h3>
                    <p v-if="item.originalTitle" class="mt-1 text-sm text-[#6e6e73]">{{ item.originalTitle }}</p>
                    <div class="mt-3 flex flex-wrap gap-3 text-sm text-[#4a4a4f]">
                      <span>★ {{ item.rating ?? '--' }}</span>
                      <span>{{ formatDate(item.releaseDate) }}</span>
                      <span v-if="item.duration">{{ item.duration }} 分钟</span>
                    </div>
                    <p class="mt-4 line-clamp-4 text-sm leading-7 text-[#4a4a4f]">
                      {{ item.summary || '暂无简介' }}
                    </p>
                  </div>
                </div>
                <div class="mt-5 flex items-center justify-between">
                  <button class="secondary-button px-4 py-2.5 text-sm" @click="openDetail(item.id)">查看详情</button>
                  <button class="primary-button px-4 py-2.5 text-sm" @click="askAiForItem(item.title)">
                    用 AI 继续搜
                  </button>
                </div>
              </article>
            </div>

            <div v-else class="surface-panel rounded-[30px] px-6 py-14 text-center">
              <div class="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-[#0071e3]/10 text-[#0071e3]">?</div>
              <h3 class="mb-2 text-xl font-semibold tracking-[-0.03em]">没有找到匹配内容</h3>
              <p class="mx-auto max-w-lg text-sm leading-7 text-[#6e6e73]">
                可以换一个更具体的片名、题材或上映状态。如果是模糊需求，建议切换到 AI 搜片模式。
              </p>
              <button class="secondary-button mt-5 px-5 py-2.5 text-sm" @click="resetFilters">重置筛选</button>
            </div>

            <div v-if="!useAI && total > pageSize" class="mt-8 flex justify-center">
              <el-pagination
                v-model:current-page="currentPage"
                v-model:page-size="pageSize"
                :page-sizes="[12, 24, 36]"
                :total="total"
                background
                class="apple-pagination"
                layout="prev, pager, next"
                @change="submitSearch"
              />
            </div>
          </main>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { mediaApi, type Media } from '@/api/media'

const route = useRoute()
const router = useRouter()

const fallbackPoster = 'https://placehold.co/960x1440/e5e7eb/1f2937?text=Jelly+Cinema'

const types = [
  { value: 0, label: '全部' },
  { value: 1, label: '电影' },
  { value: 2, label: '电视剧' },
  { value: 3, label: '动画' }
]

const statuses = [
  { value: null as number | null, label: '全部' },
  { value: 2, label: '热映中' },
  { value: 1, label: '待上映' },
  { value: 0, label: '筹备中' },
  { value: 3, label: '已下线' }
]

const quickPrompts = [
  '找一部最近口碑不错的动画',
  '来点轻松一点的喜剧电影',
  '想看节奏快的悬疑片',
  '推荐一部适合下班后放松的剧'
]

const searchQuery = ref((route.query.q as string) || '')
const useAI = ref(route.query.ai === '1')
const activeType = ref(route.query.type ? Number(route.query.type) : 0)
const activeStatus = ref(route.query.status ? Number(route.query.status) : (null as number | null))
const mediaList = ref<Media[]>([])
const loading = ref(false)
const total = ref(0)
const currentPage = ref(route.query.page ? Number(route.query.page) : 1)
const pageSize = ref(route.query.pageSize ? Number(route.query.pageSize) : 12)
const aiAnswer = ref('')
const aiMeta = ref({
  normalizedQuery: '',
  intentSummary: '',
  retrievalMode: ''
})

const searchTitle = computed(() => {
  if (!searchQuery.value.trim()) return '浏览全部内容'
  return useAI.value ? `AI 正在理解“${searchQuery.value.trim()}”` : `“${searchQuery.value.trim()}”的搜索结果`
})

const resultDescription = computed(() => {
  if (useAI.value) {
    return 'AI 会先理解用户意图，再结合当前片库进行检索增强。'
  }
  return '标准搜索会基于关键词、类型和上映状态直接查询片库。'
})

const syncRoute = () => {
  router.replace({
    path: '/search',
    query: {
      q: searchQuery.value.trim() || undefined,
      ai: useAI.value ? '1' : undefined,
      type: activeType.value || undefined,
      status: activeStatus.value ?? undefined,
      page: !useAI.value && currentPage.value > 1 ? String(currentPage.value) : undefined,
      pageSize: !useAI.value && pageSize.value !== 12 ? String(pageSize.value) : undefined
    }
  })
}

const composeAiQuery = () => {
  const extras: string[] = []
  if (activeType.value) extras.push(`仅限${typeLabel(activeType.value)}`)
  if (activeStatus.value !== null) extras.push(`状态为${statusLabel(activeStatus.value)}`)
  return [searchQuery.value.trim(), ...extras].filter(Boolean).join('，')
}

const submitSearch = async () => {
  syncRoute()
  loading.value = true
  aiAnswer.value = ''

  try {
    if (useAI.value) {
      currentPage.value = 1
      const res = await mediaApi.naturalLanguageSearch({
        query: composeAiQuery(),
        page: 1,
        pageSize: 12
      })
      if (res.code === 200) {
        aiAnswer.value = res.data.answer
        mediaList.value = res.data.mediaList || []
        total.value = res.data.matchedCount ?? mediaList.value.length
        aiMeta.value = {
          normalizedQuery: res.data.normalizedQuery || '',
          intentSummary: res.data.intentSummary || '',
          retrievalMode: res.data.retrievalMode || ''
        }
      } else {
        ElMessage.error(res.msg || 'AI 搜片失败')
      }
    } else {
      const res = await mediaApi.searchMedia({
        keyword: searchQuery.value.trim() || undefined,
        type: activeType.value || undefined,
        status: activeStatus.value ?? undefined,
        page: currentPage.value,
        pageSize: pageSize.value
      })
      if (res.code === 200) {
        mediaList.value = res.data.records
        total.value = res.data.total
      } else {
        ElMessage.error(res.msg || '搜索失败')
      }
    }
  } catch {
    ElMessage.error(useAI.value ? 'AI 搜片请求失败，请稍后再试' : '搜索请求失败，请稍后再试')
  } finally {
    loading.value = false
  }
}

const switchMode = (next: boolean) => {
  useAI.value = next
  submitSearch()
}

const applyPrompt = (prompt: string) => {
  searchQuery.value = prompt
  useAI.value = true
  submitSearch()
}

const resetFilters = () => {
  activeType.value = 0
  activeStatus.value = null
  useAI.value = false
  currentPage.value = 1
  pageSize.value = 12
  submitSearch()
}

const openDetail = (id: number) => {
  router.push(`/media/${id}`)
}

const askAiForItem = (title: string) => {
  searchQuery.value = `推荐和${title}气质相近的作品`
  useAI.value = true
  submitSearch()
}

const typeLabel = (type?: number) => ({ 1: '电影', 2: '电视剧', 3: '动画' }[type || 0] || '全部')
const statusLabel = (status?: number | null) =>
  ({ 0: '筹备中', 1: '待上映', 2: '热映中', 3: '已下线' }[status ?? -1] || '全部')
const formatDate = (date?: string) => (date ? new Date(date).toLocaleDateString('zh-CN') : '待更新')

watch(
  () => route.query,
  (query) => {
    searchQuery.value = (query.q as string) || ''
    useAI.value = query.ai === '1'
    activeType.value = query.type ? Number(query.type) : 0
    activeStatus.value = query.status ? Number(query.status) : null
    currentPage.value = query.page ? Number(query.page) : 1
    pageSize.value = query.pageSize ? Number(query.pageSize) : 12
  }
)

onMounted(() => {
  submitSearch()
})
</script>

<template>
  <div class="page-shell">
    <div class="page-container">
      <section v-if="media" class="relative overflow-hidden rounded-[42px]">
        <div class="detail-backdrop">
          <img :src="media.backdropUrl || media.coverUrl || fallbackPoster" :alt="media.title" class="h-full w-full object-cover" />
        </div>

        <div class="relative z-10 grid gap-8 px-6 py-10 text-white sm:px-10 lg:grid-cols-[320px_minmax(0,1fr)] lg:px-12 lg:py-12">
          <div class="surface-panel rounded-[34px] bg-white/10 p-4 shadow-[0_24px_60px_rgba(15,23,42,0.24)] backdrop-blur-2xl">
            <img :src="media.coverUrl || fallbackPoster" :alt="media.title" class="w-full rounded-[28px] object-cover" />
          </div>

          <div class="self-end">
            <div class="mb-4 flex flex-wrap gap-2">
              <span class="chip border-white/15 bg-white/12 text-white">{{ typeLabel(media.type) }}</span>
              <span class="chip border-white/15 bg-white/12 text-white">{{ statusLabel(media.status) }}</span>
              <span class="chip border-white/15 bg-white/12 text-white">★ {{ media.rating ?? '--' }}</span>
            </div>
            <h1 class="mb-3 max-w-3xl text-[2.8rem] font-semibold tracking-[-0.06em] sm:text-[4rem]">
              {{ media.title }}
            </h1>
            <p v-if="media.originalTitle" class="mb-4 text-base text-white/72">{{ media.originalTitle }}</p>
            <p class="max-w-3xl text-base leading-8 text-white/82">
              {{ media.summary || '暂无简介' }}
            </p>

            <div class="mt-8 flex flex-wrap gap-3">
              <button class="primary-button" @click="generateAiSummary" :disabled="generatingSummary">
                {{ generatingSummary ? 'AI 生成中...' : '生成 AI 导语' }}
              </button>
              <button class="secondary-button bg-white/14 text-white" @click="focusQa">打开智能问答</button>
            </div>
          </div>
        </div>
      </section>

      <section v-if="media" class="mt-8 grid gap-6 lg:grid-cols-[minmax(0,1.3fr)_minmax(320px,0.9fr)]">
        <div class="space-y-6">
          <article class="surface-panel-strong rounded-[34px] p-6 sm:p-8">
            <p class="mb-2 text-sm font-medium uppercase tracking-[0.24em] text-[#6e6e73]">Overview</p>
            <h2 class="title-section mb-4">作品信息</h2>
            <div class="grid gap-3 text-sm text-[#4a4a4f] sm:grid-cols-2">
              <div class="chip justify-between"><span>上映日期</span><strong>{{ formatDate(media.releaseDate) }}</strong></div>
              <div class="chip justify-between"><span>片长</span><strong>{{ media.duration ? `${media.duration} 分钟` : '待更新' }}</strong></div>
              <div class="chip justify-between"><span>评论数</span><strong>{{ media.commentCount ?? 0 }}</strong></div>
              <div class="chip justify-between"><span>综合评分</span><strong>{{ media.rating ?? '--' }}</strong></div>
            </div>
            <p class="mt-5 text-sm leading-8 text-[#4a4a4f]">
              {{ media.summary || '暂无简介' }}
            </p>
          </article>

          <article class="surface-panel-strong rounded-[34px] p-6 sm:p-8">
            <div class="mb-4 flex items-center justify-between gap-4">
              <div>
                <p class="mb-2 text-sm font-medium uppercase tracking-[0.24em] text-[#6e6e73]">AI Summary</p>
                <h2 class="title-section">AI 导语润色</h2>
              </div>
              <button class="secondary-button px-4 py-2.5 text-sm" @click="generateAiSummary" :disabled="generatingSummary">
                刷新导语
              </button>
            </div>

            <div class="rounded-[28px] bg-[#f8fbff] p-5 text-sm leading-8 text-[#1d1d1f]">
              <template v-if="generatingSummary">AI 正在根据影片资料生成更适合详情页首屏展示的导语...</template>
              <template v-else-if="generatedSummary">{{ generatedSummary }}</template>
              <template v-else>点击上方按钮生成一段更适合详情页展示的 AI 导语。</template>
            </div>
          </article>

          <article v-if="media.actors?.length" class="surface-panel-strong rounded-[34px] p-6 sm:p-8">
            <p class="mb-2 text-sm font-medium uppercase tracking-[0.24em] text-[#6e6e73]">Cast</p>
            <h2 class="title-section mb-5">主创与演员</h2>
            <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              <div
                v-for="actor in media.actors"
                :key="actor.id"
                class="rounded-[26px] bg-white/72 p-4 shadow-[0_18px_34px_rgba(15,23,42,0.08)]"
              >
                <div class="mb-4 flex items-center gap-4">
                  <img
                    :src="actor.avatarUrl || `https://api.dicebear.com/9.x/thumbs/svg?seed=${actor.name}`"
                    :alt="actor.name"
                    class="h-16 w-16 rounded-full object-cover"
                  />
                  <div>
                    <h3 class="text-base font-semibold text-[#1d1d1f]">{{ actor.name }}</h3>
                    <p class="text-sm text-[#6e6e73]">{{ roleLabel(actor.roleType) }}</p>
                  </div>
                </div>
                <p v-if="actor.characterName" class="text-sm text-[#4a4a4f]">饰演：{{ actor.characterName }}</p>
              </div>
            </div>
          </article>
        </div>

        <aside ref="qaPanel" class="surface-panel-strong rounded-[34px] p-6 sm:p-8">
          <p class="mb-2 text-sm font-medium uppercase tracking-[0.24em] text-[#6e6e73]">Media Q&A</p>
          <h2 class="title-section mb-5">基于影片信息的智能问答</h2>

          <div class="mb-4 space-y-3">
            <div
              v-for="item in qaMessages"
              :key="item.id"
              class="rounded-[24px] px-4 py-4 text-sm leading-7"
              :class="item.role === 'user' ? 'bg-[#1d1d1f] text-white' : 'bg-[#f8fbff] text-[#1d1d1f]'"
            >
              {{ item.content }}
            </div>
            <div v-if="!qaMessages.length" class="rounded-[24px] bg-[#f8fbff] px-4 py-5 text-sm leading-7 text-[#4a4a4f]">
              试着问它：
              “这部片值不值得看？”
              “剧情节奏怎么样？”
              “观众评论整体是什么风向？”
            </div>
          </div>

          <form class="space-y-3" @submit.prevent="submitQuestion">
            <div class="input-shell items-start">
              <textarea
                v-model="question"
                rows="4"
                placeholder="输入你想问这部作品的问题"
                class="resize-none"
              ></textarea>
            </div>
            <div class="flex items-center justify-between gap-3">
              <div class="text-xs text-[#6e6e73]">
                AI 会优先结合影片资料和社区评论回答。
              </div>
              <button class="primary-button px-5 py-2.5 text-sm" :disabled="askingQuestion">
                {{ askingQuestion ? '回答中...' : '发送问题' }}
              </button>
            </div>
          </form>
        </aside>
      </section>

      <section v-else class="surface-panel-strong rounded-[40px] px-6 py-20 text-center">
        <h2 class="mb-2 text-2xl font-semibold tracking-[-0.04em]">影片不存在</h2>
        <p class="text-sm leading-7 text-[#6e6e73]">当前链接没有匹配到可展示的影视内容。</p>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { mediaApi, type Media } from '@/api/media'

const route = useRoute()
const qaPanel = ref<HTMLElement | null>(null)

const fallbackPoster = 'https://placehold.co/960x1440/e5e7eb/1f2937?text=Jelly+Cinema'

const media = ref<Media | null>(null)
const generatedSummary = ref('')
const generatingSummary = ref(false)
const question = ref('')
const askingQuestion = ref(false)
const conversationId = ref('')
const qaMessages = ref<{ id: number; role: 'user' | 'assistant'; content: string }[]>([])

const mediaId = Number(route.params.id)

const loadMediaDetail = async () => {
  try {
    const res = await mediaApi.getMediaDetail(mediaId)
    if (res.code === 200) {
      media.value = res.data
    } else {
      ElMessage.error(res.msg || '加载影片详情失败')
    }
  } catch {
    ElMessage.error('加载影片详情失败')
  }
}

const generateAiSummary = async () => {
  if (!media.value) return
  generatingSummary.value = true
  try {
    const res = await mediaApi.generateSummary({
      mediaId: media.value.id,
      originalSummary: media.value.summary || ''
    })
    if (res.code === 200) {
      generatedSummary.value = res.data
    } else {
      ElMessage.error(res.msg || 'AI 导语生成失败')
    }
  } catch {
    ElMessage.error('AI 导语生成失败')
  } finally {
    generatingSummary.value = false
  }
}

const submitQuestion = async () => {
  const text = question.value.trim()
  if (!media.value || !text) return

  qaMessages.value.push({
    id: Date.now(),
    role: 'user',
    content: text
  })

  askingQuestion.value = true
  try {
    const res = await mediaApi.askQuestion(media.value.id, {
      question: text,
      conversationId: conversationId.value || undefined
    })
    if (res.code === 200) {
      conversationId.value = res.data.conversationId
      qaMessages.value.push({
        id: Date.now() + 1,
        role: 'assistant',
        content: res.data.answer
      })
      question.value = ''
      await nextTick()
      qaPanel.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    } else {
      ElMessage.error(res.msg || 'AI 问答失败')
    }
  } catch {
    ElMessage.error('AI 问答失败')
  } finally {
    askingQuestion.value = false
  }
}

const focusQa = () => {
  qaPanel.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

const typeLabel = (type?: number) => ({ 1: '电影', 2: '电视剧', 3: '动画' }[type || 0] || '内容')
const statusLabel = (status?: number) => ({ 0: '筹备中', 1: '待上映', 2: '热映中', 3: '已下线' }[status || 0] || '待更新')
const roleLabel = (role?: number) => ({ 1: '导演', 2: '编剧', 3: '主演', 4: '演员' }[role || 4] || '演员')
const formatDate = (date?: string) => (date ? new Date(date).toLocaleDateString('zh-CN') : '待更新')

onMounted(loadMediaDetail)
</script>

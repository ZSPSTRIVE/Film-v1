<template>
  <header class="fixed inset-x-0 top-0 z-50">
    <div class="page-container px-4 pt-4 sm:px-6">
      <div
        class="surface-panel-strong flex items-center justify-between rounded-[30px] px-4 py-3 sm:px-5"
        :class="isScrolled ? 'shadow-[0_30px_80px_rgba(15,23,42,0.16)]' : ''"
      >
        <div class="flex items-center gap-3">
          <router-link to="/" class="flex items-center gap-3">
            <div class="flex h-11 w-11 items-center justify-center rounded-[18px] bg-[#111827] text-sm font-semibold text-white shadow-[0_12px_26px_rgba(17,24,39,0.24)]">
              JC
            </div>
            <div class="hidden sm:block">
              <div class="text-sm font-semibold tracking-[0.24em] text-[#6e6e73]">JELLY CINEMA</div>
              <div class="text-base font-semibold tracking-[-0.03em] text-[#1d1d1f]">智能影视票务与内容平台</div>
            </div>
          </router-link>
        </div>

        <nav class="hidden items-center gap-1 rounded-full bg-white/70 p-1 lg:flex">
          <router-link
            v-for="item in navItems"
            :key="item.to"
            :to="item.to"
            class="rounded-full px-4 py-2 text-sm font-medium transition"
            :class="route.path === item.to ? 'bg-[#1d1d1f] text-white' : 'text-[#4a4a4f] hover:bg-white hover:text-[#1d1d1f]'"
          >
            {{ item.label }}
          </router-link>
        </nav>

        <div class="flex items-center gap-3">
          <form class="input-shell hidden min-w-[280px] py-2 lg:flex" @submit.prevent="submitQuickSearch">
            <el-icon class="text-[#6e6e73]"><Search /></el-icon>
            <input v-model="quickQuery" placeholder="片名、题材、导演、自然语言搜片" />
          </form>

          <template v-if="userStore.isLoggedIn">
            <button class="secondary-button px-4 py-2.5 text-sm" @click="handleLogout">退出登录</button>
          </template>
          <template v-else>
            <router-link to="/login" class="secondary-button px-4 py-2.5 text-sm">登录</router-link>
          </template>
        </div>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const navItems = [
  { label: '首页', to: '/' },
  { label: '搜索', to: '/search' }
]

const quickQuery = ref((route.query.q as string) || '')
const isScrolled = ref(false)

const handleScroll = () => {
  isScrolled.value = window.scrollY > 8
}

const submitQuickSearch = () => {
  const keyword = quickQuery.value.trim()
  if (!keyword) return
  router.push({ path: '/search', query: { q: keyword } })
}

const handleLogout = async () => {
  await userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}

watch(
  () => route.query.q,
  (value) => {
    quickQuery.value = (value as string) || ''
  }
)

onMounted(() => {
  window.addEventListener('scroll', handleScroll)
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
})
</script>

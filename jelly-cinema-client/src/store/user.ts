import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi, type UserInfo } from '@/api/auth'

export const useUserStore = defineStore('user', () => {
    const token = ref<string>(localStorage.getItem('jelly_token') || '')
    const userInfo = ref<UserInfo | null>(null)

    const isLoggedIn = computed(() => !!token.value)
    const isAdmin = computed(() => userInfo.value?.role === 'ADMIN')

    function setToken(t: string) {
        token.value = t
        localStorage.setItem('jelly_token', t)
    }

    function clearToken() {
        token.value = ''
        localStorage.removeItem('jelly_token')
        userInfo.value = null
    }

    async function fetchUserInfo() {
        if (!token.value) return
        try {
            const res = await authApi.getUserInfo()
            if (res.code === 200) {
                userInfo.value = res.data
            }
        } catch (e) {
            console.error('Failed to fetch user info', e)
        }
    }

    async function logout() {
        try {
            await authApi.logout()
        } catch (e) {
            // ignore
        } finally {
            clearToken()
        }
    }

    return { token, userInfo, isLoggedIn, isAdmin, setToken, clearToken, fetchUserInfo, logout }
})

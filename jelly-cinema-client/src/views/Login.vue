<template>
  <div class="login-page">
    <div class="login-card">
      <h2 class="login-title">🍮 欢迎回来</h2>
      <p class="login-sub">登录果冻影院，探索无限精彩</p>
      <el-form :model="form" :rules="rules" ref="formRef" @submit.prevent="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" size="large" prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" placeholder="密码" size="large" type="password" prefix-icon="Lock" show-password />
        </el-form-item>
        <el-button
          type="primary"
          size="large"
          style="width:100%;margin-top:8px"
          :loading="loading"
          native-type="submit"
        >
          登录
        </el-button>
      </el-form>
      <div class="login-footer">
        <span>还没有账号？</span>
        <router-link to="/register" class="link">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance } from 'element-plus'
import { authApi } from '@/api/auth'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = ref({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, min: 3, message: '密码不少于3位', trigger: 'blur' }]
}

const handleLogin = async () => {
  if (!await formRef.value?.validate().catch(() => false)) return
  loading.value = true
  try {
    const res = await authApi.login(form.value)
    if (res.code === 200) {
      userStore.setToken(res.data)
      await userStore.fetchUserInfo()
      ElMessage.success('登录成功')
      router.push('/')
    } else {
      ElMessage.error(res.msg || '登录失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '登录失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0d0d1f 0%, #1a0533 50%, #0d1a2e 100%);
}

.login-card {
  background: rgba(255,255,255,0.05);
  backdrop-filter: blur(16px);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 20px;
  padding: 48px 40px;
  width: 400px;
}

.login-title {
  font-size: 28px;
  font-weight: 800;
  color: #fff;
  text-align: center;
  margin: 0 0 8px 0;
}

.login-sub {
  text-align: center;
  color: rgba(255,255,255,0.5);
  margin-bottom: 32px;
  font-size: 14px;
}

.login-card :deep(.el-input__wrapper) {
  background: rgba(255,255,255,0.07);
  box-shadow: none;
  border-color: rgba(255,255,255,0.12);
}

.login-card :deep(.el-input__inner) {
  color: #fff;
}

.login-card :deep(.el-input__prefix-icon) {
  color: rgba(255,255,255,0.4);
}

.login-footer {
  text-align: center;
  margin-top: 20px;
  font-size: 14px;
  color: rgba(255,255,255,0.4);
}

.link {
  color: #a78bfa;
  margin-left: 6px;
  text-decoration: none;
}
.link:hover { text-decoration: underline; }
</style>

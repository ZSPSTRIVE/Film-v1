<template>
  <div class="page-shell flex items-center justify-center">
    <div class="surface-panel-strong w-full max-w-md rounded-[36px] p-8">
      <p class="mb-2 text-sm font-medium uppercase tracking-[0.24em] text-[#6e6e73]">Create Account</p>
      <h1 class="mb-2 text-3xl font-semibold tracking-[-0.04em] text-[#1d1d1f]">创建账号</h1>
      <p class="mb-8 text-sm leading-7 text-[#6e6e73]">注册后即可体验智能搜片、内容详情和票务链路。</p>

      <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent="handleRegister" class="space-y-4">
        <el-form-item prop="username">
          <div class="input-shell">
            <input v-model="form.username" placeholder="用户名" />
          </div>
        </el-form-item>

        <el-form-item prop="password">
          <div class="input-shell">
            <input v-model="form.password" type="password" placeholder="密码，至少 6 位" />
          </div>
        </el-form-item>

        <el-form-item prop="confirmPassword">
          <div class="input-shell">
            <input v-model="form.confirmPassword" type="password" placeholder="确认密码" />
          </div>
        </el-form-item>

        <el-form-item prop="email">
          <div class="input-shell">
            <input v-model="form.email" placeholder="邮箱，可选" />
          </div>
        </el-form-item>

        <button type="submit" class="primary-button w-full" :disabled="loading">
          {{ loading ? '注册中...' : '创建账号' }}
        </button>
      </el-form>

      <p class="mt-6 text-sm text-[#6e6e73]">
        已有账号？
        <router-link to="/login" class="font-medium text-[#0071e3]">返回登录</router-link>
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance } from 'element-plus'
import { authApi } from '@/api/auth'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = ref({
  username: '',
  password: '',
  confirmPassword: '',
  email: ''
})

const validatePass2 = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!value) {
    callback(new Error('请再次输入密码'))
    return
  }
  if (value !== form.value.password) {
    callback(new Error('两次输入的密码不一致'))
    return
  }
  callback()
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '长度在 6 到 20 个字符', trigger: 'blur' }
  ],
  confirmPassword: [{ required: true, validator: validatePass2, trigger: 'blur' }]
}

const handleRegister = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await authApi.register({
      username: form.value.username,
      password: form.value.password,
      email: form.value.email
    })
    if (res.code === 200) {
      ElMessage.success('注册成功，请登录')
      router.push('/login')
    } else {
      ElMessage.error(res.msg || '注册失败')
    }
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.msg || '网络请求失败')
  } finally {
    loading.value = false
  }
}
</script>

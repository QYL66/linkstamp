<!-- LinkStamp changes: redesigned login and registration. -->
<template>
  <main class="auth-page">
    <aside class="auth-art">
      <div class="art-image" aria-hidden="true"></div>
      <div class="art-brand"><img src="/linkstamp.svg" width="32" height="32" alt="" /> 链签 LinkStamp</div>
      <p>短链接管理<br />与访问统计</p>
    </aside>
    <section class="auth-panel">
      <div class="auth-content">
        <div class="mobile-brand"><img src="/linkstamp.svg" width="30" height="30" alt="" /> 链签 LinkStamp</div>
        <h1>{{ isLogin ? '登录链签' : '创建账号' }}</h1>
        <p class="auth-intro">{{ isLogin ? '登录后即可管理短链和查看访问数据。' : '注册后会自动创建一个默认分组。' }}</p>
        <el-form v-if="isLogin" key="login" ref="form" class="logon" :model="loginData" :rules="loginRules" label-position="top" @submit.prevent="submit">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="loginData.username" placeholder="请输入用户名" autocomplete="username" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="loginData.password" type="password" placeholder="请输入密码" autocomplete="current-password" show-password />
          </el-form-item>
          <p v-if="errorMessage" class="auth-error" role="alert">{{ errorMessage }}</p>
          <el-button type="primary" native-type="submit" :loading="loading" class="submit">登录 <el-icon><ArrowRight /></el-icon></el-button>
        </el-form>
        <el-form v-else key="register" ref="form" :model="registerData" :rules="registerRules" label-position="top" @submit.prevent="submit">
          <el-form-item label="用户名" prop="username">
            <el-input v-model.trim="registerData.username" placeholder="请输入用户名" maxlength="11" autocomplete="username" />
          </el-form-item>
          <div class="form-pair">
            <el-form-item label="姓名" prop="realName">
              <el-input v-model.trim="registerData.realName" placeholder="请输入姓名" autocomplete="name" />
            </el-form-item>
            <el-form-item label="手机号" prop="phone">
              <el-input v-model.trim="registerData.phone" placeholder="请输入手机号" autocomplete="tel" maxlength="11" inputmode="numeric" />
            </el-form-item>
          </div>
          <el-form-item label="邮箱" prop="mail">
            <el-input v-model.trim="registerData.mail" placeholder="请输入邮箱" autocomplete="email" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="registerData.password" type="password" placeholder="输入 8–128 位密码" maxlength="128" autocomplete="new-password" show-password />
          </el-form-item>
          <p v-if="errorMessage" class="auth-error" role="alert">{{ errorMessage }}</p>
          <el-button type="primary" native-type="submit" :loading="loading" class="submit">注册</el-button>
        </el-form>
        <p class="auth-switch">
          {{ isLogin ? '还没有账号？' : '已有账号？' }}
          <button type="button" :disabled="loading" @click="switchMode">{{ isLogin ? '去注册' : '去登录' }}</button>
        </p>
      </div>
    </section>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { saveSession } from '@/core/auth.js'
import API from '@/api'

const router = useRouter()
const form = ref()
const loading = ref(false)
const isLogin = ref(true)
const errorMessage = ref('')
const loginData = reactive({ username: '', password: '' })
const registerData = reactive({ username: '', password: '', realName: '', phone: '', mail: '' })
const required = (message) => ({ required: true, message, trigger: 'blur' })
const loginRules = { username: [required('请输入用户名')], password: [required('请输入密码')] }
const registerRules = {
  username: [required('请输入用户名')],
  realName: [required('请输入姓名')],
  phone: [required('请输入手机号'), { pattern: /^1[3-9]\d{9}$/, message: '请输入有效的手机号', trigger: 'blur' }],
  mail: [required('请输入邮箱'), { type: 'email', message: '请输入有效的邮箱', trigger: 'blur' }],
  password: [required('请输入密码'), { min: 8, max: 128, message: '密码长度须为 8–128 位', trigger: 'blur' }]
}

function switchMode() {
  isLogin.value = !isLogin.value
  errorMessage.value = ''
}

async function submit() {
  if (loading.value) return
  errorMessage.value = ''
  loading.value = true
  try {
    const valid = await form.value.validate().catch(() => false)
    if (!valid) return
    const credentials = isLogin.value ? loginData : registerData
    if (!isLogin.value) {
      const { data } = await API.user.addUser(registerData)
      if (data.code !== '0') {
        errorMessage.value = data.message || '注册失败，请重试'
        return
      }
      loginData.username = credentials.username
    }
    const { data } = await API.user.login({ username: credentials.username, password: credentials.password })
    if (data.code !== '0' || !data.data?.token) {
      errorMessage.value = isLogin.value
        ? (data.message === '用户不存在' ? '用户名或密码不正确。' : data.message || '登录失败，请重试。')
        : '账号已注册，请切换到登录后重试。'
      return
    }
    saveSession(credentials.username, data.data.token)
    await router.replace('/home/space')
  } catch {
    errorMessage.value = '无法连接链签服务。请确认后端已启动，再重试。'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page { display: grid; grid-template-columns: minmax(330px, 1.05fr) .95fr; min-height: 100dvh; padding: 17px; background: var(--canvas); }
.auth-art { position: relative; isolation: isolate; overflow: hidden; border-radius: 10px; min-height: 540px; display: flex; flex-direction: column; align-items: flex-start; justify-content: space-between; padding: clamp(24px, 4vw, 56px); color: #1d263b; background: #eddfc6; }
.art-image { position: absolute; z-index: -1; inset: 0; background: linear-gradient(180deg, #f4e9d48a, transparent 34%), url('/linkstamp-login.webp') center / cover no-repeat; }
.art-brand, .mobile-brand { display: flex; gap: 11px; align-items: center; font-weight: 700; font-size: 17px; }
.art-brand { padding: 6px 12px 6px 6px; background: #fff9edee; border: 1px solid #e3d8c1; border-radius: 8px; }
.auth-art p { padding: 11px 14px 13px; background: #fff8eae8; border-left: 3px solid var(--accent); font-size: clamp(23px, 3vw, 40px); line-height: 1.15; font-weight: 700; letter-spacing: -.04em; }
.auth-panel { display: grid; place-items: center; padding: 34px clamp(22px, 6vw, 100px); }
.auth-content { width: 100%; max-width: 410px; }
.mobile-brand { display: none; }
h1 { font-size: clamp(34px, 4vw, 54px); font-weight: 700; line-height: 1.15; letter-spacing: -.05em; }
.auth-intro { margin: 12px 0 32px; color: var(--muted); }
.form-pair { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 14px; }
:deep(.el-form-item) { margin-bottom: 21px; }
:deep(.el-form-item__label) { font-weight: 600; }
:deep(.el-input__wrapper) { min-height: 46px; background: var(--paper); }
.submit { width: 100%; min-height: 46px; margin-top: 3px; }
.submit .el-icon { margin-left: 6px; }
.auth-error { color: #a33331; margin-bottom: 12px; padding: 9px 12px; background: #fbefed; border-radius: 8px; }
.auth-switch { margin-top: 22px; color: var(--muted); }
.auth-switch button { background: none; border: 0; color: var(--accent); font-weight: 700; padding: 5px; }
@media (max-width: 740px) {
  .auth-page { grid-template-columns: 1fr; padding: 0; }
  .auth-art { display: none; }
  .auth-panel { align-items: start; padding: clamp(24px, 8vh, 80px) 22px; }
  .mobile-brand { display: flex; margin-bottom: 34px; }
}
</style>

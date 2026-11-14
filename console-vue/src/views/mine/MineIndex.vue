<!-- LinkStamp changes: make account details and editing work on narrow screens. -->
<template>
  <section class="account-page">
    <header><h1>账号设置</h1><p>查看并更新你的个人资料。</p></header>
    <div class="profile-panel">
      <div class="profile-heading">
        <span class="profile-icon"><el-icon><User /></el-icon></span>
        <h2>个人信息</h2>
        <el-button type="primary" :disabled="!profile" @click="openEditor">修改个人信息</el-button>
      </div>
      <el-skeleton v-if="loading" :rows="4" animated />
      <div v-else-if="errorMessage" class="profile-error" role="alert">
        <p>{{ errorMessage }}</p><el-button @click="loadProfile">重试</el-button>
      </div>
      <dl v-else class="profile-details">
        <div><dt>用户名</dt><dd>{{ profile?.username }}</dd></div>
        <div><dt>姓名</dt><dd>{{ profile?.realName || '—' }}</dd></div>
        <div><dt>手机号</dt><dd>{{ profile?.phone || '—' }}</dd></div>
        <div><dt>邮箱</dt><dd>{{ profile?.mail || '—' }}</dd></div>
      </dl>
    </div>
    <el-dialog v-model="editing" title="修改个人信息" width="480px">
      <el-form ref="form" :model="draft" :rules="rules" label-position="top" @submit.prevent="saveProfile">
        <el-form-item label="姓名" prop="realName"><el-input v-model.trim="draft.realName" autocomplete="name" /></el-form-item>
        <el-form-item label="手机号" prop="phone"><el-input v-model.trim="draft.phone" autocomplete="tel" maxlength="11" /></el-form-item>
        <el-form-item label="邮箱" prop="mail"><el-input v-model.trim="draft.mail" autocomplete="email" /></el-form-item>
        <el-form-item label="新密码" prop="password"><el-input v-model="draft.password" type="password" show-password maxlength="128" autocomplete="new-password" placeholder="留空则沿用当前密码" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editing = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveProfile">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getUsername } from '@/core/auth'
import API from '@/api'

const profile = ref()
const loading = ref(true)
const errorMessage = ref('')
const editing = ref(false)
const saving = ref(false)
const form = ref()
const draft = reactive({ realName: '', phone: '', mail: '', password: '' })
const rules = {
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [{ required: true, pattern: /^1[3-9]\d{9}$/, message: '请输入有效的手机号', trigger: 'blur' }],
  mail: [{ required: true, type: 'email', message: '请输入有效的邮箱', trigger: 'blur' }],
  password: [{ min: 8, max: 128, message: '密码长度须为 8–128 位', trigger: 'blur' }]
}

async function loadProfile() {
  loading.value = true
  errorMessage.value = ''
  try {
    const { data } = await API.user.queryUserInfo(getUsername())
    if (data.code !== '0') throw new Error(data.message)
    profile.value = data.data
  } catch {
    errorMessage.value = '个人信息加载失败，请重试。'
  } finally {
    loading.value = false
  }
}

function openEditor() {
  const { realName, phone, mail } = profile.value
  Object.assign(draft, { realName: realName ?? '', phone: phone ?? '', mail: mail ?? '', password: '' })
  editing.value = true
}

async function saveProfile() {
  if (saving.value) return
  saving.value = true
  try {
    if (!await form.value.validate().catch(() => false)) return
    const { data } = await API.user.editUser({ username: profile.value.username, ...draft })
    if (data.code !== '0') throw new Error(data.message)
    editing.value = false
    ElMessage.success('个人信息已保存')
    await loadProfile()
  } catch (error) {
    ElMessage.error(error.message || '保存失败，请重试')
  } finally {
    saving.value = false
  }
}

onMounted(loadProfile)
</script>

<style scoped>
.account-page { max-width: 770px; margin: 0 auto; }
h1 { font-size: clamp(30px, 4vw, 46px); line-height: 1.15; letter-spacing: -.04em; }
header p { color: var(--muted); margin-top: 9px; }
.profile-panel { border: 1px solid var(--line); background: var(--paper); border-radius: 15px; padding: clamp(16px, 3vw, 26px); margin-top: 25px; }
.profile-heading { display: flex; align-items: center; gap: 10px; margin-bottom: 22px; }
.profile-heading h2 { font-size: 19px; }
.profile-heading .el-button { margin-left: auto; }
.profile-icon { display: grid; place-items: center; width: 35px; height: 35px; border-radius: 9px; background: var(--accent-soft); color: var(--accent); }
.profile-details { margin: 0; }
.profile-details div { display: grid; grid-template-columns: 100px minmax(0, 1fr); border-top: 1px solid var(--line); gap: 8px; padding: 13px 2px; }
.profile-details dt, .profile-error { color: var(--muted); }
.profile-details dd { margin: 0; overflow-wrap: anywhere; }
.profile-error p { margin-bottom: 12px; }
</style>

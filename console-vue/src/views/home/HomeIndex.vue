<!-- LinkStamp changes: workspace navigation and account controls. -->
<template>
  <div class="workspace">
    <a class="skip-link" href="#workspace-content">跳到内容</a>
    <header class="topbar">
      <div class="topbar-inner">
        <RouterLink class="brand" to="/home/space">
          <img src="/linkstamp.svg" width="34" height="34" alt="" />
          <span>链签 <small>LinkStamp</small></span>
        </RouterLink>
        <nav class="top-nav" aria-label="主导航">
          <RouterLink to="/home/space"><el-icon><Link /></el-icon> 短链管理</RouterLink>
          <RouterLink to="/home/account"><el-icon><Setting /></el-icon> 账号设置</RouterLink>
        </nav>
        <el-dropdown trigger="click" @command="handleAccount">
          <button type="button" class="account-button" aria-label="账号菜单">
            <span class="avatar">{{ username.slice(0, 1).toUpperCase() || '链' }}</span>
            <span class="account-name">{{ username }}</span>
            <el-icon><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人信息</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>
    <main id="workspace-content" class="workspace-main" tabindex="-1">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { clearSession, getUsername } from '@/core/auth.js'
import API from '@/api'

const router = useRouter()
const username = getUsername() ?? ''

async function handleAccount(command) {
  if (command === 'profile') {
    await router.push('/home/account')
    return
  }
  try {
    const { data } = await API.user.logout()
    if (data.code !== '0') throw new Error(data.message || '退出失败，请重试')
    clearSession()
    await router.replace('/login')
  } catch (error) {
    if (error.response?.status !== 401) ElMessage.error(error.message || '退出失败，请重试')
  }
}
</script>

<style scoped>
.workspace { min-height: 100dvh; }
.topbar { background: var(--paper); border-bottom: 1px solid var(--line); }
.topbar-inner {
  max-width: 1536px;
  min-height: 68px;
  margin: auto;
  padding: 0 clamp(16px, 3vw, 44px);
  display: flex;
  align-items: center;
  gap: clamp(18px, 4vw, 64px);
}
.brand { display: inline-flex; align-items: center; gap: 10px; color: var(--ink); font-weight: 700; font-size: 18px; white-space: nowrap; }
.brand small { font-size: 13px; color: var(--muted); font-weight: 600; margin-left: 4px; }
.top-nav { display: flex; align-self: stretch; align-items: center; gap: 6px; }
.top-nav a { display: inline-flex; align-items: center; gap: 7px; padding: 8px 13px; border-radius: 8px; color: var(--muted); white-space: nowrap; }
.top-nav a:hover, .top-nav .router-link-active { color: var(--accent); background: var(--accent-soft); }
.top-nav .router-link-active { box-shadow: inset 0 -2px var(--accent); }
.topbar-inner > :last-child { margin-left: auto; }
.account-button { display: flex; gap: 9px; align-items: center; padding: 5px 8px; border: 1px solid var(--line); border-radius: 10px; background: var(--paper); }
.account-button:hover { border-color: var(--accent); }
.account-name { max-width: 105px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.avatar { display: grid; place-items: center; width: 28px; height: 28px; border-radius: 8px; color: var(--accent); background: var(--accent-soft); font-weight: 700; }
.workspace-main { max-width: 1536px; margin: auto; padding: clamp(17px, 2.6vw, 39px) clamp(16px, 3vw, 44px) 36px; }
.skip-link { position: fixed; left: 12px; top: 12px; z-index: 10; padding: 10px; background: var(--paper); transform: translateY(-150%); }
.skip-link:focus { transform: none; }
@media (max-width: 620px) {
  .topbar-inner { min-height: 59px; gap: 9px; }
  .brand small, .account-name, .top-nav .el-icon { display: none; }
  .top-nav { gap: 2px; }
  .top-nav a { padding: 7px; font-size: 13px; }
}
</style>

// LinkStamp changes: route the recycle bin to the working console view.
import { createRouter, createWebHistory } from 'vue-router'
import { restoreSession } from '@/core/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/home/space' },
    { path: '/login', name: 'LoginIndex', component: () => import('@/views/login/LoginIndex.vue') },
    {
      path: '/home',
      redirect: '/home/space',
      component: () => import('@/views/home/HomeIndex.vue'),
      children: [
        { path: 'space', name: 'MySpace', component: () => import('@/views/mySpace/MySpaceIndex.vue') },
        { path: 'recycleBin', redirect: '/home/space?view=recycle' },
        { path: 'account', name: 'Mine', component: () => import('@/views/mine/MineIndex.vue') }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

router.beforeEach((to) => to.name === 'LoginIndex' || restoreSession() ? true : '/login')

export default router

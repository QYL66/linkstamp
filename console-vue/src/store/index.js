// LinkStamp changes: portfolio interface and local operation.
import { createStore } from 'vuex'

// 创建一个新的 store 实例
const store = createStore({
  state() {
    return {
      domain: import.meta.env.VITE_SHORTLINK_DOMAIN || '127.0.0.1:8003'
    }
  }
})

export default store

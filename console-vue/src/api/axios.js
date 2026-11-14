// LinkStamp changes: preserve network errors and clear expired sessions.
import axios from 'axios'
import { clearSession, getToken, getUsername } from '@/core/auth.js'
import router from '@/router'

const http = axios.create({ baseURL: '/api/short-link/admin/v1', timeout: 15000 })

http.interceptors.request.use((config) => {
  config.headers.Token = getToken() || ''
  config.headers.Username = getUsername() || ''
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      clearSession()
      void router.replace('/login')
    }
    return Promise.reject(error)
  }
)

export default http

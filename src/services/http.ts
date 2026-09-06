import axios from 'axios'

export const http = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api', timeout: 10_000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('atlas-token')
  if (token) config.headers.set('Authorization', `Bearer ${token}`)
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) localStorage.removeItem('atlas-token')
    return Promise.reject(error)
  },
)

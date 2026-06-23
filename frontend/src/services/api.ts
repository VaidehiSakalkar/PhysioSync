import axios from 'axios'

const api = axios.create({ baseURL: '/api' })

api.interceptors.request.use(cfg => {
  const token = localStorage.getItem('physiolink_token')
  if (token) cfg.headers.Authorization = `Bearer ${token}`
  return cfg
})

api.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('physiolink_token')
      localStorage.removeItem('physiolink_role')
      localStorage.removeItem('physiolink_userId')
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

export default api

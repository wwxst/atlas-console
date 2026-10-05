import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Eye, EyeOff } from 'lucide-react'
import { useLocation, useNavigate } from 'react-router-dom'
import { CURRENT_SYS_USER_QUERY_KEY, getCurrentSysUser, loginSysUser } from '@/features/auth/api'
import type { LoginInput } from '@/features/auth/api'
import { clearAuthTokens, saveAuthTokens } from '@/services/http'
import { AppButton, AuthField, IconButton, Toast } from '@ui/index'
import loginOrbit from '@/assets/login-orbit.svg'
import styles from './LoginPage.module.less'

interface LoginLocationState {
  from?: string
}

interface LoginFieldErrors {
  username?: string
  password?: string
}

export default function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const [showPassword, setShowPassword] = useState(false)
  const [fieldErrors, setFieldErrors] = useState<LoginFieldErrors>({})
  const [loginSuccess, setLoginSuccess] = useState(false)
  const loginMutation = useMutation({
    mutationFn: async (input: LoginInput) => {
      // 登录返回双 Token；系统用户资料随后由 /me 获取
      const tokenPair = await loginSysUser(input)
      saveAuthTokens(tokenPair.accessToken, tokenPair.refreshToken)
      try {
        return await getCurrentSysUser()
      } catch (error) {
        clearAuthTokens()
        throw error
      }
    },
    onSuccess: (currentUser) => {
      queryClient.setQueryData(CURRENT_SYS_USER_QUERY_KEY, currentUser)
      setLoginSuccess(true)
    },
  })

  useEffect(() => {
    if (!loginSuccess) return

    const state = location.state as LoginLocationState | null
    const timer = window.setTimeout(() => navigate(state?.from ?? '/', { replace: true }), 900)
    return () => window.clearTimeout(timer)
  }, [loginSuccess, location.state, navigate])

  const submitLogin = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const formData = new FormData(event.currentTarget)
    const username = String(formData.get('username') ?? '').trim()
    const password = String(formData.get('password') ?? '')
    const nextErrors: LoginFieldErrors = {
      username: username ? undefined : '请输入登录账号',
      password: password ? undefined : '请输入登录密码',
    }

    setFieldErrors(nextErrors)
    if (nextErrors.username || nextErrors.password) return

    loginMutation.mutate({ username, password })
  }

  const clearFieldError = (field: keyof LoginFieldErrors) => {
    setFieldErrors((errors) => errors[field] ? { ...errors, [field]: undefined } : errors)
  }

  return <main className={styles.page}>
    <aside className={styles.visualPanel}>
      <div className={styles.visualContent}>
        <h1>Atlas Console<br />运营管理平台</h1>
        <p>集中管理用户、数据与系统设置</p>
        <img className={styles.visualIllustration} src={loginOrbit} alt="" aria-hidden="true" />
      </div>
    </aside>
    <section className={styles.loginPanel}>
      <div className={styles.logo} role="img" aria-label="Atlas Console 运营平台">
        <strong>Atlas Console</strong>
        <span>运营平台</span>
      </div>

      <form className={styles.form} onSubmit={submitLogin} noValidate>
        <label>
          <span className={styles.visuallyHidden}>登录账号</span>
          <AuthField invalid={Boolean(fieldErrors.username)}>
            <input name="username" required maxLength={20} autoComplete="username" autoFocus placeholder="请输入登录账号" aria-invalid={Boolean(fieldErrors.username)} aria-describedby={fieldErrors.username ? 'username-error' : undefined} onChange={() => clearFieldError('username')} />
          </AuthField>
          {fieldErrors.username && <span id="username-error" className={styles.fieldError}>{fieldErrors.username}</span>}
        </label>
        <label>
          <span className={styles.visuallyHidden}>登录密码</span>
          <AuthField invalid={Boolean(fieldErrors.password)} endAdornment={
            <IconButton type="button" label={showPassword ? '隐藏密码' : '显示密码'} onClick={() => setShowPassword((visible) => !visible)}>
              {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
            </IconButton>
          }>
            <input name="password" required maxLength={24} type={showPassword ? 'text' : 'password'} autoComplete="current-password" placeholder="请输入登录密码" aria-invalid={Boolean(fieldErrors.password)} aria-describedby={fieldErrors.password ? 'password-error' : undefined} onChange={() => clearFieldError('password')} />
          </AuthField>
          {fieldErrors.password && <span id="password-error" className={styles.fieldError}>{fieldErrors.password}</span>}
        </label>

        <AppButton className={styles.submit} type="submit" variant="primary" disabled={loginMutation.isPending}>
          {loginMutation.isPending ? '正在登录...' : '登录'}
        </AppButton>
      </form>
      <Toast open={loginSuccess}>登录成功</Toast>
    </section>
  </main>
}

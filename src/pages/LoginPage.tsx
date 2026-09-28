import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Eye, EyeOff } from 'lucide-react'
import { useLocation, useNavigate } from 'react-router-dom'
import { ADMIN_TOKEN_KEY, CURRENT_SYS_USER_QUERY_KEY, loginSysUser } from '@/features/auth/api'
import { AppButton, AuthField, IconButton, Toast } from '@ui/index'
import loginVisual from '@/assets/login-visual.png'
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
    mutationFn: loginSysUser,
    onSuccess: (result) => {
      localStorage.setItem(ADMIN_TOKEN_KEY, result.token)
      queryClient.setQueryData(CURRENT_SYS_USER_QUERY_KEY, {
        id: result.id,
        username: result.username,
        nickname: result.nickname,
      })
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
    <aside className={styles.visualPanel} aria-hidden="true">
      <img src={loginVisual} alt="" />
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

import { FormEvent, useState } from 'react'
import { LogIn } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from './AuthProvider'
import { ErrorState } from '../components/AsyncState'

export function LoginPage() {
  const navigate = useNavigate()
  const { login, isLoading, error, clearError } = useAuth()
  const [organizationId, setOrganizationId] = useState('')
  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    clearError()
    try {
      await login({ organizationId, login: loginId, password })
      navigate('/customer/dashboard')
    } catch {
      // The provider exposes the normalized error for the form.
    }
  }

  return (
    <main className="login-page">
      <div className="login-art"><span className="login-mark">Q</span><p>Less waiting.<br /><strong>More certainty.</strong></p><small>Queue management for the moments that matter.</small></div>
      <div className="login-form-wrap">
        <form className="login-form" onSubmit={submit}>
          <p className="eyebrow">QueueWise account</p>
          <h1>Sign in</h1>
          <p className="login-copy">Use your organization and account credentials to continue.</p>
          {error && <ErrorState error={error} />}
          <label>Organization ID<input value={organizationId} onChange={(event) => setOrganizationId(event.target.value)} required /></label>
          <label>Email or phone<input value={loginId} onChange={(event) => setLoginId(event.target.value)} required /></label>
          <label>Password<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} required /></label>
          <button className="primary-button primary-button-wide" disabled={isLoading}>{isLoading ? 'Signing in...' : 'Sign in'} <LogIn size={17} /></button>
        </form>
      </div>
    </main>
  )
}

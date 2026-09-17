import { useState, type ChangeEvent, type FormEvent } from 'react'
import { UserPlus } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { apiClient } from '../api/client'
import { ErrorState } from '../components/AsyncState'

interface RegistrationInput {
  organizationId: string
  firstName: string
  lastName: string
  email: string
  password: string
}

export function RegisterPage() {
  const navigate = useNavigate()
  const [form, setForm] = useState<RegistrationInput>({ organizationId: '', firstName: '', lastName: '', email: '', password: '' })
  const registration = useMutation({
    mutationFn: async (input: RegistrationInput) => apiClient.post('/auth/register', input),
    onSuccess: () => navigate('/login'),
  })

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    registration.mutate(form)
  }

  const update = (field: keyof RegistrationInput) => (event: ChangeEvent<HTMLInputElement>) => setForm({ ...form, [field]: event.target.value })

  return (
    <main className="login-page">
      <div className="login-art"><span className="login-mark">Q</span><p>Make space<br /><strong>for your day.</strong></p><small>One account for appointments, queues, and updates.</small></div>
      <div className="login-form-wrap"><form className="login-form" onSubmit={submit}><p className="eyebrow">QueueWise account</p><h1>Create your account</h1><p className="login-copy">Join your organization’s queue and appointment workspace.</p>{registration.error && <ErrorState error={registration.error} />}<label>Organization ID<input value={form.organizationId} onChange={update('organizationId')} required /></label><label>First name<input value={form.firstName} onChange={update('firstName')} required /></label><label>Last name<input value={form.lastName} onChange={update('lastName')} required /></label><label>Email<input type="email" value={form.email} onChange={update('email')} required /></label><label>Password<input type="password" value={form.password} onChange={update('password')} minLength={12} required /></label><button className="primary-button primary-button-wide" disabled={registration.isPending}>{registration.isPending ? 'Creating account...' : 'Create account'} <UserPlus size={17} /></button><p className="login-footer">Already registered? <Link className="text-button" to="/login">Sign in</Link></p></form></div>
    </main>
  )
}

import { AlertCircle, LoaderCircle } from 'lucide-react'
import type { ReactNode } from 'react'
import { ApiRequestError } from '../api/errors'

export function LoadingState({ label = 'Loading...' }: { label?: string }) {
  return <div className="async-state"><LoaderCircle className="spin" size={20} /><span>{label}</span></div>
}

export function ErrorState({ error, children }: { error: unknown; children?: ReactNode }) {
  const message = error instanceof ApiRequestError ? error.message : 'Something went wrong. Please try again.'
  return <div className="async-state async-state-error"><AlertCircle size={20} /><span>{message}</span>{children}</div>
}

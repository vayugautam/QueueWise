import axios from 'axios'

export interface ApiErrorBody {
  timestamp?: string
  status?: number
  error?: string
  message?: string
  path?: string
  correlationId?: string
  errors?: { field: string; code: string; message: string }[]
}

export class ApiRequestError extends Error {
  readonly status?: number
  readonly code: string
  readonly correlationId?: string
  readonly fieldErrors: ApiErrorBody['errors']

  constructor(body: ApiErrorBody = {}, fallback = 'The request could not be completed.') {
    super(body.message || fallback)
    this.name = 'ApiRequestError'
    this.status = body.status
    this.code = body.error || 'REQUEST_FAILED'
    this.correlationId = body.correlationId
    this.fieldErrors = body.errors || []
  }
}

export function normalizeApiError(error: unknown): ApiRequestError {
  if (error instanceof ApiRequestError) return error
  if (axios.isAxiosError<ApiErrorBody>(error)) {
    return new ApiRequestError(error.response?.data, error.message)
  }
  return new ApiRequestError()
}

export type Envelope<T> = {
  status: 'SUCCESS' | 'ERROR'
  message?: string
  data?: T
  errors?: { code?: string; message?: string; fieldErrors?: Record<string, string> }[]
}

export function Badge({ value }: { value?: string }) {
  if (!value) return null
  return <span className={`badge ${value.toLowerCase()}`}>{value.replace('_', ' ')}</span>
}

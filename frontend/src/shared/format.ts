export const money = (n: unknown) =>
  n == null || n === '' ? '—' : Number(n).toLocaleString('es', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

export const num = (n: unknown) => (n == null || n === '' ? '—' : Number(n).toLocaleString('es', { maximumFractionDigits: 3 }))

export const fecha = (s: unknown) => {
  if (!s) return '—'
  const str = String(s)
  return new Date(str.length === 10 ? `${str}T00:00:00` : str).toLocaleDateString('es')
}

/** Muestra un texto o, si llega un objeto anidado, su nombre más legible. */
export const txt = (v: unknown): string => {
  if (v == null || v === '') return '—'
  if (typeof v === 'object') {
    const o = v as Record<string, unknown>
    return String(o.razonSocial ?? o.nombre ?? o.descripcion ?? o.codigo ?? '—')
  }
  return String(v)
}

export const today = () => new Date().toISOString().slice(0, 10)

export const fechaHora = (s: unknown) => (s ? new Date(String(s)).toLocaleString('es', { dateStyle: 'short', timeStyle: 'short' }) : '—')

export const daysAgo = (d: number) => new Date(Date.now() - d * 86_400_000).toISOString().slice(0, 10)

export const productoLabel = (p: any) => (p ? `${p.codigo ?? ''} — ${p.descripcion ?? ''}` : '—')

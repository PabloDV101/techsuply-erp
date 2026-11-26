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
    return String(o.nombreRazonSocial ?? o.razonSocial ?? o.nombre ?? o.descripcion ?? o.codigo ?? '—')
  }
  return String(v)
}

const businessDateInput = (date: Date, daysOffset = 0) => {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'America/Mexico_City', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(date)
  const part = (type: Intl.DateTimeFormatPartTypes) => Number(parts.find((item) => item.type === type)?.value)
  return new Date(Date.UTC(part('year'), part('month') - 1, part('day') - daysOffset)).toISOString().slice(0, 10)
}

export const today = () => businessDateInput(new Date())

export const fechaHora = (s: unknown) => (s ? new Date(String(s)).toLocaleString('es', { dateStyle: 'short', timeStyle: 'short' }) : '—')

export const daysAgo = (days: number) => {
  return businessDateInput(new Date(), days)
}

export const productoLabel = (p: any) => (p ? `${p.sku ?? p.codigo ?? ''} — ${p.nombre ?? p.descripcion ?? ''}` : '—')

import type { AxiosResponse } from 'axios'
import { http } from '../../api/http'
import { PATHS } from '../../config/contract'

export type ReportParams = Record<string, string | number | undefined>

/** Parse decimal JSON tokens as strings so JavaScript does not round report values. */
function parsePreservingDecimals(raw: unknown): unknown {
  if (typeof raw !== 'string') return raw
  let output = ''
  let inString = false
  let escaped = false
  for (let index = 0; index < raw.length;) {
    const char = raw[index]
    if (inString) {
      output += char
      index += 1
      if (escaped) escaped = false
      else if (char === '\\') escaped = true
      else if (char === '"') inString = false
      continue
    }
    if (char === '"') {
      inString = true
      output += char
      index += 1
      continue
    }
    if (char === '-' || (char >= '0' && char <= '9')) {
      const match = raw.slice(index).match(/^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?/)
      if (match) {
        const token = match[0]
        const unsafeInteger = !token.includes('.') && !/[eE]/.test(token)
          && (BigInt(token) > BigInt(Number.MAX_SAFE_INTEGER) || BigInt(token) < BigInt(Number.MIN_SAFE_INTEGER))
        output += token.includes('.') || /[eE]/.test(token) || unsafeInteger ? JSON.stringify(token) : token
        index += token.length
        continue
      }
    }
    output += char
    index += 1
  }
  return JSON.parse(output) as unknown
}

export async function getReport<T>(path: string, params: ReportParams): Promise<T> {
  const response = await http.get<T>(path, {
    params,
    responseType: 'text',
    transformResponse: [parsePreservingDecimals],
  })
  return response.data
}

function filenameFrom(response: AxiosResponse<Blob>, fallback: string) {
  const header = response.headers['content-disposition'] as string | undefined
  const filename = header?.match(/filename\s*=\s*"?([^";]+)"?/i)?.[1]
  return filename && /^[\w.-]+$/.test(filename) ? filename : fallback
}

export async function downloadReport(params: ReportParams, fallbackFilename: string) {
  const response = await http.get<Blob>(PATHS.reportExport, { params, responseType: 'blob' })
  saveBlob(response, fallbackFilename)
}

export async function downloadDashboardCsv(params: ReportParams) {
  const response = await http.get<Blob>(PATHS.dashboardExportCsv, { params, responseType: 'blob' })
  saveBlob(response, 'dashboard.csv')
}

function saveBlob(response: AxiosResponse<Blob>, fallbackFilename: string) {
  const url = URL.createObjectURL(response.data)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filenameFrom(response, fallbackFilename)
  document.body.append(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 0)
}

export function exactDecimal(value: unknown, fractionDigits?: number) {
  if (value == null || value === '') return '—'
  const source = String(value)
  const match = source.match(/^(-?)(\d+)(?:\.(\d+))?$/)
  if (!match) return source
  const [, sign, integer, fraction = ''] = match
  const grouped = BigInt(integer).toLocaleString('es-MX')
  const shown = fractionDigits == null ? fraction : fraction.slice(0, fractionDigits).padEnd(fractionDigits, '0')
  return `${sign}${grouped}${shown ? `,${shown}` : ''}`
}

export function reportLabel(value: string) {
  return value.replace(/([a-z0-9])([A-Z])/g, '$1 $2').replace(/^./, (first) => first.toUpperCase())
}

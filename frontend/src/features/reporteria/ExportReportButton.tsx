import { useState } from 'react'
import { Button, Menu, MenuItem } from '@mui/material'
import DownloadIcon from '@mui/icons-material/FileDownloadOutlined'
import { errorMessage } from '../../api/http'
import { useNotify } from '../../shared/Notify'
import { downloadReport, type ReportParams } from './reportingApi'

export default function ExportReportButton({ report, params, filename, disabled = false }: {
  report: string
  params: ReportParams
  filename: string
  disabled?: boolean
}) {
  const [anchor, setAnchor] = useState<HTMLElement | null>(null)
  const [loading, setLoading] = useState(false)
  const notify = useNotify()
  const exportFile = async (format: 'csv' | 'pdf' | 'xlsx') => {
    setAnchor(null)
    setLoading(true)
    try {
      await downloadReport({ ...params, report, format }, `${filename}.${format}`)
    } catch (error) {
      notify(errorMessage(error), 'error')
    } finally {
      setLoading(false)
    }
  }
  return <>
    <Button size="small" startIcon={<DownloadIcon/>} disabled={disabled || loading} onClick={(event) => setAnchor(event.currentTarget)}>
      {loading ? 'Generando…' : 'Exportar'}
    </Button>
    <Menu anchorEl={anchor} open={!!anchor} onClose={() => setAnchor(null)}>
      {(['csv', 'pdf', 'xlsx'] as const).map((format) => <MenuItem key={format} onClick={() => void exportFile(format)}>{format.toUpperCase()}</MenuItem>)}
    </Menu>
  </>
}

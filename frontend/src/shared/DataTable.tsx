import type { ReactNode } from 'react'
import { Paper, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow } from '@mui/material'
import { errorMessage } from '../api/http'
import type { PageData, Row } from './api'

export interface Col {
  header: string
  render: (row: Row) => ReactNode
  align?: 'right'
}

interface Props {
  columns: Col[]
  data?: PageData
  loading: boolean
  error?: unknown
  page: number
  size: number
  onPage: (p: number) => void
  onSize: (s: number) => void
  emptyText: string
  actions?: (row: Row) => ReactNode
}

export default function DataTable({ columns, data, loading, error, page, size, onPage, onSize, emptyText, actions }: Props) {
  const span = columns.length + (actions ? 1 : 0)
  return (
    <Paper elevation={0}>
      <TableContainer>
        <Table size="small">
          <TableHead>
            <TableRow>
              {columns.map((c) => <TableCell key={c.header} align={c.align} sx={{ fontWeight: 600 }}>{c.header}</TableCell>)}
              {actions && <TableCell align="right" sx={{ fontWeight: 600 }}>Acciones</TableCell>}
            </TableRow>
          </TableHead>
          <TableBody>
            {loading && <TableRow><TableCell colSpan={span}>Cargando…</TableCell></TableRow>}
            {!!error && <TableRow><TableCell colSpan={span} sx={{ color: 'error.main' }}>{errorMessage(error)}</TableCell></TableRow>}
            {!loading && !error && data?.rows.length === 0 && (
              <TableRow><TableCell colSpan={span} sx={{ py: 4, textAlign: 'center' }}>{emptyText}</TableCell></TableRow>
            )}
            {data?.rows.map((r) => (
              <TableRow key={r.id} hover>
                {columns.map((c) => <TableCell key={c.header} align={c.align}>{c.render(r)}</TableCell>)}
                {actions && <TableCell align="right">{actions(r)}</TableCell>}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      <TablePagination
        component="div" count={data?.total ?? 0} page={page} rowsPerPage={size} rowsPerPageOptions={[10, 25, 50]}
        onPageChange={(_e, p) => onPage(p)} onRowsPerPageChange={(e) => onSize(Number(e.target.value))}
        labelRowsPerPage="Filas por página" labelDisplayedRows={({ from, to, count }) => `${from}–${to} de ${count}`}
      />
    </Paper>
  )
}

import { useState, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Alert, Box, MenuItem, Paper, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from '@mui/material'
import { errorMessage } from '../../api/http'
import { PATHS, PERM } from '../../config/contract'
import { normalizePage, type Row } from '../../shared/api'
import { fecha } from '../../shared/format'
import { useAuth } from '../auth/AuthContext'
import { accountLabel, allPages } from './accountingApi'
import ExportReportButton from '../reporteria/ExportReportButton'
import { exactDecimal, getReport } from '../reporteria/reportingApi'

export default function ReportesTab() {
  const { can } = useAuth()
  const [periodId, setPeriodId] = useState(''); const [accountId, setAccountId] = useState('')
  const periods = useQuery({ queryKey: [PATHS.periodosContables, 'reports'], enabled: can(PERM.periodosLeer), queryFn: () => allPages(PATHS.periodosContables, { sort: 'fechaInicio', direction: 'desc' }) })
  const accounts = useQuery({ queryKey: [PATHS.cuentasContables, 'reports'], enabled: !!periodId && can(PERM.contabilidadLeer), queryFn: () => allPages(PATHS.cuentasContables, { sort: 'codigo', direction: 'asc' }) })
  const enabled = !!periodId && can(PERM.reportesContablesLeer)
  const params = { periodId }
  const trial = useQuery({ queryKey: [PATHS.balancePrueba, periodId], enabled, queryFn: async () => {
    const data = await getReport<Row>(PATHS.balancePrueba, { ...params, page: 0, size: 100, sort: 'codigoCuenta', direction: 'asc' })
    return { page: normalizePage(data), totals: data }
  } })
  const income = useQuery({ queryKey: [PATHS.estadoResultados, periodId], enabled, queryFn: () => getReport<Row>(PATHS.estadoResultados, params) })
  const balance = useQuery({ queryKey: [PATHS.balanceGeneral, periodId], enabled, queryFn: () => getReport<Row>(PATHS.balanceGeneral, params) })
  const cash = useQuery({ queryKey: [PATHS.flujoEfectivo, periodId], enabled, queryFn: () => getReport<Row>(PATHS.flujoEfectivo, params) })
  const summary = useQuery({ queryKey: [PATHS.resultadoFinanciero, periodId], enabled, queryFn: () => getReport<Row>(PATHS.resultadoFinanciero, params) })
  const ledger = useQuery({ queryKey: [PATHS.periodosContables, periodId, 'ledger', accountId], enabled: enabled && !!accountId, queryFn: () => getReport<Row>(`${PATHS.periodosContables}/${periodId}/accounts/${accountId}/ledger`, { page: 0, size: 100 }) })
  return <Box>
    <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { sm: '1fr 1fr' }, mb: 2 }}>{can(PERM.periodosLeer) ? <TextField select label="Periodo" value={periodId} onChange={(event) => { setPeriodId(event.target.value); setAccountId('') }} error={!!periods.error} helperText={periods.error ? errorMessage(periods.error) : ''}><MenuItem value="">Selecciona un periodo</MenuItem>{periods.data?.map((period) => <MenuItem key={period.id} value={period.id}>{period.fechaInicio} — {period.fechaFin} · {period.estado}</MenuItem>)}</TextField> : <TextField label="ID de periodo" value={periodId} onChange={(event) => setPeriodId(event.target.value)} slotProps={{ htmlInput: { inputMode: 'numeric' }}} helperText="No tienes permiso para consultar el catálogo de periodos."/>}{periodId && can(PERM.contabilidadLeer) && <TextField select label="Cuenta para mayor" value={accountId} onChange={(event) => setAccountId(event.target.value)}><MenuItem value="">Selecciona una cuenta</MenuItem>{accounts.data?.map((account) => <MenuItem key={account.id} value={account.id}>{accountLabel(account)}</MenuItem>)}</TextField>}</Box>
    {periodId && <>
      {trial.error && <Alert severity="error" sx={{ mb: 2 }}>{errorMessage(trial.error)}</Alert>}{income.error && <Alert severity="error" sx={{ mb: 2 }}>Estado de resultados: {errorMessage(income.error)}</Alert>}{balance.error && <Alert severity="error" sx={{ mb: 2 }}>Balance general: {errorMessage(balance.error)}</Alert>}{cash.error && <Alert severity="error" sx={{ mb: 2 }}>Flujo de efectivo: {errorMessage(cash.error)}</Alert>}
      <Box sx={{ display: 'grid', gridTemplateColumns: { lg: '1fr 1fr' }, gap: 2 }}>
        <ReportCard title="Balance de prueba" toolbar={periodId && <ExportReportButton report="finance/trial-balance" params={{ ...params, page: 0, size: 100, sort: 'codigoCuenta', direction: 'asc' }} filename="balance-prueba" disabled={trial.isLoading || !!trial.error}/>} data={trial.data?.page.rows} loading={trial.isLoading} error={trial.error} totals={trial.data ? `Debe ${exactDecimal(trial.data.totals.totalMovimientosDebe, 2)} · Haber ${exactDecimal(trial.data.totals.totalMovimientosHaber, 2)}` : undefined}>
          {trial.data?.page.rows.map((line: Row) => <TableRow key={line.cuentaId}><TableCell>{line.codigoCuenta}</TableCell><TableCell>{line.nombreCuenta}</TableCell><TableCell align="right">{exactDecimal(line.cierreDeudor, 2)}</TableCell><TableCell align="right">{exactDecimal(line.cierreAcreedor, 2)}</TableCell></TableRow>)}
        </ReportCard>
        <FinancialCard title="Estado de resultados" report={income.data} reportId="finance/income-statement" periodId={periodId}/><FinancialCard title="Balance general" report={balance.data} reportId="finance/balance-sheet" periodId={periodId}/><FinancialCard title="Flujo de efectivo" report={cash.data} reportId="finance/cash-flow" periodId={periodId}/>
        <Paper variant="outlined" sx={{ p: 2 }}><Box sx={{ display: 'flex', alignItems: 'center' }}><Typography variant="h6" sx={{ flexGrow: 1 }}>Resultado del periodo</Typography>{periodId && <ExportReportButton report="finance/result-summary" params={params} filename="resultado-periodo" disabled={summary.isLoading || !!summary.error}/>}</Box>{summary.isLoading ? <Typography>Cargando…</Typography> : summary.error ? <Alert severity="error">{errorMessage(summary.error)}</Alert> : <Typography sx={{ mt: 1 }}>{exactDecimal(summary.data?.resultado, 2)} · {summary.data?.inicio} a {summary.data?.fin}</Typography>}</Paper>
        {accountId && <Paper variant="outlined" sx={{ p: 2, gridColumn: { lg: '1 / -1' } }}><Box sx={{ display: 'flex', alignItems: 'center' }}><Typography variant="h6" sx={{ flexGrow: 1 }}>Mayor: {ledger.data?.codigoCuenta} — {ledger.data?.nombreCuenta}</Typography><ExportReportButton report="finance/trial-balance" params={{ ...params, page: 0, size: 100, sort: 'codigoCuenta', direction: 'asc' }} filename="mayor-contable" disabled={ledger.isLoading || !!ledger.error}/></Box>{ledger.error && <Alert severity="error">{errorMessage(ledger.error)}</Alert>}{ledger.data && <><Typography variant="body2" sx={{ my: 1 }}>Saldo inicial {exactDecimal(ledger.data.saldoInicial, 2)} · Debe {exactDecimal(ledger.data.movimientoDebe, 2)} · Haber {exactDecimal(ledger.data.movimientoHaber, 2)} · Saldo final {exactDecimal(ledger.data.saldoFinal, 2)}</Typography><Table size="small"><TableHead><TableRow><TableCell>Fecha</TableCell><TableCell>Asiento</TableCell><TableCell>Descripción</TableCell><TableCell align="right">Debe</TableCell><TableCell align="right">Haber</TableCell><TableCell align="right">Saldo</TableCell></TableRow></TableHead><TableBody>{ledger.data.content.map((line: Row, index: number) => <TableRow key={`${line.asientoId}-${line.numeroLinea}-${index}`}><TableCell>{fecha(line.fechaContable)}</TableCell><TableCell>{line.folio}</TableCell><TableCell>{line.descripcionPartida ?? line.descripcionAsiento}</TableCell><TableCell align="right">{exactDecimal(line.debe, 2)}</TableCell><TableCell align="right">{exactDecimal(line.haber, 2)}</TableCell><TableCell align="right">{exactDecimal(line.saldo, 2)}</TableCell></TableRow>)}</TableBody></Table></>}</Paper>}
      </Box>
    </>}
  </Box>
}

function ReportCard({ title, data, loading, error, totals, children, toolbar }: { title: string; data?: Row[]; loading: boolean; error: unknown; totals?: string; children?: ReactNode; toolbar?: ReactNode }) {
  return <Paper variant="outlined" sx={{ p: 2 }}><Box sx={{ display: 'flex', alignItems: 'center' }}><Typography variant="h6" sx={{ flexGrow: 1 }}>{title}</Typography>{toolbar}</Box>{loading && <Typography>Cargando…</Typography>}{Boolean(error) && <Alert severity="error" sx={{ mt: 1 }}>{errorMessage(error)}</Alert>}{!loading && !error && data?.length === 0 && <Typography color="text.secondary" sx={{ mt: 1 }}>Sin líneas en este periodo.</Typography>}{children && <Table size="small"><TableHead><TableRow><TableCell>Código</TableCell><TableCell>Cuenta</TableCell><TableCell align="right">Deudor</TableCell><TableCell align="right">Acreedor</TableCell></TableRow></TableHead><TableBody>{children}</TableBody></Table>}{totals && <Typography variant="body2" sx={{ mt: 1 }}>{totals}</Typography>}</Paper>
}
function FinancialCard({ title, report, reportId, periodId }: { title: string; report?: Row; reportId: string; periodId: string }) {
  if (!report) return <Paper variant="outlined" sx={{ p: 2 }}><Typography variant="h6">{title}</Typography><Typography color="text.secondary">Selecciona un periodo.</Typography></Paper>
  const available = report.estado !== 'NO_DISPONIBLE'
  return <Paper variant="outlined" sx={{ p: 2 }}><Box sx={{ display: 'flex', alignItems: 'center' }}><Typography variant="h6" sx={{ flexGrow: 1 }}>{title}</Typography><ExportReportButton report={reportId} params={{ periodId }} filename={reportId.split('/').at(-1) ?? 'reporte'} disabled={!available}/></Box><Typography variant="caption" color="text.secondary">{report.inicio} — {report.fin}</Typography>{!available && <Alert severity="warning" sx={{ mt: 1 }}>No disponible: {report.detalle ?? 'Faltan datos o clasificaciones.'}</Alert>}{available && <Table size="small"><TableBody>{(report.rubros ?? []).map((item: Row, index: number) => <TableRow key={`${item.nombre}-${index}`}><TableCell>{item.nombre}</TableCell><TableCell align="right">{exactDecimal(item.importe, 2)}</TableCell></TableRow>)}</TableBody></Table>}</Paper>
}

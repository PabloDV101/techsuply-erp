import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Paper, Tab, Tabs,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import { http, errorMessage } from '../../api/http'
import { PATHS } from '../../config/contract'
import { normalizePage } from '../../shared/api'
import { useNotify } from '../../shared/Notify'
import { useAuth } from '../auth/AuthContext'

interface Cuenta { id:number; codigo:string; nombre:string; tipo:string; naturaleza:string; nivel:number; activa:boolean; aceptaMovimientos:boolean }
interface Periodo { id:number; fechaInicio:string; fechaFin:string; estado:string }
interface Asiento { id:number; folio:string; fechaContable:string; descripcion:string; estado:string; totalDebe:number; totalHaber:number }
interface JournalLine { cuentaId:string; debe:string; haber:string }
interface ReportData {
  status:string
  lines:{accountCode:string;accountName:string;balance:number}[]
  totalsByType?:Record<string,number>
}

export default function ContabilidadPage() {
  const qc=useQueryClient()
  const notify=useNotify()
  const {can}=useAuth()
  const puedeAdministrar=can('CONTABILIDAD_ADMIN')
  const [tab,setTab]=useState(0)
  const [periodoId,setPeriodoId]=useState('')
  const [dialog,setDialog]=useState<'cuenta'|'periodo'|'asiento'|null>(null)
  const cuentas=useQuery({queryKey:[PATHS.cuentasContables],queryFn:async()=>{const r=await http.get(PATHS.cuentasContables);return r.data as Cuenta[]}})
  const periodos=useQuery({queryKey:[PATHS.periodosContables],queryFn:async()=>{const r=await http.get(PATHS.periodosContables);return r.data as Periodo[]}})
  const asientos=useQuery({queryKey:[PATHS.asientosContables,periodoId],queryFn:async()=>{const params:any={page:0,size:100};if(periodoId)params.periodoId=Number(periodoId);const r=await http.get(PATHS.asientosContables,{params});return normalizePage(r.data).rows as Asiento[]}})
  const balance=useQuery({queryKey:[PATHS.balancePrueba,periodoId],enabled:Boolean(periodoId),queryFn:async()=>{const r=await http.get(PATHS.balancePrueba,{params:{periodoId:Number(periodoId)}});return r.data as any}})
  const mayor=useQuery({queryKey:[PATHS.mayorContable,periodoId],enabled:Boolean(periodoId),queryFn:async()=>{const r=await http.get(PATHS.mayorContable,{params:{periodoId:Number(periodoId)}});return r.data as any}})
  const resultados=useQuery({queryKey:[PATHS.estadoResultados,periodoId],enabled:Boolean(periodoId),queryFn:async()=>{const r=await http.get(PATHS.estadoResultados,{params:{periodoId:Number(periodoId)}});return r.data as any}})
  const general=useQuery({queryKey:[PATHS.balanceGeneral,periodoId],enabled:Boolean(periodoId),queryFn:async()=>{const r=await http.get(PATHS.balanceGeneral,{params:{periodoId:Number(periodoId)}});return r.data as any}})
  const flujo=useQuery({queryKey:[PATHS.flujoEfectivo,periodoId],enabled:Boolean(periodoId),queryFn:async()=>{const r=await http.get(PATHS.flujoEfectivo,{params:{periodoId:Number(periodoId)}});return r.data as any}})

  const crear=useMutation({mutationFn:(body:{kind:'cuenta'|'periodo';data:any})=>body.kind==='cuenta'?http.post(PATHS.cuentasContables,body.data):http.post(PATHS.periodosContables,body.data),onSuccess:(_,v)=>{qc.invalidateQueries({queryKey:v.kind==='cuenta'?[PATHS.cuentasContables]:[PATHS.periodosContables]});notify(v.kind==='cuenta'?'Cuenta creada.':'Periodo creado.');setDialog(null)},onError:e=>notify(errorMessage(e),'error')})
  const crearAsiento=useMutation({
    mutationFn:(data:{fechaContable:string;descripcion:string;origenModulo:string;origenTipo:string;partidas:{cuentaId:number;debe:number;haber:number}[]})=>
      http.post(PATHS.asientosContables,data,{headers:{'Idempotency-Key':crypto.randomUUID()}}),
    onSuccess:()=>{qc.invalidateQueries({queryKey:[PATHS.asientosContables]});notify('Asiento creado como borrador.');setDialog(null)},
    onError:e=>notify(errorMessage(e),'error'),
  })
  const accionAsiento=useMutation({
    mutationFn:(body:{id:number;accion:'contabilizar'|'revertir'})=>http.post(`${PATHS.asientosContables}/${body.id}/${body.accion}`),
    onSuccess:()=>{qc.invalidateQueries({queryKey:[PATHS.asientosContables]});[PATHS.balancePrueba,PATHS.mayorContable,PATHS.estadoResultados,PATHS.balanceGeneral,PATHS.flujoEfectivo].forEach(path=>qc.invalidateQueries({queryKey:[path]}));notify('Asiento actualizado.')},
    onError:e=>notify(errorMessage(e),'error'),
  })

  return <Box>
    <Typography variant="h5" sx={{mb:2}}>Contabilidad</Typography>
    <Paper elevation={0} sx={{mb:2}}><Tabs value={tab} onChange={(_,v)=>setTab(v)} variant="scrollable"><Tab label="Plan de cuentas"/><Tab label="Periodos"/><Tab label="Asientos"/><Tab label="Reportes"/></Tabs></Paper>
    {tab===0 && <Paper elevation={0}>
      <Box sx={{p:2,display:'flex',justifyContent:'space-between',alignItems:'center'}}><Typography>Plan de cuentas</Typography>{puedeAdministrar&&<Button startIcon={<AddIcon />} onClick={()=>setDialog('cuenta')}>Nueva cuenta</Button>}</Box>
      <TableContainer><Table size="small"><TableHead><TableRow><TableCell>Código</TableCell><TableCell>Nombre</TableCell><TableCell>Tipo</TableCell><TableCell>Naturaleza</TableCell><TableCell>Estado</TableCell></TableRow></TableHead><TableBody>
        {cuentas.isLoading&&<TableRow><TableCell colSpan={5}>Cargando…</TableCell></TableRow>}
        {cuentas.isError&&<TableRow><TableCell colSpan={5} sx={{color:'error.main'}}>{errorMessage(cuentas.error)}</TableCell></TableRow>}
        {!cuentas.isLoading&&!cuentas.isError&&cuentas.data?.length===0&&<TableRow><TableCell colSpan={5} sx={{py:4,textAlign:'center'}}>Aún no hay cuentas contables.</TableCell></TableRow>}
        {cuentas.data?.map(c=><TableRow key={c.id}><TableCell>{c.codigo}</TableCell><TableCell>{c.nombre}</TableCell><TableCell>{c.tipo}</TableCell><TableCell>{c.naturaleza}</TableCell><TableCell>{c.activa?'Activa':'Inactiva'}</TableCell></TableRow>)}
      </TableBody></Table></TableContainer>
    </Paper>}
    {tab===1 && <Paper elevation={0}>
      <Box sx={{p:2,display:'flex',justifyContent:'space-between',alignItems:'center'}}><Typography>Periodos contables</Typography>{puedeAdministrar&&<Button startIcon={<AddIcon />} onClick={()=>setDialog('periodo')}>Nuevo periodo</Button>}</Box>
      <TableContainer><Table size="small"><TableHead><TableRow><TableCell>Inicio</TableCell><TableCell>Fin</TableCell><TableCell>Estado</TableCell></TableRow></TableHead><TableBody>
        {periodos.isLoading&&<TableRow><TableCell colSpan={3}>Cargando…</TableCell></TableRow>}
        {periodos.isError&&<TableRow><TableCell colSpan={3} sx={{color:'error.main'}}>{errorMessage(periodos.error)}</TableCell></TableRow>}
        {!periodos.isLoading&&!periodos.isError&&periodos.data?.length===0&&<TableRow><TableCell colSpan={3} sx={{py:4,textAlign:'center'}}>Aún no hay periodos contables.</TableCell></TableRow>}
        {periodos.data?.map(p=><TableRow key={p.id}><TableCell>{p.fechaInicio}</TableCell><TableCell>{p.fechaFin}</TableCell><TableCell>{p.estado}</TableCell></TableRow>)}
      </TableBody></Table></TableContainer>
    </Paper>}
    {tab===2 && <Paper elevation={0}>
      <Box sx={{p:2,display:'flex',justifyContent:'space-between',alignItems:'center',gap:2,flexWrap:'wrap'}}>
        <TextField select size="small" label="Periodo" value={periodoId} onChange={e=>setPeriodoId(e.target.value)} sx={{width:250}}>
          <MenuItem value="">Todos</MenuItem>{periodos.data?.map(p=><MenuItem key={p.id} value={p.id}>{p.fechaInicio} – {p.fechaFin}</MenuItem>)}
        </TextField>
        {puedeAdministrar&&<Button startIcon={<AddIcon />} variant="contained" onClick={()=>setDialog('asiento')}>Nuevo asiento</Button>}
      </Box>
      <TableContainer><Table size="small"><TableHead><TableRow><TableCell>Folio</TableCell><TableCell>Fecha</TableCell><TableCell>Descripción</TableCell><TableCell>Debe</TableCell><TableCell>Haber</TableCell><TableCell>Estado</TableCell><TableCell align="right">Acciones</TableCell></TableRow></TableHead><TableBody>
        {asientos.isLoading&&<TableRow><TableCell colSpan={7}>Cargando…</TableCell></TableRow>}
        {asientos.isError&&<TableRow><TableCell colSpan={7} sx={{color:'error.main'}}>{errorMessage(asientos.error)}</TableCell></TableRow>}
        {!asientos.isLoading&&!asientos.isError&&asientos.data?.length===0&&<TableRow><TableCell colSpan={7} sx={{py:4,textAlign:'center'}}>Aún no hay asientos contables.</TableCell></TableRow>}
        {asientos.data?.map(a=><TableRow key={a.id}><TableCell>{a.folio}</TableCell><TableCell>{a.fechaContable}</TableCell><TableCell>{a.descripcion}</TableCell><TableCell>{a.totalDebe}</TableCell><TableCell>{a.totalHaber}</TableCell><TableCell>{a.estado}</TableCell><TableCell align="right">
          {puedeAdministrar&&a.estado==='BORRADOR'&&<Button size="small" disabled={accionAsiento.isPending} onClick={()=>accionAsiento.mutate({id:a.id,accion:'contabilizar'})}>Contabilizar</Button>}
          {puedeAdministrar&&a.estado==='CONTABILIZADO'&&<Button size="small" color="secondary" disabled={accionAsiento.isPending} onClick={()=>accionAsiento.mutate({id:a.id,accion:'revertir'})}>Revertir</Button>}
        </TableCell></TableRow>)}
      </TableBody></Table></TableContainer>
    </Paper>}
    {tab===3 && <Paper elevation={0} sx={{p:2}}>
      <TextField select size="small" label="Periodo" value={periodoId} onChange={e=>setPeriodoId(e.target.value)} sx={{width:250,mb:2}}>
        {periodos.data?.map(p=><MenuItem key={p.id} value={p.id}>{p.fechaInicio} – {p.fechaFin}</MenuItem>)}
      </TextField>
      {!periodoId?<Typography color="text.secondary">Selecciona un periodo contable.</Typography>:<Box sx={{display:'grid',gap:2,gridTemplateColumns:{md:'1fr 1fr'}}}>
        {[
          ['Balance de prueba',balance],
          ['Resumen del mayor por cuenta',mayor],
          ['Estado de resultados',resultados],
          ['Balance general',general],
          ['Flujo de efectivo',flujo],
        ].map(([title,query]:any[])=> <ReportPanel key={title} title={title} query={query}/>)}
      </Box>}
    </Paper>}
    {dialog&&dialog!=='asiento'&&<ContabilidadDialog kind={dialog} saving={crear.isPending} onClose={()=>setDialog(null)} onSubmit={data=>crear.mutate({kind:dialog,data})}/>}
    {dialog==='asiento'&&<JournalDialog
      accounts={cuentas.data??[]}
      periods={periodos.data??[]}
      saving={crearAsiento.isPending}
      onClose={()=>setDialog(null)}
      onSubmit={data=>crearAsiento.mutate(data)}
    />}
  </Box>
}

function ReportPanel({title,query}:{title:string;query:{isLoading:boolean;isError:boolean;error:unknown;data?:ReportData}}) {
  const report=query.data
  return <Paper variant="outlined" sx={{p:2}}>
    <Typography sx={{fontWeight:700,mb:1}}>{title}</Typography>
    {query.isLoading&&<Typography color="text.secondary">Cargando reporte…</Typography>}
    {query.isError&&<Typography role="alert" color="error.main">{errorMessage(query.error)}</Typography>}
    {!query.isLoading&& !query.isError&&report?.status==='NO_DISPONIBLE'&&<Typography color="text.secondary">No disponible: faltan una definición y/o fuente transaccional aprobada.</Typography>}
    {!query.isLoading&& !query.isError&&report?.status!=='NO_DISPONIBLE'&&report?.lines?.length===0&&<Typography color="text.secondary">Sin movimientos contabilizados.</Typography>}
    {!query.isLoading&& !query.isError&&report?.status!=='NO_DISPONIBLE'&&report?.lines?.map((line,index)=><Typography key={`${line.accountCode}-${index}`} variant="body2">{line.accountCode} · {line.accountName}: {line.balance}</Typography>)}
    {!!report?.totalsByType&&Object.entries(report.totalsByType).map(([type,total])=><Typography key={type} variant="body2" sx={{fontWeight:700,mt:1}}>{type}: {total}</Typography>)}
  </Paper>
}

function JournalDialog({accounts,periods,saving,onClose,onSubmit}:{accounts:Cuenta[];periods:Periodo[];saving:boolean;onClose():void;onSubmit(data:{fechaContable:string;descripcion:string;origenModulo:string;origenTipo:string;partidas:{cuentaId:number;debe:number;haber:number}[]}):void}) {
  const [fechaContable,setFechaContable]=useState(periods.find(period=>period.estado==='ABIERTO')?.fechaInicio??'')
  const [descripcion,setDescripcion]=useState('')
  const [lines,setLines]=useState<JournalLine[]>([{cuentaId:'',debe:'',haber:''},{cuentaId:'',debe:'',haber:''}])
  const activeAccounts=accounts.filter(account=>account.activa&&account.aceptaMovimientos)
  const debit=lines.reduce((sum,line)=>sum+Number(line.debe||0),0)
  const credit=lines.reduce((sum,line)=>sum+Number(line.haber||0),0)
  const validAmount=(value:string)=>value===''||/^\d+(\.\d{1,2})?$/.test(value)
  const validLines=lines.length>=2&&lines.every(line=>
    Boolean(line.cuentaId)
    &&validAmount(line.debe)
    &&validAmount(line.haber)
    &&(Number(line.debe||0)>0)!==(Number(line.haber||0)>0))
  const balanced=Math.round(debit*100)===Math.round(credit*100)
  const valid=Boolean(fechaContable&&descripcion.trim()&&validLines&&balanced)
  const updateLine=(index:number,field:keyof JournalLine,value:string)=>{
    setLines(current=>current.map((line,lineIndex)=>lineIndex===index?{...line,[field]:value}:line))
  }
  const submit=()=>{
    onSubmit({
      fechaContable,
      descripcion:descripcion.trim(),
      origenModulo:'MANUAL',
      origenTipo:'MANUAL',
      partidas:lines.map(line=>({cuentaId:Number(line.cuentaId),debe:Number(line.debe||0),haber:Number(line.haber||0)})),
    })
  }
  return <Dialog open onClose={onClose} fullWidth maxWidth="md">
    <DialogTitle>Nuevo asiento contable</DialogTitle>
    <DialogContent sx={{display:'grid',gap:2,pt:'8px!important'}}>
      <TextField select label="Fecha dentro de un periodo abierto" type="date" value={fechaContable} onChange={event=>setFechaContable(event.target.value)} slotProps={{inputLabel:{shrink:true}}} required>
        {periods.filter(period=>period.estado==='ABIERTO').map(period=><MenuItem key={period.id} value={period.fechaInicio}>{period.fechaInicio} – {period.fechaFin}</MenuItem>)}
      </TextField>
      <TextField label="Descripción" value={descripcion} onChange={event=>setDescripcion(event.target.value)} required slotProps={{htmlInput:{maxLength:300}}}/>
      {lines.map((line,index)=><Box key={index} sx={{display:'grid',gap:1,alignItems:'center',gridTemplateColumns:{xs:'1fr',sm:'2fr 1fr 1fr auto'}}}>
        <TextField select label={`Cuenta · partida ${index+1}`} value={line.cuentaId} onChange={event=>updateLine(index,'cuentaId',event.target.value)} required>
          {activeAccounts.map(account=><MenuItem key={account.id} value={account.id}>{account.codigo} · {account.nombre}</MenuItem>)}
        </TextField>
        <TextField label="Debe" type="number" value={line.debe} onChange={event=>{updateLine(index,'debe',event.target.value);if(Number(event.target.value)>0)updateLine(index,'haber','')}} slotProps={{htmlInput:{min:0,step:'0.01'}}}/>
        <TextField label="Haber" type="number" value={line.haber} onChange={event=>{updateLine(index,'haber',event.target.value);if(Number(event.target.value)>0)updateLine(index,'debe','')}} slotProps={{htmlInput:{min:0,step:'0.01'}}}/>
        <Button color="error" disabled={lines.length<=2} onClick={()=>setLines(current=>current.filter((_,lineIndex)=>lineIndex!==index))}>Quitar</Button>
      </Box>)}
      <Button startIcon={<AddIcon />} onClick={()=>setLines(current=>[...current,{cuentaId:'',debe:'',haber:''}])}>Agregar partida</Button>
      <Typography color={balanced?'text.secondary':'error.main'}>
        Debe: {debit.toFixed(2)} · Haber: {credit.toFixed(2)}
      </Typography>
      {!periods.some(period=>period.estado==='ABIERTO')&&<Typography color="error.main">No hay periodos contables abiertos.</Typography>}
      {activeAccounts.length===0&&<Typography color="error.main">No hay cuentas activas que acepten movimientos.</Typography>}
    </DialogContent>
    <DialogActions><Button onClick={onClose}>Cancelar</Button><Button variant="contained" disabled={saving||!valid||activeAccounts.length===0} onClick={submit}>{saving?'Guardando…':'Guardar borrador'}</Button></DialogActions>
  </Dialog>
}

function ContabilidadDialog({kind,saving,onClose,onSubmit}:{kind:'cuenta'|'periodo';saving:boolean;onClose():void;onSubmit(data:any):void}) {
  const [data,setData]=useState<any>(kind==='cuenta'?{codigo:'',nombre:'',tipo:'ACTIVO',naturaleza:'DEUDORA',nivel:1,cuentaPadreId:null,aceptaMovimientos:true}:{fechaInicio:'',fechaFin:''})
  const set=(name:string,value:any)=>setData((d:any)=>({...d,[name]:value}))
  return <Dialog open onClose={onClose} fullWidth maxWidth="sm"><DialogTitle>{kind==='cuenta'?'Nueva cuenta contable':'Nuevo periodo contable'}</DialogTitle><DialogContent sx={{display:'grid',gap:2,pt:'8px!important'}}>
    {kind==='cuenta'?<>
      <TextField label="Código" value={data.codigo} onChange={e=>set('codigo',e.target.value)} required/>
      <TextField label="Nombre" value={data.nombre} onChange={e=>set('nombre',e.target.value)} required/>
      <TextField select label="Tipo" value={data.tipo} onChange={e=>set('tipo',e.target.value)}>{['ACTIVO','PASIVO','CAPITAL','INGRESO','COSTO','GASTO'].map(v=><MenuItem key={v} value={v}>{v}</MenuItem>)}</TextField>
      <TextField select label="Naturaleza" value={data.naturaleza} onChange={e=>set('naturaleza',e.target.value)}>{['DEUDORA','ACREEDORA'].map(v=><MenuItem key={v} value={v}>{v}</MenuItem>)}</TextField>
    </>:<>
      <TextField type="date" label="Fecha inicio" value={data.fechaInicio} onChange={e=>set('fechaInicio',e.target.value)} slotProps={{inputLabel:{shrink:true}}}/>
      <TextField type="date" label="Fecha fin" value={data.fechaFin} onChange={e=>set('fechaFin',e.target.value)} slotProps={{inputLabel:{shrink:true}}}/>
    </>}
  </DialogContent><DialogActions><Button onClick={onClose}>Cancelar</Button><Button variant="contained" disabled={saving} onClick={()=>onSubmit(data)}>{saving?'Guardando…':'Guardar'}</Button></DialogActions></Dialog>
}

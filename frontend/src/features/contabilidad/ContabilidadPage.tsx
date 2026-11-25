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

interface Cuenta { id:number; codigo:string; nombre:string; tipo:string; naturaleza:string; nivel:number; activa:boolean; aceptaMovimientos:boolean }
interface Periodo { id:number; fechaInicio:string; fechaFin:string; estado:string }
interface Asiento { id:number; folio:string; fechaContable:string; descripcion:string; estado:string; totalDebe:number; totalHaber:number }

export default function ContabilidadPage() {
  const qc=useQueryClient()
  const notify=useNotify()
  const [tab,setTab]=useState(0)
  const [periodoId,setPeriodoId]=useState('')
  const [dialog,setDialog]=useState<'cuenta'|'periodo'|null>(null)
  const cuentas=useQuery({queryKey:[PATHS.cuentasContables],queryFn:async()=>{const r=await http.get(PATHS.cuentasContables);return r.data as Cuenta[]}})
  const periodos=useQuery({queryKey:[PATHS.periodosContables],queryFn:async()=>{const r=await http.get(PATHS.periodosContables);return r.data as Periodo[]}})
  const asientos=useQuery({queryKey:[PATHS.asientosContables,periodoId],queryFn:async()=>{const params:any={page:0,size:100};if(periodoId)params.periodoId=Number(periodoId);const r=await http.get(PATHS.asientosContables,{params});return normalizePage(r.data).rows as Asiento[]}})
  const balance=useQuery({queryKey:[PATHS.balancePrueba,periodoId],enabled:Boolean(periodoId),queryFn:async()=>{const r=await http.get(PATHS.balancePrueba,{params:{periodoId:Number(periodoId)}});return r.data as any}})
  const mayor=useQuery({queryKey:[PATHS.mayorContable,periodoId],enabled:Boolean(periodoId),queryFn:async()=>{const r=await http.get(PATHS.mayorContable,{params:{periodoId:Number(periodoId)}});return r.data as any}})
  const resultados=useQuery({queryKey:[PATHS.estadoResultados,periodoId],enabled:Boolean(periodoId),queryFn:async()=>{const r=await http.get(PATHS.estadoResultados,{params:{periodoId:Number(periodoId)}});return r.data as any}})
  const general=useQuery({queryKey:[PATHS.balanceGeneral,periodoId],enabled:Boolean(periodoId),queryFn:async()=>{const r=await http.get(PATHS.balanceGeneral,{params:{periodoId:Number(periodoId)}});return r.data as any}})

  const crear=useMutation({mutationFn:(body:{kind:'cuenta'|'periodo';data:any})=>body.kind==='cuenta'?http.post(PATHS.cuentasContables,body.data):http.post(PATHS.periodosContables,body.data),onSuccess:(_,v)=>{qc.invalidateQueries({queryKey:v.kind==='cuenta'?[PATHS.cuentasContables]:[PATHS.periodosContables]});notify(v.kind==='cuenta'?'Cuenta creada.':'Periodo creado.');setDialog(null)},onError:e=>notify(errorMessage(e),'error')})
  const accionAsiento=useMutation({
    mutationFn:(body:{id:number;accion:'contabilizar'|'revertir'})=>http.post(`${PATHS.asientosContables}/${body.id}/${body.accion}`),
    onSuccess:()=>{qc.invalidateQueries({queryKey:[PATHS.asientosContables]});notify('Asiento actualizado.')},
    onError:e=>notify(errorMessage(e),'error'),
  })

  return <Box>
    <Typography variant="h5" sx={{mb:2}}>Contabilidad</Typography>
    <Paper elevation={0} sx={{mb:2}}><Tabs value={tab} onChange={(_,v)=>setTab(v)} variant="scrollable"><Tab label="Plan de cuentas"/><Tab label="Periodos"/><Tab label="Asientos"/><Tab label="Reportes"/></Tabs></Paper>
    {tab===0 && <Paper elevation={0}>
      <Box sx={{p:2,display:'flex',justifyContent:'space-between',alignItems:'center'}}><Typography>Plan de cuentas</Typography><Button startIcon={<AddIcon />} onClick={()=>setDialog('cuenta')}>Nueva cuenta</Button></Box>
      <TableContainer><Table size="small"><TableHead><TableRow><TableCell>Código</TableCell><TableCell>Nombre</TableCell><TableCell>Tipo</TableCell><TableCell>Naturaleza</TableCell><TableCell>Estado</TableCell></TableRow></TableHead><TableBody>
        {cuentas.isLoading&&<TableRow><TableCell colSpan={5}>Cargando…</TableCell></TableRow>}
        {cuentas.isError&&<TableRow><TableCell colSpan={5} sx={{color:'error.main'}}>{errorMessage(cuentas.error)}</TableCell></TableRow>}
        {!cuentas.isLoading&&!cuentas.isError&&cuentas.data?.length===0&&<TableRow><TableCell colSpan={5} sx={{py:4,textAlign:'center'}}>Aún no hay cuentas contables.</TableCell></TableRow>}
        {cuentas.data?.map(c=><TableRow key={c.id}><TableCell>{c.codigo}</TableCell><TableCell>{c.nombre}</TableCell><TableCell>{c.tipo}</TableCell><TableCell>{c.naturaleza}</TableCell><TableCell>{c.activa?'Activa':'Inactiva'}</TableCell></TableRow>)}
      </TableBody></Table></TableContainer>
    </Paper>}
    {tab===1 && <Paper elevation={0}>
      <Box sx={{p:2,display:'flex',justifyContent:'space-between',alignItems:'center'}}><Typography>Periodos contables</Typography><Button startIcon={<AddIcon />} onClick={()=>setDialog('periodo')}>Nuevo periodo</Button></Box>
      <TableContainer><Table size="small"><TableHead><TableRow><TableCell>Inicio</TableCell><TableCell>Fin</TableCell><TableCell>Estado</TableCell></TableRow></TableHead><TableBody>
        {periodos.isLoading&&<TableRow><TableCell colSpan={3}>Cargando…</TableCell></TableRow>}
        {periodos.isError&&<TableRow><TableCell colSpan={3} sx={{color:'error.main'}}>{errorMessage(periodos.error)}</TableCell></TableRow>}
        {!periodos.isLoading&&!periodos.isError&&periodos.data?.length===0&&<TableRow><TableCell colSpan={3} sx={{py:4,textAlign:'center'}}>Aún no hay periodos contables.</TableCell></TableRow>}
        {periodos.data?.map(p=><TableRow key={p.id}><TableCell>{p.fechaInicio}</TableCell><TableCell>{p.fechaFin}</TableCell><TableCell>{p.estado}</TableCell></TableRow>)}
      </TableBody></Table></TableContainer>
    </Paper>}
    {tab===2 && <Paper elevation={0}>
      <Box sx={{p:2}}><TextField select size="small" label="Periodo" value={periodoId} onChange={e=>setPeriodoId(e.target.value)} sx={{width:250}}>
        <MenuItem value="">Todos</MenuItem>{periodos.data?.map(p=><MenuItem key={p.id} value={p.id}>{p.fechaInicio} – {p.fechaFin}</MenuItem>)}
      </TextField></Box>
      <TableContainer><Table size="small"><TableHead><TableRow><TableCell>Folio</TableCell><TableCell>Fecha</TableCell><TableCell>Descripción</TableCell><TableCell>Debe</TableCell><TableCell>Haber</TableCell><TableCell>Estado</TableCell><TableCell align="right">Acciones</TableCell></TableRow></TableHead><TableBody>
        {asientos.isLoading&&<TableRow><TableCell colSpan={7}>Cargando…</TableCell></TableRow>}
        {asientos.isError&&<TableRow><TableCell colSpan={7} sx={{color:'error.main'}}>{errorMessage(asientos.error)}</TableCell></TableRow>}
        {!asientos.isLoading&&!asientos.isError&&asientos.data?.length===0&&<TableRow><TableCell colSpan={7} sx={{py:4,textAlign:'center'}}>Aún no hay asientos contables.</TableCell></TableRow>}
        {asientos.data?.map(a=><TableRow key={a.id}><TableCell>{a.folio}</TableCell><TableCell>{a.fechaContable}</TableCell><TableCell>{a.descripcion}</TableCell><TableCell>{a.totalDebe}</TableCell><TableCell>{a.totalHaber}</TableCell><TableCell>{a.estado}</TableCell><TableCell align="right">
          {a.estado==='BORRADOR'&&<Button size="small" disabled={accionAsiento.isPending} onClick={()=>accionAsiento.mutate({id:a.id,accion:'contabilizar'})}>Contabilizar</Button>}
          {a.estado==='CONTABILIZADO'&&<Button size="small" color="secondary" disabled={accionAsiento.isPending} onClick={()=>accionAsiento.mutate({id:a.id,accion:'revertir'})}>Revertir</Button>}
        </TableCell></TableRow>)}
      </TableBody></Table></TableContainer>
    </Paper>}
    {tab===3 && <Paper elevation={0} sx={{p:2}}>
      <TextField select size="small" label="Periodo" value={periodoId} onChange={e=>setPeriodoId(e.target.value)} sx={{width:250,mb:2}}>
        {periodos.data?.map(p=><MenuItem key={p.id} value={p.id}>{p.fechaInicio} – {p.fechaFin}</MenuItem>)}
      </TextField>
      {!periodoId?<Typography color="text.secondary">Selecciona un periodo contable.</Typography>:<Box sx={{display:'grid',gap:2,gridTemplateColumns:{md:'1fr 1fr'}}}>
        {[
          ['Balance de prueba',balance.data],
          ['Mayor general',mayor.data],
          ['Estado de resultados',resultados.data],
          ['Balance general',general.data],
        ].map(([title,report]:any[])=> <Paper key={title} variant="outlined" sx={{p:2}}><Typography sx={{fontWeight:700,mb:1}}>{title}</Typography>
          {report?.lines?.length===0?<Typography color="text.secondary">Sin movimientos contabilizados.</Typography>:report?.lines?.map((l:any,index:number)=><Typography key={index} variant="body2">{l.accountCode} · {l.accountName}: {l.balance}</Typography>)}
        </Paper>)}
      </Box>}
    </Paper>}
    {dialog&&<ContabilidadDialog kind={dialog} saving={crear.isPending} onClose={()=>setDialog(null)} onSubmit={data=>crear.mutate({kind:dialog,data})}/>}
  </Box>
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

import { useState } from 'react'
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, TextField } from '@mui/material'

interface Props {
  title: string
  text: string
  confirmLabel: string
  loading?: boolean
  onSubmit: (motivo: string) => void
  onClose: () => void
}

/** Pide un motivo obligatorio antes de ejecutar una acción sensible. */
export function MotivoDialog({ title, text, confirmLabel, loading, onSubmit, onClose }: Props) {
  const [motivo, setMotivo] = useState('')
  const [touched, setTouched] = useState(false)
  const invalid = touched && motivo.trim() === ''
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>{text}</DialogContentText>
        <TextField label="Motivo" fullWidth multiline minRows={2} autoFocus required value={motivo}
          error={invalid} helperText={invalid ? 'Indica el motivo' : undefined}
          onChange={(e) => setMotivo(e.target.value)} onBlur={() => setTouched(true)} />
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancelar</Button>
        <Button variant="contained" disabled={loading}
          onClick={() => { setTouched(true); if (motivo.trim()) onSubmit(motivo.trim()) }}>{confirmLabel}</Button>
      </DialogActions>
    </Dialog>
  )
}

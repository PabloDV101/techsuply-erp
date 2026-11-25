import { createTheme } from '@mui/material'

export const theme = createTheme({
  palette: {
    primary: { main: '#0f5c6e' },
    secondary: { main: '#c2571a' },
    background: { default: '#f4f6f8' },
  },
  shape: { borderRadius: 6 },
  typography: {
    fontFamily: '"Segoe UI", system-ui, -apple-system, Roboto, sans-serif',
    h5: { fontWeight: 600 },
    button: { textTransform: 'none', fontWeight: 600 },
  },
})

import { expect, test } from '@playwright/test'
import { requireBackend, signIn } from './helpers'

test('loads the executive dashboard and real period reports for an authorized user', async ({ page }) => {
  test.skip(!process.env.E2E_USERNAME || !process.env.E2E_PASSWORD,
    'Set E2E_USERNAME and E2E_PASSWORD to a test account with reporting permissions')
  await requireBackend(page)
  await signIn(page, process.env.E2E_USERNAME!, process.env.E2E_PASSWORD!)

  await expect(page.getByText('Resumen financiero')).toBeVisible()
  await expect(page.getByText('Indicadores pendientes de fuente o definición')).toBeVisible()
  await expect(page.getByText('Rentabilidad y margen')).toBeVisible()
})

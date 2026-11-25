import { expect, test } from '@playwright/test'
import { requireBackend } from './helpers'

test('rejects invalid credentials without opening a protected page', async ({ page }) => {
  await requireBackend(page)
  await page.goto('/login')
  await page.getByLabel('Usuario o correo').fill(`e2e_${Date.now()}`)
  await page.getByLabel('Contraseña').fill('incorrect-password')
  await page.getByRole('button', { name: 'Entrar' }).click()

  await expect(page.getByRole('alert')).toBeVisible()
  await expect(page).toHaveURL(/\/login$/)
})

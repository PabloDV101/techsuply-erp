import { expect, test } from '@playwright/test'
import { requireBackend, signIn } from './helpers'

test('registers, signs in, and reloads a persisted account session', async ({ page }) => {
  test.skip(process.env.E2E_ALLOW_USER_CREATION !== 'true',
    'Set E2E_ALLOW_USER_CREATION=true only when using a disposable test database')
  await requireBackend(page)

  const username = `e2e_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
  const email = `${username}@example.test`
  const password = `Valid-test-${Math.random().toString(36).slice(2, 10)}`
  await page.goto('/registro')
  await page.getByLabel('Usuario').fill(username)
  await page.getByLabel('Correo electrónico').fill(email)
  await page.getByLabel('Contraseña').fill(password)
  await page.getByLabel('Confirmar contraseña').fill(password)
  await page.getByRole('button', { name: 'Crear cuenta' }).click()
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('alert')).toContainText('Cuenta creada')

  await signIn(page, username, password)
  await expect(page.getByText(`Hola, ${username}`)).toBeVisible()

  const currentUser = page.waitForResponse((response) =>
    response.url().endsWith('/api/v1/auth/me') && response.request().method() === 'GET')
  await page.reload()
  await expect(currentUser).resolves.toMatchObject({ status: 200 })
  await expect(page.getByText(`Hola, ${username}`)).toBeVisible()
})

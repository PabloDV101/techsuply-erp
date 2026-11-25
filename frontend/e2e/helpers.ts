import { expect, test, type Page } from '@playwright/test'

export const apiUrl = process.env.E2E_API_URL ?? 'http://localhost:8081'

export async function requireBackend(page: Page) {
  const health = await page.request.get(`${apiUrl}/actuator/health`).catch(() => undefined)
  test.skip(!health?.ok(), `Backend health check is unavailable at ${apiUrl}/actuator/health`)
  expect(health?.ok()).toBe(true)
}

export async function signIn(page: Page, username: string, password: string) {
  await page.goto('/login')
  await page.getByLabel('Usuario o correo').fill(username)
  await page.getByLabel('Contraseña').fill(password)
  await page.getByRole('button', { name: 'Entrar' }).click()
}

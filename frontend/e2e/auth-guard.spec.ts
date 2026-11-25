import { expect, test } from '@playwright/test'

test('redirects unauthenticated access to the accounting screen to login', async ({ page }) => {
  await page.goto('/contabilidad')
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('heading', { name: 'Iniciar sesión' })).toBeVisible()
})

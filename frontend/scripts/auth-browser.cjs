/* Browser journey against the disposable backend started by auth-integration.py. */
const assert = require('node:assert/strict')
const { chromium } = require('playwright')

async function main() {
  const base = process.env.AUTH_TEST_BASE_URL
  const username = process.env.AUTH_TEST_USERNAME
  const password = process.env.AUTH_TEST_PASSWORD
  if (!base || !username || !password) throw new Error('Falta la configuración temporal de prueba')
  const browser = await chromium.launch({ headless: true })
  const context = await browser.newContext()
  const page = await context.newPage()
  page.setDefaultTimeout(15000)
  const login = async (name, pass) => {
    await page.getByLabel('Usuario o correo').fill(name)
    await page.getByLabel('Contraseña').fill(pass)
    await page.getByRole('button', { name: 'Entrar' }).click()
  }
  const expectLogin = async (target) => {
    await target.waitForURL('**/login')
    await target.getByRole('button', { name: 'Entrar' }).waitFor()
  }
  try {
    await page.goto(base + '/')
    await expectLogin(page)
    assert.equal(await page.getByText('Crear cuenta nueva').count(), 0)
    await page.goto(base + '/registro')
    await expectLogin(page)
    const badLogin = page.waitForResponse(r => r.url().endsWith('/api/v1/auth/login') && r.request().method() === 'POST')
    await login(username, 'incorrecta')
    assert.equal((await badLogin).status(), 401)
    await page.getByText('Credenciales inválidas o cuenta no disponible').waitFor()
    assert.equal(await page.evaluate(() => localStorage.getItem('erp_token')), null)
    await page.route('**/api/v1/auth/login', route => route.abort())
    await login(username, password)
    await page.getByText('No hay conexión con el servidor. Revisa que el backend esté activo.').waitFor()
    assert.equal(await page.evaluate(() => localStorage.getItem('erp_token')), null)
    await page.unroute('**/api/v1/auth/login')
    await login(username, password)
    await page.getByText('Hola, ' + username).waitFor()
    assert.equal(await page.evaluate(() => !!localStorage.getItem('erp_token')), true)
    assert.equal(await page.evaluate(() => localStorage.getItem('erp_user')), null)
    await page.reload()
    await page.getByText('Hola, ' + username).waitFor()

    await page.route('**/api/v1/auth/me', route => route.abort())
    await page.reload()
    await page.getByText(/No se pudo comprobar la sesión/).waitFor()
    assert.equal(await page.evaluate(() => !!localStorage.getItem('erp_token')), true)
    await page.unroute('**/api/v1/auth/me')
    await page.getByRole('button', { name: 'Reintentar' }).click()
    await page.getByText('Hola, ' + username).waitFor()

    const second = await context.newPage()
    await second.goto(base + '/')
    await second.getByText('Hola, ' + username).waitFor()
    const token = await page.evaluate(() => localStorage.getItem('erp_token'))
    const revokedResponse = page.waitForResponse(r => r.url().endsWith('/api/v1/auth/logout') && r.request().method() === 'POST')
    await page.getByRole('button', { name: 'Menú de usuario' }).click()
    await page.getByRole('menuitem', { name: 'Cerrar sesión' }).click()
    assert.equal((await revokedResponse).status(), 204)
    await expectLogin(page)
    await expectLogin(second)
    const afterLogout = await page.evaluate(async t => (await fetch('/api/v1/auth/me', { headers: { Authorization: 'Bearer ' + t } })).status, token)
    assert.equal(afterLogout, 401)
    await second.close()

    await login('lector_prueba', password)
    await page.getByText('Hola, lector_prueba').waitFor()
    const denied = await page.evaluate(async () => {
      const { http } = await import('/src/api/http.ts')
      try { await http.get('/api/v1/categories'); return 200 } catch (error) { return error.response?.status }
    })
    assert.equal(denied, 403)
    assert.equal(await page.evaluate(() => !!localStorage.getItem('erp_token')), true)
    const externalRevoke = await page.evaluate(async () => (await fetch('/api/v1/auth/logout', { method: 'POST', headers: { Authorization: 'Bearer ' + localStorage.getItem('erp_token') } })).status)
    assert.equal(externalRevoke, 204)
    await page.getByRole('link', { name: 'Proveedores' }).click()
    await expectLogin(page)
    assert.equal(await page.evaluate(() => localStorage.getItem('erp_token')), null)

    await login(username, password)
    await page.getByText('Hola, ' + username).waitFor()
    const unrevoked = await page.evaluate(() => localStorage.getItem('erp_token'))
    await page.route('**/api/v1/auth/logout', route => route.abort())
    await page.getByRole('button', { name: 'Menú de usuario' }).click()
    await page.getByRole('menuitem', { name: 'Cerrar sesión' }).click()
    await expectLogin(page)
    await page.getByText(/no se pudo confirmar su revocación/).waitFor()
    assert.equal(await page.evaluate(() => localStorage.getItem('erp_token')), null)
    await page.unroute('**/api/v1/auth/logout')
    assert.equal(await page.evaluate(async t => (await fetch('/api/v1/auth/me', { headers: { Authorization: 'Bearer ' + t } })).status, unrevoked), 200)

    await login(username, password)
    await page.getByText('Hola, ' + username).waitFor()
    await page.evaluate(() => localStorage.setItem('erp_expires', '2000-01-01T00:00:00Z'))
    await page.reload()
    await expectLogin(page)
    assert.equal(await page.evaluate(() => localStorage.getItem('erp_token')), null)
    console.log('OK: login, credenciales inválidas, restauración, fallo de red/reintento, logout y revocación, pestañas, 403, 401, expiración y fallo de logout')
  } finally {
    await context.close()
    await browser.close()
  }
}
main().catch(error => {
  const message = String(error.message)
  const password = process.env.AUTH_TEST_PASSWORD
  console.error(password ? message.split(password).join('[REDACTADO]') : message)
  process.exitCode = 1
})

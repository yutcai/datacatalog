import { expect, test as base } from '@playwright/test'
import { RegisteredUser } from './registered-user'

const test = base.extend<{ registeredUser: RegisteredUser }>({
    registeredUser: async ({ page }, use) => {
        const username = `e2e-${Date.now()}-${Math.floor(Math.random() * 1e6)}`
        const password = 'pw-12345'
        const registeredUser = new RegisteredUser(page, username, password)
        await registeredUser.signUp()
        await expect(page.getByTestId('current-user')).toBeVisible()
        await registeredUser.signOut()
        await expect(page.getByRole('button', { name: 'Sign In'})).toBeVisible()
        await use(registeredUser)
    }
});

test.describe('logged-out auth flows', () => {
    test.use({ storageState: { cookies: [], origins: [] } })

    test('confirm logout status', async ({ page }) => {
        // go to /login page
        await page.goto('/login')

        // assert login form
        await expect(page.getByLabel('Username')).toBeVisible()
        await expect(page.getByLabel('Password')).toBeVisible()
        await expect(page.getByRole('button', { name: 'Register' })).toBeVisible()

        // assert login link
        await expect(page.getByRole('link', { name: 'Log In'})).toBeVisible()

        // assert no user login
        await expect(page.getByTestId('current-user')).not.toBeVisible()
    })

    test('register with a duplicated username', async ({ page, registeredUser }) => {
        // click register button
        await registeredUser.signUp()

        // check the alert
        const alertLocator = page.getByRole('alert')
        await expect(alertLocator).toBeVisible()
        await expect(alertLocator).toHaveText('That username is taken')

        // check the current-user is not there
        await expect(page.getByTestId('current-user')).not.toBeVisible()
    })

    test("login with wrong password", async ( { page, registeredUser } ) => {
        // login with wrong password
        await page.getByLabel('Username').fill(registeredUser.username)
        await page.getByLabel('Password').fill('wrong-pw')
        await page.getByRole('button', { name: 'Sign In' }).click()

        // assert the alert
        const alertLocator = page.getByRole('alert')
        await expect(alertLocator).toBeVisible()
        await expect(alertLocator).toHaveText('Invalid username or password')

        // check the current-user is not there
        await expect(page.getByTestId('current-user')).not.toBeVisible()
    })
})




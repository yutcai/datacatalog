import type { Page, Locator } from '@playwright/test'

export class RegisteredUser {
    private readonly registerButton: Locator
    private readonly usernameField: Locator
    private readonly passwordField: Locator
    private readonly signUpButton: Locator
    private readonly signOutButton: Locator

    constructor(public readonly page: Page, public readonly username: string, public readonly password: string) {
        this.registerButton = this.page.getByRole('button', { name: 'Register' })
        this.usernameField = this.page.getByLabel('Username')
        this.passwordField = this.page.getByLabel('Password')
        this.signUpButton = this.page.getByRole('button', { name: 'Sign up' })
        this.signOutButton = this.page.getByRole('button', { name: 'Log out'})
    }

    async signUp() {
        await this.page.goto('/login')
        await this.registerButton.click()
        await this.usernameField.fill(this.username)
        await this.passwordField.fill(this.password)
        await this.signUpButton.click()
    }

    async signOut() {
        await this.signOutButton.click()
    }
}
import tseslint from 'typescript-eslint'

// The one rule this setup exists for is @typescript-eslint/no-floating-promises:
// almost every Playwright action and assertion returns a promise, and a missing
// `await` doesn't fail — the check silently never runs. That rule needs type
// information, hence the project service wiring below.
export default tseslint.config(
  { ignores: ['node_modules/**', 'playwright-report/**', 'test-results/**'] },
  ...tseslint.configs.recommended,
  {
    files: ['**/*.ts'],
    languageOptions: {
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    rules: {
      '@typescript-eslint/no-floating-promises': 'error',
    },
  },
)

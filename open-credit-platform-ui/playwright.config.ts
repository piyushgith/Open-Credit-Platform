import { defineConfig } from '@playwright/test'

// Runs against an already-running stack (backend on :9000 with the dev profile, PostgreSQL, and
// `npm run dev` on :5173). Uses the locally installed Chrome, so no browser download is needed.
export default defineConfig({
  testDir: 'e2e',
  timeout: 90_000,
  reporter: 'list',
  use: { baseURL: process.env.UI_URL ?? 'http://localhost:5173', channel: 'chrome', headless: true },
})

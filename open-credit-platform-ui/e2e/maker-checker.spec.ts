import { expect, test, type Browser, type Page } from '@playwright/test'

async function signIn(browser: Browser, username: string): Promise<Page> {
  const page = await (await browser.newContext()).newPage()
  await page.goto('/#/console')
  await page.getByRole('button', { name: username, exact: true }).click()
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('navigation', { name: 'Console' })).toBeVisible()
  return page
}

test('a maker prepares a loan and a different checker approves it into OFFERED', async ({ browser }) => {
  // Maker: apply, add a statement, analyze, score, decide, open the case and recommend approval.
  const maker = await signIn(browser, 'alice.maker')
  await maker.getByRole('link', { name: 'New application' }).click()
  await maker.getByLabel('Amount').fill('1500000')
  await maker.getByRole('button', { name: 'Create application' }).click()
  await expect(maker).toHaveURL(/#\/console\/loan\/LN-\d+/)
  const reference = maker.url().split('/').pop()!

  await maker.getByRole('button', { name: 'Add financial statement' }).click()
  await maker.getByRole('button', { name: 'Submit statement' }).click()
  await maker.getByRole('button', { name: 'Analyze' }).click()
  await maker.getByRole('button', { name: 'Compute score' }).click()
  await maker.getByRole('button', { name: 'Run decision' }).click()
  await maker.getByRole('button', { name: 'Open approval case' }).click()
  await expect(maker.getByText('PENDING MAKER')).toBeVisible()
  await maker.getByRole('button', { name: 'Record decision' }).click()
  await expect(maker.getByText('PENDING CHECKER')).toBeVisible()

  // The maker cannot take the checker's actions.
  await expect(maker.getByRole('button', { name: 'Sanction selected offer' })).toHaveCount(0)

  // Checker: sees the case waiting, approves it, and the application moves to OFFERED.
  const checker = await signIn(browser, 'bob.checker')
  await checker.goto(`/#/console/loan/${reference}`)
  await expect(checker.getByText('Waiting for a checker')).toHaveCount(0)
  await checker.getByRole('button', { name: 'Record decision' }).click()
  await expect(checker.getByText('Checker decision: done')).toBeVisible()
  await expect(checker.locator('.detail .box .badge', { hasText: 'OFFERED' })).toBeVisible()
})

import { test, expect } from '@playwright/test';

test('homepage has APIX AI Gateway title', async ({ page }) => {
  await page.goto('/dashboard');
  await expect(page.getByRole('link', { name: 'APIX AI Gateway' })).toBeVisible();
});

test('can navigate to configuration page', async ({ page }) => {
  await page.goto('/dashboard');
  await page.click('text=Configuration');
  await expect(page).toHaveURL(/.*\/config/);
});

test('can navigate to metrics page', async ({ page }) => {
  await page.goto('/dashboard');
  await page.click('text=System Metrics');
  await expect(page).toHaveURL(/.*\/metrics/);
});

test('can navigate to AI models page', async ({ page }) => {
  await page.goto('/dashboard');
  await page.click('text=AI Management');
  await page.click('text=Models');
  await expect(page).toHaveURL(/.*\/ai\/models/);
});

test('can navigate to AI routing page', async ({ page }) => {
  await page.goto('/dashboard');
  await page.click('text=AI Management');
  await page.click('text=Routing Rules');
  await expect(page).toHaveURL(/.*\/ai\/routing/);
});

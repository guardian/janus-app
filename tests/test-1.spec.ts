import { test, expect } from '@playwright/test';
import dotenv from 'dotenv';
import path from 'path';

test('test', async ({ page }) => {
  dotenv.config({ path: path.resolve(__dirname, '.env') });
  await page.goto(`https://${process.env.DOMAIN}/`);
  await page.getByRole('textbox', { name: 'Enter your email' }).fill(process.env.USERNAME);
  await page.getByRole('button', { name: 'Next' }).click();
  await page.getByRole('textbox', { name: 'Enter your password' }).click();
  await page.getByRole('textbox', { name: 'Enter your password' }).fill(process.env.PASSWORD);
  await page.getByRole('button', { name: 'Next' }).click();
  await page.getByRole('link', { name: 'Get a verification code from' }).click();
  await page.getByRole('textbox', { name: 'Enter code' }).fill('TODO');
  await page.getByRole('button', { name: 'Next' }).click();
  await page.getByRole('button', { name: 'Register a new passkey' }).click();
});

import { chromium } from "playwright";
import { mkdir } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "../../..");
const out = join(root, "deck/screenshots/app.png");
await mkdir(dirname(out), { recursive: true });

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
await page.goto("http://127.0.0.1:5173/", { waitUntil: "networkidle" });

// Load KYC fraud example and analyze
await page.getByRole("button", { name: /SBI KYC/i }).click();
await page.getByRole("button", { name: /^Analyze/i }).click();
await page.waitForSelector('.verdict[data-label="FRAUD"]', { timeout: 120_000 });
await page.waitForTimeout(800);
await page.screenshot({ path: out, fullPage: false });
await browser.close();
console.log("wrote", out);

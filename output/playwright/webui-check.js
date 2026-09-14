const { chromium } = require('playwright');

const URL = 'http://192.168.63.237:9978/';
const TOKEN = 'fYHpqWp9pRdUCPTGsoLmKIT5K6VH9h8I';

async function check(width, height, tag) {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width, height } });
  const errors = [];
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  page.on('pageerror', (e) => errors.push(String(e)));
  await page.goto(URL);
  await page.evaluate((t) => localStorage.setItem('miruplay_web_token', t), TOKEN);
  await page.goto(URL);
  await page.waitForSelector('.app-shell', { timeout: 15000 });
  await page.waitForTimeout(1500);
  const metrics = await page.evaluate(() => {
    const vw = document.documentElement.clientWidth;
    const shell = document.querySelector('.app-shell').getBoundingClientRect();
    const pane = document.querySelector('.main-pane').getBoundingClientRect();
    const burgerVisible = !!document.querySelector('.nav-burger') && getComputedStyle(document.querySelector('.nav-burger')).display !== 'none';
    return { vw, shellW: shell.width, paneW: pane.width, paneRight: pane.right, burgerVisible };
  });
  console.log(`[${tag}] vw=${metrics.vw} shellW=${metrics.shellW} paneW=${metrics.paneW} paneRight=${metrics.paneRight} burger=${metrics.burgerVisible} errors=${errors.length}`);
  if (errors.length) console.log('console errors:', errors.slice(0, 3).join(' | '));
  await page.screenshot({ path: `webui-${tag}-${width}.png`, fullPage: false });
  await browser.close();
  return { tag, metrics, errors };
}

(async () => {
  const results = [];
  results.push(await check(390, 844, 'mobile'));
  results.push(await check(768, 1024, 'tablet'));
  results.push(await check(1280, 800, 'desktop'));
  const m = results[0].metrics;
  if (Math.abs(m.paneW - m.vw) > 5) { console.log('FAIL: mobile pane does not fill viewport'); process.exit(1); }
  const d = results[2].metrics;
  if (d.paneW <= d.vw * 0.6) { console.log('FAIL: desktop pane too narrow'); process.exit(1); }
  console.log('PASS');
})().catch((e) => { console.error(e); process.exit(1); });

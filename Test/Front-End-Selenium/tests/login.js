const { By, until } = require("selenium-webdriver");
const assert = require("assert");

const TIMEOUT = 10000; // 10 seconds

// staff account that already exists in the users table
const ADMIN_USER = process.env.TRIVIA_ADMIN_USER || "admin";
const ADMIN_PASS = process.env.TRIVIA_ADMIN_PASS || "changeme";

/**
 * Build a URL for another page, relative to the page the driver is on.
 */
async function pageUrl(driver, page) {
  return new URL(page, await driver.getCurrentUrl()).href;
}

async function waitVisible(driver, css) {
  const el = await driver.wait(until.elementLocated(By.css(css)), TIMEOUT);
  await driver.wait(until.elementIsVisible(el), TIMEOUT);
  return el;
}

async function setText(driver, css, text) {
  const el = await waitVisible(driver, css);
  await el.clear();
  if (text) await el.sendKeys(text);
}

async function click(driver, css) {
  const el = await waitVisible(driver, css);
  await el.click();
}

async function waitText(driver, css, text) {
  const el = await waitVisible(driver, css);
  await driver.wait(
    until.elementTextIs(el, text),
    TIMEOUT,
    `Expected "${text}" in ${css}`,
  );
}

/**
 * All tests share one browser window, so drop the session cookie to
 * make sure nothing is left logged in from an earlier test.
 */
async function clearSession(driver) {
  await driver.manage().deleteAllCookies();
}

async function login(driver, user, pass) {
  await clearSession(driver);
  await driver.get(await pageUrl(driver, "login.html"));
  await setText(driver, "#name", user);
  await setText(driver, "#password", pass);
  await click(driver, "#submitBtn");
  await driver.wait(
    async () => !(await driver.getCurrentUrl()).includes("login.html"),
    TIMEOUT,
    "Login did not leave login.html",
  );
}

async function openAdminPage(driver) {
  await driver.get(await pageUrl(driver, "adminPage.html"));
  await waitVisible(driver, "#panels");
}

async function addCategory(driver, name) {
  await setText(driver, "#catName", name);
  await click(driver, "#addCategoryBtn");
  await waitText(driver, "#message", `Added category "${name}".`);
}

async function deleteCategory(driver, name) {
  const del = await driver.findElement(
    By.xpath(
      `//li[contains(@class,'cat-row')][.//span[text()='${name}']]//button[contains(@class,'danger')]`,
    ),
  );
  await del.click();
  await driver.wait(until.alertIsPresent(), TIMEOUT);
  await driver.switchTo().alert().accept();
  await waitText(driver, "#message", `Deleted category "${name}".`);
}

async function testLoginWrongPassword(driver) {
  await setText(driver, "#name", ADMIN_USER);
  await setText(driver, "#password", "definitely-wrong");
  await click(driver, "#submitBtn");

  await waitText(driver, "#error", "Wrong name or password.");
  assert(
    (await driver.getCurrentUrl()).includes("login.html"),
    "Should stay on login.html",
  );
}

async function testLoginSuccess(driver) {
  try {
    await login(driver, ADMIN_USER, ADMIN_PASS);

    await driver.get(await pageUrl(driver, "index.html"));
    await waitText(driver, "#who", ADMIN_USER);

    const logout = await driver.findElement(By.id("logoutBtn"));
    assert(
      await logout.isDisplayed(),
      "Logout button should be visible after login",
    );
  } finally {
    await clearSession(driver); // never leave this window logged in
  }
}

module.exports = [
  {
    name: "Login: Wrong Password Shows Error",
    path: "login.html",
    run: testLoginWrongPassword,
  },
  {
    name: "Login: Valid Login Shows Username",
    path: "login.html",
    run: testLoginSuccess,
  },
];
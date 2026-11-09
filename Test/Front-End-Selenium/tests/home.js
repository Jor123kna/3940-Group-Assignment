const { By, until } = require("selenium-webdriver");
const assert = require("assert");

async function test(driver) {
  // Solo button should lead to triviaSolo.html
  const soloBtn = driver.findElement(By.css("body > main > nav > a.blue"));
  const soloHref = await soloBtn.getDomAttribute("href");
  assert.strictEqual(
    soloHref,
    "triviaSolo.html",
    "Solo button does not lead to triviaSolo.html",
  );

  // Multiplayer button should lead to triviaUser.html
  const multiBtn = driver.findElement(By.css("body > main > nav > a.green"));
  const multiHref = await multiBtn.getDomAttribute("href");
  assert.strictEqual(
    multiHref,
    "triviaUser.html",
    "Multiplayer button does not lead to triviaUser.html",
  );

  // Login button should lead to login.html
  const loginBtn = driver.findElement(By.css("body > header > div > a"));
  const loginHref = await loginBtn.getDomAttribute("href");
  assert.strictEqual(
    loginHref,
    "login.html",
    "Login button does not lead to login.html",
  );
}

module.exports = [
  {
    name: "Landing Page UI",
    path: "",
    run: test,
  },
];

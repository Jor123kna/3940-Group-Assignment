const { Builder, By, until } = require("selenium-webdriver");
const assert = require("assert");

const TIMEOUT = 10000; // 10 seconds
const LOBBY_TIMEOUT = 20000;

// staff account that already exists in the users table
const ADMIN_USER = process.env.TRIVIA_ADMIN_USER || "admin";
const ADMIN_PASS = process.env.TRIVIA_ADMIN_PASS || "changeme";

async function pageUrl(driver, page) {
  return new URL(page, await driver.getCurrentUrl()).href;
}

async function waitVisible(driver, css, timeout = TIMEOUT) {
  const el = await driver.wait(until.elementLocated(By.css(css)), timeout);
  await driver.wait(until.elementIsVisible(el), timeout);
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

async function acceptAlertIfPresent(driver) {
  try {
    await driver.wait(until.alertIsPresent(), 2000);
    await driver.switchTo().alert().accept();
  } catch (e) {}
}

async function waitConnected(driver) {
  await driver.wait(
    until.elementLocated(By.css("#connection[data-state='on']")),
    LOBBY_TIMEOUT,
    "Lobby never reached the Connected state",
  );
}

// All tests share one browser window, so clear any login left behind.
async function clearSession(driver) {
  await driver.manage().deleteAllCookies();
}

// Logs in with the same request the login page sends.
async function login(driver, user, pass) {
  await clearSession(driver);
  await driver.get(await pageUrl(driver, "index.html"));

  const result = await driver.executeAsyncScript(
    function (user, pass, done) {
      const xhr = new XMLHttpRequest();
      xhr.open("POST", "login", true);
      xhr.setRequestHeader("X-Requested-With", "XMLHttpRequest");
      xhr.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");
      xhr.onload = function () {
        done({ status: xhr.status, body: xhr.responseText });
      };
      xhr.onerror = function () {
        done({ status: 0, body: "network error" });
      };
      xhr.send(
        "name=" +
          encodeURIComponent(user) +
          "&password=" +
          encodeURIComponent(pass),
      );
    },
    user,
    pass,
  );

  if (result.status !== 200) {
    throw new Error(`Login request failed (${result.status}): ${result.body}`);
  }
}

// The first submit after a fresh login sometimes does nothing, so retry once.
async function createGroup(driver, name) {
  await setText(driver, "#groupName", name);
  await click(driver, "#groupForm button[type=submit]");
  try {
    await waitVisible(driver, "#groupInfo", 5000);
    return;
  } catch (e) {
    console.log(`  note: "Create group" had no effect the first time (${name}), retrying`);
  }
  await setText(driver, "#groupName", name);
  await click(driver, "#groupForm button[type=submit]");
  await waitVisible(driver, "#groupInfo", LOBBY_TIMEOUT);
}

async function closeGroupIfOpen(driver) {
  try {
    const buttons = await driver.findElements(By.id("closeGroupBtn"));
    if (buttons.length > 0 && (await buttons[0].isDisplayed())) {
      await buttons[0].click();
      await acceptAlertIfPresent(driver);
    }
  } catch (e) {}
}

async function testMultiplayerPlayerView(driver) {
  for (const sel of ["#tabletView", "#nameForm", "#groupsPanel"]) {
    await waitVisible(driver, sel);
  }

  const admin = await driver.findElement(By.id("adminView"));
  assert(
    !(await admin.isDisplayed()),
    "Admin controls should be hidden for a guest",
  );

  const home = await driver.findElement(By.css("a.home"));
  assert.strictEqual(await home.getDomAttribute("href"), "index.html");
}

async function testMultiplayerHostCreatesGroup(driver) {
  try {
    await login(driver, ADMIN_USER, ADMIN_PASS);
    await driver.get(await pageUrl(driver, "triviaUser.html"));
    await waitVisible(driver, "#adminView", LOBBY_TIMEOUT);
    await waitConnected(driver);

    const group = "SelGroup" + (Date.now() % 100000);
    await createGroup(driver, group);

    const title = await driver.findElement(By.id("groupTitle"));
    await driver.wait(
      async () => (await title.getText()).includes(group),
      TIMEOUT,
      "Group title should show the group name",
    );
  } finally {
    await closeGroupIfOpen(driver);
    await clearSession(driver);
  }
}

async function testMultiplayerPlayerJoinsGroup(host) {
  let player;
  try {
    await login(host, ADMIN_USER, ADMIN_PASS);
    await host.get(await pageUrl(host, "triviaUser.html"));
    await waitVisible(host, "#adminView", LOBBY_TIMEOUT);
    await waitConnected(host);

    const group = "SelJoin" + (Date.now() % 100000);
    const playerName = "Tester" + (Date.now() % 100000);

    await createGroup(host, group);

    // second browser for the player
    player = await new Builder().forBrowser("chrome").build();
    await player.get(await pageUrl(host, "triviaUser.html"));
    await waitConnected(player);

    await setText(player, "#nameInput", playerName);
    await click(player, "#nameForm button[type=submit]");

    const joinBtn = await player.wait(
      until.elementLocated(
        By.xpath(`//ul[@id='groupList']//li[contains(., '${group}')]//button`),
      ),
      LOBBY_TIMEOUT,
      "Group should appear in the player's list",
    );
    await joinBtn.click();

    await waitVisible(player, "#joinedPanel", LOBBY_TIMEOUT);
    await waitVisible(player, "#waiting");

    const members = await host.findElement(By.id("members"));
    await host.wait(
      async () => (await members.getText()).includes(playerName),
      LOBBY_TIMEOUT,
      "Host should see the player in the member list",
    );
  } finally {
    if (player) await player.quit();
    await closeGroupIfOpen(host);
    await clearSession(host);
  }
}

module.exports = [
  {
    name: "Multiplayer: Player View UI",
    path: "triviaUser.html",
    run: testMultiplayerPlayerView,
  },
  {
    name: "Multiplayer: Host Creates Group",
    path: "login.html",
    run: testMultiplayerHostCreatesGroup,
  },
  {
    name: "Multiplayer: Player Joins Group",
    path: "login.html",
    run: testMultiplayerPlayerJoinsGroup,
  },
];
/**
 * @description
 * Tests are ran automatically by docker, but can also be ran manually by running the following commands:
 * ```
 * cd Test/Front-End-Selenium/
 * npm install
 * node test-runner.js
 * ```
 */
const { Builder, Browser } = require("selenium-webdriver");

const BASE_URL = "http://localhost:8081/3940-Group-Assignment/";

// [!] Append tests to this list.
// prettier-ignore
const TESTS = [
  require("./tests/home.js"),
  require("./tests/solo.js"),
  require("./tests/login.js"),
  require("./tests/multiplayer.js"),
  require("./tests/apis.js"),
].flat();

(async function runTests() {
  // Setup.
  const driver = await new Builder().forBrowser(Browser.CHROME).build();

  // Run tests
  for (const test of TESTS) {
    try {
      await driver.get(BASE_URL + (test.path || ""));
      await driver.manage().deleteAllCookies();
      await driver.get(BASE_URL + (test.path || ""));

      await test.run(driver);

      console.log(`✅ Test: "${test.name}" PASS`);
    } catch (error) {
      console.error(
        `❌ Test: "${test.name}" FAILED with error:`,
        error.message,
      );
    }
  }

  await driver.quit();
})();

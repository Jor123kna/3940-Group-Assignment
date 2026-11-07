const { By, until } = require("selenium-webdriver");
const assert = require("assert");

const TIMEOUT = 10000; // 10 seconds

/**
 * Wait for category elements to load.
 * @private
 */
async function getCategories(driver) {
  await driver.wait(until.elementLocated(By.css("#categories")), TIMEOUT);

  return await driver.wait(
    async () => {
      const categories = await driver.findElements(By.css("#categories > *"));
      return categories.length > 0 ? categories : false;
    },
    TIMEOUT,
    "Categories failed to load",
  );
}

/**
 * Get the current game state.
 * @private
 */
async function getGameState(driver) {
  return await driver.executeScript(() => {
    const currentGame = typeof game !== "undefined" ? game : window.game;
    if (!currentGame) return null;

    const results = currentGame.results || {};
    const keys = Object.keys(results);

    return {
      index: currentGame.index,
      resultCount: keys.length,
      correct: keys.length ? results[keys[0]].correct : null,
    };
  });
}

/**
 * Home Path Test
 */
async function testHomePath(driver) {
  const btn = await driver.findElement(By.css("body > div > a"));
  const href = await btn.getDomAttribute("href");

  assert.strictEqual(
    href,
    "index.html",
    "Home button should link to index.html",
  );
}

/**
 * Question Answer Test
 */
async function testQuestionAnswer(driver) {
  // Select a random category.
  const categories = await getCategories(driver);
  const category = categories[0]; //Math.floor(Math.random() * categories.length)];
  await category.click();

  // Wait for questions to load.
  const choices = await driver.wait(
    async () => {
      const elements = await driver.findElements(By.css("#choices > *"));
      return elements.length > 0 ? elements : false;
    },
    TIMEOUT,
    "Question choices failed to load",
  );

  // Select a random answer.
  const answer = choices[Math.floor(Math.random() * choices.length)];
  await answer.click();

  // Wait for answer feedback.
  const feedback = await driver.wait(
    async () => {
      const correct = await driver.findElements(
        By.id(`[class*="feedback"][class*="correct"]`),
      );
      const incorrect = await driver.findElements(
        By.css(`[class*="feedback"][class*="wrong"]`),
      );

      if (correct.length > 0) return { correct: true };
      if (incorrect.length > 0) return { correct: false };
      return null;
    },
    TIMEOUT,
    "Answer feedback failed to appear",
  );

  const isCorrect = feedback?.correct;

  // Check internal game state against visual feedback.
  const gameState = await getGameState(driver);

  assert(gameState, "Game object should exist");
  assert.strictEqual(
    gameState.resultCount,
    1,
    "Game should contain exactly one result",
  );
  assert.strictEqual(
    gameState.correct,
    isCorrect,
    "Visual feedback should match the internal game result",
  );
}

/**
 * Category Switch Reset Test
 */
async function testCategorySwitchReset(driver) {
  // Select an initial category.
  const categories = await getCategories(driver);

  assert(categories.length >= 2, "At least two categories are required");

  const firstIndex = Math.floor(Math.random() * categories.length);
  await categories[firstIndex].click();

  // Wait for the initial category to load.
  await driver.wait(
    async () => {
      const choices = await driver.findElements(By.css("#choices > *"));
      return choices.length > 0;
    },
    TIMEOUT,
    "Initial category failed to load",
  );

  // Inject random data into game.
  await driver.executeScript(() => {
    const currentGame = typeof game !== "undefined" ? game : window.game;

    currentGame.results = {
      test: {},
      test2: {},
    };
    currentGame.index = 100;
  });

  // Select a different category.
  const newCategories = await getCategories(driver);
  const secondIndex = (firstIndex + 1) % newCategories.length;
  await newCategories[secondIndex].click();

  // Wait for game state to reset.
  await driver.wait(
    async () => {
      const state = await getGameState(driver);
      return state && state.index === 0 && state.resultCount === 0;
    },
    TIMEOUT,
    "Game state did not reset after category switch",
  );

  const gameState = await getGameState(driver);

  assert.strictEqual(gameState.index, 0, "Game index should reset to 0");
  assert.strictEqual(
    gameState.resultCount,
    0,
    "Game results should be cleared",
  );
}

module.exports = [
  {
    name: "Solo: Home UI",
    path: "triviaSolo.html",
    run: testHomePath,
  },
  {
    name: "Solo: Simulate Question Answer",
    path: "triviaSolo.html",
    run: testQuestionAnswer,
  },
  {
    name: "Solo: Reset Game Data on Category Switch",
    path: "triviaSolo.html",
    run: testCategorySwitchReset,
  },
];

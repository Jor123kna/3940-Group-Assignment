const assert = require("assert");

/**
 * Helper fetch function
 */
async function seleniumFetch(driver, url, options = {}) {
  return await driver
    .executeAsyncScript(
      async function (url, options, callback) {
        try {
          const response = await fetch(url, options);

          if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
          }

          const data = await response.json();
          callback(data);
        } catch (error) {
          callback({ seleniumFetchError: error.message });
        }
      },
      url,
      options,
    )
    .then((data) => {
      if (data.seleniumFetchError) {
        throw new Error(data.seleniumFetchError);
      }

      return data;
    });
}

module.exports = [
  {
    name: "API -- Category Fetch",
    run: async (driver) => {
      const MIN_CATEGORY_COUNT = 3;

      const data = await seleniumFetch(driver, "./categories");

      assert(Array.isArray(data), "Categories response should be an array");
      assert(
        data.length > MIN_CATEGORY_COUNT,
        `Expected more than ${MIN_CATEGORY_COUNT} categories, received ${data.length}`,
      );
    },
  },
  {
    name: "API -- Category Questions Fetch",
    run: async (driver) => {
      const MIN_QUESTION_COUNT = 5;

      const categories = await seleniumFetch(driver, "./categories");

      assert(
        Array.isArray(categories),
        "Categories response should be an array",
      );

      for (let i = 0; i < categories.length; i++) {
        const categoryId = i + 1;

        const data = await seleniumFetch(
          driver,
          `./questions?category_id=${categoryId}`,
        );

        assert(
          Array.isArray(data),
          `Questions for category ${categoryId} should be an array`,
        );
        assert(
          data.length > MIN_QUESTION_COUNT,
          `Category ${categoryId} should have more than ${MIN_QUESTION_COUNT} questions, received ${data.length}`,
        );
      }
    },
  },
  {
    name: "API -- Questions Fetch",
    run: async (driver) => {
      const questionList = await seleniumFetch(
        driver,
        "./questions?category_id=1",
      );

      assert(
        Array.isArray(questionList),
        "Questions response should be an array",
      );
      assert(questionList.length > 0, "Questions list should not be empty");

      const question =
        questionList[Math.floor(Math.random() * questionList.length)];

      assert(question.question_id > 0, "Question ID should be positive");
      assert(
        question.question_text.length > 5,
        "Question text should be longer than 5 characters",
      );
      assert(Array.isArray(question.answers), "Answers should be an array");
      assert.ok(
        question.answers.length > 1,
        "Each question should have at least 2 answers",
      );
    },
  },
  {
    name: "API -- Question Answer Fetch",
    run: async (driver) => {
      const questionList = await seleniumFetch(
        driver,
        "./questions?category_id=2",
      );

      assert(
        Array.isArray(questionList),
        "Questions response should be an array",
      );
      assert(questionList.length > 0, "Questions list should not be empty");

      const question =
        questionList[Math.floor(Math.random() * questionList.length)];

      const answers = question.answers;

      assert(Array.isArray(answers), "Answers should be an array");
      assert.ok(answers.length > 1, "Expected at least 2 answers");

      let incorrectCount = 0;
      let correctCount = 0;

      for (const answer of answers) {
        const result = await seleniumFetch(driver, "./answer", {
          method: "POST",
          headers: {
            "Content-Type": "application/x-www-form-urlencoded",
          },
          body: new URLSearchParams({
            answer_id: String(answer.answer_id),
          }).toString(),
        });

        assert(
          typeof result.correct === "boolean",
          "Answer response should contain a boolean 'correct' property",
        );

        if (result.correct) correctCount++;
        else incorrectCount++;
      }

      assert.strictEqual(
        correctCount,
        1,
        `Expected exactly 1 correct answer, received ${correctCount}`,
      );
      assert.ok(
        incorrectCount > 0,
        `Expected at least 1 incorrect answers, received ${incorrectCount}`,
      );
    },
  },
];

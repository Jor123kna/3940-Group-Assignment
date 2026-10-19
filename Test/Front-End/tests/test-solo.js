// TODO This URL should change when this branch is out of date.
const { WebTest } =
  await import("https://raw.githack.com/Jor123kna/3940-Group-Assignment/Testing-QA/Test/Front-End/engine/Web-Test.js");

/* ------------- APIs -------------- */
WebTest.test("Category Fetch", "/triviaSolo.html", async () => {
  const MIN_CATEGORY_COUNT = 3;
  const response = await fetch("./categories");

  const data = await response.json();
  return (
    typeof data === "object" &&
    Array.isArray(data) &&
    data.length > MIN_CATEGORY_COUNT
  );
});

WebTest.test("Category Questions Fetch", "/triviaSolo.html", async () => {
  const MIN_QUESTION_COUNT = 5;

  const response = await fetch("./categories");
  const categories = await response.json();

  for (let i = 0; i < categories.length; i++) {
    const response = await fetch("./questions?category_id=" + (i + 1));

    const data = await response.json();
    return (
      typeof data === "object" &&
      Array.isArray(data) &&
      data.length > MIN_QUESTION_COUNT
    );
  }
});

WebTest.test("Questions Test", "/triviaSolo.html", async () => {
  const response = await fetch("./questions?category_id=1");
  const questionList = await response.json();
  const question =
    questionList[Math.round(Math.random() * questionList.length)];

  return (
    question["question_id"] > 0 &&
    question["question_text"].length > 5 &&
    Array.isArray(question["answers"]) &&
    question["answers"].length === 4
  );
});

WebTest.test("Question Answer Test", "/triviaSolo.html", async () => {
  const response = await fetch("./questions?category_id=2");
  const questionList = await response.json();
  const question =
    questionList[Math.round(Math.random() * questionList.length)];

  const answers = question["answers"];

  let incorrectCount = 0;
  let correctCount = 0;

  for (const answer of answers) {
    const response = await fetch("./answer", {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body: new URLSearchParams({
        answer_id: String(answer["answer_id"]),
      }),
    });

    const result = await response.json();
    if (result["correct"]) correctCount++;
    else incorrectCount++;
  }

  return correctCount === 1 && incorrectCount === 3;
});

/* ------------- GUI -------------- */
WebTest.test("Home Path", "/triviaSolo.html", () => {
  const btn = WebTest.getElement("body > div > a");

  return btn.getAttribute("href") === "index.html";
});

WebTest.test("Question Answer", "/triviaSolo.html", async () => {
  // Slight delay to fetch categories
  await new Promise((resolve) => setTimeout(resolve, 2000));

  // Select Category
  const categories = Array.from(WebTest.getElement("#categories").children);
  categories[Math.round(Math.random() * categories.length)].click();

  // Slight delay to fetch questions
  await new Promise((resolve) => setTimeout(resolve, 3000));

  // Pick random box
  const options = Array.from(WebTest.getElement("#choices").children);
  options[Math.round(Math.random() * options.length)].click();

  // Slight delay to check if the answer is correct
  await new Promise((resolve) => setTimeout(resolve, 1000));

  // Check if visual result equals internal result
  const isCorrect = WebTest.getElement(".feedback.correct") !== null;
  return (
    game &&
    typeof game === "object" &&
    Object.keys(game.results).length === 1 &&
    Object.values(game.results)[0].correct === isCorrect
  );
});

WebTest.test("Category Switch Reset", "/triviaSolo.html", async () => {
  // Slight delay to fetch categories
  await new Promise((resolve) => setTimeout(resolve, 2000));

  // Select Category
  const categories = Array.from(WebTest.getElement("#categories").children);
  categories[Math.round(Math.random() * categories.length)].click();

  // Inject random data into 'game'
  game.results = { test: {}, test2: {} };
  game.index = 100;

  // Select new Category
  categories[Math.round(Math.random() * categories.length)].click();

  // Game data should be cleared
  return game.index === 0 && Object.keys(game.results).length === 0;
});

// TODO This URL should change when this branch is out of date.
const { WebTest } =
  await import("https://raw.githack.com/Jor123kna/3940-Group-Assignment/Testing-QA/Test/Front-End/engine/Web-Test.js");

WebTest.test("Solo Path", () => {
  const btn = WebTest.getElement("body > main > nav > a.blue");

  return btn.getAttribute("href") === "triviaSolo.html";
});

WebTest.test("Multiplayer Path", () => {
  const btn = WebTest.getElement("body > main > nav > a.green");

  return btn.getAttribute("href") === "triviaUser.html";
});

WebTest.test("Login Path", () => {
  const btn = WebTest.getElement("body > header > div > a");

  return btn.getAttribute("href") === "login.html";
});

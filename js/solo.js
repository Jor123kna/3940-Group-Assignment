"use strict";

// Game state
// category: the category being played
// questions: the questions in this category
// index: which question is on screen
// results: question_id -> { answerId, correct, correctAnswerId }
// pending: question_id of the answers being checked by the server
let game = null;

function setStatus(text, kind) {
    setNotice($("status"), text, kind);
    $("status").hidden = text === "";
}

// categories: one tab per category, along the top

async function loadCategories() {
    let categories;
    try {
        categories = await request("GET", "categories");
    } catch (e) {
        setStatus(`Could not load categories. ${e.message}`, "error");
        return;
    }

    if (categories.length === 0) {
        setStatus("No categories found.", "info");
        return;
    }

    setStatus("Pick a category to start.", "info");
    $("categories").replaceChildren(...categories.map((category) =>
        el("button", {
            type: "button",
            className: "tab",
            textContent: category.category_name,
            onclick: (event) => selectCategory(event.currentTarget, category)
        })
    ));
}

async function selectCategory(button, category) {
    document.querySelectorAll("#categories .tab").forEach((tab) => {
        tab.classList.toggle("selected", tab === button);
    });

    // starting a new category throws away the old game
    const thisGame = game = { category, questions: [], index: 0, results: {}, pending: new Set() };

    $("game").hidden = true;
    $("results").hidden = true;
    setStatus("Loading questions...", "info");

    let questions;
    try {
        questions = await request("GET", `questions?${new URLSearchParams({ category_id: category.category_id })}`);
    } catch (e) {
        if (game === thisGame) setStatus(`Could not load questions. ${e.message}`, "error");
        return;
    }

    if (game !== thisGame) return; // the user already picked another category

    if (questions.length === 0) {
        setStatus(`There are no questions in ${category.category_name} yet.`, "info");
        return;
    }

    thisGame.questions = questions;
    setStatus("");
    renderQuestion();
}

// the question on screen

function renderQuestion() {
    const q = game.questions[game.index];
    const result = game.results[q.question_id];

    $("results").hidden = true;
    $("game").hidden = false;
    $("progress").textContent =
        `${game.category.category_name} \u2022 Question ${game.index + 1} of ${game.questions.length}`;
    $("questionText").textContent = q.question_text;
    showMedia($("questionMedia"), q.media);

    $("choices").className = result ? "choices revealed" : "choices";
    $("choices").replaceChildren(...q.answers.map((answer) => {
        const button = choiceButton(answer.answer_name);

        if (result) {
            button.disabled = true;
            if (answer.answer_id === result.correctAnswerId) {
                button.classList.add("correct");
            } else if (answer.answer_id === result.answerId) {
                button.classList.add("wrong");
            }
        } else {
            button.disabled = game.pending.has(q.question_id);
            button.onclick = () => chooseAnswer(q, answer);
        }
        return button;
    }));

    const feedback = $("feedback");
    if (!result) {
        setNotice(feedback);
    } else if (result.correct) {
        setNotice(feedback, "\u2713 Correct!", "ok");
    } else {
        const right = q.answers.find((a) => a.answer_id === result.correctAnswerId);
        setNotice(feedback, "\u2717 Not quite" + (right ? ` \u2014 the answer is ${right.answer_name}` : ""), "error");
    }

    // Back is available after the first question.
    // Next only unlocks once this question has been answered.
    $("backBtn").disabled = game.index === 0;
    $("nextBtn").disabled = !result;
    $("nextBtn").textContent = game.index === game.questions.length - 1 ? "Finish" : "Next";
}

// The player picks a choice: ask the server whether it was right.
async function chooseAnswer(q, answer) {
    const thisGame = game;
    if (thisGame.results[q.question_id] || thisGame.pending.has(q.question_id)) return;

    thisGame.pending.add(q.question_id);
    document.querySelectorAll("#choices .choice").forEach((button) => { button.disabled = true; });   // no double-clicks

    let failure = null;
    try {
        const result = await request("POST", "answer", { answer_id: answer.answer_id });
        thisGame.results[q.question_id] = {
            answerId: answer.answer_id,
            correct: result.correct,
            correctAnswerId: result.correct_answer_id
        };
    } catch (e) {
        failure = e;
    }
    thisGame.pending.delete(q.question_id);

    if (game !== thisGame || thisGame.questions[thisGame.index] !== q) return;   // the player moved on

    renderQuestion();   // on failure this re-enables the choices so they can try again
    if (failure) setNotice($("feedback"), `Could not check your answer. ${failure.message}`, "error");
}

// Back / Next / results

$("backBtn").onclick = () => {
    if (game && game.index > 0) {
        game.index--;
        renderQuestion();
    }
};

$("nextBtn").onclick = () => {
    if (!game || !game.results[game.questions[game.index].question_id]) return; // must answer first

    if (game.index < game.questions.length - 1) {
        game.index++;
        renderQuestion();
    } else {
        showResults();
    }
};

function showResults() {
    const total = game.questions.length;
    const correct = Object.values(game.results).filter((r) => r.correct).length;

    $("game").hidden = true;
    $("results").hidden = false;
    $("score").textContent = `${correct} / ${total}`;
    $("scoreText").textContent =
        `You got ${correct} of ${total} right in ${game.category.category_name}. Play again or pick another category above.`;
}

$("againBtn").onclick = () => {
    Object.assign(game, { index: 0, results: {}, pending: new Set() });
    renderQuestion();
};

loadCategories();
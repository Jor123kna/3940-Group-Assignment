"use strict";

const ANSWER_SLOTS = 4;

let categories = [];
let selected = null;        // the selected category object, or null
let questionsToken = 0;     // lets a slow answer for a previously selected category be ignored

const answerBoxes = () => $("answers").querySelectorAll("input[type=text]");

function say(text, isError = false) {
    setNotice($("message"), text, isError ? "error" : "ok");
}

// Same as request(), but an expired login (401) sends the admin to the login page.
async function api(method, url, params) {
    try {
        return await request(method, url, params);
    } catch (error) {
        if (error.status !== 401) throw error;
        location.href = "login.html";
        return new Promise(() => {}); // never settles: the page is going away
    }
}

// start: must be logged in

(async function start() {
    const session = await loadSession();
    if (!session.loggedIn) {
        location.href = "login.html";
        return;
    }

    $("who").textContent = session.displayName;
    $("panels").hidden = false;
    loadCategories();
})();

$("logoutBtn").onclick = async () => {
    await logout();
    location.href = "index.html";
};

// The answer rows: a "correct" radio button and a text box, repeated ANSWER_SLOTS times.
for (let i = 0; i < ANSWER_SLOTS; i++) {
    const n = i + 1;
    $("answers").append(el("div", {},
        el("input", { type: "radio", name: "correct", value: i, defaultChecked: i === 0, ariaLabel: `Answer ${n} is correct` }),
        el("input", { type: "text", maxLength: 150, placeholder: `Answer ${n}${i >= 2 ? " (optional)" : ""}` })
    ));
}

// ---------- categories ----------

// Resolves to false (after showing the error) when the list could not be loaded.
async function loadCategories() {
    try {
        categories = await api("GET", "admin/categories");
    } catch (e) {
        say(`Could not load categories. ${e.message}`, true);
        return false;
    }

    // keep the selection if that category still exists
    selected = selected && categories.find((c) => c.category_id === selected.category_id) || null;
    renderCategories();
    renderQuestionPanel();
    return true;
}

function renderCategories() {
    $("categoriesEmpty").hidden = categories.length > 0;

    $("categoryList").replaceChildren(...categories.map((category) => el("li", {},
        el("button", {
            type: "button",
            className: category === selected ? "tab selected" : "tab",
            onclick: () => selectCategory(category)
        },
            el("span", {}, category.category_name),
            el("span", { className: "badge", textContent: category.question_count, title: `${category.question_count} question(s)` })
        ),
        el("button", {
            type: "button",
            className: "btn red small",
            textContent: "Delete",
            ariaLabel: `Delete category ${category.category_name}`,
            onclick: (event) => deleteCategory(category, event.currentTarget)
        })
    )));
}

function selectCategory(category) {
    selected = category;
    say("");
    renderCategories();
    renderQuestionPanel();
    loadQuestions();
}

$("categoryForm").onsubmit = async (event) => {
    event.preventDefault();

    const name = $("catName").value.trim();
    if (name === "") {
        say("Enter a category name.", true);
        $("catName").focus();
        return;
    }

    event.submitter.disabled = true;
    try {
        const result = await api("POST", "admin/category-add", { name, description: $("catDesc").value });
        event.target.reset();

        if (await loadCategories()) {
            const added = categories.find((c) => c.category_id === result.category_id);
            if (added) selectCategory(added);     // this clears the message line...
            say(`Added category "${name}".`);     // ...so say it afterwards
        }
    } catch (e) {
        say(e.message, true);
    } finally {
        event.submitter.disabled = false;
    }
};

async function deleteCategory(category, button) {
    const n = category.question_count;
    const warning = n > 0
        ? `Delete the category "${category.category_name}" and its ${n} question${n === 1 ? "" : "s"}?\n\nThis cannot be undone.`
        : `Delete the empty category "${category.category_name}"?`;

    if (!window.confirm(warning)) return;

    button.disabled = true;
    try {
        await api("POST", "admin/category-delete", { category_id: category.category_id });
    } catch (e) {
        button.disabled = false;
        say(e.message, true);
        return;
    }

    say(`Deleted category "${category.category_name}".`);
    if (selected && selected.category_id === category.category_id) selected = null;
    loadCategories();
}

// ---------- questions of the selected category ----------

function renderQuestionPanel() {
    $("questionsHeading").textContent = selected ? `Questions in ${selected.category_name}` : "Questions";
    $("addQuestionTo").textContent = selected ? selected.category_name : "";
    $("addQuestion").hidden = !selected;

    if (!selected) {
        $("questionList").replaceChildren();
        showQuestionsNote("Select a category to see and manage its questions.");
    }
}

function showQuestionsNote(text) {
    $("questionsEmpty").textContent = text;
    $("questionsEmpty").hidden = text === "";
}

async function loadQuestions() {
    if (!selected) return;

    const token = ++questionsToken;
    const params = new URLSearchParams({ category_id: selected.category_id });

    $("questionList").replaceChildren();
    showQuestionsNote("Loading questions...");

    try {
        const questions = await api("GET", `admin/questions?${params}`);
        if (token === questionsToken) renderQuestions(questions);
    } catch (e) {
        if (token !== questionsToken) return;
        showQuestionsNote("");
        say(`Could not load questions. ${e.message}`, true);
    }
}

function renderQuestions(questions) {
    showQuestionsNote(questions.length === 0 ? "There are no questions in this category yet." : "");

    $("questionList").replaceChildren(...questions.map((q) => el("article", { className: "card" },
        el("header", {},
            el("p", {}, q.question_text),
            el("button", {
                type: "button",
                className: "btn red small",
                textContent: "Delete",
                ariaLabel: `Delete question ${q.question_text}`,
                onclick: (event) => deleteQuestion(q, event.currentTarget)
            })
        ),
        el("ul", {}, ...q.answers.map((a) =>
            el("li", { className: a.correct ? "correct" : "", textContent: (a.correct ? "\u2713 " : "") + a.answer_name })
        ))
    )));
}

async function deleteQuestion(q, button) {
    if (!window.confirm(`Delete this question?\n\n${q.question_text}`)) return;

    button.disabled = true;
    try {
        await api("POST", "admin/question-delete", { question_id: q.question_id });
    } catch (e) {
        button.disabled = false;
        say(e.message, true);
        return;
    }

    say("Question deleted.");
    if (await loadCategories()) loadQuestions();   // refreshes the counts too
}

$("questionForm").onsubmit = async (event) => {
    event.preventDefault();

    if (!selected) { say("Select a category first.", true); return; }

    const text = $("qText").value.trim();
    if (text === "") { say("Enter the question.", true); $("qText").focus(); return; }

    const boxes = [...answerBoxes()];
    if (boxes.filter((box) => box.value.trim() !== "").length < 2) { say("Enter at least two answers.", true); return; }

    const correct = Number(document.querySelector("input[name=correct]:checked").value);
    if (boxes[correct].value.trim() === "") {
        say("The answer you ticked as correct is empty. Tick one that has text.", true);
        return;
    }

    const params = [
        ["category_id", selected.category_id],
        ["question_text", text],
        ["correct", correct],
        ...boxes.map((box) => ["answer", box.value])
    ];

    event.submitter.disabled = true;
    try {
        await api("POST", "admin/question-add", params);
        event.target.reset();
        say("Question added.");
        if (await loadCategories()) loadQuestions();
        $("qText").focus();
    } catch (e) {
        say(e.message, true);
    } finally {
        event.submitter.disabled = false;
    }
};

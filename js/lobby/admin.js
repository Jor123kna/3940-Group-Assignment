"use strict";

// The admin (a staff account): creates a group, picks a quiz from the database,
// asks its questions one by one and sees the answers come in.

let group = null;         // { id, name, members: { "Guest5": { name: "Grandma", cid } }, scores: { cid: points }, scored: Set of question ids }
                          //   members are keyed by hub name (one per connection); cid is the tablet's own id,
                          //   so votes and points survive a reconnect
let asked = null;         // the question the group sees now:
                          //   { id, questionId, text, media, answers, correct, votes: { cid: { index, name } },
                          //     revealed, points }
let quiz = null;          // the category being browsed:
//   { category, index, questions: [{ question_id, question_text, media, answers: [{ answer_id, answer_name, correct }] }] }

// ---------- messages from the tablets ----------

// our own hello after a reconnect (new hub name), or someone new arrived:
// announce the group again so they can see it
function onHello() {
    if (group) announceGroup();
}

// a tablet joined (or came back after a reconnect)
function onJoin(sender, [target, id, who, cid]) {
    if (target !== hubName || !group || id !== group.id) return;

    cid = clean(cid || "") || sender;
    for (const [hub, member] of Object.entries(group.members)) {
        if (member.cid === cid && hub !== sender) delete group.members[hub];   // its old connection
    }
    group.members[sender] = { name: shortName(who) || sender, cid };
    group.scores[cid] = group.scores[cid] || 0;

    renderGroup();
    showBoard();
    if (asked) {
        // the newcomer gets the question that is open right now, or the answer if it is already given
        if (asked.revealed) sendReveal(); else sendQuestion();
    }
    sendBoard();
}

function onLeave(sender, [target, id]) {
    if (target !== hubName || !group || id !== group.id) return;
    forget(sender);
}

// a tablet changed its display name
function onRename(sender, [target, id, who]) {
    const member = group && group.members[sender];
    if (target !== hubName || !member || id !== group.id) return;

    member.name = shortName(who) || sender;
    if (asked && member.cid in asked.votes) asked.votes[member.cid].name = member.name;
    renderGroup();
    renderResults();
    showBoard();
    sendBoard();
}

// a tablet answered. Answers addressed to someone else are not ours.
function onAnswer(sender, [target, groupId, id, answerNo]) {
    const index = Number(answerNo);
    const member = group && group.members[sender];

    if (target !== hubName || !member || groupId !== group.id || !asked || id !== asked.id || asked.revealed) return;
    if (member.cid in asked.votes || !(index >= 0 && index < asked.answers.length)) return;   // first answer counts

    asked.votes[member.cid] = { index, name: member.name };
    renderResults();
    revealIfEveryoneAnswered();
}

// someone's connection ended: forget them
function onGone(name) {
    if (group && name in group.members) forget(name);
}

function forget(hub) {
    delete group.members[hub];
    renderGroup();
    showBoard();
    sendBoard();
    revealIfEveryoneAnswered();   // the ones who are left may all have answered
}

// display names: no "|", not too long
const shortName = (text) => clean(text || "").slice(0, 30);

const adminRole = { handlers: { H: onHello, J: onJoin, N: onRename, L: onLeave, A: onAnswer }, gone: onGone };

// ---------- the group ----------

$("groupForm").onsubmit = (event) => {
    event.preventDefault();

    const name = clean($("groupName").value);
    if (name === "") return say("Enter a name for the group.");
    if (!isConnected()) return say("Not connected yet.");

    say("");
    group = { id: newId(), name, members: {}, scores: {}, scored: new Set() };
    asked = null;
    $("groupName").value = "";
    announceGroup();
    renderGroup();
    renderResults();
    renderPreview();
    showBoard();
};

$("closeGroupBtn").onclick = () => {
    if (!group || !window.confirm(`Close the group "${group.name}" for everyone?`)) return;

    if (isConnected()) wsSendMessage(`X|${group.id}`);
    group = null;
    asked = null;
    renderGroup();
    renderResults();
    renderPreview();
    showBoard();
};

function announceGroup() {
    wsSendMessage(`G|${group.id}|${group.name}`);
}

function renderGroup() {
    $("groupForm").hidden = group !== null;
    $("groupInfo").hidden = group === null;
    $("askPanel").hidden = group === null;
    if (!group) return;

    const names = Object.values(group.members);   // [{ name, cid }]

    $("groupTitle").replaceChildren(
        group.name, " ",
        el("span", { className: "badge" }, `${names.length} joined`)
    );
    $("members").replaceChildren(...names.map((member) => el("li", {}, member.name)));
}

// ---------- the quiz: categories and questions come from the database ----------
// Same data as the solo quiz. The admin needs the correct answers, so the
// questions come from the staff-only admin/questions; the pictures are only in
// the public questions list, so both are loaded and put together.

function quizStatus(text, kind) {
    setNotice($("quizStatus"), text, kind);
    $("quizStatus").hidden = text === "";
}

// called by lobby.js once we know this is a staff account
async function loadQuizCategories() {
    let categories;
    try {
        categories = await request("GET", "admin/categories");
    } catch (e) {
        quizStatus(`Could not load the categories. ${e.message}`, "error");
        return;
    }

    if (categories.length === 0) {
        quizStatus("There are no categories yet. Add some on the admin page.", "info");
        return;
    }

    quizStatus("Pick a category to choose the questions from.", "info");
    $("quizCategories").replaceChildren(...categories.map((category) =>
        el("button", {
            type: "button",
            className: "tab",
            textContent: `${category.category_name} (${category.question_count})`,
            disabled: category.question_count === 0,
            onclick: (event) => selectQuizCategory(event.currentTarget, category)
        })
    ));
}

async function selectQuizCategory(button, category) {
    document.querySelectorAll("#quizCategories .tab").forEach((tab) => {
        tab.classList.toggle("selected", tab === button);
    });

    const thisQuiz = quiz = { category, index: 0, questions: [] };
    $("quizPreview").hidden = true;
    quizStatus("Loading questions...", "info");

    const query = new URLSearchParams({ category_id: category.category_id });
    let questions, withPictures;
    try {
        [questions, withPictures] = await Promise.all([
            request("GET", `admin/questions?${query}`),
            request("GET", `questions?${query}`).catch(() => [])   // no pictures is not fatal
        ]);
    } catch (e) {
        if (quiz === thisQuiz) quizStatus(`Could not load the questions. ${e.message}`, "error");
        return;
    }

    if (quiz !== thisQuiz) return;   // the admin already picked another category

    const media = new Map(withPictures.map((q) => [q.question_id, q.media]));
    const usable = questions
        .filter((q) => q.answers.length >= 2 && q.answers.some((a) => a.correct))
        .map((q) => ({ ...q, media: media.get(q.question_id) || null }));

    if (usable.length === 0) {
        quizStatus(`There are no usable questions in ${category.category_name} yet.`, "info");
        return;
    }

    thisQuiz.questions = usable;

    const skipped = questions.length - usable.length;
    quizStatus(skipped > 0 ? `${skipped} incomplete question${skipped === 1 ? " was" : "s were"} skipped.` : "", "info");
    renderPreview();
}

// the question the admin is looking at (not yet sent to the group)
function renderPreview() {
    const ready = quiz !== null && quiz.questions.length > 0;
    $("quizPreview").hidden = !ready;
    if (!ready) return;

    const q = quiz.questions[quiz.index];
    const live = asked !== null && asked.questionId === q.question_id;

    $("quizProgress").textContent =
        `${quiz.category.category_name} • Question ${quiz.index + 1} of ${quiz.questions.length}`;
    $("liveBadge").hidden = !live;
    $("previewQuestion").textContent = q.question_text;
    showMedia($("previewMedia"), q.media);

    // shown like the solo quiz's answer reveal: the correct answer has the tick
    $("previewAnswers").replaceChildren(...q.answers.map((answer) => {
        const button = choiceButton(answer.answer_name);

        button.disabled = true;
        button.classList.toggle("correct", answer.correct);
        return button;
    }));

    $("prevQuestionBtn").disabled = quiz.index === 0;
    $("nextQuestionBtn").disabled = quiz.index === quiz.questions.length - 1;
    $("askBtn").textContent = live ? "Ask again" : "Ask the group";
}

$("prevQuestionBtn").onclick = () => {
    if (quiz && quiz.index > 0) {
        quiz.index--;
        renderPreview();
    }
};

$("nextQuestionBtn").onclick = () => {
    if (quiz && quiz.index < quiz.questions.length - 1) {
        quiz.index++;
        renderPreview();
    }
};

$("askBtn").onclick = () => {
    if (!quiz || !group) return;
    if (!isConnected()) return say("Not connected yet.");

    const q = quiz.questions[quiz.index];

    say("");
    asked = {
        id: newId(),
        questionId: q.question_id,
        text: clean(q.question_text),
        media: q.media && (q.media.type || "image") === "image" ? clean(q.media.url || "") : "",
        answers: q.answers.map((answer) => clean(answer.answer_name)),
        correct: q.answers.findIndex((answer) => answer.correct),
        votes: {},
        revealed: false,
        points: 0
    };
    sendQuestion();
    renderResults();
    renderPreview();
};

// the correct answer stays on the admin's screen; it is never sent
function sendQuestion() {
    wsSendMessage(["Q", group.id, asked.id, asked.media, asked.text, ...asked.answers].join("|"));
}

// ---------- the answer, the points and the leaderboard ----------

// Once every tablet in the group has answered, the right answer goes out to all of
// them (they see "correct" or "incorrect") and the points are counted. The admin can
// also do it early, for a tablet that never answers.
function revealIfEveryoneAnswered() {
    if (!group || !asked || asked.revealed) return;

    const members = Object.values(group.members);
    if (members.length > 0 && members.every((member) => member.cid in asked.votes)) revealAnswer();
}

function revealAnswer() {
    if (!group || !asked || asked.revealed) return;
    if (!isConnected()) return say("Not connected yet.");

    asked.revealed = true;

    // a question that is asked a second time does not earn points a second time
    if (!group.scored.has(asked.questionId)) {
        group.scored.add(asked.questionId);
        asked.points = 1;
        for (const [cid, vote] of Object.entries(asked.votes)) {
            if (vote.index === asked.correct) group.scores[cid] = (group.scores[cid] || 0) + 1;
        }
    }

    sendReveal();
    sendBoard();
    renderResults();
    showBoard();
}

$("revealBtn").onclick = revealAnswer;

function sendReveal() {
    wsSendMessage(`R|${group.id}|${asked.id}|${asked.correct}|${asked.points}`);
}

// best first; same points: alphabetical, so the order does not jump around
function boardRows() {
    return Object.values(group.members)
        .map((member) => ({ id: member.cid, name: member.name, points: group.scores[member.cid] || 0 }))
        .sort((a, b) => b.points - a.points || a.name.localeCompare(b.name));
}

function sendBoard() {
    if (!group || !isConnected()) return;
    wsSendMessage(["B", group.id, ...boardRows().flatMap((row) => [row.id, row.name, row.points])].join("|"));
}

function showBoard() {
    const rows = group ? boardRows() : [];

    $("noBoard").hidden = rows.length > 0;
    $("adminBoard").hidden = rows.length === 0;
    renderBoard($("adminBoard"), rows, null);
}

function renderResults() {
    $("noQuestion").hidden = asked !== null;
    $("results").hidden = asked === null;
    if (!asked) return;

    const votes = Object.values(asked.votes);

    $("resultsQuestion").textContent = asked.text;
    const here = Object.values(group.members);
    const answered = here.filter((member) => member.cid in asked.votes).length;   // not those who left since
    $("resultsCount").textContent = asked.revealed
        ? `${answered} of ${here.length} answered. The answer was sent to the group${asked.points ? "." : ", no points: this question was already scored."}`
        : `${answered} of ${here.length} answered`;
    $("revealBtn").disabled = asked.revealed;
    $("resultsList").replaceChildren(...asked.answers.map((answer, i) => {
        const who = votes.filter((vote) => vote.index === i).map((vote) => vote.name);

        return el("li", { className: i === asked.correct ? "correct" : "" },
            `${answer} \u2014 ${who.length}`,
            el("small", {}, who.join(", "))
        );
    }));
}

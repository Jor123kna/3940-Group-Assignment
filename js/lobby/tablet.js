"use strict";

// A tablet (a senior, logged in or not): picks a group, reads the question, taps an answer.

const ADMIN_GRACE_MS = 10000;   // wait this long for a vanished admin to come back

let groups = {};          // id -> { id, name, admin, timer }   the groups on offer
let joined = null;        // id of the group this tablet joined
let received = null;      // { id, media, text, answers, chosen, correct, points }   correct: null until the admin reveals it
let board = [];           // the leaderboard the admin sent: [{ id, name, points }]
const NAME_KEY = "lobbyGuestName";

// ---------- messages from the admin ----------

// an admin announced a group
function onGroup(admin, [id, name]) {
    const known = groups[id];
    if (known) clearTimeout(known.timer);   // the admin is back
    groups[id] = { id, name, admin };

    // the admin came back under a new hub name: tell the new name that we are in
    if (known && known.admin !== admin && joined === id) sendJoin();
    renderTablet();
}

// the admin closed a group
function onClosed(admin, [id]) {
    if (groups[id] && groups[id].admin === admin) dropGroup(id);
}

// the admin asked something
function onQuestion(admin, [groupId, id, media, text, ...answers]) {
    if (joined !== groupId || (received && received.id === id)) return;

    received = { id, media, text, answers, chosen: null, correct: null, points: 0 };
    renderTablet();
}

// everybody answered: the admin tells us which answer was right
function onReveal(admin, [groupId, id, correct, points]) {
    const right = Number(correct);

    if (!fromMyAdmin(admin, groupId) || !received || received.id !== id) return;
    if (!(right >= 0 && right < received.answers.length)) return;

    received.correct = right;
    received.points = Number(points) || 0;
    renderTablet();
}

// the leaderboard: <id>|<name>|<points> repeated
function onBoard(admin, [groupId, ...flat]) {
    if (!fromMyAdmin(admin, groupId)) return;

    board = [];
    for (let i = 0; i + 2 < flat.length; i += 3) {
        board.push({ id: flat[i], name: flat[i + 1], points: Number(flat[i + 2]) || 0 });
    }
    renderTablet();
}

const fromMyAdmin = (admin, groupId) => joined === groupId && groups[joined] !== undefined && groups[joined].admin === admin;

// our own hello came back: if this is a reconnect, tell the admin that we are still here
function onHello(sender, [who]) {
    if (who === nonce && joined !== null && groups[joined]) sendJoin();
    renderTablet();   // our hub name is known now: "The others see you as Guest3"
}

// someone's connection ended. An admin that vanished may only be having a blip
// (it announces its group again), so keep the group for a moment.
function onGone(name) {
    Object.values(groups).filter((g) => g.admin === name).forEach((g) => {
        clearTimeout(g.timer);
        g.timer = setTimeout(() => dropGroup(g.id), ADMIN_GRACE_MS);
    });
}

const tabletRole = { handlers: { H: onHello, G: onGroup, X: onClosed, Q: onQuestion, R: onReveal, B: onBoard }, gone: onGone };

function dropGroup(id) {
    if (groups[id]) clearTimeout(groups[id].timer);
    delete groups[id];

    if (joined === id) {
        joined = null;
        received = null;
        board = [];
    }
    renderTablet();
}

// ---------- joining and leaving ----------

function sendJoin() {
    wsSendMessage(`J|${groups[joined].admin}|${joined}|${myName}|${myId}`);
}

function joinGroup(id) {
    if (!isConnected()) return say("Not connected yet.");

    say("");
    joined = id;
    received = null;
    board = [];
    sendJoin();
    renderTablet();
}

$("leaveBtn").onclick = () => {
    if (joined !== null && groups[joined] && isConnected()) {
        wsSendMessage(`L|${groups[joined].admin}|${joined}`);
    }
    joined = null;
    received = null;
    board = [];
    renderTablet();
};

// ---------- my display name ----------
// Everybody can pick the name the others see. Guests are remembered in this
// browser; a logged-in account starts with its own name. Empty = the hub name ("Guest3").

$("nameForm").onsubmit = (event) => {
    event.preventDefault();

    myName = clean($("nameInput").value).slice(0, 20);
    $("nameInput").value = myName;
    if (guest) {
        try { localStorage.setItem(NAME_KEY, myName); } catch (e) { /* no storage: fine */ }
    }

    if (joined !== null && groups[joined] && isConnected()) {
        wsSendMessage(`N|${groups[joined].admin}|${joined}|${myName}`);
    }
    setNotice($("nameNotice"), "Name saved.", "ok");
    renderTablet();
};

$("nameInput").oninput = () => setNotice($("nameNotice"));

let guest = true;

// called by lobby.js once the session is known
function startTablet(session) {
    guest = !session.loggedIn;

    if (guest) {
        try { myName = clean(localStorage.getItem(NAME_KEY) || "").slice(0, 20); } catch (e) { /* no storage */ }
    }
    $("nameInput").value = myName;
    renderTablet();
}

// ---------- the screen ----------

function renderTablet() {
    const mine = joined === null ? null : groups[joined];

    $("nameHint").textContent = `The others see you as ${myName || hubName || "..."}.`;

    // which part to show: the groups on offer, or the group I am in
    $("groupsPanel").hidden = !!mine;
    $("joinedPanel").hidden = !mine;

    const offered = Object.values(groups);
    $("noGroups").hidden = offered.length > 0;
    $("groupList").replaceChildren(...offered.map((g) => el("li", { className: "card" },
        el("strong", {}, g.name),
        el("button", { type: "button", className: "btn green small", textContent: "Join", onclick: () => joinGroup(g.id) })
    )));

    if (!mine) return;

    renderTabletBoard();
    $("joinedName").textContent = mine.name;
    $("waiting").hidden = received !== null;
    $("quiz").hidden = received === null;
    if (!received) return;

    const answered = received.chosen !== null;
    const revealed = received.correct !== null;

    $("questionText").textContent = received.text;
    showMedia($("questionMedia"), received.media ? { type: "image", url: received.media } : null);   // "" = no picture
    setNotice(...feedback(answered, revealed));

    $("choices").className = revealed ? "choices revealed" : answered ? "choices locked" : "choices";
    $("choices").replaceChildren(...received.answers.map((answer, i) => {
        const button = choiceButton(answer);

        button.disabled = answered || revealed;
        button.classList.toggle("picked", received.chosen === i);
        button.classList.toggle("correct", revealed && i === received.correct);
        button.classList.toggle("wrong", revealed && received.chosen === i && i !== received.correct);
        button.onclick = () => chooseAnswer(i);
        return button;
    }));
}

// the message under the question: [element, text, kind] for setNotice
function feedback(answered, revealed) {
    const target = $("feedback");

    if (!revealed) return [target, answered ? "Answer sent. Waiting for the others..." : "", "wait"];

    const right = received.answers[received.correct];
    if (!answered) return [target, `No answer from you. The right answer was ${right}.`, "info"];
    if (received.chosen === received.correct) {
        return [target, received.points ? "Correct! +1 point" : "Correct!", "ok"];
    }
    return [target, `Incorrect. The right answer was ${right}.`, "error"];
}

function chooseAnswer(index) {
    if (received.chosen !== null || received.correct !== null || !isConnected()) return;

    received.chosen = index;
    wsSendMessage(`A|${groups[joined].admin}|${joined}|${received.id}|${index}|${myName}`);   // addressed to the admin
    renderTablet();
}

// the leaderboard, below the question
function renderTabletBoard() {
    $("noScores").hidden = board.length > 0;
    $("board").hidden = board.length === 0;
    renderBoard($("board"), board, myId);
}

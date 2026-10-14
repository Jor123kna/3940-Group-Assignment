"use strict";

// The connection to Tomcat's built-in chat hub (the one from Chat.html), and the
// message plumbing that the admin and the tablet share.
//
// The hub does one thing: whatever one browser sends, every browser receives
// as "GuestN: <text>". So groups, questions and answers are all chat text, each
// message starting with a letter and with fields separated by "|":
//
//   H|<nonce>                                    hello: lets a browser learn its own GuestN name
//   G|<group>|<name>                             admin  -> all     "this group exists"
//   X|<group>                                    admin  -> all     "this group is closed"
//   J|<admin>|<group>|<who>|<my id>              tablet -> admin   "I joined your group"; <who> is the
//                                                                  chosen display name ("" = none), <my id>
//                                                                  stays the same when the connection (and
//                                                                  so the hub name) changes
//   N|<admin>|<group>|<who>                      tablet -> admin   "I changed my display name"
//   L|<admin>|<group>                            tablet -> admin   "I left your group"
//   Q|<group>|<id>|<picture>|<question>|<a1>|<a2>|...
//                                                admin  -> group   the question, taken from the
//                                                                  database; <picture> is a file
//                                                                  name or url, empty = none
//   A|<admin>|<group>|<id>|<answer no.>|<who>    tablet -> admin   the answer
//   R|<group>|<id>|<right answer no.>|<points>   admin  -> group   everybody answered: the right answer
//                                                                  (<points> is 1 if it counted for the
//                                                                  leaderboard, 0 if it was asked before)
//   B|<group>|<id>|<name>|<points>|<id>|...      admin  -> group   the leaderboard, best first
//
// Every browser receives every message, so "one to one" is by addressing: a
// tablet ignores everything that is not for the group it joined, and the admin
// only reads the messages addressed to its own name.
//
// admin.js and tablet.js each describe one role; lobby.js picks the role and
// plugs it into `role` below.

// The hub lives in Tomcat's "examples" webapp. To use another server, open the
// page as triviaUser.html?hub=host:port
const hub = new URLSearchParams(location.search).get("hub") || `${location.hostname || "localhost"}:8081`;
const CHAT_URL = `${location.protocol === "https:" ? "wss" : "ws"}://${hub}/examples/websocket/chat`;
const RETRY_MS = 3000;
const GIVE_UP_MS = 6000;   // no answer from the hub for this long = tell the user

// js/common.js and js/quiz.js must be deployed next to the lobby scripts
if (typeof $ !== "function" || typeof choiceButton !== "function") {
    document.getElementById("connection").textContent = "Missing js/common.js or js/quiz.js";
    throw new Error("the multiplayer page needs js/common.js and js/quiz.js");
}

const nonce = Math.random().toString(36).slice(2, 8);

let myName = "";          // account name, shown to the admin ("" for guests: the hub name is used)
let hubName = null;       // "Guest3": the name the hub gave this connection, learnt from our own hello
let webSocket = null;
let retryTimer = null;

// What this browser does with incoming messages; set by lobby.js to adminRole or tabletRole.
//   handlers: message letter -> function(sender, fields)
//   gone:     function(name), called when the hub says that someone disconnected
let role = { handlers: {}, gone() {} };

// ---------- small helpers ----------

// "|" and line breaks would break the message format
const clean = (text) => text.replace(/[|\r\n]+/g, " ").trim();
const newId = () => Math.random().toString(36).slice(2, 8);

// Who I am across reconnects: a new connection gets a new hub name, this id stays.
// It lets the admin keep my points when my tablet falls asleep and comes back.
const myId = (() => {
    try {
        let id = sessionStorage.getItem("lobbyId");
        if (!id) sessionStorage.setItem("lobbyId", id = newId());
        return id;
    } catch (e) {
        return newId();   // no storage: fine, only reconnects start from zero
    }
})();

// The leaderboard, as drawn on the admin's and on the tablets' screen.
//   rows: [{ id, name, points }], best first;  me: the id to highlight
function renderBoard(list, rows, me) {
    list.replaceChildren(...rows.map((row, i) => el("li", { className: [row.id === me && "me", i === 0 && row.points > 0 && "leader"].filter(Boolean).join(" ") },
        el("span", {}, row.name),
        el("strong", {}, `${row.points} pt${row.points === 1 ? "" : "s"}`)
    )));
}

function say(text) {
    setNotice($("notice"), text, "error");
}

function log(text) {
    $("log").value += text + "\n";
}

function setConnection(state) {
    const labels = { on: "Connected", off: "Offline, retrying...", try: "Connecting..." };
    $("connection").dataset.state = state;
    $("connection").textContent = labels[state];
}

// ---------- the WebSocket ----------

function connect() {
    clearTimeout(retryTimer);
    setConnection("try");

    const socket = webSocket = new WebSocket(CHAT_URL);
    const ifCurrent = (handler) => (event) => { if (socket === webSocket) handler(event); };
    const unreachable = () => say(`Cannot reach the chat hub at ${CHAT_URL}. Is Tomcat's "examples" webapp deployed there?`);

    // a hub that never answers is not an error event, so give up on our own
    const giveUp = setTimeout(() => {
        if (socket === webSocket && socket.readyState === WebSocket.CONNECTING) {
            unreachable();
            socket.close();
        }
    }, GIVE_UP_MS);

    socket.onopen = ifCurrent(() => { clearTimeout(giveUp); wsOpen(); });
    socket.onmessage = ifCurrent(wsGetMessage);
    socket.onclose = ifCurrent(() => { clearTimeout(giveUp); wsClose(); });
    socket.onerror = ifCurrent(() => { log("Error ..."); unreachable(); });
}

function wsOpen() {
    log("Connected ...");
    setConnection("on");
    say("");
    wsSendMessage(`H|${nonce}`);   // our own copy comes back with our hub name on it
}

function wsSendMessage(text) {
    webSocket.send(text);
    log(`Message sent to the server: ${text}`);
}

const isConnected = () => webSocket !== null && webSocket.readyState === WebSocket.OPEN && hubName !== null;

function wsGetMessage(event) {
    // the hub escapes < > & " so the text is safe to print; undo that for display
    const text = event.data
        .replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, '"').replace(/&amp;/g, "&");
    log(`Message received from the server: ${text}`);

    const left = /^(\S+) has disconnected\.$/.exec(text);   // the hub's own notice
    if (left) return role.gone(left[1]);

    const chat = /^(.+?): ([\s\S]*)$/.exec(text);   // "Guest3: H|abc"; "Guest3 has joined." does not match
    if (!chat) return;

    const [, sender, body] = chat;
    const [kind, ...fields] = body.split("|");

    if (kind === "H" && fields[0] === nonce) hubName = sender;   // that was our own hello

    if (Object.hasOwn(role.handlers, kind)) role.handlers[kind](sender, fields);
}

function wsClose() {
    log("Disconnect ...");
    setConnection("off");
    hubName = null;
    retryTimer = setTimeout(connect, RETRY_MS);   // tablets go to sleep: just come back
}

"use strict";

// Entry point of the multiplayer page. Loaded last, after
//   js/lobby/hub.js     the connection to the chat hub
//   js/lobby/admin.js   what a staff account sees and does
//   js/lobby/tablet.js  what everybody else sees and does

(async function start() {
    const session = await loadSession();
    const isAdmin = session.loggedIn && session.isStaff === true;

    myName = session.loggedIn ? clean(session.displayName || "") : "";
    role = isAdmin ? adminRole : tabletRole;

    $("adminView").hidden = !isAdmin;
    $("tabletView").hidden = isAdmin;
    if (isAdmin) loadQuizCategories();   // the quizzes the admin can ask come from the database
    else startTablet(session);           // restores a guest's chosen name
    connect();
})();

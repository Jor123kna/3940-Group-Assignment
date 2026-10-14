"use strict";

// Ask the server who is logged in, then show the matching buttons.
async function showSession() {
    const session = await loadSession();

    document.querySelectorAll("[data-auth]").forEach((element) => {
        element.hidden = (element.dataset.auth === "in") !== session.loggedIn;
    });
    $("who").textContent = session.displayName || "";
    document.querySelector(".topbar").classList.remove("loading");
}

$("logoutBtn").onclick = async () => {
    await logout();
    showSession();
};

showSession();

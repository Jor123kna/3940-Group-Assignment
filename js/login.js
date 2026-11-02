"use strict";

// Someone who is already logged in has no business here: send them home.
loadSession().then((session) => {
    if (session.loggedIn) location.href = "index.html";
});

$("loginForm").onsubmit = async (event) => {
    event.preventDefault();

    const name = $("name").value;
    const password = $("password").value;
    const error = $("error");

    if (name.trim() === "" || password === "") {
        setNotice(error, "Enter your name and password.", "error");
        return;
    }

    setNotice(error);
    $("submitBtn").disabled = true;

    try {
        await request("POST", "login", { name, password });
        location.href = "index.html";
    } catch (e) {
        $("submitBtn").disabled = false;
        setNotice(error, e.message, "error");
        $("password").value = "";
        $("password").focus();
    }
};

"use strict";

// Helpers shared by every page.

const $ = (id) => document.getElementById(id);

// Builds an element: el("button", { className: "btn", onclick: fn }, "text", otherElement)
function el(tag, props = {}, ...children) {
    const node = Object.assign(document.createElement(tag), props);
    node.append(...children.filter((child) => child !== false && child != null));
    return node;
}

// XMLHttpRequest wrapped in a Promise. `params` (an object or a list of
// [name, value] pairs) is sent as a form body. Resolves with the parsed JSON;
// rejects with an Error whose `status` is the HTTP status (0 = no answer).
// X-Requested-With is required by the server on every change (POST).
function request(method, url, params) {
    return new Promise((resolve, reject) => {
        const xhttp = new XMLHttpRequest();
        xhttp.open(method, url);
        xhttp.setRequestHeader("X-Requested-With", "XMLHttpRequest");
        if (params) {
            xhttp.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");
        }

        const fail = (message) => reject(Object.assign(new Error(message), { status: xhttp.status }));

        xhttp.onerror = () => fail("Could not reach the server.");
        xhttp.onload = () => {
            let data = null;
            try { data = JSON.parse(xhttp.responseText); } catch (e) { /* not JSON */ }

            if (xhttp.status !== 200) {
                fail((data && data.error) || `The server returned an error (status ${xhttp.status}).`);
            } else if (data === null) {
                fail("The server sent a response that could not be read.");
            } else {
                resolve(data);
            }
        };
        xhttp.send(params ? new URLSearchParams(params) : null);
    });
}

// Who is logged in? Any failure counts as "not logged in".
const loadSession = () => request("GET", "session").catch(() => ({ loggedIn: false }));

const logout = () => request("POST", "logout").catch(() => {});

// Fills a .notice element. kind: "ok" | "error" | "info" | "wait"; no text clears it.
function setNotice(element, text = "", kind = "") {
    element.textContent = text;
    element.dataset.kind = text ? kind : "";
}

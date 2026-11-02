"use strict";

// Question rendering shared by the solo and the multiplayer quiz.

// Shows the picture or video that belongs to a question inside `box` (a <div>),
// or hides the box when there is none. `media` is { type, url } or null.
// A url is an http(s) address (an image, a video file or a YouTube link) or a
// file name in the webapp's images folder.
const YOUTUBE_LINK = /^https?:\/\/(?:www\.|m\.)?(?:youtube\.com\/(?:watch\?(?:[^#]*&)?v=|embed\/|shorts\/)|youtu\.be\/)([\w-]{11})/i;
const VIDEO_FILE = /\.(?:mp4|webm|ogg|ogv|mov|m4v)(?:[?#].*)?$/i;

function showMedia(box, media) {
    const url = media && media.url ? String(media.url).trim() : "";
    const safe = /^https?:\/\/\S+$/i.test(url) || /^[\w.-]+$/.test(url);   // never javascript: and friends

    box.replaceChildren();
    box.hidden = !safe;
    if (!safe) return;

    const source = /^https?:\/\//i.test(url) ? url : "images/" + encodeURIComponent(url);
    const youtube = url.match(YOUTUBE_LINK);
    let node;

    if (youtube) {
        node = el("iframe", {
            src: `https://www.youtube-nocookie.com/embed/${youtube[1]}`,
            title: "Question video",
            allowFullscreen: true,
            loading: "lazy",
            referrerPolicy: "strict-origin-when-cross-origin"
        });
    } else if (media.type === "video" || VIDEO_FILE.test(url)) {
        node = el("video", { src: source, controls: true, preload: "metadata", playsInline: true });
    } else {
        node = el("img", { src: source, alt: "" });
    }

    node.onerror = () => { box.hidden = true; };   // missing file: just hide it
    box.append(node);
}

// One answer button; css/quiz.css adds its colour, shape and tick/cross.
function choiceButton(text) {
    return el("button", { type: "button", className: "choice" }, el("span", {}, text));
}

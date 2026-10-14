"use strict";

// Question rendering shared by the solo and the multiplayer quiz.

// Shows the picture that belongs to a question, or hides the <img> when there is none.
// A url is either absolute or a file in the webapp's images folder.
function showMedia(img, media) {
    const isImage = media && media.url && (media.type || "image") === "image";

    img.hidden = !isImage;
    if (!isImage) {
        img.removeAttribute("src");
        return;
    }

    img.onerror = () => { img.hidden = true; };   // missing file: just hide it
    img.src = /^https?:\/\//i.test(media.url) ? media.url : "images/" + encodeURIComponent(media.url);
}

// One answer button; css/quiz.css adds its colour, shape and tick/cross.
function choiceButton(text) {
    return el("button", { type: "button", className: "choice" }, el("span", {}, text));
}

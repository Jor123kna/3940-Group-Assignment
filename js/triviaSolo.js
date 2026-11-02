// Kahoot-style shapes for the four choice colours
const SHAPES = ["\u25B2", "\u25C6", "\u25CF", "\u25A0"];   // triangle, diamond, circle, square

// AJAX helpers (XMLHttpRequest). URLs are relative, so this works at
// /GroupAssignment1/ on any host or port.
function request(method, url, body, onSuccess, onError) {
    const xhttp = new XMLHttpRequest();
    xhttp.onreadystatechange = function () {
        if (this.readyState != 4) return;

        if (this.status == 200) {
            try {
                onSuccess(JSON.parse(this.responseText));
            } catch (e) {
                onError("The server sent a response that could not be read.");
            }
        } else {
            onError("The server returned an error (status " + this.status + ").");
        }
    };
    xhttp.open(method, url, true);
    if (body !== null) {
        xhttp.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");
    }
    xhttp.send(body);
}

function getJson(url, onSuccess, onError) {
    request("GET", url, null, onSuccess, onError);
}

function postForm(url, body, onSuccess, onError) {
    request("POST", url, body, onSuccess, onError);
}

function $(id) { return document.getElementById(id); }

function setStatus(text, isError) {
    const status = $("status");
    status.textContent = text;
    status.className = isError ? "status error" : "status";
    status.hidden = (text === "");
}

// Categories: one tab per category, along the top
function loadCategories() {
    getJson("categories", showCategories, function (message) {
        setStatus("Could not load categories. " + message, true);
    });
}

function showCategories(categories) {
    const holder = $("categories");
    holder.textContent = "";

    if (categories.length == 0) {
        setStatus("No categories found.", false);
        return;
    }

    setStatus("Pick a category to start.", false);

    categories.forEach(function (category) {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "cat";
        button.textContent = category.category_name;
        button.onclick = function () { selectCategory(button, category); };
        holder.appendChild(button);
    });
}

// Game state
//   questions : the questions in this category
//   index     : which question is on screen
//   results   : question_id -> { answerId, correct, correctAnswerId }
//   pending   : question_id -> true while an answer is being checked
let game = null;

function selectCategory(button, category) {
    document.querySelectorAll("#categories .cat").forEach(function (b) {
        b.classList.remove("selected");
    });
    button.classList.add("selected");

    // starting a new category throws away the old game
    const thisGame = {
        category: category, questions: [], index: 0,
        results: {}, pending: {}
    };
    game = thisGame;

    $("game").hidden = true;
    $("results").hidden = true;
    setStatus("Loading questions...", false);

    getJson(
        "questions?category_id=" + encodeURIComponent(category.category_id),
        function (questions) {
            if (game !== thisGame) return;   // user already picked another category

            if (questions.length == 0) {
                setStatus("There are no questions in " + category.category_name +
                    " yet.", false);
                return;
            }

            thisGame.questions = questions;
            setStatus("", false);
            renderQuestion();
        },
        function (message) {
            if (game !== thisGame) return;
            setStatus("Could not load questions. " + message, true);
        }
    );
}

// Show the current question
function mediaSource(url) {
    if (/^https?:\/\//i.test(url)) return url;
    return "images/" + encodeURIComponent(url);   // file in the webapp's images folder
}

function renderQuestion() {
    const q = game.questions[game.index];
    const res = game.results[q.question_id];
    const last = (game.index == game.questions.length - 1);

    $("results").hidden = true;
    $("game").hidden = false;

    $("progress").textContent =
        game.category.category_name + " \u2022 Question " +
        (game.index + 1) + " of " + game.questions.length;

    $("questionText").textContent = q.question_text;

    // optional picture that belongs to the question
    const img = $("questionMedia");
    if (q.media && q.media.type == "image") {
        img.onerror = function () { img.hidden = true; }; // missing file: just hide it
        img.src = mediaSource(q.media.url);
        img.hidden = false;
    } else {
        img.hidden = true;
        img.removeAttribute("src");
    }

    // the choices
    const choices = $("choices");
    choices.textContent = "";
    choices.className = res ? "choices answered" : "choices";

    q.answers.forEach(function (answer, i) {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "choice c" + (i % 4);

        const shape = document.createElement("span");
        shape.className = "shape";
        shape.textContent = SHAPES[i % 4];

        const label = document.createElement("span");
        label.className = "label";
        label.textContent = answer.answer_name;

        const mark = document.createElement("span");
        mark.className = "mark";

        button.appendChild(shape);
        button.appendChild(label);
        button.appendChild(mark);

        if (res) {
            button.disabled = true;
            if (answer.answer_id == res.correctAnswerId) {
                button.classList.add("correct");
                mark.textContent = "\u2713";
            } else if (answer.answer_id == res.answerId) {
                button.classList.add("wrong");
                mark.textContent = "\u2717";
            }
        } else {
            button.disabled = !!game.pending[q.question_id];
            button.onclick = function () { chooseAnswer(q, answer); };
        }

        choices.appendChild(button);
    });

    // feedback line
    const feedback = $("feedback");
    feedback.className = "feedback";
    feedback.textContent = "";
    if (res) {
        if (res.correct) {
            feedback.classList.add("correct");
            feedback.textContent = "\u2713 Correct!";
        } else {
            const right = q.answers.filter(function (a) {
                return a.answer_id == res.correctAnswerId;
            })[0];
            feedback.classList.add("wrong");
            feedback.textContent = "\u2717 Not quite" +
                (right ? " \u2014 the answer is " + right.answer_name : "");
        }
    }

    // Back is available after the first question.
    // Next only unlocks once this question has been answered.
    $("backBtn").disabled = (game.index == 0);
    $("nextBtn").disabled = !res;
    $("nextBtn").textContent = last ? "Finish" : "Next";
}

// Player picks a choice -> ask the server whether it was right
function chooseAnswer(q, answer) {
    const thisGame = game;

    if (thisGame.results[q.question_id] || thisGame.pending[q.question_id]) return;

    thisGame.pending[q.question_id] = true;
    document.querySelectorAll("#choices .choice").forEach(function (b) {
        b.disabled = true;     // no double-clicks while we wait
    });

    postForm(
        "answer",
        "answer_id=" + encodeURIComponent(answer.answer_id),
        function (result) {
            delete thisGame.pending[q.question_id];
            thisGame.results[q.question_id] = {
                answerId: answer.answer_id,
                correct: result.correct,
                correctAnswerId: result.correct_answer_id
            };
            if (game === thisGame && thisGame.questions[thisGame.index] === q) {
                renderQuestion();
            }
        },
        function (message) {
            delete thisGame.pending[q.question_id];
            if (game === thisGame && thisGame.questions[thisGame.index] === q) {
                renderQuestion();    // re-enable the choices so they can try again
                const feedback = $("feedback");
                feedback.className = "feedback error";
                feedback.textContent = "Could not check your answer. " + message;
            }
        }
    );
}

// Back / Next / results
$("backBtn").onclick = function () {
    if (game && game.index > 0) {
        game.index--;
        renderQuestion();
    }
};

$("nextBtn").onclick = function () {
    if (!game) return;
    const q = game.questions[game.index];
    if (!game.results[q.question_id]) return; // must answer first

    if (game.index < game.questions.length - 1) {
        game.index++;
        renderQuestion();
    } else {
        showResults();
    }
};

function showResults() {
    let correct = 0;
    game.questions.forEach(function (q) {
        const r = game.results[q.question_id];
        if (r && r.correct) correct++;
    });

    $("game").hidden = true;
    $("results").hidden = false;
    $("score").textContent = correct + " / " + game.questions.length;
    $("scoreText").textContent =
        "You got " + correct + " of " + game.questions.length +
        " right in " + game.category.category_name + ". " +
        "Play again or pick another category above.";
}

$("againBtn").onclick = function () {
    game.index = 0;
    game.results = {};
    game.pending = {};
    renderQuestion();
};

loadCategories();
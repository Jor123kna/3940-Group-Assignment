/**
 * Class for testing various elements and systems on the web app.
 *
 * This includes testing for UI components, API endpoints, and navigation.
 *
 * @information
 * To run the tests, first, paste this class code into the developer console of an open web page of the application.
 * Then paste the contents of the other test files (e.g., ../tests/button-test.js) into the console.
 * The tests will execute automatically and log results to the console.
 */
class WebTest {
  static APP_URL = "http://localhost:8081/3940-Group-Assignment";

  static currentWindow = null;
  static currentPage = null;
  static queue = Promise.resolve();

  /**
   * Wait until a window has finished loading.
   *
   * @private
   */
  static _waitForLoad(testWindow) {
    if (testWindow.document.readyState === "complete") return Promise.resolve();

    return new Promise((resolve, reject) => {
      const onLoad = () => {
        cleanup();
        resolve();
      };

      const onError = () => {
        cleanup();
        reject(new Error("Test window failed to load."));
      };

      const cleanup = () => {
        testWindow.removeEventListener("load", onLoad);
        testWindow.removeEventListener("error", onError);
      };

      testWindow.addEventListener("load", onLoad, { once: true });
      testWindow.addEventListener("error", onError, { once: true });

      if (testWindow.document.readyState === "complete") {
        cleanup();
        resolve();
      }
    });
  }

  /**
   * Close the current window and wait for it to close.
   *
   * @private
   */
  static async _closeCurrentWindow() {
    const oldWindow = WebTest.currentWindow;
    WebTest.currentWindow = null;
    WebTest.currentPage = null;

    if (!oldWindow || oldWindow.closed) return;

    oldWindow.close();

    while (!oldWindow.closed) {
      await new Promise((resolve) => setTimeout(resolve, 50));
    }
  }

  /**
   * Get a window for a test page.
   * Reuse the current window if the requested page is unchanged.
   *
   * @private
   */
  static async _getTestWindow(page = "") {
    const url = WebTest.APP_URL + page;

    if (
      WebTest.currentWindow &&
      !WebTest.currentWindow.closed &&
      WebTest.currentPage === url
    ) {
      return WebTest.currentWindow;
    }

    // Different page: close the previous window before opening another.
    await WebTest._closeCurrentWindow();

    const testWindow = window.open(
      url,
      "_blank",
      "popup=yes,width=1000,height=800",
    );

    if (!testWindow) {
      throw new Error(
        "Popup blocked. Allow popups for this site and try again.",
      );
    }

    WebTest.currentWindow = testWindow;
    WebTest.currentPage = url;
    await WebTest._waitForLoad(testWindow);

    return testWindow;
  }

  /**
   * Compile test code and inject helper functions.
   *
   * @private
   */
  static _compileScript(scriptCode) {
    return scriptCode.replaceAll("WebTest.", "").replace(
      "{",
      `{
          function ${WebTest.clickMouse.toString()}
          function ${WebTest.getElement.toString()}
        `,
    );
  }

  /**
   * Execute a test in the specified window. Resolves to true or false.
   *
   * @private
   */
  static _newTestScript(testWindow, scriptCode) {
    return new Promise((resolve, reject) => {
      if (!testWindow || testWindow.closed) {
        reject(new Error("Target window is unavailable."));
        return;
      }

      const tDocument = testWindow.document;
      const script = tDocument.createElement("script");
      const expectedOrigin = new URL(WebTest.APP_URL).origin;

      const cleanup = () => {
        window.removeEventListener("message", listener);
        script.remove();
      };

      const listener = (event) => {
        if (event.source !== testWindow) return;
        if (event.origin !== expectedOrigin) return;
        if (event.data?.type !== "webtest-result") return;

        cleanup();

        if (event.data.error) reject(new Error(event.data.error));
        else resolve(event.data.pass === true);
      };

      window.addEventListener("message", listener);

      try {
        const compiledScript = WebTest._compileScript(scriptCode);

        script.textContent = `
          (() => {
            try {
              const result = (${compiledScript})();
              window.opener.postMessage({ type: "webtest-result", pass: result === true }, "*");
            } catch (err) {
              window.opener.postMessage({ type: "webtest-result", pass: false, error: String(err) }, "*");
            }
          })();
        `;

        tDocument.body.appendChild(script);
      } catch (error) {
        cleanup();
        reject(error);
      }
    });
  }

  /**
   * Helper available to injected test functions.
   *
   * @private
   */
  static getElement(selector) {
    return document.querySelector(selector);
  }

  /**
   * Helper available to injected test functions.
   *
   * @private
   */
  static clickMouse(x, y) {
    let element = document.elementFromPoint(x, y);
    if (!element) element = document.body;

    element.dispatchEvent(
      new MouseEvent("click", {
        view: window,
        bubbles: true,
        cancelable: true,
        clientX: x,
        clientY: y,
      }),
    );
  }

  /**
   * Runs a test.
   *
   * @param {String} name Name of the test
   * @param {String} [page] Optional page path to open in the test window
   * @param {Function} func The test function to execute which should return a boolean indicating pass/fail
   */
  static test(...args) {
    const name = args[0];

    let page = "";
    let func;

    if (args.length === 2 && typeof args[1] === "function") {
      func = args[1];
    } else if (args.length === 3 && typeof args[2] === "function") {
      page = args[1];
      func = args[2];
    }

    // Append this test to the queue.
    const result = WebTest.queue.then(async () => {
      try {
        const testWindow = await WebTest._getTestWindow(page);
        const passed = await WebTest._newTestScript(
          testWindow,
          func.toString(),
        );

        if (passed) console.log(`[+] Test: "${name}" PASS`);
        else console.error(`[−] Test: "${name}" FAIL`);

        return passed;
      } catch (error) {
        console.error(`[−] Test: "${name}" ERROR`, error);
        return false;
      }
    });

    // Keep the queue running even if a test fails.
    WebTest.queue = result.then(
      () => undefined,
      () => undefined,
    );

    return result;
  }
}

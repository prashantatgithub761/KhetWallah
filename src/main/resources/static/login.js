(() => {
    "use strict";

    const T = key => window.KhetI18n?.t(key) || key;
    const form = document.getElementById("login-form");
    const password = document.getElementById("login-password");
    const toggle = document.getElementById("toggle-password");
    const button = document.getElementById("login-submit");
    const message = document.getElementById("login-message");
    const feedback = document.getElementById("login-feedback");
    const csrfInput = document.getElementById("login-csrf");
    let submitting = false;

    const params = new URLSearchParams(window.location.search);
    document.getElementById("login-return").value = params.get("returnTo") || params.get("next") || "";
    if (params.has("error")) {
        feedback.hidden = false;
        feedback.classList.add("feedback-error");
        feedback.textContent = T("We couldn\u2019t sign you in. Check your email and password. If the problem continues, your account may be unavailable.");
    } else if (params.has("logout")) {
        feedback.hidden = false;
        feedback.textContent = T("You have signed out. Sign in whenever you are ready to return.");
    }

    toggle.addEventListener("click", () => {
        const visible = password.type === "password";
        password.type = visible ? "text" : "password";
        toggle.setAttribute("aria-pressed", String(visible));
        toggle.textContent = visible ? T("Hide password") : T("Show password");
    });

    function reset() {
        submitting = false;
        button.disabled = false;
        button.textContent = T("Sign in \u2192");
        form.removeAttribute("aria-busy");
        csrfInput.value = "";
    }

    document.addEventListener("khet:languagechange", () => {
        button.textContent=T(submitting ? "Signing in…" : "Sign in →");
        toggle.textContent=T(password.type === "password" ? "Show password" : "Hide password");
    });
    // A page restored from the browser's back/forward cache must be usable again.
    window.addEventListener("pageshow", reset);

    form.addEventListener("submit", async event => {
        event.preventDefault();
        if (submitting || !form.reportValidity()) return;
        submitting = true;
        button.disabled = true;
        button.textContent = T("Signing in\u2026");
        form.setAttribute("aria-busy", "true");
        message.classList.remove("feedback-error");
        message.textContent = T("Preparing your secure sign-in\u2026");
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 15000);

        try {
            const response = await fetch("/api/csrf", {
                credentials: "same-origin", cache: "no-store", redirect: "error",
                headers: {Accept: "application/json"}, signal: controller.signal
            });
            if (!response.ok) throw new Error("We couldn’t prepare sign-in. Refresh this page and try again.");
            const csrf = await response.json();
            if (!csrf || typeof csrf.token !== "string" || !csrf.token) {
                throw new Error("We couldn’t prepare sign-in. Refresh this page and try again.");
            }
            csrfInput.value = csrf.token;
            message.textContent = T("Signing in\u2026");
            // Keep the normal Spring Security POST and saved-request redirect.
            // Passwords and security tokens are never written to browser storage.
            HTMLFormElement.prototype.submit.call(form);
        } catch (error) {
            reset();
            message.classList.add("feedback-error");
            message.textContent = T(error.name === "AbortError"
                ? "KhetWallah took too long to respond. Check your connection and try again."
                : error instanceof TypeError
                    ? "We couldn’t reach KhetWallah. Check your connection and try again."
                    : error.message);
        } finally {
            clearTimeout(timeout);
        }
    });
})();

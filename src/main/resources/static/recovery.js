(() => {
    "use strict";
    const t = (key, params) => window.KhetI18n?.t(key, params) || key;
    const element = id => document.getElementById(id);
    const startForm = element("recovery-start-form");
    const finishForm = element("recovery-finish-form");
    const section = element("recovery-code-section");
    const email = element("recovery-email");
    const password = element("recovery-password");
    const confirmPassword = element("recovery-confirm");
    const message = element("recovery-message");
    const resend = element("recovery-resend");
    const startButton = element("recovery-start");
    const finishButton = element("recovery-finish");
    const changeEmail = element("recovery-change-email");
    let busy = false;
    let normalizedEmail = "";
    let resendAt = 0;
    let complete = false;

    const fields = {
        email: [email, element("recovery-email-error")],
        code: [element("recovery-code"), element("recovery-code-error")],
        newPassword: [password, element("recovery-password-error")]
    };
    function clearErrors() {
        for (const [input, error] of Object.values(fields)) {
            input.removeAttribute("aria-invalid");
            error.textContent = "";
        }
    }
    function tell(text, error = false) {
        message.textContent = t(text);
        message.classList.toggle("feedback-error", error);
    }
    function updateButtons() {
        const seconds = Math.max(0, Math.ceil((resendAt - Date.now()) / 1000));
        resend.disabled = busy || seconds > 0 || complete;
        resend.textContent = seconds > 0 ? t("recoveryCooldown",{seconds}) : t("Request another code");
        startButton.disabled = busy || !!normalizedEmail || complete;
        finishButton.disabled = busy || complete;
        changeEmail.disabled = busy || complete;
    }
    function setBusy(value, finish = false) {
        busy = value;
        startForm.setAttribute("aria-busy", String(value));
        finishForm.setAttribute("aria-busy", String(value));
        startButton.textContent = value && !finish ? t("Requesting code…") : t("Request recovery code →");
        finishButton.textContent = value && finish ? t("Saving password…") : t("Save new password →");
        updateButtons();
    }
    async function post(path, body) {
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), 20000);
        try {
            const options = {credentials: "same-origin", cache: "no-store", redirect: "error", signal: controller.signal};
            const csrfResponse = await fetch("/api/csrf", {...options, headers: {Accept: "application/json"}});
            if (!csrfResponse.ok) throw new Error("We could not prepare this request. Refresh the page and try again.");
            const csrf = await csrfResponse.json();
            if (!csrf || typeof csrf.token !== "string" || typeof csrf.headerName !== "string") {
                throw new Error("We could not prepare this request. Refresh the page and try again.");
            }
            const response = await fetch(path, {...options, method: "POST", headers: {
                Accept: "application/json", "Content-Type": "application/json", [csrf.headerName]: csrf.token
            }, body: JSON.stringify(body)});
            if (!response.ok) {
                const data = await response.json().catch(() => ({}));
                if (data.fieldErrors) {
                    for (const [name, text] of Object.entries(data.fieldErrors)) {
                        if (fields[name] && typeof text === "string") {
                            fields[name][0].setAttribute("aria-invalid", "true");
                            fields[name][1].textContent = text;
                        }
                    }
                }
                throw new Error(response.status === 403
                    ? "Your security session changed. Refresh the page before trying again."
                    : response.status === 429
                        ? "Too many requests from this connection. Please try again after one hour."
                        : data.message || "This request could not be completed. Please try again later.");
            }
            return response.status === 204 ? null : response.json();
        } finally { clearTimeout(timer); }
    }
    function failure(error, finishing) {
        const interrupted = error.name === "AbortError" || error instanceof TypeError;
        tell(interrupted
            ? finishing
                ? "The connection was interrupted. Your password may have changed; try signing in with the new password before retrying."
                : "The connection was interrupted. A code may have been sent; check your inbox and wait a minute before requesting another."
            : error.message, true);
        message.focus();
    }
    async function requestCode() {
        if (busy || complete) return;
        clearErrors();
        setBusy(true);
        try {
            const reply = await post("/api/auth/recovery/start", {email: normalizedEmail || email.value.trim().toLowerCase()});
            normalizedEmail = email.value.trim().toLowerCase();
            email.value = normalizedEmail;
            email.readOnly = true;
            resendAt = Date.now() + 60000;
            section.hidden = false;
            element("recovery-target").textContent = t("recoveryTarget",{email:normalizedEmail});
            tell(reply.message);
            element("recovery-code").value = "";
            element("recovery-code").focus();
        } catch (error) {
            // Keep the code form usable after an ambiguous start; it may have reached the server.
            if (error.name === "AbortError" || error instanceof TypeError) {
                normalizedEmail = email.value.trim().toLowerCase();
                email.readOnly = true;
                section.hidden = false;
                element("recovery-target").textContent = t("recoveryTargetShort",{email:normalizedEmail});
                resendAt = Date.now() + 60000;
            }
            failure(error, false);
        } finally { setBusy(false); }
    }
    startForm.addEventListener("submit", event => {
        event.preventDefault();
        if (startForm.reportValidity()) requestCode();
    });
    resend.addEventListener("click", () => {
        if (Date.now() >= resendAt) requestCode();
    });
    changeEmail.addEventListener("click", () => {
        if (busy || complete) return;
        normalizedEmail = "";
        resendAt = 0;
        email.readOnly = false;
        finishForm.reset();
        password.type = confirmPassword.type = "password";
        element("recovery-toggle").setAttribute("aria-pressed", "false");
        element("recovery-toggle").textContent = t("Show passwords");
        section.hidden = true;
        clearErrors();
        tell("");
        updateButtons();
        email.focus();
    });
    const updateMatch = () => confirmPassword.setCustomValidity(
        confirmPassword.value && password.value !== confirmPassword.value ? t("The passwords do not match.") : "");
    password.addEventListener("input", updateMatch);
    confirmPassword.addEventListener("input", updateMatch);
    element("recovery-toggle").addEventListener("click", () => {
        const show = password.type === "password";
        password.type = confirmPassword.type = show ? "text" : "password";
        element("recovery-toggle").setAttribute("aria-pressed", String(show));
        element("recovery-toggle").textContent = show ? t("Hide passwords") : t("Show passwords");
    });
    finishForm.addEventListener("submit", async event => {
        event.preventDefault();
        updateMatch();
        if (busy || complete || !normalizedEmail || !finishForm.reportValidity()) return;
        clearErrors();
        setBusy(true, true);
        try {
            await post("/api/auth/recovery/finish", {email: normalizedEmail,
                code: element("recovery-code").value, newPassword: password.value});
            complete = true;
            finishForm.reset();
            section.hidden = true;
            startForm.hidden = true;
            tell("Your password has been reset. Previous sessions have ended. Sign in with your new password to continue.");
            element("recovery-sign-in").textContent = t("Sign in with your new password →");
            message.focus();
        } catch (error) { failure(error, true); }
        finally { setBusy(false); }
    });
    let interval = setInterval(updateButtons, 1000);
    window.addEventListener("pageshow", () => {
        if (interval === null) interval = setInterval(updateButtons, 1000);
        updateButtons();
    });
    window.addEventListener("pagehide", () => {
        clearInterval(interval);
        interval = null;
        password.value = confirmPassword.value = element("recovery-code").value = "";
    });
    document.addEventListener("khet:languagechange", () => {
        updateButtons();
        if (!busy) { startButton.textContent=t("Request recovery code →"); finishButton.textContent=t("Save new password →"); }
        element("recovery-toggle").textContent = t(password.type === "password" ? "Show passwords" : "Hide passwords");
        if (normalizedEmail) element("recovery-target").textContent = t("recoveryTarget",{email:normalizedEmail});
        updateMatch();
    });
    updateButtons();
})();

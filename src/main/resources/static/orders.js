(() => {
    "use strict";
    const farmerView = document.body.dataset.ordersView === "farmer";
    const container = document.getElementById("orders");
    const message = document.getElementById("message");
    const loginLink = document.getElementById("login-link");
    const previous = document.getElementById("previous");
    const next = document.getElementById("next");
    const refresh = document.getElementById("refresh");
    const pageLabel = document.getElementById("page-label");
    const currency = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" });
    let currentPage = 0;
    let totalPages = 0;
    let busy = false;

    function text(parent, tag, value, className) {
        const element = document.createElement(tag);
        element.textContent = value;
        if (className) element.className = className;
        parent.appendChild(element);
        return element;
    }

    function setBusy(value) {
        busy = value;
        previous.disabled = value || currentPage === 0;
        next.disabled = value || currentPage + 1 >= totalPages;
        refresh.disabled = value;
        container.setAttribute("aria-busy", String(value));
        container.querySelectorAll("button, input, textarea")
            .forEach(element => element.disabled = value);
    }

    async function request(url, options = {}) {
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), 15000);
        try {
            const response = await fetch(url, {
                ...options,
                credentials: "same-origin",
                redirect: "manual",
                signal: controller.signal,
                headers: { Accept: "application/json", ...options.headers }
            });
            if (response.type === "opaqueredirect" || response.status === 401) {
                loginLink.hidden = false;
                throw new Error("Please sign in again.");
            }
            if (!response.ok) {
                const error = await response.json().catch(() => ({}));
                const fields = Object.values(error.fieldErrors || {}).join(" ");
                throw new Error(fields || error.message || `Request failed (${response.status}).`);
            }
            return response.status === 204 ? null : await response.json();
        } catch (error) {
            if (error.name === "AbortError" || error instanceof TypeError) {
                throw new Error("Connection problem. Refresh to check the latest order status before retrying.");
            }
            throw error;
        } finally {
            clearTimeout(timer);
        }
    }

    async function loadOrders(page, successMessage = "") {
        setBusy(true);
        loginLink.hidden = true;
        message.textContent = "Loading orders…";
        try {
            const endpoint = farmerView ? "/api/me/received-orders" : "/api/me/orders";
            const data = await request(`${endpoint}?page=${page}&size=6`);
            if (page > 0 && data.content.length === 0) {
                await loadOrders(0, successMessage);
                return;
            }
            currentPage = data.page;
            totalPages = data.totalPages;
            container.replaceChildren();
            data.content.forEach(renderOrder);
            pageLabel.textContent = totalPages ? `Page ${currentPage + 1} of ${totalPages}` : "No pages";
            message.textContent = successMessage || (data.totalElements
                ? `${data.totalElements} order(s).`
                : farmerView ? "You haven't received any orders yet." : "You haven't placed any orders yet.");
        } catch (error) {
            message.textContent = error.message;
        } finally {
            setBusy(false);
        }
    }

    async function performAction(orderId, action, payload) {
        if (busy) return;
        const questions = {
            accept: `Accept order #${orderId}? You can then set its pickup details.`,
            reject: `Reject order #${orderId} and restore its reserved stock?`,
            cancel: `Cancel order #${orderId}?`,
            complete: `Confirm you have actually collected the produce for order #${orderId}?`
        };
        if (action !== "pickup" && !window.confirm(questions[action])) return;
        setBusy(true);
        loginLink.hidden = true;
        message.textContent = "Saving…";
        try {
            const csrf = await request("/api/csrf");
            const headers = { [csrf.headerName]: csrf.token };
            if (payload) headers["Content-Type"] = "application/json";
            await request(`/api/orders/${orderId}/${action}`, {
                method: "POST",
                headers,
                ...(payload ? { body: JSON.stringify(payload) } : {})
            });
            const messages = {
                accept: "Order accepted. Enter the pickup time and meeting instructions below.",
                reject: "Order rejected and reserved stock restored.",
                cancel: "Order cancelled and reserved stock restored.",
                pickup: "Pickup details saved. The buyer can now see them in My Orders.",
                complete: "Collection confirmed. Your order is completed."
            };
            await loadOrders(currentPage, messages[action]);
        } catch (error) {
            message.textContent = error.message;
        } finally {
            setBusy(false);
        }
    }

    function addAction(parent, order, action, label) {
        const button = text(parent, "button", label);
        button.type = "button";
        button.addEventListener("click", () => performAction(order.id, action));
    }

    function localDateTimeValue(instant) {
        const date = new Date(instant);
        const pad = number => String(number).padStart(2, "0");
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
    }

    function addPickupForm(card, order) {
        const form = document.createElement("form");
        form.className = "pickup-form";
        const timeLabel = text(form, "label", "Pickup date and time");
        const time = document.createElement("input");
        time.type = "datetime-local";
        time.required = true;
        time.name = "pickupAt";
        time.value = order.pickupAt ? localDateTimeValue(order.pickupAt) : "";
        timeLabel.appendChild(time);
        text(form, "p", `Times use your device's timezone (${Intl.DateTimeFormat().resolvedOptions().timeZone}).`, "pickup-note");
        const instructionsLabel = text(form, "label", "Meeting location and instructions");
        const instructions = document.createElement("textarea");
        instructions.name = "pickupInstructions";
        instructions.required = true;
        instructions.maxLength = 500;
        instructions.rows = 3;
        instructions.placeholder = "Example: Main gate of Jani village market, beside the water tank.";
        instructions.value = order.pickupInstructions || "";
        instructionsLabel.appendChild(instructions);
        const submit = text(form, "button", order.pickupAt ? "Update pickup details" : "Save pickup details");
        submit.type = "submit";
        form.addEventListener("submit", event => {
            event.preventDefault();
            if (busy || !form.reportValidity()) return;
            const pickup = new Date(time.value);
            if (!Number.isFinite(pickup.getTime()) || pickup.getTime() <= Date.now()) {
                message.textContent = "Choose a pickup time in the future.";
                return;
            }
            if (!instructions.value.trim()) {
                message.textContent = "Enter the pickup location and instructions.";
                return;
            }
            performAction(order.id, "pickup", {
                pickupAt: pickup.toISOString(),
                pickupInstructions: instructions.value.trim()
            });
        });
        card.appendChild(form);
    }

    function renderOrder(order) {
        const card = document.createElement("article");
        text(card, "h2", `Order #${order.id}`);
        text(card, "p", `Listing ID: ${order.listingId}`);
        text(card, "p", `Quantity: ${order.quantityKg} kg`);
        text(card, "p", `Agreed price: ${currency.format(order.pricePerKg)} / kg`);
        text(card, "p", `Total: ${currency.format(order.totalAmount)}`);
        text(card, "p", `Status: ${order.status}`);
        if (order.pickupAt) {
            text(card, "p", `Pickup: ${new Date(order.pickupAt).toLocaleString(undefined, {
                dateStyle: "medium", timeStyle: "short"
            })} (your local time)`);
            text(card, "p", order.pickupInstructions || "", "pickup-details");
        }
        const actions = document.createElement("div");
        actions.className = "order-actions";
        card.appendChild(actions);
        if (order.status === "PENDING") {
            if (farmerView) {
                addAction(actions, order, "accept", "Accept");
                addAction(actions, order, "reject", "Reject");
            } else {
                addAction(actions, order, "cancel", "Cancel order");
            }
        }
        if (order.status === "ACCEPTED") {
            if (farmerView) {
                addPickupForm(card, order);
                text(card, "p", "The buyer confirms collection after receiving the produce.", "pickup-note");
            } else if (order.pickupAt && order.pickupInstructions) {
                addAction(actions, order, "complete", "I have collected my order");
            } else {
                text(card, "p", "Waiting for the farmer to set pickup details.");
            }
        }
        container.appendChild(card);
    }

    previous.addEventListener("click", () => { if (!busy) loadOrders(currentPage - 1); });
    next.addEventListener("click", () => { if (!busy) loadOrders(currentPage + 1); });
    refresh.addEventListener("click", () => { if (!busy) loadOrders(currentPage); });
    loadOrders(0);
})();

(() => {
    "use strict";
    const t = (key, params) => window.KhetI18n?.t(key, params) || key;
    const farmerView = document.body.dataset.ordersView === "farmer";
    const container = document.getElementById("orders");
    const message = document.getElementById("message");
    const loginLink = document.getElementById("login-link");
    const previous = document.getElementById("previous");
    const next = document.getElementById("next");
    const refresh = document.getElementById("refresh");
    const pageLabel = document.getElementById("page-label");
    const currency = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" });
    const requestedOrder = new URLSearchParams(location.search).get("orderId");
    const singleOrder = requestedOrder && /^[1-9]\d{0,17}$/.test(requestedOrder) ? requestedOrder : null;
    if (singleOrder) {
        document.body.classList.add("single-order-view");
        const back = document.createElement("a");
        back.className = "button-link secondary single-order-back";
        back.href = farmerView ? "/received-orders.html" : "/my-orders.html";
        back.textContent = "← View all orders";
        message.insertAdjacentElement("beforebegin", back);
    }
    const actionDialog = document.getElementById("order-action-dialog");
    const confirmButton = document.getElementById("confirm-order-action");
    let confirmationResolve = null;
    let currentPage = 0;
    let totalPages = 0;
    let busy = false;

    function finishConfirmation(confirmed) {
        const resolve = confirmationResolve;
        confirmationResolve = null;
        if (actionDialog.open) actionDialog.close();
        if (resolve) resolve(confirmed);
    }
    document.getElementById("cancel-order-action").addEventListener("click", () => finishConfirmation(false));
    document.getElementById("close-order-action").addEventListener("click", () => finishConfirmation(false));
    confirmButton.addEventListener("click", () => finishConfirmation(true));
    actionDialog.addEventListener("close", () => finishConfirmation(false));

    function confirmOrderAction(orderId, action, question) {
        if (actionDialog.open) return Promise.resolve(false);
        const titles = {accept: "Accept this request?", reject: "Decline this request?",
            cancel: "Cancel this request?", complete: "Have you collected your produce?"};
        const labels = {accept: "Accept request", reject: "Decline request",
            cancel: "Cancel request", complete: "Yes, I have collected it"};
        document.getElementById("order-action-title").textContent = t(titles[action]);
        document.getElementById("order-action-description").textContent = t(question);
        document.getElementById("order-action-reference").textContent = t("orderReference", {id:orderId});
        confirmButton.textContent = t(labels[action]);
        return new Promise(resolve => {
            confirmationResolve = resolve;
            actionDialog.showModal();
            document.getElementById("cancel-order-action").focus();
        });
    }

    function text(parent, tag, value, className) {
        const element = document.createElement(tag);
        element.textContent = value;
        if (className) element.className = className;
        parent.appendChild(element);
        return element;
    }

    function ui(parent, tag, key, className) {
        const result = text(parent, tag, "", className);
        const copy = document.createElement("span");
        copy.dataset.i18n = key;
        copy.textContent = t(key);
        result.appendChild(copy);
        return result;
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

    async function loadOrders(page, successMessage = "", focusOrderId = null) {
        setBusy(true);
        loginLink.hidden = true;
        message.textContent = t("Loading orders…");
        try {
            const endpoint = farmerView ? "/api/me/received-orders" : "/api/me/orders";
            const data = singleOrder
                ? {content:[await request(`/api/me/orders/${singleOrder}`)], page:0, totalPages:1, totalElements:1}
                : await request(`${endpoint}?page=${page}&size=6`);
            if (page > 0 && data.content.length === 0) {
                await loadOrders(0, successMessage, focusOrderId);
                return;
            }
            currentPage = data.page;
            totalPages = data.totalPages;
            container.replaceChildren();
            data.content.forEach(renderOrder);
            pageLabel.textContent = totalPages ? t("pageOf",{page:currentPage+1,total:totalPages}) : t("No pages");
            message.textContent = successMessage ? t(successMessage) : (data.totalElements
                ? `${data.totalElements} ${data.totalElements === 1 ? "order" : "orders"}. Check each order for its next step.`
                : farmerView ? "You haven't received any orders yet." : "You haven't placed any orders yet.");
            if (!data.content.length) {
                const empty = document.createElement("div");
                empty.className = "orders-empty";
                ui(empty, "h2", farmerView ? "Your next buyer starts here." : "Your first harvest is waiting.");
                ui(empty, "p", farmerView ? "Post an available crop so buyers can send you an order request."
                    : "Browse crops, choose a quantity, and send a request to a farmer.");
                const link = ui(empty, "a", farmerView ? "Post a crop →" : "Browse crops →", "button-link");
                link.href = farmerView ? "/create-listing.html" : "/";
                container.appendChild(empty);
            }
        } catch (error) {
            container.replaceChildren();
            currentPage = 0; totalPages = 0; pageLabel.textContent = "";
            message.textContent = error.message;
        } finally {
            setBusy(false);
            if (focusOrderId != null) {
                const target = document.getElementById(`order-${focusOrderId}`)?.querySelector(".order-next-step") || message;
                target.tabIndex = -1;
                target.focus({ preventScroll: true });
            }
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
        if (action !== "pickup" && !await confirmOrderAction(orderId, action, questions[action])) return;
        if (busy) return;
        setBusy(true);
        loginLink.hidden = true;
        message.textContent = t("Saving…");
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
            await loadOrders(currentPage, messages[action], orderId);
        } catch (error) {
            message.textContent = error.message;
        } finally {
            setBusy(false);
        }
    }

    function addAction(parent, order, action, label) {
        const button = ui(parent, "button", label);
        button.type = "button";
        button.setAttribute("aria-label", `${label} for order #${order.id}`);
        if (action === "cancel" || action === "reject") button.className = "secondary";
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
        form.setAttribute("aria-label", `Pickup details for order #${order.id}`);
        const timeLabel = ui(form, "label", "Pickup date and time");
        const time = document.createElement("input");
        time.id = `pickup-time-${order.id}`;
        time.type = "datetime-local";
        time.required = true;
        time.name = "pickupAt";
        time.value = order.pickupAt ? localDateTimeValue(order.pickupAt) : "";
        timeLabel.appendChild(time);
        text(form, "p", `Times use your device's timezone (${Intl.DateTimeFormat().resolvedOptions().timeZone}).`, "pickup-note");
        const instructionsLabel = ui(form, "label", "Meeting location and instructions");
        const instructions = document.createElement("textarea");
        instructions.id = `pickup-instructions-${order.id}`;
        instructions.name = "pickupInstructions";
        instructions.required = true;
        instructions.maxLength = 500;
        instructions.rows = 3;
        instructions.placeholder = "Example: Main gate of Jani village market, beside the water tank.";
        instructions.value = order.pickupInstructions || "";
        instructionsLabel.appendChild(instructions);
        const submit = ui(form, "button", order.pickupAt ? "Update pickup details" : "Save pickup details");
        submit.type = "submit";
        submit.setAttribute("aria-label", `${submit.textContent} for order #${order.id}`);
        form.addEventListener("submit", event => {
            event.preventDefault();
            if (busy || !form.reportValidity()) return;
            const pickup = new Date(time.value);
            if (!Number.isFinite(pickup.getTime()) || pickup.getTime() <= Date.now()) {
                message.textContent = t("Choose a pickup time in the future.");
                return;
            }
            if (!instructions.value.trim()) {
                message.textContent = t("Enter the pickup location and instructions.");
                return;
            }
            performAction(order.id, "pickup", {
                pickupAt: pickup.toISOString(),
                pickupInstructions: instructions.value.trim()
            });
        });
        card.appendChild(form);
    }

    function pickupIsArranged(order) {
        return Boolean(order.pickupAt && Number.isFinite(new Date(order.pickupAt).getTime())
            && typeof order.pickupInstructions === "string" && order.pickupInstructions.trim());
    }

    function addProgress(card, order) {
        const ended = order.status === "REJECTED" || order.status === "CANCELLED";
        const complete = order.status === "COMPLETED";
        const stage = complete ? 3 : order.status === "ACCEPTED" ? (pickupIsArranged(order) ? 2 : 1) : 0;
        const labels = ended ? ["Requested", order.status === "CANCELLED" ? "Cancelled by buyer" : "Declined by farmer"]
            : ["Requested", "Farmer accepted", "Pickup arranged", "Collected"];
        const list = document.createElement("ol");
        list.className = ended ? "order-progress progress-ended" : "order-progress";
        list.setAttribute("aria-label", `Progress of order #${order.id}`);
        labels.forEach((label, index) => {
            const current = index === (ended ? 1 : stage);
            const done = ended || complete || index < stage;
            const step = document.createElement("li");
            step.className = `progress-step${done ? " step-done" : ""}${current ? " step-current" : ""}`;
            if (current) step.setAttribute("aria-current", "step");
            const marker = text(step, "span", done ? "✓" : String(index + 1), "progress-marker");
            marker.setAttribute("aria-hidden", "true");
            const copy = document.createElement("span"); copy.className = "progress-copy";
            ui(copy, "span", label, "progress-label");
            ui(copy, "span", current ? (ended ? "Request ended" : complete ? "Complete" : "Current stage")
                : done ? "Done" : "Upcoming", "progress-state");
            step.appendChild(copy); list.appendChild(step);
        });
        card.appendChild(list);
    }

    function nextStep(order) {
        if (order.status === "COMPLETED") return ["Collection confirmed", farmerView
            ? "Collection has been confirmed. No further order action is needed."
            : "Collection has been confirmed. This order is complete."];
        if (order.status === "REJECTED") return ["Request declined", farmerView
            ? "You declined this request. Its reserved quantity has been returned to the listing."
            : "The farmer declined this request. You can browse other available crops."];
        if (order.status === "CANCELLED") return ["Request cancelled", farmerView
            ? "The buyer cancelled this request. Its reserved quantity has been returned to the listing."
            : "You cancelled this request. You can place a new request when you are ready."];
        if (order.status === "PENDING") return [farmerView ? "Review this request" : "Waiting for the farmer", farmerView
            ? "Accept if you can fulfil this quantity, or decline to release the reserved stock."
            : "The farmer needs to accept your request. You can cancel while it is pending."];
        if (order.status === "ACCEPTED" && !pickupIsArranged(order)) return [farmerView ? "Arrange the pickup" : "Pickup details are next", farmerView
            ? "Set the pickup date, time, meeting location and instructions below."
            : "Your request was accepted. Wait for the farmer to add a pickup time and meeting instructions."];
        if (order.status === "ACCEPTED") return [farmerView ? "Pickup is arranged" : "Your pickup is arranged", farmerView
            ? "The buyer can see the pickup details. At handover, verify the buyer’s current pickup code to complete the order."
            : "Follow the pickup details below. Generate a pickup code at handover and share it after receiving the produce."];
        return ["Check the latest status", "Refresh this page before taking another action."];
    }

    function hasText(value) {
        return typeof value === "string" && value.trim().length > 0;
    }

    function addProduceSummary(card, order) {
        const summary = document.createElement("div");
        summary.className = "order-produce-summary";
        const photo = document.createElement("div");
        photo.className = "order-produce-photo";
        summary.appendChild(photo);
        renderListingPhoto(photo, {
            produceName: hasText(order.produceName) ? order.produceName : "Produce",
            farmerName: hasText(order.farmerName) ? order.farmerName : "the farmer",
            photoUrl: order.photoUrl || null
        });
        const copy = document.createElement("div");
        copy.className = "order-produce-copy";
        text(copy, "h3", hasText(order.produceName) ? order.produceName
            : `Crop listing #${order.listingId}`, "order-produce-title");
        const participants = document.createElement("dl");
        participants.className = "order-participants";
        const details = [["Farmer", order.farmerName], ["Pickup area", order.pickupArea]];
        if (farmerView) details.splice(1, 0, ["Buyer", order.buyerName]);
        for (const [label, value] of details) {
            if (!hasText(value)) continue;
            const item = document.createElement("div");
            ui(item, "dt", label); text(item, "dd", value);
            participants.appendChild(item);
        }
        if (participants.childElementCount) copy.appendChild(participants);
        if (order.createdAt && Number.isFinite(new Date(order.createdAt).getTime())) {
            const placed = ui(copy, "p", "Requested ", "order-created");
            const time = text(placed, "time", new Date(order.createdAt).toLocaleString(undefined, {
                dateStyle: "medium", timeStyle: "short"
            }));
            time.dateTime = new Date(order.createdAt).toISOString();
            ui(placed, "span", " · your local time");
        }
        const listingId = Number(order.listingId);
        if (Number.isSafeInteger(listingId) && listingId > 0) {
            const link = ui(copy, "a", "View current listing ↗", "order-listing-link");
            link.href = `/listing.html?id=${listingId}`;
            link.setAttribute("aria-label", `View current listing for order #${order.id}`);
        }
        summary.appendChild(copy);
        card.appendChild(summary);
    }

    function renderOrder(order) {
        const card = document.createElement("article");
        card.className = "order-card"; card.id = `order-${order.id}`;
        const heading = document.createElement("div"); heading.className = "order-card-heading";
        text(heading, "h2", t("orderReference", {id:order.id}));
        ui(heading, "span", String(order.status).replaceAll("_", " "), `order-status status-${String(order.status).toLowerCase()}`);
        card.appendChild(heading);
        addProduceSummary(card, order);
        const summary = document.createElement("dl"); summary.className = "order-summary";
        for (const [label, value] of [["Quantity", `${order.quantityKg} kg`],
                ["Agreed / kg", currency.format(order.pricePerKg)], ["Order total", currency.format(order.totalAmount)]]) {
            const item = document.createElement("div");
            ui(item, "dt", label); text(item, "dd", value); summary.appendChild(item);
        }
        card.appendChild(summary);
        if (hasText(order.produceName)) {
            ui(card, "p", "The photo and pickup area reflect the current listing. Your agreed price stays fixed.",
                "order-current-details-note");
        }
        addProgress(card, order);
        const guidance = document.createElement("div"); guidance.className = "order-next-step";
        const [title, description] = nextStep(order);
        ui(guidance, "h3", title); ui(guidance, "p", description); card.appendChild(guidance);
        if (pickupIsArranged(order)) {
            const pickup = document.createElement("div"); pickup.className = "order-pickup-summary";
            ui(pickup, "h3", "Pickup details");
            text(pickup, "p", `${new Date(order.pickupAt).toLocaleString(undefined, {
                dateStyle: "medium", timeStyle: "short"
            })} (your local time)`);
            text(pickup, "p", order.pickupInstructions, "pickup-details");
            card.appendChild(pickup);
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
                ui(card, "p", "Confirm handover with the buyer's pickup code after the produce is collected.", "pickup-note");
            }
            if (pickupIsArranged(order)) window.addOrderPickupCode(card, order, {farmerView,
                onCompleted: () => loadOrders(currentPage, "Collection confirmed. Your order is completed.", order.id)});
        }
        if (!farmerView) window.addOrderReview?.(card, order);
        window.addOrderMessages?.(card, order, {farmerView});
        container.appendChild(card);
    }

    previous.addEventListener("click", () => { if (!busy) loadOrders(currentPage - 1); });
    next.addEventListener("click", () => { if (!busy) loadOrders(currentPage + 1); });
    refresh.addEventListener("click", () => { if (!busy) loadOrders(currentPage); });
    document.addEventListener("khet:languagechange", () => {
        pageLabel.textContent = totalPages ? t("pageOf",{page:currentPage+1,total:totalPages}) : t("No pages");
        window.KhetI18n?.apply(container);
        // Keep entered pickup details and any displayed handover code intact.
    });
    loadOrders(0);
})();

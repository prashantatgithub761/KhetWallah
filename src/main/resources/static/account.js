"use strict";

(() => {
    const T = (key, params) => window.KhetI18n?.t(key, params) || key;
    const content = document.getElementById("account-content");
    const message = document.getElementById("account-message");
    const refresh = document.getElementById("refresh-account");
    const login = document.getElementById("account-login");
    const pickupList = document.getElementById("pickup-list");
    const number = new Intl.NumberFormat("en-IN", { maximumFractionDigits: 2 });
    const money = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 2 });
    const dateTime = new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" });
    let activeRequest, cachedDashboard;

    function element(tag, className, text) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (text !== undefined) node.textContent = text;
        return node;
    }

    function pickupCard(pickup) {
        const card = element("article", "dashboard-pickup");
        const details = element("div", "dashboard-pickup-copy");
        const reference = element("p", "workspace-kicker", `${T("orderReference", {id:pickup.orderId})} · ${T(pickup.role === "BUYER" ? "Buying" : "Selling")}`);
        details.append(reference, element("h3", "", pickup.produceName));
        const counterpart = pickup.role === "BUYER" ? `${T("Farmer")}: ${pickup.farmerName}` : `${T("Buyer")}: ${pickup.buyerName}`;
        details.append(element("p", "section-description", `${counterpart} · ${T("Pickup area")}: ${pickup.pickupArea}`));
        details.append(element("p", "", `${number.format(pickup.quantityKg)} kg · ${T("Order total")} ${money.format(pickup.totalAmount)}`));
        const actions = element("div", "dashboard-pickup-actions");
        const time = element("time", "", dateTime.format(new Date(pickup.pickupAt)));
        time.dateTime = pickup.pickupAt;
        const link = element("a", "button-link secondary", T("View order details"));
        link.href = `${pickup.role === "BUYER" ? "/my-orders.html" : "/received-orders.html"}?orderId=${encodeURIComponent(pickup.orderId)}#order-${encodeURIComponent(pickup.orderId)}`;
        link.setAttribute("aria-label", `View order #${pickup.orderId} details`);
        actions.append(time, link);
        card.append(details, actions);
        return card;
    }

    function render(data) {
        cachedDashboard = data;
        document.getElementById("account-heading").textContent = T("welcomeName",{name:data.displayName});
        document.getElementById("account-intro").textContent = `${data.email} · ${T("One account for buying and selling produce.")}`;
        document.getElementById("active-listings").textContent = number.format(data.activeListings);
        document.getElementById("pending-orders").textContent = number.format(data.pendingOrders);
        document.getElementById("pending-received").textContent = number.format(data.pendingReceivedOrders);
        document.getElementById("upcoming-pickups").textContent = number.format(data.upcomingPickups);
        pickupList.replaceChildren(...data.pickups.map(pickupCard));
        document.getElementById("pickup-empty").hidden = data.pickups.length > 0;
        document.getElementById("pickup-summary").textContent = data.upcomingPickups > data.pickups.length
            ? `Showing the next ${data.pickups.length} of ${number.format(data.upcomingPickups)} scheduled pickups`
            : `${number.format(data.pickups.length)} scheduled ${data.pickups.length === 1 ? "pickup" : "pickups"}`;
        document.getElementById("account-updated").textContent = `Overview updated ${dateTime.format(new Date(data.asOf))}. Refresh to check for changes.`;
        content.hidden = false;
        message.textContent = "";
    }

    async function loadAccount() {
        if (activeRequest) activeRequest.abort();
        const controller = new AbortController();
        activeRequest = controller;
        const timeout = setTimeout(() => controller.abort(), 15000);
        refresh.disabled = true;
        refresh.textContent = T("Refreshing…");
        content.hidden = true;
        pickupList.replaceChildren();
        login.hidden = true;
        message.textContent = T("Loading your account…");
        try {
            const response = await fetch("/api/me/dashboard", {
                credentials: "same-origin", cache: "no-store", headers: { Accept: "application/json" }, signal: controller.signal
            });
            if (response.status === 401 || response.redirected || !response.headers.get("Content-Type")?.includes("application/json")) {
                login.hidden = false;
                message.textContent = T("Your sign-in may have expired. Sign in to see your account overview.");
                return;
            }
            if (response.status === 403) {
                message.textContent = T("This account cannot access the dashboard. Contact the site administrator if you need help.");
                return;
            }
            if (!response.ok) throw new Error("request-failed");
            const data = await response.json();
            if (activeRequest !== controller) return;
            if (!Array.isArray(data.pickups)) throw new Error("invalid-response");
            render(data);
        } catch (error) {
            if (activeRequest !== controller) return;
            message.textContent = error.name === "AbortError"
                ? T("The overview took too long to load. Check your connection and try Refresh overview.")
                : T("We could not load your overview. Check your connection and try Refresh overview.");
        } finally {
            clearTimeout(timeout);
            if (activeRequest === controller) {
                refresh.disabled = false;
                refresh.textContent = T("Refresh overview");
            }
        }
    }

    document.addEventListener("khet:languagechange", () => {
        if (cachedDashboard && !content.hidden) render(cachedDashboard);
        if (refresh.disabled) refresh.textContent = T("Refreshing…");
    });
    refresh.addEventListener("click", loadAccount);
    loadAccount();
})();

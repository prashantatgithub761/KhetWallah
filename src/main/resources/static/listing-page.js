(() => {
    "use strict";
    const T = (key, params) => window.KhetI18n?.t(key, params) || key;
    const id = new URLSearchParams(location.search).get("id") || "";
    const validId = /^[1-9]\d{0,17}$/.test(id) && Number.isSafeInteger(Number(id));
    const content = document.getElementById("listing-content");
    const message = document.getElementById("listing-message");
    const retry = document.getElementById("retry-listing");
    const form = document.getElementById("listing-order-form");
    const quantity = document.getElementById("listing-quantity");
    const submit = document.getElementById("request-listing");
    const feedback = document.getElementById("listing-order-message");
    const login = document.getElementById("listing-login");
    const money = {format: value => new Intl.NumberFormat(window.KhetI18n?.locale || "en-IN", {style:"currency", currency:"INR"}).format(value)};
    const number = {format: value => new Intl.NumberFormat(window.KhetI18n?.locale || "en-IN", {maximumFractionDigits:2}).format(value)};
    let listing, loading = false, submitting = false, orderSent = false;
    function minimumOf(crop) {
        const minimum = Number(crop?.minimumOrderQuantityKg);
        return Number.isFinite(minimum) && minimum > 0 ? minimum : 0.01;
    }
    function canOrder(crop) {
        return Number(crop?.availableQuantityKg) >= minimumOf(crop);
    }
    function renderCrop(resetQuantity = false) {
        if (!listing) return;
        document.title = `${listing.produceName} | KhetWallah`;
        document.getElementById("listing-title").textContent = listing.produceName;
        if (!document.getElementById("listing-photo").dataset.galleryLoaded) {
            document.getElementById("listing-photo").dataset.galleryLoaded="true";
            renderListingGallery(document.getElementById("listing-photo"), listing);
        }
        window.enhanceListingActions?.(document.getElementById("listing-farmer-actions"),listing);
        const stock = Number(listing.availableQuantityKg);
        const available = canOrder(listing), minimum = minimumOf(listing);
        document.getElementById("listing-stock").textContent = T(stock <= 0 ? "Out of stock" : available ? "Available for pickup" : "Below minimum order");
        document.getElementById("listing-stock").classList.toggle("stock-empty", !available);
        document.getElementById("listing-price").textContent = money.format(listing.pricePerKg);
        const facts = document.getElementById("listing-facts"); facts.replaceChildren();
        const rows = [["Farmer",listing.farmerName],["Available",`${number.format(stock)} kg`],["Pickup area",listing.pickupArea],
            ...(listing.district ? [["District",listing.district]] : []),
            ...(listing.pinCode ? [["PIN code",listing.pinCode]] : []),
            ...(listing.availableUntil ? [["Available until",listing.availableUntil]] : []),
            ["Minimum order",`${number.format(minimum)} kg`],
            ...(listing.variety ? [["Variety",listing.variety]] : []),
            ...(listing.harvestDate ? [["Harvest date (farmer-reported)",listing.harvestDate]] : []),
            ...(listing.packagingDetails ? [["Packaging",listing.packagingDetails]] : [])];
        for (const [label,value] of rows) {
            const row = document.createElement("div"), term = document.createElement("dt"), detail = document.createElement("dd");
            term.textContent = T(label); detail.textContent = value; detail.dataset.i18nSkip = "";
            row.append(term,detail); facts.append(row);
        }
        quantity.min = String(minimum); quantity.max = String(stock);
        if (resetQuantity) quantity.value = available ? String(Math.max(minimum, Math.min(1,stock))) : "";
        quantity.disabled = submit.disabled = !available || orderSent || submitting;
        submit.textContent = T(orderSent ? "Request sent" : available ? "Request produce →" : stock <= 0 ? "Out of stock" : "Below minimum order");
        document.getElementById("quantity-hint").textContent = `${number.format(stock)} kg · ${T("minimumQuantity", {quantity:number.format(minimum)})}`;
        document.getElementById("harvest-disclaimer").hidden = !listing.harvestDate;
        login.href = `/login?next=${encodeURIComponent(`/listing.html?id=${id}`)}`;
        estimate(); content.hidden = false;
    }

    function estimate() {
        document.getElementById("listing-total").textContent = money.format(
            Math.max(0, Number(quantity.value) || 0) * Number(listing?.pricePerKg || 0));
    }
    async function request(path, options = {}) {
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), 20000);
        try {
            const response = await fetch(path, {...options, credentials:"same-origin", redirect:"manual", signal:controller.signal,
                headers:{Accept:"application/json", ...options.headers}});
            if (response.type === "opaqueredirect" || response.status === 401) {
                login.hidden = false;
                throw new Error(T("Please sign in to request produce."));
            }
            const data = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(response.status === 404
                ? T("This crop is no longer available. Browse the marketplace for other listings.")
                : Object.values(data.fieldErrors || {}).join(" ") || data.message || `Request failed (${response.status}).`);
            return data;
        } finally { clearTimeout(timer); }
    }
    function networkMessage(error, isOrder = false) {
        return error.name === "AbortError" || error instanceof TypeError
            ? isOrder ? T("Connection interrupted. Check My orders before retrying; your request may have been saved.")
                : T("We could not reach KhetWallah. Check your connection and try again.")
            : error.message;
    }
    async function load() {
        if (loading || submitting) return;
        if (!validId) { message.textContent = T("This crop link is invalid. Return to Browse crops to choose a listing."); return; }
        loading = true; retry.hidden = true; content.hidden = true;
        message.textContent = T("Loading crop details…");
        try {
            listing = await request(`/api/listings/${id}`);
            renderCrop(true);
            message.textContent = "";
            document.getElementById("crop-reviews").hidden = false;
            window.renderListingReviews?.(document.getElementById("crop-reviews"), id);
        } catch(error) { message.textContent = networkMessage(error); retry.hidden = false; }
        finally { loading = false; }
    }
    quantity.addEventListener("input", () => { quantity.setCustomValidity(""); estimate(); });
    retry.addEventListener("click", load);
    form.addEventListener("submit", async event => {
        event.preventDefault();
        if (submitting || orderSent || !listing || !canOrder(listing) || !form.reportValidity()) return;
        const requestedQuantity = Number(quantity.value);
        if (!Number.isFinite(requestedQuantity) || requestedQuantity < minimumOf(listing)
                || requestedQuantity > Number(listing.availableQuantityKg)) {
            quantity.setCustomValidity(T("minimumQuantity", {quantity:number.format(minimumOf(listing))}));
            quantity.reportValidity();
            return;
        }
        submitting = true; quantity.disabled = submit.disabled = true; login.hidden = true;
        feedback.textContent = T("Sending your request…"); form.setAttribute("aria-busy","true");
        try {
            const csrf = await request("/api/csrf");
            const data = await request("/api/orders", {method:"POST",headers:{"Content-Type":"application/json",[csrf.headerName]:csrf.token},
                body:JSON.stringify({listingId:Number(id),quantityKg:requestedQuantity})});
            feedback.textContent = `Order #${data.id} requested for ${data.quantityKg} kg. Confirmed total: ${money.format(data.totalAmount)}. `;
            const link = document.createElement("a"); link.href = `/my-orders.html?orderId=${data.id}`; link.textContent = T("View your order →"); feedback.append(link); feedback.focus();
            orderSent = true;
            submit.textContent = T("Request sent"); // Keep disabled to prevent accidental duplicate orders.
        } catch(error) {
            feedback.textContent = networkMessage(error,true); feedback.focus();
            quantity.disabled = submit.disabled = !canOrder(listing);
        } finally { submitting = false; form.removeAttribute("aria-busy"); }
    });
    document.getElementById("copy-listing").addEventListener("click", async () => {
        const url = new URL(`/listing.html?id=${id}`, location.origin).href;
        const status = document.getElementById("listing-share-message");
        try { await navigator.clipboard.writeText(url); status.textContent = T("Crop link copied. Share it with someone looking for this produce."); }
        catch (_) {
            document.getElementById("listing-share-fallback").hidden = false;
            const input = document.getElementById("listing-share-url"); input.value = url; input.focus(); input.select();
            status.textContent = T("Select and copy the crop link below.");
        }
    });
    document.addEventListener("khet:languagechange", () => {
        if (listing && !loading) renderCrop(false);
        else if (loading) message.textContent = T("Loading crop details…");
    });
    load();
})();

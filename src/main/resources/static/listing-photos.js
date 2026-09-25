function validateListingPhoto(file) {
    if (!file) return;

    if (!["image/jpeg", "image/png"].includes(file.type)) {
        throw new Error("Choose a JPEG or PNG photo.");
    }

    if (file.size > 5 * 1024 * 1024) {
        throw new Error("The photo must be 5 MB or smaller.");
    }
}

async function uploadListingPhoto(listingId, file) {
    validateListingPhoto(file);

    if (!file) {
        throw new Error("Select a photo first.");
    }

    const tokenResponse = await fetch("/api/csrf", {
        redirect: "error"
    });

    if (!tokenResponse.ok) {
        throw new Error("Could not prepare the upload. Refresh and try again.");
    }

    const csrf = await tokenResponse.json();
    const body = new FormData();
    body.append("photo", file);

    const response = await fetch(`/api/listings/${listingId}/photo`, {
        method: "POST",
        redirect: "manual",
        headers: {
            "Accept": "application/json",
            [csrf.headerName]: csrf.token
        },
        body
    });

    if (response.type === "opaqueredirect" || response.status === 401) {
        throw new Error("Please sign in again.");
    }

    if (!response.ok) {
        const error = await response.json().catch(() => ({}));

        throw new Error(
            error.message || `Photo upload failed (${response.status}).`
        );
    }
}

function renderListingPhoto(card, listing) {
    let frame = card.querySelector(".listing-photo");

    if (!frame) {
        frame = document.createElement("div");
        frame.className = "listing-photo";
        frame.style.marginBottom = "16px";
        card.prepend(frame);
    }

    frame.replaceChildren();

    function showPlaceholder() {
        frame.replaceChildren();
        const placeholder = document.createElement("div");
        placeholder.textContent = "🌾 No photo yet";

        Object.assign(placeholder.style, {
            height: "180px",
            display: "grid",
            placeItems: "center",
            background: "#eef3e9",
            borderRadius: "10px",
            color: "#526256"
        });

        frame.appendChild(placeholder);
    }

    if (!listing.photoUrl) {
        showPlaceholder();
        return;
    }

    const image = document.createElement("img");
    image.src = listing.photoUrl;
    image.alt = `${listing.produceName} offered by ${listing.farmerName}`;
    image.loading = "lazy";

    Object.assign(image.style, {
        width: "100%",
        height: "180px",
        objectFit: "cover",
        borderRadius: "10px"
    });

    image.addEventListener("error", showPlaceholder, { once: true });
    frame.appendChild(image);
}

function addListingPhotoUploader(card, listing) {
    const form = document.createElement("form");
    form.style.marginTop = "16px";

    const label = document.createElement("label");
    label.textContent = "Crop photo — JPEG or PNG, up to 5 MB";

    const input = document.createElement("input");
    input.type = "file";
    input.accept = "image/jpeg,image/png";
    input.required = true;
    input.style.display = "block";
    input.style.margin = "8px 0";
    input.style.maxWidth = "100%";
    label.appendChild(input);

    const button = document.createElement("button");
    button.type = "submit";
    button.textContent = listing.photoUrl ? "Replace photo" : "Upload photo";

    const feedback = document.createElement("p");
    feedback.setAttribute("role", "status");

    form.append(label, button, feedback);
    card.appendChild(form);

    form.addEventListener("submit", async event => {
        event.preventDefault();

        if (button.disabled) return;

        const file = input.files[0];
        button.disabled = true;
        input.disabled = true;
        feedback.textContent = "Uploading photo…";

        try {
            await uploadListingPhoto(listing.id, file);

            listing.photoUrl =
                `/api/listings/${listing.id}/photo?v=${Date.now()}`;

            renderListingPhoto(card, listing);
            button.textContent = "Replace photo";
            feedback.textContent = "Photo saved.";
            input.value = "";
        } catch (error) {
            feedback.textContent = error instanceof TypeError
                ? "Connection problem. Refresh to check whether the photo was saved."
                : error.message;
        } finally {
            button.disabled = false;
            input.disabled = false;
        }
    });
}
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
        placeholder.className = "photo-placeholder";
        const symbols = {tomato:"🍅", potato:"🥔", onion:"🧅", wheat:"🌾", banana:"🍌", apple:"🍎", carrot:"🥕", corn:"🌽", rice:"🌾", mango:"🥭"};
        const symbol = document.createElement("span");
        symbol.className = "placeholder-symbol";
        symbol.setAttribute("aria-hidden", "true");
        symbol.textContent = symbols[String(listing.produceName || "").trim().toLowerCase()] || "🌱";
        const caption = document.createElement("span");
        caption.className = "placeholder-caption";
        caption.textContent = "Photo not added";
        placeholder.append(symbol, caption);

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


async function galleryRequest(url, options = {}) {
    const controller = new AbortController(), timer = setTimeout(() => controller.abort(), 20000);
    try {
        const headers = {Accept: "application/json", ...options.headers};
        if (options.method && options.method !== "GET") {
            const token = await fetch("/api/csrf", {redirect:"error", signal:controller.signal});
            if (!token.ok) throw new Error("Please sign in again.");
            const csrf = await token.json(); headers[csrf.headerName] = csrf.token;
        }
        const response = await fetch(url, {...options, headers, redirect:"manual", signal:controller.signal});
        if (response.type === "opaqueredirect" || response.status === 401) throw new Error("Please sign in again.");
        const data = await response.json().catch(() => null);
        if (!response.ok) throw new Error(data?.message || `Request failed (${response.status}).`);
        return data;
    } finally { clearTimeout(timer); }
}

async function renderListingGallery(mount, listing, editable = false) {
    const T = (key) => window.KhetI18n?.t(key) || key;
    const request = String(Date.now()) + Math.random(); mount.dataset.galleryRequest = request;
    let photos;
    try { photos = await galleryRequest(`/api/listings/${listing.id}/photos`); }
    catch (_) { if (!editable) renderListingPhoto(mount,listing); return; }
    if (mount.dataset.galleryRequest !== request) return;
    mount.replaceChildren(); mount.classList.add("listing-gallery");
    if (!photos.length) { renderListingPhoto(mount, {...listing,photoUrl:null}); return; }
    const display = document.createElement("div"); display.className = "gallery-main";
    const image = document.createElement("img"); image.alt = `${listing.produceName} offered by ${listing.farmerName}`;
    image.decoding="async"; image.loading="lazy"; image.width=800; image.height=600;
    display.append(image); mount.append(display);
    const thumbs = document.createElement("div"); thumbs.className="gallery-thumbnails"; thumbs.setAttribute("role","group"); thumbs.setAttribute("aria-label",T("Crop photos"));
    function select(photo,index) {
        image.src=photo.url; image.alt=`${listing.produceName} — ${T("Photo")} ${index+1}`;
        [...thumbs.querySelectorAll("button[data-photo]")].forEach(button => button.setAttribute("aria-pressed",String(button.dataset.photo===String(photo.id))));
    }
    photos.forEach((photo,index) => {
        const item=document.createElement("div"); item.className="gallery-thumbnail-item";
        const button=document.createElement("button"); button.type="button"; button.className="gallery-thumbnail secondary";
        button.dataset.photo=photo.id; button.setAttribute("aria-label",`${T("View photo")} ${index+1}`); button.setAttribute("aria-pressed",String(index===0));
        const thumb=document.createElement("img"); thumb.src=photo.url; thumb.alt=""; thumb.loading="lazy"; thumb.width=64; thumb.height=64;
        button.append(thumb); button.addEventListener("click",()=>select(photo,index)); item.append(button);
        if(editable) {
            const remove=document.createElement("button"); remove.type="button"; remove.className="secondary gallery-remove"; remove.textContent=T("Remove photo");
            let armed=false;
            remove.addEventListener("click",async()=> {
                if(!armed) { armed=true;remove.textContent=T("Confirm removal");return; }
                remove.disabled=true;
                try { await galleryRequest(`/api/listings/${listing.id}/photos/${photo.id}`,{method:"DELETE"});
                    await renderListingGallery(mount,listing,true);
                    listing.photoUrl=mount.querySelector(".gallery-main img")?.getAttribute("src") || null;
                } catch(error) { remove.textContent=error.name==="AbortError"?T("Refresh to check removal"):error.message; remove.disabled=false; armed=false; }
            }); item.append(remove);
        }
        thumbs.append(item);
    });
    mount.append(thumbs); select(photos[0],0);
}

function addListingPhotoUploader(card, listing) {
    const T = (key) => window.KhetI18n?.t(key) || key;
    const gallery=document.createElement("div"); card.querySelector(".listing-photo")?.remove();card.prepend(gallery);
    renderListingGallery(gallery,listing,true);
    const form=document.createElement("form"); form.className="gallery-upload";
    const label=document.createElement("label"); label.textContent=T("Add crop photo — up to four photos in total");
    const input=document.createElement("input"); input.type="file"; input.accept="image/jpeg,image/png"; input.required=true;
    label.append(input);
    const help=document.createElement("p"); help.className="form-help"; help.textContent=T("JPEG or PNG, up to 5 MB and 8 megapixels. Photos are resized for faster loading.");
    const button=document.createElement("button");button.type="submit";button.textContent=T("Add photo");
    const feedback=document.createElement("p");feedback.setAttribute("role","status");form.append(label,help,button,feedback);card.append(form);
    const preview=attachCropPhotoPreview(input);
    form.addEventListener("submit",async event=> {
        event.preventDefault();if(button.disabled || !form.reportValidity())return;
        button.disabled=input.disabled=true;feedback.textContent=T("Uploading photo…");
        try {
            const file=input.files[0];validateListingPhoto(file);const body=new FormData();body.append("photo",file);
            await galleryRequest(`/api/listings/${listing.id}/photos`,{method:"POST",body});
            feedback.textContent=T("Photo saved.");input.value="";preview.clear();await renderListingGallery(gallery,listing,true);
        } catch(error) { feedback.textContent=error instanceof TypeError || error.name==="AbortError"?T("Connection problem. Refresh to check whether the photo was saved."):error.message; }
        finally { button.disabled=input.disabled=false; }
    });
}

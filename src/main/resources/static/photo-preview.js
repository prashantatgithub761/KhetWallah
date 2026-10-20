"use strict";

// Local previews never upload the selected file. The existing upload API validates it again.
function attachCropPhotoPreview(input) {
    const frame = document.createElement("figure");
    frame.className = "photo-preview";
    frame.hidden = true;
    const image = document.createElement("img");
    image.alt = "Selected crop photo preview";
    const caption = document.createElement("figcaption");
    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "secondary";
    remove.textContent = "Remove selected photo";
    const feedback = document.createElement("p");
    feedback.className = "form-help photo-preview-feedback";
    feedback.setAttribute("role", "status");
    frame.append(image, caption, remove);
    input.insertAdjacentElement("afterend", frame);
    frame.insertAdjacentElement("afterend", feedback);
    let objectUrl = null;
    let generation = 0;

    function clear() {
        generation++;
        image.onload = image.onerror = null;
        image.removeAttribute("src");
        if (objectUrl) URL.revokeObjectURL(objectUrl);
        objectUrl = null;
        frame.hidden = true;
        image.hidden = false;
        caption.textContent = "";
        feedback.textContent = "";
        input.setCustomValidity("");
    }
    function preview() {
        clear();
        const file = input.files[0];
        if (!file) return;
        const current = generation;
        function fail(message) {
            if (current !== generation) return;
            clear();
            input.setCustomValidity(message);
            image.hidden = true;
            caption.textContent = file.name;
            frame.hidden = false;
            feedback.textContent = message;
        }
        if (!["image/jpeg", "image/png"].includes(file.type)) {
            fail("Choose a JPEG or PNG photo."); return;
        }
        if (file.size > 5 * 1024 * 1024) {
            fail("Choose a photo of 5 MB or less."); return;
        }
        objectUrl = URL.createObjectURL(file);
        feedback.textContent = "Preparing photo preview…";
        image.onload = () => {
            if (current !== generation) return;
            if (image.naturalWidth * image.naturalHeight > 8000000) {
                fail("Choose a photo of 8 megapixels or less."); return;
            }
            caption.textContent = `${file.name} · ${image.naturalWidth} × ${image.naturalHeight} pixels`;
            feedback.textContent = "Preview only. Your photo is uploaded when you publish or save it.";
            frame.hidden = false;
        };
        image.onerror = () => fail("This photo could not be read. Choose another JPEG or PNG.");
        image.src = objectUrl;
    }
    input.addEventListener("change", preview);
    remove.addEventListener("click", () => {
        input.value = "";
        clear();
        input.focus();
    });
    input.form?.addEventListener("reset", clear);
    window.addEventListener("pagehide", clear);
    return {clear};
}

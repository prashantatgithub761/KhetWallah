'use strict';
function addListingReportControl(card, listing) {
    const details = document.createElement('details');
    details.className = 'report-control';
    const summary = document.createElement('summary');
    summary.textContent = 'Report this listing';
    const form = document.createElement('form');
    const label = document.createElement('label');
    label.textContent = 'What is wrong with this listing?';
    const reason = document.createElement('textarea');
    reason.required = true;
    reason.maxLength = 500;
    reason.rows = 3;
    reason.placeholder = 'Describe misleading details, inappropriate content, or another concern.';
    label.append(reason);
    const hint = document.createElement('small');
    hint.textContent = 'Sign in to submit. One report per account per listing. Reports are reviewed by an administrator.';
    const submit = document.createElement('button');
    submit.type = 'submit';
    submit.textContent = 'Send report';
    const message = document.createElement('p');
    message.setAttribute('role', 'status');
    form.append(label, hint, submit, message);
    details.append(summary, form);
    card.append(details);
    let busy = false;
    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (busy) return;
        if (!reason.value.trim()) { message.textContent = 'Please describe the issue.'; return; }
        busy = true; submit.disabled = true;
        try {
            const tokenResponse = await fetch('/api/csrf', {credentials:'same-origin'});
            if (!tokenResponse.ok) throw new Error('Unable to prepare the report. Please refresh and try again.');
            const csrf = await tokenResponse.json();
            const response = await fetch(`/api/listings/${listing.id}/reports`, {
                method:'POST', credentials:'same-origin', redirect:'manual',
                headers:{'Content-Type':'application/json', [csrf.headerName]:csrf.token},
                body:JSON.stringify({reason:reason.value.trim()})
            });
            if (response.type === 'opaqueredirect' || response.status === 401) {
                message.replaceChildren(document.createTextNode('Please '));
                const login = document.createElement('a'); login.href='/login'; login.textContent='sign in';
                message.append(login, document.createTextNode(' before reporting.')); return;
            }
            if (!response.ok) {
                const messages = {400:'You cannot report your own listing, and a reason is required.',
                    403:'Your session is unavailable. Sign in again and retry.',
                    404:'This listing is no longer available.',
                    409:'You already reported this listing, or it is no longer active.'};
                throw new Error(messages[response.status] || 'Could not send the report. Please try again.');
            }
            message.textContent='Report received. An administrator will review it.';
            reason.disabled=true; submit.hidden=true;
        } catch(error) { message.textContent=error.message; }
        finally { busy=false; submit.disabled=false; }
    });
}

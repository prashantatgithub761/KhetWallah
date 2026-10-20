'use strict';
const pages = { listings: 0, users: 0 };
const notice = document.getElementById('notice');
let csrf;

async function request(path, options = {}) {
  const response = await fetch(path, { credentials: 'same-origin', redirect: 'manual', ...options });
  if (response.type === 'opaqueredirect' || response.status === 401) {
    throw new Error('Your session expired. Sign in again at /login.');
  }
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error.message || (response.status === 403 ? 'Access denied. Sign in again as an administrator.' : `Request failed (${response.status})`));
  }
  return response.json();
}

function add(parent, tag, value, className) {
  const element = document.createElement(tag);
  element.textContent = value;
  if (className) element.className = className;
  parent.append(element);
  return element;
}

function actionButton(parent, label, path, danger) {
  const wrapper = add(parent, 'div', '', 'moderation-action');
  const button = add(wrapper, 'button', label, danger ? 'danger' : '');
  button.type = 'button';
  button.setAttribute('aria-expanded', 'false');
  const form = document.createElement('form');
  form.className = 'moderation-form';
  form.hidden = true;
  const reasonLabel = add(form, 'label', `Reason for ${label.toLowerCase()}`);
  const reason = document.createElement('textarea');
  reason.required = true;
  reason.maxLength = 500;
  reason.rows = 3;
  reasonLabel.append(reason);
  add(form, 'small', 'Enter 1–500 characters. Your explanation is saved in the moderation history.');
  const confirm = add(form, 'button', 'Confirm', danger ? 'danger' : '');
  confirm.type = 'submit';
  const cancel = add(form, 'button', 'Cancel');
  cancel.type = 'button';
  const feedback = add(form, 'p', '');
  feedback.setAttribute('role', 'status');
  wrapper.append(form);
  let busy = false;
  button.addEventListener('click', () => {
    form.hidden = false;
    button.setAttribute('aria-expanded', 'true');
    reason.focus();
  });
  cancel.addEventListener('click', () => {
    form.hidden = true;
    button.setAttribute('aria-expanded', 'false');
    feedback.textContent = '';
    button.focus();
  });
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (busy) return;
    if (!reason.value.trim()) {
      feedback.textContent = 'Please enter a reason.';
      reason.focus();
      return;
    }
    busy = true;
    confirm.disabled = true;
    cancel.disabled = true;
    button.disabled = true;
    feedback.textContent = 'Saving…';
    let saved = false;
    try {
      // Fetch a fresh token for the current login session before each action.
      csrf = await request('/api/csrf');
      await request(path, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
        body: JSON.stringify({ reason: reason.value.trim() })
      });
      saved = true;
      feedback.textContent = `${label} completed.`;
      notice.textContent = `${label} completed.`;
      await Promise.all([load('listings'), load('users'), loadActions(), loadReports()]);
    } catch (error) {
      const message = saved
        ? `${label} completed, but the page could not refresh. Reload to see the result.`
        : error.message;
      feedback.textContent = message;
      notice.textContent = message;
    } finally {
      busy = false;
      confirm.disabled = saved;
      button.disabled = saved;
      cancel.disabled = false;
    }
  });
}

async function load(kind) {
  const result = await request(`/api/admin/${kind}?page=${pages[kind]}&size=20`);
  const box = document.getElementById(kind);
  box.replaceChildren();
  if (!result.content.length) add(box, 'p', 'Nothing to show.');
  for (const item of result.content) {
    const row = add(box, 'div', '', 'row');
    const details = add(row, 'div', '', 'details');
    if (kind === 'listings') {
      add(details, 'strong', `#${item.id} ${item.produceName} — ${item.status}`);
      add(details, 'small', `${item.farmerName} · ${item.ownerEmail} · ${item.pickupArea}`);
      if (!['HIDDEN','CLOSED'].includes(item.status)) actionButton(row, 'Hide', `/api/admin/listings/${item.id}/hide`, true);
      if (item.status === 'HIDDEN') actionButton(row, 'Restore', `/api/admin/listings/${item.id}/restore`, false);
    } else {
      add(details, 'strong', `#${item.id} ${item.displayName} — ${item.suspended ? 'SUSPENDED' : item.role}`);
      add(details, 'small', item.email);
      if (item.role !== 'ADMIN') actionButton(row, item.suspended ? 'Reinstate' : 'Suspend', `/api/admin/users/${item.id}/${item.suspended ? 'reinstate' : 'suspend'}`, !item.suspended);
    }
  }
  document.getElementById(`${kind}-page`).textContent = `Page ${pages[kind] + 1} of ${Math.max(result.totalPages, 1)}`;
  document.getElementById(`${kind}-prev`).disabled = pages[kind] === 0;
  document.getElementById(`${kind}-next`).disabled = result.last;
}

async function loadActions() {
  const result = await request('/api/admin/actions?size=20');
  const box = document.getElementById('actions');
  box.replaceChildren();
  for (const item of result.content) {
    const row = add(box, 'div', '', 'row');
    const details = add(row, 'div', '', 'details');
    add(details, 'strong', `${item.action} · ${item.targetType} #${item.targetId}`);
    add(details, 'small', `${item.actorEmail} · ${new Date(item.createdAt).toLocaleString()} · ${item.reason}`);
  }
  if (!result.content.length) add(box, 'p', 'No actions yet.');
}

for (const kind of ['listings', 'users']) {
  for (const direction of ['prev', 'next']) {
    document.getElementById(`${kind}-${direction}`).addEventListener('click', () => {
      pages[kind] += direction === 'next' ? 1 : -1;
      load(kind).catch(error => { notice.textContent = error.message; });
    });
  }
}
let reportPage = 0;
async function loadReports() {
  const status = document.getElementById('reports-status').value;
  const result = await request(`/api/admin/reports?status=${status}&page=${reportPage}&size=20`);
  if (reportPage > 0 && result.content.length === 0) { reportPage--; return loadReports(); }
  const box = document.getElementById('reports');
  box.replaceChildren();
  if (!result.content.length) add(box, 'p', 'No reports in this category.');
  for (const item of result.content) {
    const row = add(box, 'div', '', 'row');
    const details = add(row, 'div', '', 'details');
    add(details, 'strong', `Report #${item.id} · ${item.produceName} (listing #${item.listingId})`);
    add(details, 'small', `Owner: ${item.ownerEmail} · Reported by: ${item.reporterEmail}`);
    add(details, 'small', `${item.pickupArea} · ₹${item.pricePerKg}/kg · Listing ${item.listingStatus}`);
    const photo = document.createElement('img');
    photo.src = `/api/listings/${item.listingId}/photo`;
    photo.alt = `Photo for reported listing: ${item.produceName}`;
    photo.loading = 'lazy';
    photo.style.cssText = 'display:block;max-width:220px;max-height:160px;object-fit:contain;margin-top:10px';
    photo.addEventListener('error', () => photo.remove(), {once:true});
    details.append(photo);
    add(details, 'p', item.reason);
    add(details, 'small', new Date(item.createdAt).toLocaleString());
    if (item.status === 'OPEN') {
      const controls = add(row, 'div', '', 'order-actions');
      actionButton(controls, 'Dismiss report', `/api/admin/reports/${item.id}/dismiss`, false);
      actionButton(controls, 'Hide listing and resolve', `/api/admin/reports/${item.id}/hide`, true);
    } else {
      add(details, 'small', `${item.status} · ${item.resolvedBy} · ${item.resolutionReason}`);
    }
  }
  document.getElementById('reports-page').textContent = `Page ${reportPage + 1} of ${Math.max(result.totalPages, 1)}`;
  document.getElementById('reports-prev').disabled = reportPage === 0;
  document.getElementById('reports-next').disabled = result.last;
}
document.getElementById('reports-status').addEventListener('change', () => {
  reportPage = 0; loadReports().catch(error => notice.textContent = error.message);
});
for (const direction of ['prev', 'next']) {
  document.getElementById(`reports-${direction}`).addEventListener('click', () => {
    reportPage += direction === 'next' ? 1 : -1;
    loadReports().catch(error => notice.textContent = error.message);
  });
}

Promise.all([load('listings'), load('users'), loadActions(), loadReports()]).catch(error => {
  notice.textContent = `${error.message}. Some sections could not load. Refresh after correcting the error.`;
});

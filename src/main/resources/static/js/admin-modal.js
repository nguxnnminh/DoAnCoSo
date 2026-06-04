/*
 * Admin AJAX modal — opens create / edit / detail screens as an overlay dialog
 * instead of navigating to a standalone page.
 *
 * Usage from a list page:
 *   <a href="/admin/categories/create" data-modal>Add New</a>
 *   <a href="/admin/categories/5/edit" data-modal>Edit</a>
 *
 * The server returns a fragment containing an element marked
 * [data-modal-content]. Inside it, a <form data-modal-form> is submitted via
 * fetch; the controller answers AJAX submits with JSON:
 *   { "ok": true,  "message": "Saved" }            → close + toast + refresh list
 *   { "ok": false, "error":   "Slug exists" }       → show inline error, stay open
 *
 * Wide layout: add data-modal-wide to the trigger for an 880px box (products).
 */
(function () {
    const overlay = document.getElementById('ajaxModal');
    const box = document.getElementById('ajaxModalBox');
    if (!overlay || !box) return;

    const LOADING_HTML = '<div class="modal-loading"><span class="modal-spinner"></span> Loading…</div>';

    function csrf() {
        const token = document.querySelector('meta[name="csrf-token"]');
        const header = document.querySelector('meta[name="csrf-header"]');
        return {
            header: header ? header.getAttribute('content') : 'X-CSRF-TOKEN',
            token: token ? token.getAttribute('content') : ''
        };
    }

    function openOverlay() {
        overlay.classList.add('open');
        document.body.style.overflow = 'hidden';
    }

    function closeOverlay() {
        // Let a fragment that registered listeners (e.g. the product form) tear
        // them down, mirroring how admin-spa.js cleans up on navigation.
        if (typeof window.__adminPageCleanup === 'function') {
            try { window.__adminPageCleanup(); } catch (_) {}
            window.__adminPageCleanup = null;
        }
        overlay.classList.remove('open');
        document.body.style.overflow = '';
        // Reset to the spinner so the next open never flashes stale content.
        setTimeout(() => { box.innerHTML = LOADING_HTML; }, 200);
    }

    function toast(type, message) {
        const el = document.createElement('div');
        el.className = 'toast ' + (type === 'error' ? 'toast-error' : 'toast-success');
        el.innerHTML = '<span>' + (message || '') + '</span>';
        document.body.appendChild(el);
        setTimeout(() => {
            el.style.opacity = '0';
            el.style.transform = 'translateX(20px)';
            el.style.transition = 'all 0.3s';
            setTimeout(() => el.remove(), 300);
        }, 3000);
    }

    // Run any <script> tags that came inside the fetched fragment (auto-slug, etc.)
    function runScripts(container) {
        container.querySelectorAll('script').forEach(old => {
            const s = document.createElement('script');
            Array.from(old.attributes).forEach(a => s.setAttribute(a.name, a.value));
            s.textContent = old.textContent;
            old.replaceWith(s);
        });
    }

    function applyWidth(wide) {
        box.classList.toggle('modal-box--wide', !!wide);
    }

    function showError(msg) {
        const form = box.querySelector('[data-modal-form]');
        const host = box.querySelector('[data-modal-content]') || box;
        let banner = host.querySelector('.modal-error');
        if (!banner) {
            banner = document.createElement('div');
            banner.className = 'modal-error';
            const body = box.querySelector('.modal-form-body') || (form ? form.parentElement : host);
            body.insertBefore(banner, body.firstChild);
        }
        banner.textContent = msg;
        banner.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }

    async function loadInto(url, wide) {
        if (typeof window.__adminPageCleanup === 'function') {
            try { window.__adminPageCleanup(); } catch (_) {}
            window.__adminPageCleanup = null;
        }
        applyWidth(wide);
        box.innerHTML = LOADING_HTML;
        openOverlay();
        try {
            const res = await fetch(url, {
                headers: { 'X-Requested-With': 'XMLHttpRequest', 'Accept': 'text/html' },
                credentials: 'same-origin'
            });
            if (!res.ok) throw new Error('HTTP ' + res.status);
            const html = await res.text();
            const doc = new DOMParser().parseFromString(html, 'text/html');
            const content = doc.querySelector('[data-modal-content]');
            if (!content) {
                // Server didn't return a modal fragment — fall back to a real page load.
                window.location.href = url;
                return;
            }
            box.innerHTML = content.outerHTML;
            runScripts(box);
            const focusable = box.querySelector('input:not([type=hidden]), select, textarea');
            if (focusable) focusable.focus();
        } catch (e) {
            console.warn('Modal load failed:', e);
            window.location.href = url;
        }
    }

    async function submitForm(form) {
        const submitBtn = form.querySelector('[type=submit]');
        const oldLabel = submitBtn ? submitBtn.innerHTML : null;
        if (submitBtn) { submitBtn.disabled = true; submitBtn.innerHTML = 'Saving…'; }

        const old = box.querySelector('.modal-error');
        if (old) old.remove();

        try {
            const c = csrf();
            const headers = { 'X-Requested-With': 'XMLHttpRequest', 'Accept': 'application/json' };
            if (c.token) headers[c.header] = c.token;

            const res = await fetch(form.getAttribute('action') || window.location.href, {
                method: (form.getAttribute('method') || 'post').toUpperCase(),
                headers,
                body: new FormData(form),
                credentials: 'same-origin'
            });

            let data = null;
            const ct = res.headers.get('content-type') || '';
            if (ct.includes('application/json')) data = await res.json();

            if (res.ok && (!data || data.ok !== false)) {
                closeOverlay();
                toast('success', (data && data.message) || 'Saved successfully.');
                if (typeof window.adminSpaReload === 'function') {
                    window.adminSpaReload();
                } else {
                    window.location.reload();
                }
                return;
            }
            showError((data && data.error) || 'Could not save. Please check the form and try again.');
        } catch (e) {
            console.warn('Modal submit failed:', e);
            showError('Network error. Please try again.');
        } finally {
            if (submitBtn) { submitBtn.disabled = false; submitBtn.innerHTML = oldLabel; }
        }
    }

    // ---- Event delegation (survives SPA content swaps) ----

    document.addEventListener('click', function (e) {
        // Open trigger
        const trigger = e.target.closest('a[data-modal], button[data-modal]');
        if (trigger) {
            const url = trigger.getAttribute('href') || trigger.getAttribute('data-modal-url');
            if (url) {
                e.preventDefault();
                e.stopPropagation();
                loadInto(url, trigger.hasAttribute('data-modal-wide'));
            }
            return;
        }
        // Close trigger (X button, Cancel, backdrop)
        if (e.target.closest('[data-modal-close]')) {
            e.preventDefault();
            closeOverlay();
            return;
        }
        if (e.target === overlay) {
            closeOverlay();
        }
    });

    // Submit inside the modal
    document.addEventListener('submit', function (e) {
        const form = e.target;
        if (!(form instanceof HTMLFormElement)) return;
        if (!form.matches('[data-modal-form]')) return;
        e.preventDefault();
        e.stopPropagation();
        submitForm(form);
    });

    // Escape closes
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && overlay.classList.contains('open')) {
            closeOverlay();
        }
    });

    // Auto-open from ?modal=create | ?modal=edit&id=… (fallback when a create/edit
    // URL is opened directly without JS-driven navigation). Builds the form URL
    // from the current list path so it stays module-agnostic.
    (function autoOpenFromQuery() {
        const params = new URLSearchParams(window.location.search);
        const mode = params.get('modal');
        if (!mode) return;
        const base = window.location.pathname.replace(/\/+$/, '');
        const id = params.get('id');
        let url = null;
        if (mode === 'create') url = base + '/create';
        else if (mode === 'edit' && id) url = base + '/' + id + '/edit';
        else if (mode === 'order' && id) url = base + '/' + id;   // order detail (view)
        if (url) loadInto(url, params.get('wide') === '1');
    })();
})();

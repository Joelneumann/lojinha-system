package de.joelneumann.lojinha.ui.utils

@JsFun("""
() => {
    if (typeof navigator === 'undefined') return false;
    const ua = navigator.userAgent || '';
    const isIos = /iPad|iPhone|iPod/.test(ua) ||
        (navigator.platform === 'MacIntel' && (navigator.maxTouchPoints || 0) > 1);
    if (!isIos) return false;
    // Exclude third-party iOS browsers (Chrome CriOS, Firefox FxiOS, Edge EdgiOS, Opera OPiOS, DuckDuckGo)
    // so only iOS Safari and iOS Safari standalone PWAs use the synchronous proxy path.
    const isThirdPartyIosBrowser = /CriOS|FxiOS|EdgiOS|OPiOS|DuckDuckGo/.test(ua);
    return !isThirdPartyIosBrowser;
}
""")
private external fun jsIsIosSafari(): Boolean

@JsFun("""
(allowDecimals) => {
    if (typeof document === 'undefined' || typeof navigator === 'undefined') return;
    const hasTouch = (navigator.maxTouchPoints || 0) > 0 || ('ontouchstart' in window);
    if (!hasTouch) return;

    const inputMode = allowDecimals ? 'decimal' : 'numeric';
    let proxy = document.getElementById('mobile-keyboard-proxy');
    if (!proxy) {
        proxy = document.createElement('input');
        proxy.id = 'mobile-keyboard-proxy';
        proxy.type = 'text';
        proxy.setAttribute('aria-hidden', 'true');
        proxy.tabIndex = -1;
        proxy.autocomplete = 'off';
        proxy.autocapitalize = 'off';
        proxy.spellcheck = false;
        proxy.style.position = 'fixed';
        proxy.style.top = '0px';
        proxy.style.left = '0px';
        proxy.style.width = '1px';
        proxy.style.height = '1px';
        proxy.style.opacity = '0';
        proxy.style.border = '0';
        proxy.style.padding = '0';
        proxy.style.margin = '0';
        proxy.style.fontSize = '16px';
        proxy.style.pointerEvents = 'none';
        document.body.appendChild(proxy);
    }
    proxy.inputMode = inputMode;
    if (document.activeElement !== proxy) {
        proxy.focus({ preventScroll: true });
    }
}
""")
private external fun jsPrimeIosSafariKeyboardSync(allowDecimals: Boolean)

@JsFun("""
() => {
    if (typeof window === 'undefined' || typeof document === 'undefined' || typeof navigator === 'undefined') return;
    if (window.__lojinhaChromeDismissBlurInstalled) return;
    const hasTouch = (navigator.maxTouchPoints || 0) > 0 || ('ontouchstart' in window);
    if (!hasTouch || !window.visualViewport) return;

    window.__lojinhaChromeDismissBlurInstalled = true;
    const vv = window.visualViewport;
    let lastWidth = vv.width;
    let maxHeightForWidth = vv.height;
    let keyboardWasOpen = false;

    vv.addEventListener('resize', () => {
        const w = vv.width;
        const h = vv.height;
        if (Math.abs(w - lastWidth) >= 20) {
            lastWidth = w;
            maxHeightForWidth = h;
            keyboardWasOpen = false;
            return;
        }
        if (h > maxHeightForWidth) {
            maxHeightForWidth = h;
        }
        if ((maxHeightForWidth - h) >= 120) {
            keyboardWasOpen = true;
        } else if (keyboardWasOpen && h >= (maxHeightForWidth - 60)) {
            keyboardWasOpen = false;
            const active = document.activeElement;
            if (
                active &&
                (active.tagName === 'TEXTAREA' || active.tagName === 'INPUT') &&
                active.parentElement === document.body &&
                active.id !== 'mobile-keyboard-proxy'
            ) {
                active.blur();
            }
        }
    });
}
""")
private external fun jsInstallNonSafariKeyboardDismissBlur()

private val cachedUsesIosSafariKeyboardProxy: Boolean by lazy { jsIsIosSafari() }

actual fun usesIosSafariKeyboardProxy(): Boolean = cachedUsesIosSafariKeyboardProxy

actual fun notifyTextFieldTapGesture(allowDecimals: Boolean) {
    if (cachedUsesIosSafariKeyboardProxy) {
        jsPrimeIosSafariKeyboardSync(allowDecimals)
    }
}

actual fun ensureMobileKeyboardDismissHandlerInstalled() {
    if (!cachedUsesIosSafariKeyboardProxy) {
        jsInstallNonSafariKeyboardDismissBlur()
    }
}

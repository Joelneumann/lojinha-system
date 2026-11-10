package de.joelneumann.lojinha.ui.utils

@JsFun("""
(enable) => {
    if (typeof window === 'undefined') return;
    if (enable) {
        window.onbeforeunload = (e) => {
            e.preventDefault();
            e.returnValue = '';
            return '';
        };
    } else {
        window.onbeforeunload = null;
    }
}
""")
private external fun jsSetBeforeUnload(enable: Boolean)

actual fun setBrowserBeforeUnloadWarning(enable: Boolean) {
    jsSetBeforeUnload(enable)
}

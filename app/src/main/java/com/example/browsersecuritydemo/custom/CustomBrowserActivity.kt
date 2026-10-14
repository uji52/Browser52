package com.example.browsersecuritydemo.custom

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.browsersecuritydemo.adapter.LogAdapter
import com.example.browsersecuritydemo.data.SecurityLogRepository
import com.example.browsersecuritydemo.databinding.ActivityCustomBrowserBinding
import com.example.browsersecuritydemo.model.InterceptedData
import com.example.browsersecuritydemo.webview.InputInterceptorBridge
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class CustomBrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomBrowserBinding
    private val logAdapter = LogAdapter()
    private val allLogs = mutableListOf<InterceptedData>()
    private var currentTab = "ALL" // "ALL", "INPUT", "REQUEST", "COOKIE"
    private var isTamperingEnabled = false

    private val logListener: (InterceptedData) -> Unit = { data ->
        runOnUiThread {
            if (data.source == "CustomBrowser") {
                allLogs.add(0, data)
                applyTabFilter()
                binding.tvCustomLatestVal.text = "[${data.eventType}] ${data.fieldName} = ${data.value}"
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val targetUrl = intent.getStringExtra(EXTRA_URL) ?: "https://www.uji52.com"
        binding.etCustomUrl.setText(targetUrl)

        setupRecyclerView()
        setupListeners()
        setupWebView()

        onBackPressedDispatcher.addCallback(this) {
            if (binding.customWebView.canGoBack()) {
                binding.customWebView.goBack()
            } else {
                finish()
            }
        }

        binding.customWebView.loadUrl(targetUrl)
    }

    override fun onStart() {
        super.onStart()
        SecurityLogRepository.addListener(logListener)
    }

    override fun onStop() {
        super.onStop()
        SecurityLogRepository.removeListener(logListener)
    }

    private fun setupRecyclerView() {
        binding.rvCustomLogs.apply {
            layoutManager = LinearLayoutManager(this@CustomBrowserActivity)
            adapter = logAdapter
        }
    }

    private fun setupListeners() {
        binding.btnCustomGo.setOnClickListener {
            val url = binding.etCustomUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                val fullUrl = if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("file://")) {
                    "https://$url"
                } else {
                    url
                }
                binding.etCustomUrl.setText(fullUrl)
                binding.customWebView.loadUrl(fullUrl)
            }
        }

        binding.switchTamperHtml.setOnCheckedChangeListener { _, isChecked ->
            isTamperingEnabled = isChecked
            val message = if (isChecked) {
                "HTML改ざんモードを有効化しました。ページ更新時に悪意ある偽装バナーが強制挿入されます。"
            } else {
                "HTML改ざんモードを無効化しました。"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        binding.btnExtractCookie.setOnClickListener {
            extractCookies()
        }

        binding.btnClearCustomLogs.setOnClickListener {
            allLogs.clear()
            logAdapter.updateList(emptyList())
            binding.tvCustomLatestVal.text = "[ログをクリアしました]"
        }

        binding.btnCustomExplain.setOnClickListener {
            showThreatDialog()
        }

        // Tabs
        binding.tvTabInput.setOnClickListener {
            setTab("INPUT")
        }
        binding.tvTabNetwork.setOnClickListener {
            setTab("REQUEST")
        }
        binding.tvTabCookies.setOnClickListener {
            setTab("COOKIE")
        }
    }

    private fun setTab(tab: String) {
        currentTab = tab
        binding.tvTabInput.setTextColor(if (tab == "INPUT") Color.parseColor("#FF5252") else Color.parseColor("#AAAAAA"))
        binding.tvTabNetwork.setTextColor(if (tab == "REQUEST") Color.parseColor("#00B0FF") else Color.parseColor("#AAAAAA"))
        binding.tvTabCookies.setTextColor(if (tab == "COOKIE") Color.parseColor("#E040FB") else Color.parseColor("#AAAAAA"))
        applyTabFilter()
    }

    private fun applyTabFilter() {
        val filtered = when (currentTab) {
            "INPUT" -> allLogs.filter { it.eventType == "INPUT" || it.eventType == "KEYPRESS" || it.eventType == "SUBMIT" }
            "REQUEST" -> allLogs.filter { it.eventType == "REQUEST" }
            "COOKIE" -> allLogs.filter { it.eventType == "COOKIE" }
            else -> allLogs
        }
        logAdapter.updateList(filtered)
    }

    private fun extractCookies() {
        val currentUrl = binding.customWebView.url ?: ""
        if (currentUrl.isNotEmpty()) {
            val cookieManager = CookieManager.getInstance()
            val cookies = cookieManager.getCookie(currentUrl) ?: "(クッキーなし)"
            val data = InterceptedData(
                source = "CustomBrowser",
                eventType = "COOKIE",
                fieldName = "CookieManager (ネイティブ抽出)",
                fieldType = "cookie",
                value = cookies,
                pageUrl = currentUrl
            )
            SecurityLogRepository.addLog(data)
            Toast.makeText(this, "Cookieを取得しました: $cookies", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        with(binding.customWebView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        // FIDO / WebAuthn (Passkeys) サポートの有効化 (ブラウザモード)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) {
            WebSettingsCompat.setWebAuthenticationSupport(
                binding.customWebView.settings,
                WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER
            )
        }

        val bridge = InputInterceptorBridge(source = "CustomBrowser")
        binding.customWebView.addJavascriptInterface(bridge, InputInterceptorBridge.INTERFACE_NAME)

        binding.customWebView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    binding.customProgressBar.visibility = View.VISIBLE
                    binding.customProgressBar.progress = newProgress
                } else {
                    binding.customProgressBar.visibility = View.GONE
                }
            }
        }

        binding.customWebView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.etCustomUrl.setText(url ?: "")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.etCustomUrl.setText(url ?: "")

                // 入力値傍受スクリプトの注入
                view?.evaluateJavascript(InputInterceptorBridge.INJECTION_SCRIPT, null)

                // HTML改ざんモードがONの場合、偽装フィッシングバナーを動的注入
                if (isTamperingEnabled) {
                    val tamperScript = """
                        (function() {
                            var banner = document.createElement('div');
                            banner.id = '__tamper_alert_banner__';
                            banner.style.cssText = 'background: #B71C1C; color: #FFF; padding: 14px; text-align: center; font-weight: bold; font-size: 14px; position: fixed; top: 0; left: 0; width: 100%; z-index: 999999; box-shadow: 0 4px 10px rgba(0,0,0,0.5);';
                            banner.innerHTML = '⚠️【独自実装ブラウザによるHTML改ざんデモ】<br><span style="font-size:12px;font-weight:normal;">親アプリがWebページのDOMを自由に改変しています。偽の振込先やフィッシングフォームを挿入可能です。</span>';
                            document.body.insertBefore(banner, document.body.firstChild);
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(tamperScript, null)
                }

                // Cookie抽出も自動実行
                extractCookies()
            }

            // 通信リクエストの完全傍受
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val reqUrl = request?.url?.toString() ?: ""
                val method = request?.method ?: "GET"

                if (reqUrl.isNotEmpty()) {
                    val reqData = InterceptedData(
                        source = "CustomBrowser",
                        eventType = "REQUEST",
                        fieldName = method,
                        fieldType = "HTTP Request",
                        value = reqUrl,
                        pageUrl = view?.url ?: reqUrl
                    )
                    SecurityLogRepository.addLog(reqData)
                }

                return super.shouldInterceptRequest(view, request)
            }
        }
    }

    private fun showThreatDialog() {
        AlertDialog.Builder(this)
            .setTitle("🚨 独自実装ブラウザの致命的な脅威")
            .setMessage(
                """
                【アプリ独自実装ブラウザが危険な理由】

                1. 通信の盗聴と改ざん (shouldInterceptRequest / プロキシ):
                親アプリはユーザーが読み込むHTML、画像、JavaScript、API通信をすべてインターセプトできます。正規のWebコンテンツを書き換え、偽の入力フォームや偽の振込先口座番号を表示させる攻撃（中間者攻撃・UIスプーフィング）が容易に行えます。

                2. 認証情報（Cookie / セッショントークン）の強奪:
                CookieManagerを介して、銀行やSNS、クラウドサービスのログイン済みセッションCookieをネイティブ層がそのまま外部サーバーへ送信できます。

                3. 入力値・キーストロークの完全ロギング:
                WebViewと同様、DOMインジェクションにより、ユーザーが指で画面をタップして入力する内容が1文字残らずアプリ開発者に筒抜けになります。

                【結論】:
                機密性の高いWeb認証（OAuth認証、オンラインバンキング、クレジットカード決済等）では、アプリ独自実装ブラウザやWebViewを決して使用せず、Chrome Custom Tabs (CCT) や Trusted Web Activity (TWA) などの安全なブラウザ分離技術を使用するべきです。
                """.trimIndent()
            )
            .setPositiveButton("理解した", null)
            .show()
    }

    companion object {
        const val EXTRA_URL = "extra_target_url"
    }
}

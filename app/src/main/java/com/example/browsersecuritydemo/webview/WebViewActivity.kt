package com.example.browsersecuritydemo.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.browsersecuritydemo.adapter.LogAdapter
import com.example.browsersecuritydemo.data.SecurityLogRepository
import com.example.browsersecuritydemo.databinding.ActivityWebViewBinding
import com.example.browsersecuritydemo.model.InterceptedData

class WebViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebViewBinding
    private val logAdapter = LogAdapter()
    private val currentLogs = mutableListOf<InterceptedData>()

    private val logListener: (InterceptedData) -> Unit = { data ->
        runOnUiThread {
            if (data.source == "WebView") {
                currentLogs.add(0, data)
                logAdapter.updateList(currentLogs)
                binding.tvInterceptCount.text = "傍受件数: ${currentLogs.size}"
                binding.tvLatestKey.text = data.fieldName
                binding.tvLatestVal.text = data.value
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val targetUrl = intent.getStringExtra(EXTRA_URL) ?: "https://www.uji52.com"

        setupRecyclerView()
        setupListeners()
        setupWebView()

        onBackPressedDispatcher.addCallback(this) {
            if (binding.webView.canGoBack()) {
                binding.webView.goBack()
            } else {
                finish()
            }
        }

        binding.webView.loadUrl(targetUrl)
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
        binding.rvConsoleLogs.apply {
            layoutManager = LinearLayoutManager(this@WebViewActivity)
            adapter = logAdapter
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            if (binding.webView.canGoBack()) binding.webView.goBack()
            else finish()
        }

        binding.btnForward.setOnClickListener {
            if (binding.webView.canGoForward()) binding.webView.goForward()
        }

        binding.btnReload.setOnClickListener {
            binding.webView.reload()
        }

        binding.btnClearConsole.setOnClickListener {
            currentLogs.clear()
            logAdapter.updateList(emptyList())
            binding.tvInterceptCount.text = "傍受件数: 0"
            binding.tvLatestKey.text = "[クリア済]"
            binding.tvLatestVal.text = ""
        }

        binding.btnExplain.setOnClickListener {
            showSecurityExplanationDialog()
        }

        binding.btnFidoDetails.setOnClickListener {
            showFidoInfoDialog()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        with(binding.webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        // FIDO / WebAuthn (Passkeys) サポートの設定 (FOR_APP)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) {
            WebSettingsCompat.setWebAuthenticationSupport(
                binding.webView.settings,
                WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP
            )
            binding.tvFidoStatus.text = "🔑 FIDO (WebAuthn): 有効化済 (WEB_AUTHENTICATION_SUPPORT_FOR_APP)"
            binding.layoutFidoStatus.setBackgroundColor(android.graphics.Color.parseColor("#1B5E20"))
        } else {
            binding.tvFidoStatus.text = "⚠️ FIDO: 端末WebView未対応 (要Android 14+ / WebView 124+)"
            binding.layoutFidoStatus.setBackgroundColor(android.graphics.Color.parseColor("#B71C1C"))
        }

        // JavaScriptインターフェースの登録（アプリとJSの通信ブリッジ）
        val bridge = InputInterceptorBridge(source = "WebView")
        binding.webView.addJavascriptInterface(bridge, InputInterceptorBridge.INTERFACE_NAME)

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.progressBar.progress = newProgress
                } else {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.tvCurrentUrl.text = url ?: ""
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.tvCurrentUrl.text = url ?: ""
                
                // ページ読み込み完了時に入力監視用JavaScriptコードをWebView内部に注入
                view?.evaluateJavascript(InputInterceptorBridge.INJECTION_SCRIPT, null)
            }
        }
    }

    private fun showSecurityExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ WebViewのセキュリティ懸念について")
            .setMessage(
                """
                【なぜ入力値がアプリ側に筒抜けになるのか？】
                
                1. DOMへの完全アクセス権:
                WebViewは親アプリのプロセス（または制御下）で動作します。親アプリは evaluateJavascript() を呼ぶことで、Webページ内のDOMに任意のスクリプトを自由に注入できます。
                
                2. パスワードや機密情報の傍受:
                たとえ <input type="password"> やクレジットカード番号入力欄であっても、親アプリが注入したスクリプトにより、1文字打つごとのキーストローク（キーロガー）や送信前の値が JavascriptInterface 経由で親アプリに丸見えになります。
                
                3. Cookieやトークンの抽出:
                親アプリは CookieManager や JS からセッションIDやログインCookieを直接取得できます。
                
                【対策 / CCTとの違い】:
                Google Chrome等の「Chrome Custom Tabs (CCT)」を使用すると、ブラウザが別プロセスのサンドボックスで動作するため、親アプリ側からWebページのDOMや入力内容を傍受することは構造上不可能です。
                """.trimIndent()
            )
            .setPositiveButton("閉じる", null)
            .show()
    }

    private fun showFidoInfoDialog() {
        val isSupported = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)
        val statusText = if (isSupported) "✅ 有効 (WEB_AUTHENTICATION_SUPPORT_FOR_APP)" else "❌ 端末WebViewが未対応"

        AlertDialog.Builder(this)
            .setTitle("🔑 WebView FIDO診断 & トラブルシューティング")
            .setMessage(
                """
                【WebView FIDO 設定状況】
                • WebSettingsCompat: $statusText
                • Package: com.uji52.webviewfido
                • SHA-256: 0C:BA:1F:D1:69:A1:8B:A0:E5:D1:6A:62:40:DA:E1:A3:4D:4E:49:55:E7:33:78:82:3B:D7:B5:23:07:B9:FC:3D

                【クラッシュ / 停止の原因】
                FOR_BROWSERモードはシステム特権(CREDENTIAL_MANAGER_SET_ORIGIN)を要求するため、一般アプリではOSによりSecurityExceptionで強制終了されます。安全のためFOR_APPモードに固定しました。

                【NotReadableError の根本原因と解決策】
                WebViewでのFIDO(WebAuthn)は、OSのCredential Managerがサーバー側の assetlinks.json を検証します。
                現在、https://www.uji52.com/.well-known/assetlinks.json が【HTTP 403 AccessDenied】となっているため、検証が中断されNotReadableErrorが発生しています。

                👉 解決手順:
                CloudFront / S3 側で /.well-known/assetlinks.json へのアクセス権限（403拒絶）を解除し、パブリックに取得できるようにしてください。
                """.trimIndent()
            )
            .setPositiveButton("OK", null)
            .show()
    }

    companion object {
        const val EXTRA_URL = "extra_target_url"
    }
}

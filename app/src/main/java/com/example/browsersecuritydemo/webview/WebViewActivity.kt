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

    companion object {
        const val EXTRA_URL = "extra_target_url"
    }
}

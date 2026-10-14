package com.example.browsersecuritydemo

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.browsersecuritydemo.adapter.LogAdapter
import com.example.browsersecuritydemo.cct.CustomTabsLauncher
import com.example.browsersecuritydemo.custom.CustomBrowserActivity
import com.example.browsersecuritydemo.data.SecurityLogRepository
import com.example.browsersecuritydemo.databinding.ActivityMainBinding
import com.example.browsersecuritydemo.model.InterceptedData
import com.example.browsersecuritydemo.twa.TwaHelper
import com.example.browsersecuritydemo.webview.WebViewActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val mainLogAdapter = LogAdapter()

    private val logListener: (InterceptedData) -> Unit = {
        runOnUiThread {
            refreshLogs()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        refreshLogs()
        SecurityLogRepository.addListener(logListener)
    }

    override fun onPause() {
        super.onPause()
        SecurityLogRepository.removeListener(logListener)
    }

    private fun setupRecyclerView() {
        binding.rvMainLogs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = mainLogAdapter
        }
    }

    private fun refreshLogs() {
        val logs = SecurityLogRepository.getLogs()
        mainLogAdapter.updateList(logs)
        if (logs.isEmpty()) {
            binding.tvEmptyLogs.visibility = View.VISIBLE
            binding.rvMainLogs.visibility = View.GONE
        } else {
            binding.tvEmptyLogs.visibility = View.GONE
            binding.rvMainLogs.visibility = View.VISIBLE
        }
    }

    private fun setupListeners() {
        // Presets
        binding.btnPresetDemo.setOnClickListener {
            binding.etUrl.setText("file:///android_asset/demo_login.html")
        }

        binding.btnPresetExample.setOnClickListener {
            binding.etUrl.setText("https://www.uji52.com")
        }

        // 1. Chrome Custom Tabs (CCT)
        binding.btnLaunchCct.setOnClickListener {
            val url = getTargetUrl()
            if (url.startsWith("file:///")) {
                Toast.makeText(
                    this,
                    "⚠️ CCTはローカルファイル(file://)の読み込みをサポートしていません。HTTP/HTTPSのURLを指定してください。",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            Toast.makeText(
                this,
                "🛡️ Chrome Custom Tabsを起動します。Chromeのサンドボックス内で動作するため、親アプリによる入力値の取得は一切不可能です。",
                Toast.LENGTH_SHORT
            ).show()
            CustomTabsLauncher.launch(this, url)
        }

        // 2. Trusted Web Activity (TWA)
        binding.btnLaunchTwa.setOnClickListener {
            val url = getTargetUrl()
            if (url.startsWith("file:///")) {
                Toast.makeText(
                    this,
                    "⚠️ TWAはローカルファイル(file://)の読み込みをサポートしていません。HTTP/HTTPSのURLを指定してください。",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            Toast.makeText(
                this,
                "🛡️ TWAを起動します。CCTベースで完全サンドボックス保護されるため、親アプリによる入力値の取得は一切不可能です。",
                Toast.LENGTH_SHORT
            ).show()
            TwaHelper.launch(this, url)
        }

        // 3. WebView
        binding.btnLaunchWebView.setOnClickListener {
            val url = getTargetUrl()
            Toast.makeText(
                this,
                "⚠️ WebViewを起動します。アプリ側からJavaScript注入を行い、入力値をリアルタイム傍受します。",
                Toast.LENGTH_SHORT
            ).show()
            val intent = Intent(this, WebViewActivity::class.java).apply {
                putExtra(WebViewActivity.EXTRA_URL, url)
            }
            startActivity(intent)
        }

        // 4. Custom Implementation
        binding.btnLaunchCustom.setOnClickListener {
            val url = getTargetUrl()
            Toast.makeText(
                this,
                "🚨 独自実装ブラウザを起動します。通信リクエスト・Cookie・入力値の完全傍受およびHTML改ざんを検証します。",
                Toast.LENGTH_SHORT
            ).show()
            val intent = Intent(this, CustomBrowserActivity::class.java).apply {
                putExtra(CustomBrowserActivity.EXTRA_URL, url)
            }
            startActivity(intent)
        }

        // Clear Logs
        binding.btnClearAllLogs.setOnClickListener {
            SecurityLogRepository.clear()
            refreshLogs()
            Toast.makeText(this, "ログを消去しました", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getTargetUrl(): String {
        var url = binding.etUrl.text.toString().trim()
        if (url.isEmpty()) {
            url = "https://www.uji52.com"
            binding.etUrl.setText(url)
        } else if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("file://")) {
            url = "https://$url"
            binding.etUrl.setText(url)
        }
        return url
    }
}

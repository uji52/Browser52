package com.example.browsersecuritydemo.cct

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import com.example.browsersecuritydemo.R

object CustomTabsLauncher {

    /**
     * Chrome Custom Tabs (CCT) で指定URLを起動する。
     * 
     * 【セキュリティ解説】:
     * CCTはGoogle Chrome等のブラウザ別プロセスで動作し、完全なサンドボックス内でレンダリングされます。
     * 親アプリ（本アプリ）からは、Webページ内のDOM、入力フォーム、JavaScriptコンテキスト、
     * Cookie、POSTデータ等にアクセスするAPIは一切提供されておらず、盗聴は不可能です。
     */
    fun launch(context: Context, url: String) {
        val uri = Uri.parse(url)

        val defaultColorScheme = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(ContextCompat.getColor(context, R.color.purple_700))
            .build()

        val customTabsIntent = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(defaultColorScheme)
            .setShowTitle(true)
            .setUrlBarHidingEnabled(false)
            .build()

        // アプリ内から外部ブラウザ(Chrome)を安全に起動
        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        customTabsIntent.launchUrl(context, uri)
    }
}

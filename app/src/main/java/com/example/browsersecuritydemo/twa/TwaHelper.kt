package com.example.browsersecuritydemo.twa

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.trusted.TrustedWebActivityIntentBuilder
import androidx.core.content.ContextCompat
import com.example.browsersecuritydemo.R
import com.google.androidbrowserhelper.trusted.TwaLauncher

object TwaHelper {

    /**
     * Trusted Web Activity (TWA) で指定URLを起動する。
     *
     * 【セキュリティ解説】:
     * TWAはPWA（Progressive Web App）をネイティブアプリのように全画面表示する規格です。
     * 基盤はChrome Custom Tabs (CCT) であり、Chromeの強固なサンドボックス内で実行されます。
     * Webサイト側とアプリ側で「Digital Asset Links」の相互署名・所有権検証が行われますが、
     * 検証済みであっても親アプリ側からWebページのDOM、入力内容、パスワードを直接読み取ることは
     * 完全に禁止されています（安全）。
     */
    fun launch(context: Context, url: String) {
        val uri = Uri.parse(url)

        // TWAはHTTP/HTTPSスキームのみサポート（file:///は不可）
        if (uri.scheme != "http" && uri.scheme != "https") {
            Toast.makeText(
                context,
                "TWAは http:// または https:// のURLのみサポートしています（file:/// は不可）。",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {
            val defaultColorScheme = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(ContextCompat.getColor(context, R.color.purple_700))
                .build()

            val twaBuilder = TrustedWebActivityIntentBuilder(uri)
                .setDefaultColorSchemeParams(defaultColorScheme)

            val launcher = TwaLauncher(context)
            launcher.launch(twaBuilder, null, null, null)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "TWA起動エラー: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

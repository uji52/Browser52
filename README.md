# Browser52 (ブラウザ方式別セキュリティ比較デモアプリ)

URLを指定して以下の4つの手段でブラウズし、親アプリ側からWebページ内の入力値や通信内容が傍受可能かどうかを実証・比較するためのAndroidセキュリティ学習・啓発用アプリケーションです。

1. **Chrome Custom Tabs (CCT)**
2. **Trusted Web Activity (TWA)**
3. **WebView (標準実装)**
4. **独自実装ブラウザ (Custom Browser)**

---

## 🛡️ 背景とセキュリティ上の懸念

Androidアプリ内でWebコンテンツを表示する際、どの技術を採用するかによってセキュリティモデルとプライバシー境界が根本的に異なります。

### 比較サマリー

| ブラウズ方式 | 実行環境 / プロセス | Web入力値 (パスワード等) の傍受 | 通信・Cookieの改ざん/盗聴 | 主な用途 / セキュリティ推奨度 |
|---|---|---|---|---|
| **Chrome Custom Tabs (CCT)** | 独立したブラウザプロセス (Chrome) | ❌ **不可能** (サンドボックス保護) | ❌ **不可能** | **推奨**: OAuth認証、ID連携、外部決済、一般Webリンク |
| **Trusted Web Activity (TWA)** | 独立したブラウザプロセス (Chrome) | ❌ **不可能** (サンドボックス保護) | ❌ **不可能** | **推奨**: 自社PWAサービスの全画面アプリ化 |
| **WebView** | アプリ内プロセス (制御下) | ⚠️ **可能** (DOM操作 / JS注入) | ⚠️ **可能** (CookieManager / インターフェース) | **条件付き**: アプリ内ヘルプや自社完全管理下の静的Web |
| **独自実装ブラウザ** | アプリ内完全制御 | 🚨 **容易に完全傍受** (キーロガー) | 🚨 **容易に完全改ざん** (shouldInterceptRequest) | **極めて危険**: 外部サイトのログインや決済に絶対利用不可 |

---

## 🔍 各方式の実装とデモ内容

### 1. Chrome Custom Tabs (CCT)
- **実装クラス**: `com.example.browsersecuritydemo.cct.CustomTabsLauncher` (`androidx.browser:browser:1.8.0`)
- **動作**:
  - Google Chrome等の外部ブラウザ別プロセスで立ち上がります。
  - Webページ内のDOM、JavaScriptコンテキスト、Cookie、入力フォームのデータはアプリから完全に遮断されています。
  - 親アプリはページの入力値を取得することは一切できません。

### 2. Trusted Web Activity (TWA)
- **実装クラス**: `com.example.browsersecuritydemo.twa.TwaHelper` (`com.google.androidbrowserhelper:androidbrowserhelper:2.5.0`)
- **動作**:
  - Chrome Custom Tabs をベースに、Webサイトとアプリ間で `assetlinks.json` による相互検証を行い、URLバーを隠して全画面表示するプログレッシブWebアプリ (PWA) 向け規格。
  - CCT同様、Chromeの強力なサンドボックス内で動作するため、親アプリがDOMやフォーム入力値を盗聴することはできません。

### 3. WebView (標準実装)
- **実装クラス**: `com.example.browsersecuritydemo.webview.WebViewActivity` (`android.webkit.WebView`)
- **動作とセキュリティリスク**:
  - 親アプリが `WebView.evaluateJavascript()` を用いてDOMに任意のJavaScriptスクリプト（キーロガー・入力監視イベントリスナー）を自動注入します。
  - ユーザーがWebページ上の `<input type="text">` や `<input type="password">`、`<textarea>` に文字を入力すると、**1文字打つごとに親アプリ側のネイティブコンソールへリアルタイムに送信・表示**されます。
  - フォーム送信時には送信データ（JSON）全体がキャプチャされます。
  - **悪意のあるアプリやサードパーティSDKが紛れ込んだアプリでWebViewを使うと、ユーザーのログインIDやパスワード、クレカ情報が丸見えになる重大なセキュリティリスクを視覚的に実証します。**

### 4. 独自実装ブラウザ (Custom Browser)
- **実装クラス**: `com.example.browsersecuritydemo.custom.CustomBrowserActivity`
- **動作とセキュリティリスク**:
  - **通信インターセプト (`shouldInterceptRequest`)**: WebページがロードするHTTP/HTTPSリクエストをネイティブ層ですべてインターセプトして一覧表示。
  - **Cookie完全掌握**: `CookieManager` を介してセッションCookieやトークンをネイティブ側へ吸い上げます。
  - **HTML改ざんデモ（トグル機能）**: 親アプリ側でWebページのHTML構造を動的に書き換え、偽のフィッシング警告バナーや偽装UIを挿入する攻撃（中間者攻撃に類似した挙動）を実演可能。
  - **入力値の完全傍受**: 入力されたキーストロークやフォームデータをリアルタイム記録。

---

## 🧪 内蔵検証テストページ (`demo_login.html`)

外部サイトに個人情報を入力することなく安全に実験できるよう、アプリ内に検証用Webページを同梱しています。
- **配置場所**: `app/src/main/assets/demo_login.html`
- **内容**:
  - 疑似会員ログインフォーム (ID・パスワード)
  - 疑似クレジットカード決済フォーム (カード番号・有効期限・CVV)
  - 動的DOM追加機能 (SPA等の動的フォームでも傍受可能かを実証)
  - テスト用Cookieの発行

---

## 🚀 ビルド＆実行手順

### 必要要件
- JDK 17 または JDK 21 (Corretto 21等)
- Android SDK (API 34, Build-Tools 34.0.0)

### APKのビルド
```bash
JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto ./gradlew assembleDebug
```
生成されるAPKファイル:
`app/build/outputs/apk/debug/app-debug.apk`

### 実機またはエミュレータへのインストール
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

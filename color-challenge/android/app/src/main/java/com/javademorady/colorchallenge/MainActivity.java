package com.javademorady.colorchallenge;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity {
    private WebView webView;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        webView = new WebView(this);
        webView.setBackgroundColor(0xFF111827);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new AppBridge(), "Android");
        setContentView(webView);
        if (state == null) webView.loadUrl("file:///android_asset/index.html");
        else webView.restoreState(state);
    }
    private class AppBridge {
        @JavascriptInterface public void exitApp() { runOnUiThread(() -> finish()); }
    }
    @Override protected void onSaveInstanceState(Bundle out) { if (webView != null) webView.saveState(out); super.onSaveInstanceState(out); }
    @Override public void onBackPressed() { finish(); }
    @Override protected void onDestroy() { if (webView != null) { webView.removeJavascriptInterface("Android"); webView.destroy(); } super.onDestroy(); }
}

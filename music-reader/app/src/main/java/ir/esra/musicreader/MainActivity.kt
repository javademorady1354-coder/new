package ir.esra.musicreader

import android.app.Activity
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import android.graphics.Color

class MainActivity : Activity() {
    private lateinit var reader: WebView

    @Suppress("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(18, 39, 57)
        window.navigationBarColor = Color.rgb(18, 39, 57)
        reader = WebView(this)
        reader.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        reader.setBackgroundColor(Color.rgb(248, 247, 242))
        reader.settings.javaScriptEnabled = true
        reader.settings.domStorageEnabled = true
        reader.settings.allowFileAccess = true
        reader.settings.allowContentAccess = false
        reader.webViewClient = WebViewClient()
        reader.webChromeClient = WebChromeClient()
        setContentView(reader)
        if (savedInstanceState == null) {
            reader.loadUrl("file:///android_asset/index.html")
        } else {
            reader.restoreState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        reader.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::reader.isInitialized && reader.canGoBack()) reader.goBack()
        else super.onBackPressed()
    }
}

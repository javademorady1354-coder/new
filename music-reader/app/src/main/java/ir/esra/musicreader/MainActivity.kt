package ir.esra.musicreader

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var reader: WebView
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    @Volatile private var pageLoaded = false
    private var currentVoices: List<Voice> = emptyList()

    @Suppress("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(18, 39, 57)
        window.navigationBarColor = Color.rgb(18, 39, 57)
        reader = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.rgb(248, 247, 242))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            addJavascriptInterface(AudioBridge(), "AndroidAudio")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    pageLoaded = true
                    publishAudioState()
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    if (uri.scheme == "https" || uri.scheme == "http") {
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                        return true
                    }
                    return false
                }
            }
            webChromeClient = WebChromeClient()
        }
        setContentView(reader)
        tts = TextToSpeech(this, this)
        if (savedInstanceState == null) {
            reader.loadUrl("file:///android_asset/index.html")
        } else {
            reader.restoreState(savedInstanceState)
        }
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            val engine = tts ?: return
            try {
                engine.setLanguage(Locale("fa", "IR"))
                // Keep every Persian voice in the list so the UI can distinguish
                // installed voices from voices that the user may need to download.
                currentVoices = engine.voices
                    ?.filter { it.locale.language == "fa" }
                    ?.sortedBy { it.name.lowercase(Locale.ROOT) }
                    ?: emptyList()
            } catch (_: Exception) {
                currentVoices = emptyList()
            }
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {}
                override fun onDone(utteranceId: String) {
                    notifyPage("nativeSpeechDone()")
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String) {
                    notifyPage("nativeSpeechError()")
                }
                override fun onError(utteranceId: String, errorCode: Int) {
                    notifyPage("nativeSpeechError()")
                }
            })
        }
        publishAudioState()
    }

    private fun publishAudioState() {
        if (!pageLoaded) return
        val ready = ttsReady
        runOnUiThread {
            if (::reader.isInitialized && pageLoaded) {
                reader.evaluateJavascript("window.nativeAudioReady($ready);", null)
                reader.evaluateJavascript("if(window.loadAudioVoices)window.loadAudioVoices();", null)
            }
        }
    }

    private fun notifyPage(script: String) {
        if (!pageLoaded) return
        runOnUiThread {
            if (::reader.isInitialized && pageLoaded) {
                reader.evaluateJavascript("window.$script", null)
            }
        }
    }

    private fun genderFor(voice: Voice): String {
        val n = voice.name.lowercase(Locale.ROOT)
        return when {
            listOf("female", "woman", "fem", "زن", "zan").any { n.contains(it) } -> "female"
            listOf("male", "man", "masc", "مرد", "mard").any { n.contains(it) } -> "male"
            else -> "unknown"
        }
    }

    private fun voiceListJson(): String {
        val result = JSONObject().put("ready", ttsReady)
        val voices = JSONArray()
        currentVoices.forEach { voice ->
            voices.put(
                JSONObject()
                    .put("name", voice.name)
                    .put("gender", genderFor(voice))
                    .put("quality", voice.quality)
                    .put("installed", !voice.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED))
                    .put("network", voice.features.contains(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS))
            )
        }
        result.put("voices", voices)
        return result.toString()
    }

    inner class AudioBridge {
        @JavascriptInterface
        fun isReady(): Boolean = ttsReady

        @JavascriptInterface
        fun getVoices(): String = voiceListJson()

        @JavascriptInterface
        fun speak(text: String, voiceName: String, rate: Float, volume: Float): Boolean {
            if (!ttsReady || text.isBlank()) return false
            val engine = tts ?: return false
            return try {
                val selected = currentVoices.firstOrNull { it.name == voiceName }
                if (selected != null) {
                    engine.voice = selected
                } else if (voiceName.isNotBlank()) {
                    return false
                }
                engine.setSpeechRate(rate.coerceIn(0.65f, 1.35f))
                val params = Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f))
                }
                engine.speak(
                    text,
                    TextToSpeech.QUEUE_FLUSH,
                    params,
                    "music-reader-${System.currentTimeMillis()}"
                ) == TextToSpeech.SUCCESS
            } catch (_: Exception) {
                false
            }
        }

        @JavascriptInterface
        fun stop() {
            tts?.stop()
        }

        @JavascriptInterface
        fun openVoiceSettings() {
            runOnUiThread {
                try {
                    startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                } catch (_: Exception) {
                    try {
                        startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        reader.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        pageLoaded = false
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::reader.isInitialized && reader.canGoBack()) reader.goBack()
        else super.onBackPressed()
    }
}

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
    private var ttsReady = false
    private var currentVoices: List<Voice> = emptyList()

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
        reader.addJavascriptInterface(AudioBridge(), "AndroidAudio")
        reader.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "https" || uri.scheme == "http") {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    return true
                }
                return false
            }
        }
        reader.webChromeClient = WebChromeClient()
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
        if (!ttsReady) {
            notifyPage("nativeAudioReady(false)")
            return
        }
        val engine = tts ?: return
        engine.language = Locale("fa", "IR")
        currentVoices = engine.voices
            ?.filter { it.locale.language == "fa" && !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS) &&
                !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            ?.sortedBy { it.name.lowercase(Locale.ROOT) }
            ?: emptyList()
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
        notifyPage("nativeAudioReady(true)")
    }

    private fun notifyPage(script: String) {
        if (::reader.isInitialized) runOnUiThread {
            reader.evaluateJavascript("window.$script", null)
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
        val result = JSONObject()
        result.put("ready", ttsReady)
        val voices = JSONArray()
        currentVoices.forEach { voice ->
            voices.put(JSONObject().put("name", voice.name).put("gender", genderFor(voice))
                .put("quality", voice.quality))
        }
        result.put("voices", voices)
        return result.toString()
    }

    inner class AudioBridge {
        @JavascriptInterface
        fun getVoices(): String = voiceListJson()

        @JavascriptInterface
        fun speak(text: String, voiceName: String, rate: Float, volume: Float): Boolean {
            if (!ttsReady || text.isBlank()) return false
            val engine = tts ?: return false
            val selected = currentVoices.firstOrNull { it.name == voiceName }
            if (selected != null) engine.voice = selected
            else if (engine.voices?.any { it.name == voiceName } == true) return false
            engine.setSpeechRate(rate.coerceIn(0.65f, 1.35f))
            val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f)) }
            return engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, System.currentTimeMillis().toString()) == TextToSpeech.SUCCESS
        }

        @JavascriptInterface
        fun stop() {
            tts?.stop()
        }

        @JavascriptInterface
        fun openVoiceSettings() {
            runOnUiThread {
                try {
                    startActivity(Intent(TextToSpeech.Engine.ACTION_TTS_SETTINGS))
                } catch (_: Exception) {
                    startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        reader.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
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
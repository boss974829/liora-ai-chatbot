package app.liora.companion;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int MIC = 21;
    private WebView web;
    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private boolean pendingListen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources());
            }
        });
        web.addJavascriptInterface(new Bridge(), "LioraNative");
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
            }
        });
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new Heard());
        }
        web.loadUrl("file:///android_asset/www/index.html");
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == MIC && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED && pendingListen) {
            pendingListen = false;
            startListen();
        }
    }

    private void startListen() {
        if (recognizer == null) {
            deliver("");
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        recognizer.startListening(intent);
    }

    private void deliver(String text) {
        final String quoted;
        try {
            quoted = JSONObject.quote(text == null ? "" : text);
        } catch (Exception e) {
            return;
        }
        runOnUiThread(() -> web.evaluateJavascript("window.onHeard&&window.onHeard(" + quoted + ")", null));
    }

    private class Heard implements RecognitionListener {
        @Override public void onReadyForSpeech(Bundle params) {}
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() {}
        @Override public void onError(int error) { deliver(""); }
        @Override public void onPartialResults(Bundle partial) {}
        @Override
        public void onResults(Bundle results) {
            ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            deliver(list == null || list.isEmpty() ? "" : list.get(0));
        }
        @Override public void onEvent(int eventType, Bundle params) {}
    }

    private class Bridge {
        @JavascriptInterface
        public String chat(String key, String json) {
            if (key == null || key.trim().length() < 12) return "ERROR:Add your xAI API key under You.";
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL("https://api.x.ai/v1/responses").openConnection();
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(70000);
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + key.trim());
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytes);
                }
                int code = conn.getResponseCode();
                InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                String body = read(in);
                if (code >= 400) {
                    String snippet = body == null ? "" : body.substring(0, Math.min(160, body.length()));
                    return "ERROR:" + code + " " + snippet;
                }
                return body;
            } catch (Exception e) {
                return "ERROR:" + (e.getMessage() == null ? "network" : e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }

        @JavascriptInterface
        public void speak(String text) {
            if (tts == null || text == null || text.isEmpty()) return;
            runOnUiThread(() -> tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "liora"));
        }

        @JavascriptInterface
        public void stopSpeak() {
            if (tts != null) runOnUiThread(() -> tts.stop());
        }

        @JavascriptInterface
        public void listen() {
            runOnUiThread(() -> {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    pendingListen = true;
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC);
                    return;
                }
                if (tts != null) tts.stop();
                startListen();
            });
        }
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        return out.toString(StandardCharsets.UTF_8);
    }

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        if (recognizer != null) recognizer.destroy();
        super.onDestroy();
    }
}

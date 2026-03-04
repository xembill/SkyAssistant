package com.skywell.assistant.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.skywell.assistant.action.CarControlAction;
import com.skywell.assistant.action.SystemAction;
import com.skywell.assistant.nlu.TurkishCommandParser;
import com.skywell.assistant.nlu.TurkishCommandParser.IntentType;
import com.skywell.assistant.nlu.TurkishCommandParser.ParsedIntent;
import com.skywell.assistant.ui.AssistantOverlay;

import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.io.IOException;
import java.util.Locale;
import java.util.Random;

/**
 * Ana asistan servisi — Vosk grammar modu.
 *
 * Wake grammar:   sadece "hey sky" varyantları → hızlı, doğru
 * Command grammar: tüm komut kelimeleri → "klimayı arınç" gibi saçma sonuçlar yok
 *
 * Modlar:
 *  WAKE    → wake grammar ile bekle, "sky" duyulursa COMMAND'a geç
 *  COMMAND → command grammar ile dinle, komutu işle, WAKE'e dön
 */
public class AssistantService extends Service {

    private static final String TAG = "SkyAssistant";
    private static final String NOTIF_CHANNEL = "sky_assistant";
    private static final int NOTIF_ID = 1001;

    public static final String ACTION_START_LISTENING = "com.skywell.assistant.START_LISTENING";
    public static final String ACTION_STOP_LISTENING  = "com.skywell.assistant.STOP_LISTENING";

    // Wake word grammar — sadece ÇIFT kelime wake phrase'ler, [unk] yok
    // "sky" tek başına ÇIKARILDI — gürültüde/normal konuşmada tetikleniyor
    private static final String WAKE_GRAMMAR =
            "[\"hey sky\", \"hay sky\", \"hey ski\", \"hay ski\", \"[unk]\"]";

    // Komut grammar: tam cümle kalıpları — hem tanıma doğruluğu artar hem de
    // tek kelime noise matchini azaltır. onResult() ile işlenecek (partial değil).
    private static final String CMD_GRAMMAR =
            "[\"klimayı aç\", \"klima aç\", \"klimayı kapat\", \"klima kapat\", " +
            "\"ısıtmayı aç\", \"ısıtmayı kapat\", \"havalandırmayı aç\", \"havalandırmayı kapat\", " +
            "\"ön camı ısıt\", \"arka camı ısıt\", \"ön defrost\", \"arka defrost\", " +
            "\"anyon aç\", \"hava temizle\", \"sigara modu\", " +
            "\"kapıları kilitle\", \"kilit aç\", \"bagajı aç\", " +
            "\"pencereleri aç\", \"pencereleri kapat\", \"camları aç\", \"camları kapat\", " +
            "\"tavan aç\", \"tavan kapat\", \"sunroof aç\", \"sunroof kapat\", " +
            "\"şarj kapısını aç\", " +
            "\"eko mod\", \"spor mod\", \"kar modu\", \"uzun menzil\", " +
            "\"koltuğu ısıt\", \"koltuk ısıtma aç\", " +
            "\"sesi artır\", \"sesi azalt\", \"sesi kıs\", \"ses yükselt\", " +
            "\"daha parlak\", \"daha karanlık\", \"ekranı kapat\", " +
            "\"ana ekran\", \"anasayfa\", " +
            "\"müzik çal\", \"müziği durdur\", \"sonraki şarkı\", \"önceki şarkı\", " +
            "\"hava durumu\", \"lastik basıncı\", \"neredeyim\", " +
            "\"[unk]\"]";

    // Komut modu FREE-FORM: Grammar YOK → ambient gürültü kelimeye eşlenmez.
    // Vosk yalnızca gerçek konuşma algıladığında onResult() üretir.

    private enum Mode { WAKE, COMMAND }
    private Mode mode = Mode.WAKE;

    private Model voskModel;
    private SpeechService speechService;
    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private volatile boolean ignoringResult = false;

    private final Random random = new Random();

    // Wake word sonrası çeşitli karşılama ifadeleri (pre-recorded)
    private static final int[] LISTEN_SOUNDS = {
        com.skywell.assistant.R.raw.sky_listen_1,
        com.skywell.assistant.R.raw.sky_listen_2,
        com.skywell.assistant.R.raw.sky_listen_3,
        com.skywell.assistant.R.raw.sky_listen_4,
        com.skywell.assistant.R.raw.sky_listen_5,
    };
    // Metin yanıtları kaldırıldı — intent bazlı ses dosyaları kullanılıyor

    private TurkishCommandParser parser;
    private CarControlAction carAction;
    private SystemAction sysAction;
    private AssistantOverlay overlay;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Komut modu zaman aşımı (8 sn sessizlikte wake'e dön)
    private static final long CMD_TIMEOUT_MS = 8000;
    private final Runnable cmdTimeoutRunnable = () -> {
        if (mode == Mode.COMMAND) {
            Log.i(TAG, "Komut timeout — wake moduna dönülüyor");
            mainHandler.post(() -> {
                speak("Bir şey söylemediniz.");
                returnToWake();
            });
        }
    };

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        parser    = new TurkishCommandParser();
        carAction = new CarControlAction(this);
        sysAction = new SystemAction(this);
        overlay   = new AssistantOverlay(this);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        ensureVolume();
        startForeground(NOTIF_ID, buildNotification("Model yükleniyor..."));
        Log.i(TAG, "Servis başlatıldı.");
        new Thread(this::loadModel).start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_START_LISTENING.equals(intent.getAction())) {
            mainHandler.post(this::switchToCommandMode);
        } else if (intent != null && ACTION_STOP_LISTENING.equals(intent.getAction())) {
            mainHandler.post(this::stopAll);
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        stopSpeechService();
        if (voskModel != null) { try { voskModel.close(); } catch (Exception ignored) {} }
        if (mediaPlayer != null) { try { mediaPlayer.release(); } catch (Exception ignored) {} mediaPlayer = null; }
        overlay.hide();
        super.onDestroy();
    }

    // ─── Model yükleme ───────────────────────────────────────────────────────

    private void loadModel() {
        Log.i(TAG, "Vosk model yükleniyor (assets'ten kopyalanıyor)...");
        StorageService.unpack(this, "model", "model",
            model -> {
                voskModel = model;
                Log.i(TAG, "Vosk model hazır.");
                mainHandler.postDelayed(this::startWakeListening, 500);
            },
            e -> {
                Log.e(TAG, "Model yükleme hatası: " + e.getMessage());
                mainHandler.post(() -> updateNotification("Model yüklenemedi!"));
            }
        );
    }

    // ─── Ses oynatma (pre-recorded) ───────────────────────────────────────────

    /** Sesi garantili seviyeye getir */
    private void ensureVolume() {
        if (audioManager == null) return;
        int stream = AudioManager.STREAM_MUSIC;
        int cur = audioManager.getStreamVolume(stream);
        int max = audioManager.getStreamMaxVolume(stream);
        if (cur < max / 3) {
            int target = max * 2 / 3;
            audioManager.setStreamVolume(stream, target, 0);
            Log.i(TAG, "Ses seviyesi " + cur + " → " + target + " (max=" + max + ")");
        }
    }

    /**
     * Ses dosyası oynat; tamamlandığında onComplete Runnable çalışır (null ise çalışmaz).
     * Ana thread'den çağrılmalı.
     */
    private void playRaw(int resId, Runnable onComplete) {
        ensureVolume();
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); mediaPlayer.release(); } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        try {
            mediaPlayer = MediaPlayer.create(this, resId);
            if (mediaPlayer == null) {
                Log.e(TAG, "MediaPlayer.create başarısız: resId=" + resId);
                if (onComplete != null) mainHandler.post(onComplete);
                return;
            }
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setOnCompletionListener(mp -> {
                mp.release();
                if (mediaPlayer == mp) mediaPlayer = null;
                if (onComplete != null) mainHandler.post(onComplete);
            });
            mediaPlayer.start();
            Log.d(TAG, "Ses oynatılıyor: resId=" + resId);
        } catch (Exception e) {
            Log.e(TAG, "Ses oynatma hatası: " + e.getMessage());
            if (onComplete != null) mainHandler.post(onComplete);
        }
    }

    /** Komut öncesi rastgele prompt — oynatma bitince komut dinleme başlar */
    private void speakAndStartCommand(String unused) {
        int resId = LISTEN_SOUNDS[random.nextInt(LISTEN_SOUNDS.length)];
        playRaw(resId, this::startCommandListening);
    }

    /** Intent tipine göre uygun ses dosyasını oynat */
    public void speak(String text) {
        Log.i(TAG, "Yanıt: " + text);
        // Ses dosyası seçimi metni keyword ile eşleştiriyor
        int resId = resolveSound(text);
        playRaw(resId, null);
    }

    /** Metin → ses dosyası eşleştirme */
    private int resolveSound(String text) {
        if (text == null) return com.skywell.assistant.R.raw.sky_ok;
        String t = text.toLowerCase(java.util.Locale.ROOT);
        if (t.contains("açıldı") && t.contains("klima"))   return com.skywell.assistant.R.raw.sky_ac_on;
        if (t.contains("kapatıldı") && t.contains("klima")) return com.skywell.assistant.R.raw.sky_ac_off;
        if (t.contains("pencere") && t.contains("açıldı"))  return com.skywell.assistant.R.raw.sky_win_open;
        if (t.contains("pencere") && t.contains("kapatıldı")) return com.skywell.assistant.R.raw.sky_win_close;
        if (t.contains("müzik") && (t.contains("başlatıldı") || t.contains("çalın")))
            return com.skywell.assistant.R.raw.sky_music_on;
        if (t.contains("müzik") && t.contains("durduruldu")) return com.skywell.assistant.R.raw.sky_music_off;
        if (t.contains("anlaşılamadı") || t.contains("anlayamadım") || t.contains("hata") || t.contains("söylemediniz"))
            return com.skywell.assistant.R.raw.sky_error;
        return com.skywell.assistant.R.raw.sky_ok;
    }

    /** Rastgele bir karşılama prompt resId seç (kullanılmıyor — speakAndStartCommand içinde) */
    private int randomListenSound() {
        return LISTEN_SOUNDS[random.nextInt(LISTEN_SOUNDS.length)];
    }

    // ─── SpeechService yönetimi ──────────────────────────────────────────────

    private void stopSpeechService() {
        if (speechService != null) {
            try { speechService.stop(); speechService.shutdown(); } catch (Exception ignored) {}
            speechService = null;
        }
    }

    // ─── Wake modu ───────────────────────────────────────────────────────────

    private void startWakeListening() {
        if (voskModel == null) return;
        mode = Mode.WAKE;
        ignoringResult = false;
        stopSpeechService();
        try {
            // Free-form mod: grammar yok → Türkçe model "hey sky"yi çok daha iyi tanır
            // Grammar modda yabancı kelimeler [unk]'a map olup gözden kaçabilir
            Recognizer rec = new Recognizer(voskModel, 16000.0f);
            speechService = new SpeechService(rec, 16000.0f);
            speechService.startListening(wakeListener);
            Log.i(TAG, "Wake dinleniyor (free-form modu)...");
            updateNotification("\"Hey Sky\" bekleniyor...");
        } catch (IOException e) {
            Log.e(TAG, "Wake service başlatılamadı: " + e.getMessage());
            mainHandler.postDelayed(this::startWakeListening, 2000);
        }
    }

    private final RecognitionListener wakeListener = new RecognitionListener() {
        @Override
        public void onPartialResult(String hypothesis) {
            if (ignoringResult) return;
            Log.d(TAG, "Wake partial: " + hypothesis);
            if (containsWakeWord(hypothesis)) {
                Log.i(TAG, "Wake word tespit edildi (partial)!");
                ignoringResult = true;
                mainHandler.post(AssistantService.this::switchToCommandMode);
            }
        }

        @Override
        public void onResult(String hypothesis) {
            if (ignoringResult) return;
            Log.d(TAG, "Wake result: " + hypothesis);
            if (containsWakeWord(hypothesis)) {
                Log.i(TAG, "Wake word tespit edildi (result)!");
                ignoringResult = true;
                mainHandler.post(AssistantService.this::switchToCommandMode);
            }
            // Wake word yoksa otomatik devam ediyor (SpeechService loop)
        }

        @Override
        public void onFinalResult(String hypothesis) {
            if (ignoringResult) return;
            if (containsWakeWord(hypothesis)) {
                ignoringResult = true;
                mainHandler.post(AssistantService.this::switchToCommandMode);
            }
        }

        @Override public void onError(Exception e) {
            Log.w(TAG, "Wake hata: " + e.getMessage());
            mainHandler.postDelayed(AssistantService.this::startWakeListening, 1000);
        }

        @Override public void onTimeout() {
            Log.d(TAG, "Wake timeout, devam ediliyor...");
        }
    };

    private boolean containsWakeWord(String hypothesis) {
        if (hypothesis == null) return false;
        String text = extractText(hypothesis).toLowerCase(Locale.ROOT).trim();
        if (text.isEmpty()) return false;
        // "sky" geçmesi yeterli — "hey sky", "hey ski", "sky" tek başına, vs.
        // Türkçe model "sky" → "ski", "scai", "skai" gibi varyantlar üretebilir
        return text.contains("sky") || text.contains("ski") ||
               text.contains("hay") && (text.contains("sk") || text.contains("sc")) ||
               text.contains("hey") && (text.contains("sk") || text.contains("sc")) ||
               text.equals("hey sky") || text.equals("hay sky") ||
               text.equals("hey ski") || text.equals("hay ski");
    }

    // ─── Command modu ─────────────────────────────────────────────────────────

    private long commandStartTime = 0;
    private static final long CMD_GRACE_MS = 600; // TTS echo'sunun geçmesi için yeterli
    private volatile boolean commandHandled = false; // çift işlemi önle

    public void switchToCommandMode() {
        if (mode == Mode.COMMAND) return;
        mode = Mode.COMMAND;
        commandHandled = false;
        stopSpeechService();

        overlay.showListening();
        sendBroadcast(new Intent(FloatingButtonService.ACTION_LISTENING_START));
        updateNotification("Dinliyorum...");

        // Ses dosyası oynat → tamamlanınca komut dinleme başlar
        speakAndStartCommand(null);
    }

    private void startCommandListening() {
        if (voskModel == null || mode != Mode.COMMAND) return;
        commandStartTime = System.currentTimeMillis();
        commandHandled = false;
        try {
            // GRAMMAR modu: tam cümle kalıpları, onResult() ile işlenir (onPartialResult değil)
            Recognizer rec = new Recognizer(voskModel, 16000.0f, CMD_GRAMMAR);
            speechService = new SpeechService(rec, 16000.0f);
            speechService.startListening(commandListener);
            Log.i(TAG, "Komut dinleniyor (grammar modu, onResult)...");
            // Grace period bittikten sonra overlay'i güncelle: "Söyleyin..."
            mainHandler.postDelayed(() -> {
                if (mode == Mode.COMMAND && !commandHandled)
                    overlay.updateText("🎙 Söyleyin...");
            }, CMD_GRACE_MS);
            // 8 saniyelik timeout başlat
            mainHandler.removeCallbacks(cmdTimeoutRunnable);
            mainHandler.postDelayed(cmdTimeoutRunnable, CMD_TIMEOUT_MS);
        } catch (IOException e) {
            Log.e(TAG, "Command service başlatılamadı: " + e.getMessage());
            returnToWake();
        }
    }

    private final RecognitionListener commandListener = new RecognitionListener() {
        @Override
        public void onPartialResult(String hypothesis) {
            // Grammar modda partial'ı sadece ekranda göster, işleme
            if (hypothesis == null) return;
            long elapsed = System.currentTimeMillis() - commandStartTime;
            if (elapsed < CMD_GRACE_MS) return;
            String clean = extractText(hypothesis);
            Log.d(TAG, "CMD partial [" + elapsed + "ms]: " + clean);
            if (!clean.isEmpty()) mainHandler.post(() -> overlay.updateText(clean));
        }

        @Override
        public void onResult(String hypothesis) {
            // Vosk sessizlik algılayınca onResult() üretir — kullanıcı konuşmayı bitirdi
            long elapsed = System.currentTimeMillis() - commandStartTime;
            String text = extractText(hypothesis);
            Log.i(TAG, "CMD result [" + elapsed + "ms]: '" + text + "'");
            if (elapsed < CMD_GRACE_MS) {
                Log.w(TAG, "Grace içinde — yoksayıldı");
                return;
            }
            if (text.isEmpty()) return; // sessizlik — bekle
            if (commandHandled) return; // zaten işlendi
            commandHandled = true;
            mainHandler.removeCallbacks(cmdTimeoutRunnable);
            final SpeechService svc = speechService;
            speechService = null;
            if (svc != null) { try { svc.stop(); svc.shutdown(); } catch (Exception ignored) {} }
            mainHandler.post(() -> handleCommandResult(text));
        }

        @Override
        public void onFinalResult(String hypothesis) {
            long elapsed = System.currentTimeMillis() - commandStartTime;
            String text = extractText(hypothesis);
            Log.i(TAG, "CMD finalResult [" + elapsed + "ms]: '" + text + "'");
            if (elapsed < CMD_GRACE_MS || text.isEmpty() || commandHandled) return;
            commandHandled = true;
            mainHandler.removeCallbacks(cmdTimeoutRunnable);
            mainHandler.post(() -> handleCommandResult(text));
        }

        @Override
        public void onError(Exception e) {
            Log.w(TAG, "Komut hata: " + e.getMessage());
            mainHandler.removeCallbacks(cmdTimeoutRunnable);
            mainHandler.post(() -> {
                speak("Bir hata oluştu.");
                returnToWake();
            });
        }

        @Override
        public void onTimeout() {
            Log.i(TAG, "Komut Vosk timeout");
            mainHandler.removeCallbacks(cmdTimeoutRunnable);
            mainHandler.post(() -> {
                speak("Bir şey söylemediniz.");
                returnToWake();
            });
        }
    };

    /** Vosk JSON çıktısından metin çıkar: {"text": "klima ac"} → "klima ac" */
    private String extractText(String hypothesis) {
        if (hypothesis == null) return "";
        // "text" : "..." al
        int start = hypothesis.indexOf("\"text\"");
        if (start < 0) {
            // partial formatı: "partial" : "..."
            start = hypothesis.indexOf("\"partial\"");
            if (start < 0) return "";
            start = hypothesis.indexOf("\"", start + 9) + 1;
        } else {
            start = hypothesis.indexOf("\"", start + 6) + 1;
        }
        int end = hypothesis.indexOf("\"", start);
        if (start <= 0 || end <= start) return "";
        return hypothesis.substring(start, end).trim();
    }

    private void handleCommandResult(String text) {
        if (text.isEmpty()) {
            speak("Bir şey söylemediniz.");
            returnToWake();
            return;
        }
        ParsedIntent intent = parser.parse(text);
        Log.i(TAG, "Intent: " + intent.type + " params=" + intent.params);

        String response = null;
        if (!isSystemIntent(intent.type)) response = carAction.execute(intent);
        if (response == null) response = sysAction.execute(intent);
        if (intent.type == IntentType.UNKNOWN || response == null)
            response = "Anlayamadım: \"" + text + "\"";

        final String r = response;
        overlay.updateText(r);
        speak(r);
        mainHandler.postDelayed(() -> overlay.hide(), 5000);
        returnToWake();
    }

    private void returnToWake() {
        mode = Mode.WAKE;
        commandHandled = true;
        mainHandler.removeCallbacks(cmdTimeoutRunnable);
        stopSpeechService();
        overlay.hide();
        sendBroadcast(new Intent(FloatingButtonService.ACTION_LISTENING_STOP));
        updateNotification("\"Hey Sky\" bekleniyor...");
        mainHandler.postDelayed(this::startWakeListening, 500);
    }

    private void stopAll() {
        stopSpeechService();
        overlay.hide();
        updateNotification("Durduruldu");
    }

    // ─── Yardımcılar ──────────────────────────────────────────────────────────

    private boolean isSystemIntent(IntentType type) {
        switch (type) {
            case VOLUME_SET: case VOLUME_UP: case VOLUME_DOWN:
            case BRIGHTNESS_SET: case BRIGHTNESS_UP: case BRIGHTNESS_DOWN:
            case SCREEN_OFF: case HOME_SCREEN:
            case NAVIGATE_TO: case SEARCH_NEARBY:
            case MUSIC_PLAY: case MUSIC_STOP: case MUSIC_NEXT: case MUSIC_PREV: case PLAY_SONG:
            case CALL_CONTACT: case CALL_NUMBER:
            case WEATHER: case CURRENT_LOCATION:
                return true;
            default: return false;
        }
    }

    private Notification buildNotification(String status) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    NOTIF_CHANNEL, "SkyAssistant", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
        Intent tap = new Intent(this, AssistantService.class);
        tap.setAction(ACTION_START_LISTENING);
        PendingIntent pi = PendingIntent.getService(this, 0, tap,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, NOTIF_CHANNEL)
                .setContentTitle("SkyAssistant")
                .setContentText(status)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    private void updateNotification(String status) {
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .notify(NOTIF_ID, buildNotification(status));
    }
}

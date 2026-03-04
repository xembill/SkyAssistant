package com.skywell.assistant.action;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;

import com.skywell.assistant.nlu.TurkishCommandParser.ParsedIntent;

/**
 * Navigasyon, müzik, telefon ve sistem eylemlerini yönetir.
 */
public class SystemAction {

    private static final String TAG = "SystemAction";

    private final Context context;
    private final AudioManager audioManager;

    public SystemAction(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public String execute(ParsedIntent intent) {
        switch (intent.type) {

            // ─── SES ──────────────────────────────────────────────────────────
            case VOLUME_SET: {
                String lvl = intent.params.get("level");
                if (lvl == null) return "Hangi ses seviyesi?";
                int pct = Math.min(100, Math.max(0, Integer.parseInt(lvl)));
                int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int vol = (int) (max * pct / 100.0f);
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);
                return "Ses " + pct + " olarak ayarlandı.";
            }

            case VOLUME_UP:
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                return "Ses artırıldı.";

            case VOLUME_DOWN:
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                return "Ses azaltıldı.";

            // ─── PARLAKLIK ────────────────────────────────────────────────────
            case BRIGHTNESS_SET: {
                String lvl = intent.params.get("level");
                if (lvl == null) return "Hangi parlaklık seviyesi?";
                int pct = Math.min(100, Math.max(0, Integer.parseInt(lvl)));
                int val = (int) (255 * pct / 100.0f);
                try {
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.SCREEN_BRIGHTNESS, val);
                } catch (Exception e) {
                    Log.w(TAG, "Parlaklık ayarlanamadı: " + e.getMessage());
                }
                return "Ekran parlaklığı " + pct + " olarak ayarlandı.";
            }

            case BRIGHTNESS_UP: {
                int cur = Settings.System.getInt(context.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS, 128);
                int newVal = Math.min(255, cur + 40);
                Settings.System.putInt(context.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS, newVal);
                return "Parlaklık artırıldı.";
            }

            case BRIGHTNESS_DOWN: {
                int cur = Settings.System.getInt(context.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS, 128);
                int newVal = Math.max(10, cur - 40);
                Settings.System.putInt(context.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS, newVal);
                return "Parlaklık azaltıldı.";
            }

            case SCREEN_OFF: {
                // Güç tuşu simüle et
                Intent i = new Intent(Intent.ACTION_SCREEN_OFF);
                context.sendBroadcast(i);
                return "Ekran kapatılıyor.";
            }

            case HOME_SCREEN: {
                Intent home = new Intent(Intent.ACTION_MAIN);
                home.addCategory(Intent.CATEGORY_HOME);
                home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(home);
                return "Ana ekrana gidildi.";
            }

            // ─── NAVİGASYON ───────────────────────────────────────────────────
            case NAVIGATE_TO: {
                String dest = intent.params.get("destination");
                if (dest == null || dest.isEmpty()) return "Nereye gidelim?";
                // Önce Yandex, yoksa Google Maps
                boolean launched = tryNavigation("ru.yandex.yandexnavi", dest)
                        || tryNavigation("com.google.android.apps.maps", dest);
                if (!launched) {
                    // Herhangi bir harita uygulaması
                    Uri gmmIntentUri = Uri.parse("google.navigation:q=" + Uri.encode(dest));
                    Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                    mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { context.startActivity(mapIntent); }
                    catch (Exception e) { return dest + " için navigasyon başlatılamadı."; }
                }
                return dest + " için navigasyon başlatılıyor.";
            }

            case SEARCH_NEARBY: {
                String query = intent.params.get("query");
                if (query == null) return "Ne arayalım?";
                Uri uri = Uri.parse("geo:0,0?q=" + Uri.encode(query));
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, uri);
                mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { context.startActivity(mapIntent); }
                catch (Exception e) { return query + " araması başlatılamadı."; }
                return "En yakın " + query + " aranıyor.";
            }

            // ─── MÜZİK ────────────────────────────────────────────────────────
            case MUSIC_PLAY: {
                // Önce Spotify, yoksa sistem müzik çalar
                boolean opened = tryLaunchApp("com.spotify.music")
                        || tryLaunchApp("com.sec.android.app.music")
                        || tryLaunchApp("com.android.music");
                if (!opened) return "Müzik uygulaması bulunamadı.";
                return "Müzik açılıyor.";
            }

            case MUSIC_STOP: {
                audioManager.dispatchMediaKeyEvent(
                        new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,
                                android.view.KeyEvent.KEYCODE_MEDIA_STOP));
                return "Müzik durduruldu.";
            }

            case MUSIC_NEXT: {
                audioManager.dispatchMediaKeyEvent(
                        new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,
                                android.view.KeyEvent.KEYCODE_MEDIA_NEXT));
                return "Sonraki şarkı.";
            }

            case MUSIC_PREV: {
                audioManager.dispatchMediaKeyEvent(
                        new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,
                                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS));
                return "Önceki şarkı.";
            }

            case PLAY_SONG: {
                String song = intent.params.get("song");
                if (song == null) return "Hangi şarkıyı çalaım?";
                // YouTube'da ara
                Uri ytUri = Uri.parse("https://www.youtube.com/results?search_query=" +
                        Uri.encode(song));
                Intent ytIntent = new Intent(Intent.ACTION_VIEW, ytUri);
                ytIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { context.startActivity(ytIntent); }
                catch (Exception e) { return song + " açılamadı."; }
                return song + " için YouTube aranıyor.";
            }

            // ─── TELEFON ──────────────────────────────────────────────────────
            case CALL_CONTACT: {
                String contact = intent.params.get("contact");
                if (contact == null) return "Kimi arayalım?";
                // Direkt arama (rehberde arar)
                Intent callIntent = new Intent(Intent.ACTION_CALL);
                callIntent.setData(Uri.parse("tel:" + contact.replaceAll("\\s+", "")));
                callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { context.startActivity(callIntent); }
                catch (Exception e) {
                    return contact + " aranamadı. Telefon izni gerekiyor.";
                }
                return contact + " aranıyor.";
            }

            case CALL_NUMBER: {
                String number = intent.params.get("number");
                if (number == null) return "Hangi numarayı arayalım?";
                Intent callIntent = new Intent(Intent.ACTION_CALL,
                        Uri.parse("tel:" + number.replaceAll("\\s+", "")));
                callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { context.startActivity(callIntent); }
                catch (Exception e) { return "Arama başlatılamadı."; }
                return number + " aranıyor.";
            }

            // ─── GENEL ────────────────────────────────────────────────────────
            case WEATHER: {
                // Hava durumu uygulaması aç veya tarayıcıda aç
                Uri uri = Uri.parse("https://www.mgm.gov.tr");
                Intent webIntent = new Intent(Intent.ACTION_VIEW, uri);
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { context.startActivity(webIntent); }
                catch (Exception e) { return "Hava durumu açılamadı."; }
                return "Hava durumu açılıyor.";
            }

            default:
                return "Bu komutu anlayamadım.";
        }
    }

    private boolean tryNavigation(String pkg, String destination) {
        try {
            context.getPackageManager().getPackageInfo(pkg, 0);
            Intent i = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("google.navigation:q=" + Uri.encode(destination)));
            i.setPackage(pkg);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean tryLaunchApp(String pkg) {
        try {
            Intent i = context.getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) return false;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

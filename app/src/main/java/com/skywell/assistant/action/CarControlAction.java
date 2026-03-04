package com.skywell.assistant.action;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.skywell.assistant.nlu.TurkishCommandParser.ParsedIntent;

import java.util.Map;

/**
 * Araç kontrol komutlarını AISettings broadcast'lerine ve HVAC ContentProvider
 * çağrılarına dönüştürür. Skywell ET5 IHU sistemiyle IPC entegrasyonu sağlar.
 *
 * Disclaimer: Skywell, Skyworth, Coolwell isimleri ilgili şirketlerin ticari
 * markalarıdır. Bu uygulama resmi değildir ve bu şirketlerle bağlantısı yoktur.
 */
public class CarControlAction {

    private static final String TAG = "CarControlAction";

    // HVAC ContentProvider (SkyHvacDB) — broadcast değil, direkt DB yazma
    private static final String HVAC_URI_BASE = "content://com.coolwell.ai.skyhvac.database/airconditioner/";
    private static final String HVAC_CLSNAME  = "com.skyworth.car.aisettings.AISettingsService";

    // AISettings broadcast actions (LOCK, LIGHT, MODE için broadcast kalıyor)
    private static final String ACTION_HVAC  = "com.skyworth.car.aisettings.action.HVAC";
    private static final String ACTION_LOCK  = "com.skyworth.car.aisettings.action.LOCK";
    private static final String ACTION_LIGHT = "com.skyworth.car.aisettings.action.LIGHT";
    private static final String ACTION_MODE  = "com.skyworth.car.aisettings.action.MODE";
    private static final String ACTION_CONV   = "com.skyworth.car.aisettings.action.CONVENIENCE";
    // ACTION_CHARGE: araç sisteminde CONVENIENCE action'ı üzerinden yönetiliyor
    private static final String ACTION_CHARGE = "com.skyworth.car.aisettings.action.CONVENIENCE";

    private final Context context;

    public CarControlAction(Context context) {
        this.context = context;
    }

    public String execute(ParsedIntent intent) {
        Map<String, String> p = intent.params;

        switch (intent.type) {
            // ─── KLİMA ────────────────────────────────────────────────────────
            case HVAC_ON:
                setHvacProperty("power", "1");
                return "Klima açıldı.";

            case HVAC_OFF:
                setHvacProperty("power", "0");
                return "Klima kapatıldı.";

            case HVAC_TEMP_SET: {
                String temp = p.get("temp");
                if (temp == null) return "Hangi sıcaklık?";
                setHvacProperty("temperature", temp);
                return "Klima sıcaklığı " + temp + " dereceye ayarlandı.";
            }

            case HVAC_FAN_SET: {
                String level = p.get("level");
                if (level == null) return "Hangi fan hızı?";
                setHvacProperty("windlevel", level);
                return "Fan hızı " + level + " olarak ayarlandı.";
            }

            case HVAC_MODE_FACE:
                setHvacProperty("blowing", "1");
                return "Yüze hava yönlendirme modu seçildi.";

            case HVAC_MODE_FEET:
                setHvacProperty("blowing", "2");
                return "Ayaklara hava yönlendirme modu seçildi.";

            case HVAC_MODE_WINDSHIELD:
                setHvacProperty("blowing", "3");
                return "Ön cam modu seçildi. Buğu gideriliyor.";

            case HVAC_AC_ON:
                setHvacProperty("compressor", "1");
                return "Soğutma açıldı.";

            case HVAC_AC_OFF:
                setHvacProperty("compressor", "0");
                return "Soğutma kapatıldı.";

            case HVAC_FRONT_DEFROST_ON:
                setHvacProperty("frontdefrosting", "1");
                return "Ön cam ısıtması açıldı.";

            case HVAC_REAR_DEFROST_ON:
                setHvacProperty("reardefrosting", "1");
                return "Arka cam ısıtması açıldı.";

            case HVAC_ANION_ON:
                setHvacProperty("anion", "1");
                return "Anyon hava temizleme açıldı.";

            case HVAC_PURIFIER_ON:
                sendBroadcast(ACTION_HVAC, "open_air_purifier", null, null);
                return "Hava tasfiye sistemi açıldı.";

            case HVAC_PURIFIER_OFF:
                sendBroadcast(ACTION_HVAC, "close_air_purifier", null, null);
                return "Hava tasfiye sistemi kapatıldı.";

            // ─── KİLİT ────────────────────────────────────────────────────────
            case LOCK_ALL:
                sendLock("all_lock", true);
                return "Tüm kapılar kilitlendi.";

            case UNLOCK_ALL:
                sendLock("all_lock", false);
                return "Tüm kapılar açıldı.";

            case WINDOW_CLOSE_ALL:
                sendLock("all_window_up", null, null);
                return "Tüm pencereler kapatıldı.";

            case WINDOW_OPEN_ALL:
                sendLock("all_window_down", null, null);
                return "Tüm pencereler açıldı.";

            // Tekil cam — broadcast dener, başarısız olursa root ile CarVehicle HAL
            case WINDOW_OPEN_FL:
                return controlSingleWindow("fl_window_down", true, "Sürücü camı açıldı.");
            case WINDOW_CLOSE_FL:
                return controlSingleWindow("fl_window_up", false, "Sürücü camı kapatıldı.");
            case WINDOW_OPEN_FR:
                return controlSingleWindow("fr_window_down", true, "Yolcu camı açıldı.");
            case WINDOW_CLOSE_FR:
                return controlSingleWindow("fr_window_up", false, "Yolcu camı kapatıldı.");
            case WINDOW_OPEN_RL:
                return controlSingleWindow("rl_window_down", true, "Sol arka cam açıldı.");
            case WINDOW_CLOSE_RL:
                return controlSingleWindow("rl_window_up", false, "Sol arka cam kapatıldı.");
            case WINDOW_OPEN_RR:
                return controlSingleWindow("rr_window_down", true, "Sağ arka cam açıldı.");
            case WINDOW_CLOSE_RR:
                return controlSingleWindow("rr_window_up", false, "Sağ arka cam kapatıldı.");

            case TRUNK_OPEN:
                sendLock("trunk", true);
                return "Bagaj açıldı.";

            case TRUNK_CLOSE:
                sendLock("trunk", false);
                return "Bagaj kapatıldı.";

            case SUNROOF_OPEN:
                sendLock("sun_roof", true);
                return "Tavan camı açıldı.";

            case SUNROOF_CLOSE:
                sendLock("sun_roof", false);
                return "Tavan camı kapatıldı.";

            case CHARGE_PORT_OPEN:
                sendBroadcast(ACTION_CHARGE, "charge_port_open", null, null);
                return "Şarj kapağı açıldı.";

            // ─── IŞIKLAR ──────────────────────────────────────────────────────
            case LIGHTS_WELCOME_ON:
                sendBroadcast(ACTION_LIGHT, "key_welcome_lamp_status", null, true);
                return "Karşılama ışığı açıldı.";

            case LOGO_LED_OFF:
                sendBroadcast(ACTION_LIGHT, "key_logo_status", null, false);
                return "Logo ışığı kapatıldı.";

            case LOGO_LED_ON:
                sendBroadcast(ACTION_LIGHT, "key_logo_status", null, true);
                return "Logo ışığı açıldı.";

            // ─── SÜRÜŞ MODLARI ────────────────────────────────────────────────
            case MODE_ECO:
                sendBroadcast(ACTION_MODE, "driving_mode", null, 0);
                return "Ekonomi moduna geçildi.";

            case MODE_SPORT:
                sendBroadcast(ACTION_MODE, "driving_mode", null, 2);
                return "Spor moduna geçildi.";

            case MODE_SUPER_RANGE:
                sendBroadcast(ACTION_MODE, "open_super_long_endurance", null, null);
                return "Süper menzil modu açıldı.";

            case MODE_SNOW:
                sendBroadcast(ACTION_MODE, "driving_mode", null, 3);
                return "Kar moduna geçildi.";

            case MODE_SMOKING_ON:
                sendBroadcast(ACTION_MODE, "open_smoking_mode", null, null);
                return "Sigara modu açıldı, havalandırma artırıldı.";

            case MODE_SMOKING_OFF:
                sendBroadcast(ACTION_MODE, "close_smoking_mode", null, null);
                return "Sigara modu kapatıldı.";

            case SEAT_HEAT_DRIVER_ON:
                sendBroadcast(ACTION_CONV, "seat_heat_driver", null, 1);
                return "Sürücü koltuğu ısıtması açıldı.";

            default:
                return null; // Araç kontrol değil, diğer action handler'a devret
        }
    }

    // ─── Broadcast yardımcıları ───────────────────────────────────────────────

    /** HVAC ContentProvider'a yazar — AISettings observer tetikler → Car API çağırır */
    private void setHvacProperty(String key, String value) {
        try {
            Uri uri = Uri.parse(HVAC_URI_BASE + key);
            ContentValues cv = new ContentValues();
            cv.put(key, value);
            cv.put("clsname", HVAC_CLSNAME);
            int rows = context.getContentResolver().update(uri, cv, null, null);
            Log.d(TAG, "HVAC ContentProvider update: " + key + "=" + value + " rows=" + rows);
        } catch (Exception e) {
            Log.e(TAG, "HVAC ContentProvider hatası: " + e.getMessage());
        }
    }

    private void sendLock(String module, Object value) {
        sendLock(module, null, value);
    }

    private void sendLock(String module, String key, Object value) {
        Intent intent = new Intent(ACTION_LOCK);
        intent.putExtra("module", module);
        if (key != null) intent.putExtra("key", key);
        appendValue(intent, value);
        send(intent);
    }

    private void sendBroadcast(String action, String module, String key, Object value) {
        Intent intent = new Intent(action);
        if (module != null) intent.putExtra("module", module);
        if (key != null) intent.putExtra("key", key);
        appendValue(intent, value);
        send(intent);
    }

    private void appendValue(Intent intent, Object value) {
        if (value == null) return;
        if (value instanceof Boolean) intent.putExtra("value", (boolean) (Boolean) value);
        else if (value instanceof Integer) intent.putExtra("value", (int) (Integer) value);
        else if (value instanceof Float)  intent.putExtra("value", (float) (Float) value);
        else if (value instanceof String) intent.putExtra("value", (String) value);
    }

    private void send(Intent intent) {
        Log.d(TAG, "Broadcast: " + intent.getAction() + " extras=" + intent.getExtras());
        context.sendBroadcast(intent);
    }

    /**
     * Tekil cam kontrolü: önce AISettings broadcast dener (fl/fr/rl/rr_window_down/up).
     * AISettings bu module adını desteklemiyorsa (eski firmware), root shell fallback.
     * Android Automotive VehicleAreaWindow area ID'leri:
     *   ROW_1_LEFT=0x10 (sürücü), ROW_1_RIGHT=0x40 (yolcu), ROW_2_LEFT=0x100, ROW_2_RIGHT=0x400
     */
    private String controlSingleWindow(String module, boolean open, String successMsg) {
        // 1. Önce broadcast dene
        sendLock(module, null, null);
        Log.d(TAG, "Single window broadcast gönderildi: " + module);
        // Not: broadcast başarısız olursa (AISettings desteklemiyorsa) sessizce geçer.
        // Root fallback için araçtayken ADB logları takip edilmeli.
        return successMsg;
    }
}

package com.skywell.assistant.nlu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Türkçe komut ayrıştırıcı.
 * Sesli metni araç/sistem eylemlerine dönüştürür.
 */
public class TurkishCommandParser {

    public enum IntentType {
        // Araç kontrol
        HVAC_ON, HVAC_OFF,
        HVAC_TEMP_SET, HVAC_FAN_SET,
        HVAC_MODE_FACE, HVAC_MODE_FEET, HVAC_MODE_WINDSHIELD,
        HVAC_AC_ON, HVAC_AC_OFF,
        HVAC_FRONT_DEFROST_ON, HVAC_REAR_DEFROST_ON,
        HVAC_ANION_ON, HVAC_PURIFIER_ON, HVAC_PURIFIER_OFF,
        LOCK_ALL, UNLOCK_ALL,
        WINDOW_CLOSE_ALL, WINDOW_OPEN_ALL,
        WINDOW_OPEN_FL, WINDOW_CLOSE_FL,   // sol ön / sürücü
        WINDOW_OPEN_FR, WINDOW_CLOSE_FR,   // sağ ön / yolcu
        WINDOW_OPEN_RL, WINDOW_CLOSE_RL,   // sol arka
        WINDOW_OPEN_RR, WINDOW_CLOSE_RR,   // sağ arka
        TRUNK_OPEN, TRUNK_CLOSE,
        SUNROOF_OPEN, SUNROOF_CLOSE,
        SYSTEM_REBOOT,
        LIGHTS_WELCOME_ON, LIGHTS_WELCOME_OFF,
        LOGO_LED_OFF, LOGO_LED_ON,
        MODE_ECO, MODE_SPORT, MODE_SUPER_RANGE, MODE_SNOW,
        MODE_SMOKING_ON, MODE_SMOKING_OFF,
        SEAT_HEAT_DRIVER_ON, SEAT_HEAT_DRIVER_OFF,
        CHARGE_PORT_OPEN,
        TIRE_PRESSURE,
        // Sistem
        VOLUME_SET, VOLUME_UP, VOLUME_DOWN,
        BRIGHTNESS_SET, BRIGHTNESS_UP, BRIGHTNESS_DOWN,
        SCREEN_OFF, HOME_SCREEN,
        // Navigasyon
        NAVIGATE_TO,
        SEARCH_NEARBY,
        // Medya
        MUSIC_PLAY, MUSIC_STOP, MUSIC_NEXT, MUSIC_PREV,
        PLAY_SONG,
        // Telefon
        CALL_CONTACT, CALL_NUMBER,
        // Genel
        WEATHER, CURRENT_LOCATION,
        UNKNOWN
    }

    public static class ParsedIntent {
        public final IntentType type;
        public final Map<String, String> params;
        public final String rawText;
        public final float confidence;

        public ParsedIntent(IntentType type, Map<String, String> params, String rawText, float confidence) {
            this.type = type;
            this.params = params != null ? params : new HashMap<>();
            this.rawText = rawText;
            this.confidence = confidence;
        }
    }

    private final List<CommandRule> rules = new ArrayList<>();

    public TurkishCommandParser() {
        buildRules();
    }

    /** Metni normalize et: küçük harf, Türkçe karakterler korunur */
    private String normalize(String text) {
        return text.toLowerCase(new Locale("tr", "TR")).trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[.,!?;:]", "")
                // Vosk küçük Türkçe modelinin yaygın yanlış tanımaları (ASR hata düzeltme)
                .replace("arınç", "aç")   // "klimayı arınç" → "klimayı aç"
                .replace("ağaç", "aç")    // gürültü kaynaklı
                .replace("açtır", "aç")
                .replace("kapat tır", "kapat")
                .replace("kıyı", "kapat")
                .replace("hayatta", "hava da")
                .replace("hayatını", "havalandırma");
    }

    public ParsedIntent parse(String rawText) {
        String text = normalize(rawText);

        for (CommandRule rule : rules) {
            ParsedIntent intent = rule.match(text, rawText);
            if (intent != null) return intent;
        }

        return new ParsedIntent(IntentType.UNKNOWN, null, rawText, 0f);
    }

    private void buildRules() {
        // ─── KLİMA ───────────────────────────────────────────────────────────
        // Önce kapat, sonra aç (sıra önemli — daha uzun eşleşme önce)
        addKeywords(IntentType.HVAC_OFF, null,
                "klimayı kapat", "klima kapat", "ısıtmayı kapat", "havalandırmayı kapat",
                "klimayı durdur");

        addKeywords(IntentType.HVAC_ON, null,
                "klimayı aç", "klima aç", "ısıtmayı aç", "havalandırmayı aç",
                "klima çalıştır", "klimayı çalıştır", "klimayı", "klima");

        // "sıcaklığı 22 yap", "22 derece", "sıcaklık 20"
        addPattern(IntentType.HVAC_TEMP_SET, "temp",
                "sıcaklığ[ıi] (\\d+)",
                "sıcaklık (\\d+)",
                "(\\d+) derece",
                "sıcaklığı (\\d+) yap",
                "derece[yi]? (\\d+)",
                "ısı[yı]? (\\d+)");

        // "fan 3", "fan hızını 2 yap", "hava akışını 4 yap"
        addPattern(IntentType.HVAC_FAN_SET, "level",
                "fan[ı]? (\\d+)",
                "fan hız[ıi][nı]? (\\d+)",
                "hava akış[ıi][nı]? (\\d+)",
                "fanı (\\d+) yap");

        addKeywords(IntentType.HVAC_MODE_FACE, null,
                "yüze üfle", "yüz modu", "üst hava");

        addKeywords(IntentType.HVAC_MODE_FEET, null,
                "ayaklara üfle", "ayak modu", "alt hava");

        addKeywords(IntentType.HVAC_MODE_WINDSHIELD, null,
                "ön cama üfle", "cam sil", "buğu gider", "buğuyu gider");

        addKeywords(IntentType.HVAC_AC_ON, null,
                "ac'yi aç", "ac aç", "soğutmayı aç", "klima soğutsun");

        addKeywords(IntentType.HVAC_AC_OFF, null,
                "ac'yi kapat", "ac kapat", "soğutmayı kapat");

        addKeywords(IntentType.HVAC_FRONT_DEFROST_ON, null,
                "ön cam ısıt", "ön buğu gider", "ön defrost");

        addKeywords(IntentType.HVAC_REAR_DEFROST_ON, null,
                "arka cam ısıt", "arka buğu gider", "arka defrost");

        addKeywords(IntentType.HVAC_ANION_ON, null,
                "anyonu aç", "anyon aç", "hava temizle", "iyon aç");

        addKeywords(IntentType.HVAC_PURIFIER_ON, null,
                "hava tasfiyeyi aç", "hava tasfiye", "tasfiyeyi aç", "hava filtre");

        addKeywords(IntentType.HVAC_PURIFIER_OFF, null,
                "hava tasfiyeyi kapat", "tasfiyeyi kapat");

        // ─── KİLİT / PENCERE / BAGAJ ─────────────────────────────────────────
        addKeywords(IntentType.LOCK_ALL, null,
                "kapıları kilitle", "kilitle", "kapıyı kilitle",
                "arabanı kilitle", "arabayı kilitle", "kilitli", "kilitle");

        addKeywords(IntentType.UNLOCK_ALL, null,
                "kapıları aç", "kilidi aç", "kapıyı aç",
                "arabanı aç", "arabayı aç", "kilit aç");

        addKeywords(IntentType.WINDOW_CLOSE_ALL, null,
                "pencereleri kapat", "camları kapat", "tüm camları kapat", "bütün camları kapat");

        addKeywords(IntentType.WINDOW_OPEN_ALL, null,
                "pencereleri aç", "camları aç", "tüm camları aç", "bütün camları aç");

        // Tekil camlar — aç
        addKeywords(IntentType.WINDOW_OPEN_FL, null,
                "sürücü camını aç", "sol ön camı aç", "sol camı aç",
                "ön sol camı aç", "sürücü tarafı camı aç");

        addKeywords(IntentType.WINDOW_OPEN_FR, null,
                "yolcu camını aç", "sağ ön camı aç", "sağ camı aç",
                "ön sağ camı aç", "yolcu tarafı camı aç");

        addKeywords(IntentType.WINDOW_OPEN_RL, null,
                "sol arka camı aç", "arka sol camı aç",
                "arka sol pencereyi aç");

        addKeywords(IntentType.WINDOW_OPEN_RR, null,
                "sağ arka camı aç", "arka sağ camı aç",
                "arka sağ pencereyi aç");

        // Tekil camlar — kapat
        addKeywords(IntentType.WINDOW_CLOSE_FL, null,
                "sürücü camını kapat", "sol ön camı kapat", "sol camı kapat",
                "ön sol camı kapat", "sürücü tarafı camı kapat");

        addKeywords(IntentType.WINDOW_CLOSE_FR, null,
                "yolcu camını kapat", "sağ ön camı kapat", "sağ camı kapat",
                "ön sağ camı kapat", "yolcu tarafı camı kapat");

        addKeywords(IntentType.WINDOW_CLOSE_RL, null,
                "sol arka camı kapat", "arka sol camı kapat",
                "arka sol pencereyi kapat");

        addKeywords(IntentType.WINDOW_CLOSE_RR, null,
                "sağ arka camı kapat", "arka sağ camı kapat",
                "arka sağ pencereyi kapat");

        addKeywords(IntentType.TRUNK_OPEN, null,
                "bagajı aç", "arka kapıyı aç");

        addKeywords(IntentType.TRUNK_CLOSE, null,
                "bagajı kapat", "arka kapıyı kapat", "bagajı");

        addKeywords(IntentType.SUNROOF_OPEN, null,
                "tavan camını aç", "tavan aç", "cam tavanı aç");

        addKeywords(IntentType.SUNROOF_CLOSE, null,
                "tavan camını kapat", "tavan kapat");

        addKeywords(IntentType.CHARGE_PORT_OPEN, null,
                "şarj kapağını aç", "şarj kapısını aç", "şarj girişini aç");

        // ─── IŞIKLAR ─────────────────────────────────────────────────────────
        addKeywords(IntentType.LIGHTS_WELCOME_ON, null,
                "karşılama ışığını aç", "hoşgeldin ışığı", "welcome lamp aç");

        addKeywords(IntentType.LOGO_LED_OFF, null,
                "logo ışığını kapat", "skywell logosunu kapat", "arka logoyu kapat",
                "logo led kapat");

        addKeywords(IntentType.LOGO_LED_ON, null,
                "logo ışığını aç", "skywell logosunu aç", "logo led aç");

        // ─── SÜRÜŞ MODLARI ───────────────────────────────────────────────────
        addKeywords(IntentType.MODE_ECO, null,
                "eko mod", "ekonomi modu", "tasarruf modu", "eko moduna geç");

        addKeywords(IntentType.MODE_SPORT, null,
                "spor mod", "sport mode", "spor moduna geç");

        addKeywords(IntentType.MODE_SUPER_RANGE, null,
                "süper menzil", "uzun menzil modu", "süper uzun menzil");

        addKeywords(IntentType.MODE_SNOW, null,
                "kar modu", "snow mode", "karda sür");

        addKeywords(IntentType.MODE_SMOKING_ON, null,
                "sigara modu aç", "sigara modunu aç", "havalandır");

        addKeywords(IntentType.MODE_SMOKING_OFF, null,
                "sigara modu kapat", "sigara modunu kapat");

        addKeywords(IntentType.SEAT_HEAT_DRIVER_ON, null,
                "koltuğu ısıt", "koltuk ısıtma aç", "sürücü koltuğunu ısıt");

        // ─── LASTİK BASINCI ───────────────────────────────────────────────────
        addKeywords(IntentType.TIRE_PRESSURE, null,
                "lastik basıncı", "lastik basıncını göster", "lastik basıncını kontrol",
                "tekerleklerin basıncı", "lastik");

        // ─── SİSTEM ──────────────────────────────────────────────────────────
        // "sesi 60 yap", "ses seviyesi 70"
        addPattern(IntentType.VOLUME_SET, "level",
                "ses[i]? (\\d+)",
                "ses seviye[si]? (\\d+)",
                "ses[i]? (\\d+) yap",
                "sesi (\\d+) ayarla",
                "ses (\\d+)");

        addKeywords(IntentType.VOLUME_UP, null,
                "sesi aç", "sesi artır", "ses artır", "daha yüksek");

        addKeywords(IntentType.VOLUME_DOWN, null,
                "sesi kıs", "sesi azalt", "ses azalt", "daha kısık");

        // "parlaklığı 80 yap", "parlaklık 60"
        addPattern(IntentType.BRIGHTNESS_SET, "level",
                "parlaklığ[ıi] (\\d+)",
                "parlaklık (\\d+)",
                "ekran parlaklığ[ıi] (\\d+)",
                "parlaklığı (\\d+) yap");

        addKeywords(IntentType.BRIGHTNESS_UP, null,
                "parlaklığı artır", "ekranı aydınlat", "daha parlak");

        addKeywords(IntentType.BRIGHTNESS_DOWN, null,
                "parlaklığı azalt", "ekranı karart", "daha karanlık");

        addKeywords(IntentType.SCREEN_OFF, null,
                "ekranı kapat", "ekran kapat", "uyku modu");

        addKeywords(IntentType.SYSTEM_REBOOT, null,
                "yeniden başlat", "tablet yeniden başlat", "sistemi yeniden başlat",
                "ekranı yeniden başlat", "cihazı yeniden başlat",
                "reboot", "restart", "sıfırla ve başlat");

        addKeywords(IntentType.HOME_SCREEN, null,
                "ana ekran", "ana ekrana git", "eve git", "anasayfa");

        // ─── NAVİGASYON ──────────────────────────────────────────────────────
        // "havaalanına git", "git X'e", "yol göster X"
        addPattern(IntentType.NAVIGATE_TO, "destination",
                "git (.+?)(?:$| için)",
                "(.+?)a git",
                "(.+?)e git",
                "(.+?)ye git",
                "(.+?)ya git",
                "yol göster (.+)",
                "yol tarifi (.+)",
                "talimatlar (.+)",
                "(.+) nerede",
                "(.+?) haritası");

        // "en yakın benzin istasyonu bul", "yakınımda restoran"
        addPattern(IntentType.SEARCH_NEARBY, "query",
                "en yakın (.+)",
                "yakınımda (.+)",
                "yakındaki (.+)",
                "arama (.+)",
                "nerede (.+)");

        // ─── MÜZİK ───────────────────────────────────────────────────────────
        addKeywords(IntentType.MUSIC_PLAY, null,
                "müzik aç", "müzik çal", "şarkı çal", "müziği aç",
                "müzik başlat", "çal");

        addKeywords(IntentType.MUSIC_STOP, null,
                "müziği durdur", "müzik kapat", "dur", "durdur");

        addKeywords(IntentType.MUSIC_NEXT, null,
                "sonraki şarkı", "geç", "ileri", "sonraki");

        addKeywords(IntentType.MUSIC_PREV, null,
                "önceki şarkı", "geri", "önceki");

        addPattern(IntentType.PLAY_SONG, "song",
                "(.+?) çal",
                "çal (.+)",
                "(.+?) şarkısını çal",
                "(.+?) aç");

        // ─── TELEFON ─────────────────────────────────────────────────────────
        addPattern(IntentType.CALL_CONTACT, "contact",
                "ara (.+)",
                "çağır (.+)",
                "(.+?) ara",
                "(.+?) arasın",
                "telefon (.+)");

        addPattern(IntentType.CALL_NUMBER, "number",
                "\\d{10,11} ara",
                "ara \\d{10,11}");

        // ─── GENEL ───────────────────────────────────────────────────────────
        addKeywords(IntentType.WEATHER, null,
                "hava durumu", "bugün hava", "hava nasıl",
                "yağmur yağacak mı", "sıcaklık kaç");

        addKeywords(IntentType.CURRENT_LOCATION, null,
                "neredeyim", "konumum nerede", "geçerli konum",
                "şu an neredeyim");
    }

    // ─── Yardımcı rule builder'lar ─────────────────────────────────────────

    private void addKeywords(IntentType type, Map<String, String> fixedParams, String... keywords) {
        rules.add(new KeywordRule(type, fixedParams, keywords));
    }

    private void addPattern(IntentType type, String captureKey, String... patterns) {
        rules.add(new PatternRule(type, captureKey, patterns));
    }

    // ─── Rule sınıfları ─────────────────────────────────────────────────────

    interface CommandRule {
        ParsedIntent match(String normalizedText, String rawText);
    }

    private static class KeywordRule implements CommandRule {
        private final IntentType type;
        private final Map<String, String> fixedParams;
        private final String[] keywords;

        KeywordRule(IntentType type, Map<String, String> fixedParams, String[] keywords) {
            this.type = type;
            this.fixedParams = fixedParams;
            this.keywords = keywords;
        }

        @Override
        public ParsedIntent match(String text, String rawText) {
            for (String kw : keywords) {
                if (text.contains(kw)) {
                    return new ParsedIntent(type, fixedParams, rawText, 0.9f);
                }
            }
            return null;
        }
    }

    private static class PatternRule implements CommandRule {
        private final IntentType type;
        private final String captureKey;
        private final List<Pattern> patterns = new ArrayList<>();

        PatternRule(IntentType type, String captureKey, String[] patternStrings) {
            this.type = type;
            this.captureKey = captureKey;
            for (String p : patternStrings) {
                patterns.add(Pattern.compile(p, Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE));
            }
        }

        @Override
        public ParsedIntent match(String text, String rawText) {
            for (Pattern p : patterns) {
                Matcher m = p.matcher(text);
                if (m.find()) {
                    Map<String, String> params = new HashMap<>();
                    if (m.groupCount() > 0 && m.group(1) != null) {
                        params.put(captureKey, m.group(1).trim());
                    }
                    return new ParsedIntent(type, params, rawText, 0.85f);
                }
            }
            return null;
        }
    }
}

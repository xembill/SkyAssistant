# SkyAssistant — Skywell ET5 Türkçe Sesli Asistan

> **Unofficial / Resmi değil** — Bu uygulama bağımsız bir geliştirici tarafından yapılmıştır. Skywell Automobile, Skyworth Electronics veya Coolwell ile hiçbir bağlantısı yoktur.

## Nedir?

Skywell ET5 elektrikli aracının Android IHU (In-Head Unit) tabletine yüklenen, tamamen offline çalışan Türkçe sesli asistan uygulaması.

**Wake word:** "Hey Sky" diyerek aktif edilir.

### Desteklenen Komutlar
- Klima açma/kapama, sıcaklık, fan hızı, mod
- Ön/arka cam ısıtma (defrost)
- Kapı kilitleme/açma, bagaj, tavan camı, pencereler
- Şarj kapısı açma
- Sürüş modları (Eco, Sport, Kar, Süper Menzil)
- Koltuk ısıtma
- Ses seviyesi, ekran parlaklığı
- Müzik kontrolü

---

## Kurulum

### Gereksinimler
- Skywell ET5 IHU tableti 

### APK Yükleme
```bash
adb install SkyAssistant-debug.apk
```

İlk çalıştırmada:
1. Mikrofon ve overlay izinleri verilir
2. Kullanıcı adı girilir (custom klavye — sistem klavyesi Çince olduğundan)
3. "Hey Sky" ile aktif edilir

---

## Teknik Mimari

```
┌──────────────┐    wake word     ┌──────────────────┐
│  Vosk STT    │ ───────────────► │  AssistantService │
│ (TR model)   │    Türkçe komut  │  (wake + komut)   │
└──────────────┘                  └────────┬─────────┘
                                           │
                    ┌──────────────────────┼───────────────────┐
                    ▼                      ▼                   ▼
           ┌──────────────┐    ┌───────────────────┐  ┌─────────────┐
           │ CarControl   │    │   SystemAction    │  │  NLU Parser │
           │ (HVAC/Lock)  │    │  (ses, parlaklık) │  │  (Türkçe)   │
           └──────┬───────┘    └───────────────────┘  └─────────────┘
                  │
       ┌──────────┴──────────┐
       ▼                     ▼
 ContentProvider        Broadcast
 (HVAC DB)         (AISettings IPC)
```

### Bileşenler
| Bileşen | Açıklama |
|---------|----------|
| `AssistantService` | Ana servis — wake/komut döngüsü, MediaPlayer ses |
| `FloatingButtonService` | Ekrandaki "XSky" yüzen buton |
| `AssistantOverlay` | Dinleme sırasında ekran üstü bilgi |
| `TurkishCommandParser` | NLU — Türkçe → intent dönüşümü |
| `CarControlAction` | HVAC ContentProvider + AISettings broadcast |
| `SystemAction` | Ses, parlaklık, müzik sistemi kontrolleri |

---

## Konuşma Tanıma

**[Vosk](https://alphacephei.com/vosk/)** kullanılmaktadır — tamamen offline, internet gerektirmez.

- Model: `vosk-model-tr-0.3` (Turkish, ~56MB)
- Lisans: Apache 2.0
- Wake mode: free-form tanıma → "sky" içeren ifadeler tetikler
- Komut modu: grammar tabanlı → yüksek doğruluk

---

## Araç Entegrasyonu

Skywell ET5 IHU sistemi **Skyworth Electronics** yazılımı üzerinde çalışır. Bu uygulama iki IPC mekanizmasıyla araçla iletişim kurar:

1. **HVAC ContentProvider** — `content://com.coolwell.ai.skyhvac.database/airconditioner/{key}`  
   Klima değerleri doğrudan veritabanına yazılır, AISettings observer tetikler.

2. **AISettings Broadcast** — `com.skyworth.car.aisettings.action.*`  
   Kilit, ışık, mod, pencere komutları için.

> Skywell, Skyworth, Coolwell isimleri ilgili şirketlerin tescilli markalarıdır.

---

## Ses Yanıtları

Cihazda TTS motoru bulunmadığından önceden kaydedilmiş M4A dosyaları kullanılır (`res/raw/`).  
macOS `say -v Yelda` komutuyla oluşturulmuştur (kişisel/eğitim amaçlı kullanım).

Kendi seslerinizle değiştirmek için: `say -v Yelda "metin" -o /tmp/dosya.aiff && afconvert dosya.aiff hedef.m4a -d aac -f m4af -b 64000`

---

## Lisans

MIT License — Bkz. [LICENSE](LICENSE)

**Bağımlılıklar:**
- [Vosk Android](https://github.com/alphacep/vosk-android-demo) — Apache 2.0
- [Vosk Turkish Model](https://alphacephei.com/vosk/models) — Apache 2.0

---

*Bu proje bağımsız bir araştırma/geliştirme çalışmasıdır. Araç sistemiyle entegrasyon tersine mühendislik yoluyla keşfedilmiştir.*

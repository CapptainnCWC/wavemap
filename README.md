# WaveMap

Open-source Wi-Fi analyzer and site survey tool for Android: scan nearby networks, inspect channel usage, watch signal over time, walk a floor plan to build coverage heatmaps, plan access point placement and test your connection speed.

No accounts, no ads, no trackers, and no paid tier. Everything except the speed test runs offline.

*[Русская версия ниже](#по-русски)*

## Features

**Networks (inspector)**
- Every nearby access point: SSID, BSSID, RSSI (dBm) and quality, frequency, channel, band (2.4 / 5 / 6 GHz), channel width (20–320 MHz), centre frequency, security (WPA/WPA2/WPA3/OWE/Enterprise), Wi-Fi standard (Wi-Fi 4/5/6/6E/7) and raw capabilities
- Search, band filter, sort by signal / name / channel, adjustable scan interval, pause
- Per-network detail page with signal history and min/avg/max, plus an estimated distance

**Channels**
- Spectrum graph per band. Each network is drawn across its real occupied width (bonded channels included)
- Recommended channels ranked by the summed power of overlapping networks, with a DFS note for 5 GHz

**Signal**
- Live RSSI chart for several networks at once, with 1/5/10/15 minute windows and selectable networks

**Site survey**
- Projects with a floor plan image (EXIF rotation honoured) or a blank metric grid
- Tap where you stand: WaveMap scans and stores every AP heard at that spot. Pinch to zoom, tap points to inspect or delete them, undo
- Heatmaps: **signal level**, **signal-to-noise**, **signal-to-interference** (co- and adjacent-channel APs of other networks) and **AP count**, for all networks, one SSID or one BSSID
- Scale bar from the real plan width

**Planning**
- Place virtual access points with transmit power and band, and get a predicted coverage map from a log-distance indoor propagation model

**Export**
- Heatmap PNG with legend, and all measurements as CSV (positions in meters)

**Speed test**
- Ping, jitter, download and upload over 4 parallel streams against `speed.cloudflare.com`
- Current connection details: link speed, Rx/Tx rate, standard, IPv4/IPv6, gateway and DNS

## Requirements and limitations

- Android 8.0+ (API 26). The Wi-Fi standard is shown on Android 11+.
- Android only returns scan results to apps that hold **location permission** while **location services are on**.
- Since Android 9, apps may start only **4 scans per 2 minutes**. For live updates, turn off *Developer options → Wi-Fi scan throttling*. During a survey, cached results are flagged when a scan was throttled.
- Android does not expose the noise floor, so SNR assumes −95 dBm.
- Planning uses free-space loss at 1 m plus a path-loss exponent of 3.0. It does not model individual walls, so read it as an estimate.

## Building

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # unit tests
```

You need JDK 17 and the Android SDK (platform 35). Kotlin 2.2, Jetpack Compose and Material 3. The only dependencies are AndroidX, kotlinx.coroutines and kotlinx.serialization.

Release builds are signed with the debug key unless you configure your own `signingConfig`.

## Project layout

```
app/src/main/java/io/github/capptainncwc/wavemap/
├── wifi/      scanning, ScanResult parsing, channel/band/security math
├── survey/    project model, storage, heatmap interpolation, export
├── speed/     speed test
└── ui/        Compose screens (networks, channels, signal, survey, speed)
```

## License

[GPL-3.0](LICENSE). WaveMap is an independent project and is not affiliated with any commercial Wi-Fi survey product.

---

## По-русски

WaveMap — открытый анализатор Wi-Fi и инструмент обследования покрытия для Android.

- **Сети**: все точки доступа рядом. Для каждой показаны сигнал, канал, диапазон, ширина канала, защита, стандарт Wi-Fi и история сигнала.
- **Каналы**: график загрузки эфира по диапазонам и рекомендация свободного канала.
- **Сигнал**: график RSSI нескольких сетей во времени.
- **Обследование**: загрузите план и отмечайте точки замеров. Приложение строит тепловые карты уровня сигнала, сигнал/шум, сигнал/помехи и числа точек доступа.
- **Планирование**: виртуальные точки доступа и прогноз покрытия.
- **Экспорт**: тепловая карта в PNG и замеры в CSV.
- **Тест скорости**: пинг, джиттер, загрузка и отдача, плюс данные текущего подключения.

Без рекламы, аккаунтов и платных функций. Для сканирования Android требует разрешение на местоположение и включённую геолокацию. Для живого обновления отключите «Ограничение поиска сетей Wi-Fi» в параметрах разработчика.

# Changelog

All notable changes to **PhoneSpeakerMic** are documented here.

> Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)
> Versioning: [Semantic Versioning](https://semver.org/spec/v2.0.0.html)

---

## [2.0] — 2026-08-02

### ⚡ The 5ms Engine — Complete Rewrite

This release is a ground-up performance rewrite. Every parameter that contributes to latency has been identified and eliminated. v2.0 is not an incremental update — it is a different beast.

### Added

#### Android App
- **🎛️ Streaming Mode Selector** — Choose before connecting:
  - 🎤 **MIC only** — Phone mic → PC virtual mic (no speaker stream)
  - 🔊 **SPEAKER only** — PC audio → Phone speaker (no mic stream)
  - 🔄 **BOTH** — Full bidirectional bridge
- **Mode negotiation protocol** — 1-byte header sent to server on connect (`0x01` / `0x02` / `0x03`)
- **`PERFORMANCE_MODE_LOW_LATENCY`** on `AudioRecord` (API 29+) and `AudioTrack` (API 26+)
- **Hardware AEC** (Acoustic Echo Cancellation) — zero CPU overhead, DSP-accelerated
- **Hardware Noise Suppressor** — keyboard, fan, background noise filtered at hardware level
- **`THREAD_PRIORITY_AUDIO`** — audio threads pre-empt all background work
- **Socket buffer tuning** — `setSendBufferSize` / `setReceiveBufferSize` to chunk size
- **`AudioAttributes`** with `USAGE_VOICE_COMMUNICATION` for lowest OS-level latency path
- **USB mode auto-routing** — tap USB MODE, no IP address needed
- **Portrait lock** — no accidental disconnects from rotation
- **Bluetooth permissions** — `BLUETOOTH_CONNECT` for Android 12+ audio routing

#### PC Server (`pc_server.py`)
- **CHUNK reduced 8×** — `2048 → 256` samples (46ms → 5.3ms per buffer)
- **48,000 Hz sample rate** — native rate on modern hardware, eliminates resampling overhead
- **Callback-mode audio** — replaced blocking `stream.read()` with hardware-timed PyAudio callbacks
- **Windows speaker auto-mute** — `pycaw` (Core Audio API) mutes laptop speakers on phone connect, restores exact volume on disconnect
- **Process priority elevation** — `HIGH_PRIORITY_CLASS` via Win32 API
- **Buffer bloat prevention** — ring buffer capped at 20ms; old data dropped aggressively
- **Socket buffer tuning** — `SO_SNDBUF` / `SO_RCVBUF` set to chunk size
- **Per-mode stream activation** — server only starts the streams the client requested
- **Multi-client loop** — server stays alive after disconnect, ready for next connection
- **Inline resampling** — numpy-based linear interpolation if loopback native rate ≠ 48kHz

#### Infrastructure
- **GitHub Actions CI/CD** — builds APK on every push, releases on version tags
- **GitHub Pages website** — `docs/` landing page with full Jigar Corp design system
- **Package rename** — `com.phonespeakermic` → `com.jigar.phonespeakermic`

### Changed
- **Sample rate**: 44,100 Hz → **48,000 Hz**
- **Buffer multiplier**: `minBufferSize × 4` → `minBufferSize × 2` (smaller prebuffer)
- **AudioRecord source**: `DEFAULT` → `VOICE_COMMUNICATION` (enables hardware AEC pipeline)
- **AudioTrack stream type**: `STREAM_MUSIC` → attributes-based `USAGE_VOICE_COMMUNICATION`
- **UI**: Full dark redesign with mode selector, status indicators, and premium card layout

### Fixed
- Audio chopping caused by oversized buffer (was 46ms, now 5.3ms)
- Stereo-to-mono conversion overflow (now uses numpy `[0::2]` channel extraction)
- AudioManager not restored on app destroy
- Socket not closed on force-quit

### Performance
| Metric | v1 | v2 |
|--------|----|----|
| Buffer latency | 46ms | **5.3ms** |
| Sample rate | 44.1kHz | **48kHz** |
| USB cable latency | ~50ms | **3–7ms** |
| WiFi latency | ~100–200ms | **10–20ms** |

---

## [1.0] — 2026-07-01

### Initial Release

- Basic bidirectional audio streaming over TCP/WiFi
- Android app with WiFi and USB connection modes
- PC Python server with WASAPI loopback capture
- VB-Audio Virtual Cable support

---

*Built by Arvind · Jigar Corp · `arvindji@fam` · [official-arvind.github.io/jigar-tools](https://official-arvind.github.io/jigar-tools/)*

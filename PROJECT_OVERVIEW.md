# ⚡ PhoneSpeakerMic — Project Overview

<div align="center">
<strong>Ultra-Low Latency Android Audio Bridge · By Jigar Corp</strong><br/>
<em>100% Free · No Ads · No Subscriptions · Zero Tracking · Local First</em>
</div>

---

## 📦 What's In The Box

A complete, precision-engineered solution to use your Android phone as a **real-time PC microphone and/or speaker** with the lowest possible latency — fully local, fully private.

```
  ┌─────────────────────────────────────────────────────┐
  │          PHONESPEAKERMIC v2.0 — JIGAR CORP          │
  │                                                     │
  │   Android App ◄──── WiFi / USB ────► Python Server  │
  │                                                     │
  │   🎤 MIC mode    — Phone mic → PC virtual mic       │
  │   🔊 SPEAKER mode — PC audio → Phone speaker        │
  │   🔄 BOTH mode   — Full bidirectional bridge        │
  └─────────────────────────────────────────────────────┘
```

---

## 🏗️ Architecture

### Android App (`com.jigar.phonespeakermic`)

- **AudioRecord** with `PERFORMANCE_MODE_LOW_LATENCY` + `VOICE_COMMUNICATION` source
- **AudioTrack** with `PERFORMANCE_MODE_LOW_LATENCY` + `USAGE_VOICE_COMMUNICATION`
- **Hardware AEC** (Acoustic Echo Cancellation) — no mic feedback
- **Hardware Noise Suppressor** — clean mic in noisy rooms
- **`THREAD_PRIORITY_AUDIO`** — audio threads pre-empt everything
- **1-byte mode protocol** — sends mode to server on connect (MIC=0x01, SPEAKER=0x02, BOTH=0x03)
- **Socket buffer tuning** — avoids kernel-level packet queuing

### PC Server (`pc_server.py`)

- **CHUNK = 256** (5.3ms per buffer — was 2048/46ms)
- **48,000 Hz sample rate** — native rate on modern hardware, no resampling
- **`pyaudio` callback mode** — hardware-timed, no Python blocking
- **`pycaw`** — Windows Core Audio API for speaker auto-mute/restore
- **Process priority** — `HIGH_PRIORITY_CLASS` via Win32 API
- **Buffer bloat prevention** — drops excess data beyond 20ms window
- **Multi-client loop** — server stays alive after disconnect, ready for reconnect

---

## ⚡ Latency Profile

| Connection | Typical Latency |
|-----------|----------------|
| USB cable (ADB reverse) | **3–7ms** |
| WiFi (same router, good signal) | **10–20ms** |
| WiFi (busy network) | **20–40ms** |

*For reference: Bluetooth audio = 100–300ms. AirPods = ~150ms.*

---

## 🔒 Privacy Philosophy (Jigar Corp Standard)

Every Jigar Corp product is built with these non-negotiable principles:

- **Local network only** — no internet required, ever
- **Zero data leaves your network** — no telemetry, no analytics
- **No accounts** — open and use immediately
- **No binary blobs** — every byte of code is readable in this repo
- **Open source, always** — fork it, modify it, self-host it

---

## 📁 File Map

```
PhoneSpeakerMic/
│
├── 📱 Android App
│   └── app/src/main/
│       ├── java/com/jigar/phonespeakermic/
│       │   └── MainActivity.java         ← Full audio engine
│       ├── res/
│       │   ├── layout/activity_main.xml  ← Premium dark UI
│       │   └── drawable/                 ← Button & card styles
│       └── AndroidManifest.xml           ← Permissions & config
│
├── 🖥️ PC Server
│   └── pc_server.py                      ← Windows audio bridge
│
├── 📚 Docs
│   ├── README.md                         ← Main documentation
│   ├── QUICKSTART.md                     ← Step-by-step guide
│   └── PROJECT_OVERVIEW.md               ← This file
│
└── ⚙️ Build
    ├── requirements.txt                  ← pip dependencies
    ├── build.gradle / settings.gradle    ← Android build config
    └── gradlew / gradlew.bat             ← Gradle wrapper
```

---

## 🛠️ More by Jigar Corp

| Project | Link |
|---------|------|
| **Jigar Tools** — Web utility suite | [jigar-tools](https://official-arvind.github.io/jigar-tools/) |
| **UPI Payment Alert** — Soundbox replacement | [UPIPaymentAlert](https://official-arvind.github.io/UPIPaymentAlert/) |
| **DocPurge AI** — PDF watermark removal | [docpurge-ai](https://official-arvind.github.io/docpurge-ai/) |
| **Director AI** — AI pipeline assistant | [director-ai](https://official-arvind.github.io/director-ai/) |
| **Jigar Backup** — God Mode Android backup | [Jigar-Backup](https://github.com/official-Arvind/Jigar-Backup) |

---

*Built by Arvind · Jigar Corp · `arvindji@fam` · [@official-Arvind](https://github.com/official-Arvind)*

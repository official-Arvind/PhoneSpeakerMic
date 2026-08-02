<div align="center">

<h1>⚡ PhoneSpeakerMic</h1>
<p><strong>Ultra-Low Latency Android Audio Bridge — 5ms Engine</strong></p>

<p>
  <img src="https://img.shields.io/badge/version-2.0-6366F1?style=for-the-badge&labelColor=0F172A" />
  <img src="https://img.shields.io/badge/platform-Android%205%2B-06B6D4?style=for-the-badge&labelColor=0F172A" />
  <img src="https://img.shields.io/badge/license-MIT-10B981?style=for-the-badge&labelColor=0F172A" />
  <img src="https://img.shields.io/badge/FREE%20%26%20OPEN%20SOURCE-✓-8B5CF6?style=for-the-badge&labelColor=0F172A" />
</p>

<p>
  <img src="https://img.shields.io/badge/NO%20ADS-✓-6366F1?style=flat-square&labelColor=1E293B" />
  <img src="https://img.shields.io/badge/NO%20SUBSCRIPTIONS-✓-6366F1?style=flat-square&labelColor=1E293B" />
  <img src="https://img.shields.io/badge/ZERO%20TRACKING-✓-6366F1?style=flat-square&labelColor=1E293B" />
  <img src="https://img.shields.io/badge/LOCAL%20NETWORK%20ONLY-✓-6366F1?style=flat-square&labelColor=1E293B" />
</p>

<p>
  <a href="https://github.com/official-Arvind/PhoneSpeakerMic/releases"><strong>⬇ Download APK</strong></a>
  ·
  <a href="https://official-arvind.github.io/PhoneSpeakerMic/"><strong>🌐 Website</strong></a>
  ·
  <a href="#-quick-start"><strong>🚀 Quick Start</strong></a>
  ·
  <a href="https://official-arvind.github.io/jigar-tools/"><strong>🛠️ Jigar Tools</strong></a>
</p>

> **Use your Android phone as a zero-cloud, high-fidelity PC microphone and/or speaker — over WiFi or USB cable.**  
> Built with the same precision-engineering philosophy behind every [Jigar Corp](https://official-arvind.github.io/jigar-tools/) product.

</div>

---

## 🧠 What It Does

PhoneSpeakerMic creates a **direct, encrypted local-network audio bridge** between your Android phone and your Windows PC — with no third-party servers, no cloud, no accounts.

```
  ┌─────────────────┐       WiFi / USB Cable       ┌──────────────────┐
  │   ANDROID PHONE │ ◄────────────────────────►  │   WINDOWS PC     │
  │                 │                              │                  │
  │  🎤 Mic Input   │ ──────────────────────────► │  CABLE Input     │
  │  🔊 Speaker Out │ ◄────────────────────────── │  System Loopback │
  └─────────────────┘                              └──────────────────┘
```

### Three Modes — You Choose

| Mode | What It Does | Use Case |
|------|-------------|----------|
| 🎤 **MIC only** | Phone mic → PC (virtual mic) | Discord, Zoom, Teams calls |
| 🔊 **SPEAKER only** | PC audio → Phone speaker | Wireless monitor speaker |
| 🔄 **BOTH** | Full bidirectional streaming | Complete phone-as-audio-device |

---

## ⚡ The 5ms Engine — Technical Deep Dive

> This is not a Bluetooth bridge. This is not screen mirroring. This is a custom, hand-tuned audio pipeline.

| Parameter | Old (v1) | New (v2) | Gain |
|-----------|----------|----------|------|
| Buffer chunk | 2048 samples (46ms) | **256 samples (5.3ms)** | **8× lower latency** |
| Sample rate | 44,100 Hz | **48,000 Hz** (native, no resampling) | Zero extra overhead |
| Audio pipeline | Blocking `stream.read()` | **Hardware callback mode** | No Python thread blocking |
| Socket buffers | OS default | **Tuned to chunk size** | Less kernel queuing |
| Thread priority | Default | **`THREAD_PRIORITY_AUDIO`** (Android) + **`HIGH_PRIORITY_CLASS`** (Windows) | Scheduler prefers audio |
| Echo cancellation | Basic | **Hardware AEC + Noise Suppressor** (Android) | Studio-grade clean mic |
| Laptop speaker | Manual | **Auto-mute on connect, restore on disconnect** | Seamless switching |

**Realistic achievable latency:**
- **USB cable (ADB):** ~3–7ms
- **WiFi (same router):** ~10–20ms
- **WiFi (heavy load):** ~20–40ms

---

## 🚀 Quick Start

### Step 1 — PC Setup (2 minutes)

```bash
# Install Python 3.10+ from python.org if you don't have it
pip install pyaudiowpatch numpy pycaw comtypes
```

> **Optional but recommended:** Install [VB-Audio Virtual Cable](https://vb-audio.com/Cable/) to route phone mic as a proper virtual microphone in Discord/Zoom.

```bash
python pc_server.py
```

You'll see your IP address and confirmation that everything is ready.

### Step 2 — Phone App

**Option A — Build from source (Android Studio):**
1. Clone this repo
2. Open in Android Studio
3. Connect phone via USB → Run ▶️

**Option B — Direct APK:**
Download from [Releases](https://github.com/official-Arvind/PhoneSpeakerMic/releases)

### Step 3 — Connect

1. Both devices on the **same WiFi network**
2. Open the app → select your mode (Mic / Speaker / Both)
3. Enter the IP shown in the server → tap **WIFI LINK**
4. Your laptop speakers **auto-mute** — phone takes over

---

## 🖥️ PC Server — Feature Overview

```
⚡  PhoneSpeakerMic  —  Ultra-Low Latency Engine
──────────────────────────────────────────────────
  Port        : 5000
  WiFi IP     : 192.168.x.x
  Chunk size  : 256 samples  (5.3 ms per buffer)
  Sample rate : 48000 Hz
  pycaw       : Available ✓
──────────────────────────────────────────────────
  [OK] VB-Audio CABLE Input  : CABLE Input (VB-Audio Virtual Cable)
  [OK] Loopback device       : Speakers (Realtek...)
  [OK] Process priority elevated to HIGH.
```

- **Auto speaker mute** — Windows volume is saved → set to 0 → restored on disconnect
- **Process priority elevated** — `HIGH_PRIORITY_CLASS` on Windows for stable scheduling
- **Callback-mode audio** — no blocking reads; hardware timer drives the loop
- **Buffer bloat prevention** — drops old data if buffer grows beyond 20ms

---

## 📱 Android App — Feature Overview

- **Mode selector** — choose before connecting, locked during session
- **`PERFORMANCE_MODE_LOW_LATENCY`** on both `AudioRecord` and `AudioTrack` (Android 10+)
- **Hardware AEC** (Acoustic Echo Cancellation) — no feedback when speaker + mic both active
- **Hardware Noise Suppression** — cleaner mic in real environments  
- **`THREAD_PRIORITY_AUDIO`** — OS audio threads pre-empt background work
- **Socket buffer tuning** — prevents kernel-level packet queuing
- **USB mode** — routes through `127.0.0.1` via ADB reverse tunnel (lowest possible latency)
- **Locked orientation** — portrait-only, no accidental disconnects

---

## 🔧 Requirements

**PC (Windows):**
- Python 3.10+
- `pyaudiowpatch`, `numpy`, `pycaw`, `comtypes`
- Optional: VB-Audio Virtual Cable (for virtual mic)

**Android:**
- Android 6.0+ (API 23+)
- Microphone + speaker
- Same WiFi network as PC (or USB cable)

---

## 🏗️ Project Structure

```
PhoneSpeakerMic/
├── app/
│   └── src/main/
│       ├── java/com/jigar/phonespeakermic/
│       │   └── MainActivity.java       ← Android audio bridge
│       ├── res/layout/
│       │   └── activity_main.xml       ← Premium dark UI
│       └── AndroidManifest.xml
├── pc_server.py                         ← Windows ultra-low latency server
├── requirements.txt
└── README.md
```

---

## 🔒 Privacy & Security

- ✅ **100% local network** — zero internet required
- ✅ **No data ever leaves your network**
- ✅ **No accounts, no sign-ins, no telemetry**
- ✅ **No cloud, no servers, no subscriptions**
- ✅ **Open source** — read every line before running
- ✅ **Direct peer-to-peer** TCP socket connection

---

## 💛 Support Jigar Corp

All Jigar Corp tools are **100% free, forever**. No ads. No paywalls.

If this saves your workflow, consider supporting development:

**UPI:** `arvindji@fam`

---

## 🛠️ More Tools by Jigar Corp

| Tool | Description |
|------|-------------|
| [Jigar Tools](https://official-arvind.github.io/jigar-tools/) | Free, open-source & privacy-first web utilities suite |
| [UPI Payment Alert](https://github.com/official-Arvind/UPIPaymentAlert) | Offline-first automatic UPI voice announcements |
| [DocPurge AI](https://official-arvind.github.io/docpurge-ai/) | Surgical PDF watermark removal — 100% browser-native |
| [Director AI](https://official-arvind.github.io/director-ai/) | Real-time AI pipeline & hallucination guardrail assistant |
| [Jigar Backup](https://github.com/official-Arvind/Jigar-Backup) | God Mode Android backup with Zstandard compression |

---

## 📄 License

MIT License — see [LICENSE](LICENSE)

---

<div align="center">
<p>Built with precision by <strong>Jigar Corp</strong> · <a href="https://official-arvind.github.io/jigar-tools/">official-arvind.github.io/jigar-tools</a></p>
<p><em>No Ads · No Subscriptions · Zero Tracking · Local First · Open Source Forever</em></p>
</div>

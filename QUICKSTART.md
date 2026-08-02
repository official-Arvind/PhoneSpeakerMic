# ⚡ PhoneSpeakerMic — Quick Start

> **Built by Jigar Corp** · Free · Open Source · Zero Tracking

---

## 🖥️ 1. PC Setup (2 minutes)

### Install Python dependencies

```bash
pip install pyaudiowpatch numpy pycaw comtypes
```

### (Optional) Install VB-Audio Virtual Cable

Download from → [https://vb-audio.com/Cable/](https://vb-audio.com/Cable/)  
Lets apps like Discord/Zoom/OBS see your phone mic as a real input device.

### Run the server

```bash
python pc_server.py
```

Note the **IP address** shown (e.g. `192.168.1.105`).

---

## 📱 2. Phone Setup (1 minute)

### Option A — Build yourself (recommended)
1. Install [Android Studio](https://developer.android.com/studio)
2. Open this project folder
3. Connect phone via USB → click Run ▶️

### Option B — APK
Download from [GitHub Releases](https://github.com/official-Arvind/PhoneSpeakerMic/releases)

Grant **microphone permission** when prompted.

---

## 🔗 3. Connect

1. Make sure phone & PC are on the **same WiFi network**
2. Pick your streaming mode:
   - 🎤 **MIC** — phone mic → PC
   - 🔊 **SPEAKER** — PC audio → phone
   - 🔄 **BOTH** — full bidirectional
3. Enter the PC IP address → tap **WIFI LINK**

Your **laptop speakers auto-mute** and the phone takes over!

---

## 🔌 USB Mode (Lowest Latency — ~3ms)

1. Connect phone via **USB cable**
2. Enable USB Debugging on phone (Settings → Developer Options)
3. Make sure ADB is installed on PC (comes with Android Studio)
4. Run `pc_server.py` — it runs `adb reverse` automatically
5. In the app — tap **USB MODE** (no IP needed)

---

## ❓ Troubleshooting

| Problem | Fix |
|---------|-----|
| Can't connect | Check both devices are on same WiFi. Check Windows Firewall allows Python on port 5000. |
| No audio | Check mic permission is granted on phone. Check PC volume isn't muted. |
| High latency | Move closer to router. Close other WiFi-heavy apps. Use USB mode instead. |
| pycaw error | Run `pip install pycaw comtypes` and try again. |
| Speaker doesn't mute | pycaw not installed, or running as non-admin. Try `pip install pycaw`. |
| CABLE Input not found | Install [VB-Audio Virtual Cable](https://vb-audio.com/Cable/) and restart. |

---

## 💛 Support

**UPI:** `arvindji@fam`  
**GitHub:** [@official-Arvind](https://github.com/official-Arvind)  
**Tools:** [official-arvind.github.io/jigar-tools](https://official-arvind.github.io/jigar-tools/)

*No Ads · No Subscriptions · Zero Tracking · Open Source Forever*

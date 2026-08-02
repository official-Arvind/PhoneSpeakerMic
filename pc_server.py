import socket
import threading
import sys
import os
import subprocess
import time
import ctypes
import pyaudiowpatch as pyaudio
import numpy as np

# ─── Audio Config ─────────────────────────────────────────────────────────────
CHUNK       = 256        # ~5.8ms per buffer at 44100 Hz  (was 2048 = 46ms)
FORMAT      = pyaudio.paInt16
CHANNELS    = 1
RATE        = 48000      # 48kHz — native rate on most modern hardware (no resampling)
PORT        = 5000
# ──────────────────────────────────────────────────────────────────────────────

def elevate_process_priority():
    """Raise the process to HIGH priority on Windows for minimum scheduling jitter."""
    try:
        if sys.platform == "win32":
            HIGH_PRIORITY_CLASS = 0x0080
            handle = ctypes.windll.kernel32.GetCurrentProcess()
            ctypes.windll.kernel32.SetPriorityClass(handle, HIGH_PRIORITY_CLASS)
            print("[OK] Process priority elevated to HIGH.")
    except Exception as e:
        print(f"[!] Could not elevate priority: {e}")

def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except:
        return "Unable to determine"

def setup_adb_reverse():
    print("\nAttempting to set up USB Cable mode (adb reverse)...")
    adb_paths = [
        "adb",
        os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"),
        os.path.expandvars(r"%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe")
    ]
    adb_executable = None
    for path in adb_paths:
        try:
            subprocess.run([path, "version"], capture_output=True, check=True)
            adb_executable = path
            break
        except (subprocess.CalledProcessError, FileNotFoundError, OSError):
            continue

    if not adb_executable:
        print("[!] ADB not found. USB Mode unavailable.")
        return
    try:
        result = subprocess.run([adb_executable, "reverse", f"tcp:{PORT}", f"tcp:{PORT}"],
                                capture_output=True, text=True)
        if result.returncode == 0:
            print("[OK] USB Mode ready. Press 'USB MODE' in the app!")
        else:
            print(f"[!] ADB note: device not connected ({result.stderr.strip()})")
    except Exception as e:
        print(f"[!] Error with ADB: {e}")

# ─── Windows Speaker Auto-Mute via pycaw ──────────────────────────────────────
_saved_volume = None
_pycaw_available = False

try:
    from ctypes import cast, POINTER
    from comtypes import CLSCTX_ALL
    from pycaw.pycaw import AudioUtilities, IAudioEndpointVolume
    _pycaw_available = True
except ImportError:
    pass

def _get_speaker_volume_control():
    try:
        devices  = AudioUtilities.GetSpeakers()
        interface = devices.Activate(IAudioEndpointVolume._iid_, CLSCTX_ALL, None)
        return cast(interface, POINTER(IAudioEndpointVolume))
    except Exception:
        return None

def mute_laptop_speakers():
    global _saved_volume
    if not _pycaw_available:
        print("[!] pycaw not installed — speaker auto-mute unavailable.")
        print("    Run: pip install pycaw comtypes")
        return
    try:
        vol = _get_speaker_volume_control()
        if vol:
            _saved_volume = vol.GetMasterVolumeLevelScalar()
            vol.SetMasterVolumeLevelScalar(0.0, None)
            print(f"[OK] Laptop speakers muted (was {_saved_volume*100:.0f}%).")
    except Exception as e:
        print(f"[!] Could not mute speakers: {e}")

def restore_laptop_speakers():
    global _saved_volume
    if not _pycaw_available or _saved_volume is None:
        return
    try:
        vol = _get_speaker_volume_control()
        if vol:
            vol.SetMasterVolumeLevelScalar(_saved_volume, None)
            print(f"[OK] Laptop speakers restored to {_saved_volume*100:.0f}%.")
            _saved_volume = None
    except Exception as e:
        print(f"[!] Could not restore speakers: {e}")
# ──────────────────────────────────────────────────────────────────────────────

class AudioServer:
    """
    Protocol header (1 byte sent by client right after TCP connect):
      0x01 = MIC only   (phone mic → PC CABLE Input)
      0x02 = SPEAKER only (PC loopback → phone speaker)
      0x03 = BOTH (bidirectional)
    """
    MODE_MIC     = 0x01
    MODE_SPEAKER = 0x02
    MODE_BOTH    = 0x03

    def __init__(self):
        self.p = pyaudio.PyAudio()
        self.server_socket = None
        self.client_socket = None
        self.is_running = False
        self.mode = self.MODE_BOTH

        self.default_loopback = None
        self.loopback_rate    = RATE
        self.loopback_channels = 1
        self.cable_output     = None

        self._find_devices()

    def _find_devices(self):
        # ── Find default loopback (system audio) ──
        try:
            wasapi_info     = self.p.get_host_api_info_by_type(pyaudio.paWASAPI)
            default_speakers = self.p.get_device_info_by_index(wasapi_info["defaultOutputDevice"])

            if not default_speakers.get("isLoopbackDevice", False):
                for lb in self.p.get_loopback_device_info_generator():
                    if default_speakers["name"] in lb["name"]:
                        self.default_loopback = lb
                        break
            else:
                self.default_loopback = default_speakers

            if self.default_loopback:
                self.loopback_rate     = int(self.default_loopback.get("defaultSampleRate", RATE))
                self.loopback_channels = int(self.default_loopback.get("maxInputChannels", 1))
        except Exception as e:
            print(f"Warning discovering loopback: {e}")

        # ── Find VB-Audio CABLE Input ──
        for i in range(self.p.get_device_count()):
            dev = self.p.get_device_info_by_index(i)
            if dev["maxOutputChannels"] > 0 and "CABLE Input" in dev["name"]:
                self.cable_output = dev
                break

    # ──────────────────────────────────────────────────────────────────────────
    def start_server(self):
        self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.server_socket.bind(('0.0.0.0', PORT))
        self.server_socket.listen(1)

        print("=" * 62)
        print(" ⚡  PhoneSpeakerMic  —  Ultra-Low Latency Engine")
        print("=" * 62)
        print(f"  Port        : {PORT}")
        print(f"  WiFi IP     : {get_local_ip()}")
        print(f"  Chunk size  : {CHUNK} samples  ({CHUNK/RATE*1000:.1f} ms per buffer)")
        print(f"  Sample rate : {RATE} Hz")
        print(f"  pycaw       : {'Available ✓' if _pycaw_available else 'Not installed (speaker mute disabled)'}")

        setup_adb_reverse()

        print("-" * 62)
        if self.cable_output:
            print(f"  [OK] VB-Audio CABLE Input  : {self.cable_output['name']}")
        else:
            print("  [!] CABLE Input not found — phone mic will go to default speakers.")

        if self.default_loopback:
            print(f"  [OK] Loopback device       : {self.default_loopback['name']}")
            print(f"       Native rate           : {self.loopback_rate} Hz  |  ch: {self.loopback_channels}")
        else:
            print("  [!] No loopback device found — SPEAKER mode unavailable.")

        print("\n  Waiting for phone to connect…")
        print("=" * 62)

        while True:
            try:
                self.client_socket, addr = self.server_socket.accept()
            except OSError:
                break

            # ── Socket tuning ──
            self.client_socket.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            # Shrink socket buffers so data is not pre-queued (lower kernel latency)
            self.client_socket.setsockopt(socket.SOL_SOCKET, socket.SO_SNDBUF, CHUNK * 4)
            self.client_socket.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, CHUNK * 4)

            print(f"\n  ✓ Phone connected from {addr[0]}")

            # ── Read 1-byte mode flag from phone ──
            try:
                mode_byte = self.client_socket.recv(1)
                self.mode = mode_byte[0] if mode_byte else self.MODE_BOTH
            except Exception:
                self.mode = self.MODE_BOTH

            mode_names = {
                self.MODE_MIC:     "MIC only (phone mic → PC)",
                self.MODE_SPEAKER: "SPEAKER only (PC audio → phone)",
                self.MODE_BOTH:    "BOTH (bidirectional)",
            }
            print(f"  Mode        : {mode_names.get(self.mode, 'BOTH')}")
            print("  Press Ctrl+C to stop.\n")

            # ── Mute laptop speakers when streaming starts ──
            mute_laptop_speakers()

            self.is_running = True
            threads = []

            if self.mode in (self.MODE_MIC, self.MODE_BOTH):
                t = threading.Thread(target=self.receive_audio, daemon=True)
                threads.append(t)

            if self.mode in (self.MODE_SPEAKER, self.MODE_BOTH):
                t = threading.Thread(target=self.send_audio, daemon=True)
                threads.append(t)

            for t in threads:
                t.start()
            for t in threads:
                t.join()

            restore_laptop_speakers()
            print("\n  Client disconnected. Waiting for next connection…\n")

    # ──────────────────────────────────────────────────────────────────────────
    def receive_audio(self):
        """Phone mic → PC (CABLE Input / speakers). Callback mode — zero blocking."""
        output_idx = self.cable_output["index"] if self.cable_output else None
        buf = bytearray()
        lock = threading.Lock()
        chunk_bytes = CHUNK * 2  # int16 = 2 bytes per sample

        def playback_callback(in_data, frame_count, time_info, status):
            nonlocal buf
            with lock:
                needed = frame_count * 2
                if len(buf) >= needed:
                    data = bytes(buf[:needed])
                    del buf[:needed]
                else:
                    # underrun: pad with silence
                    data = bytes(buf) + b'\x00' * (needed - len(buf))
                    buf.clear()
            return (data, pyaudio.paContinue)

        try:
            stream = self.p.open(
                format=FORMAT,
                channels=CHANNELS,
                rate=RATE,
                output=True,
                output_device_index=output_idx,
                frames_per_buffer=CHUNK,
                stream_callback=playback_callback,
            )
            stream.start_stream()

            # Network receive loop — fills the ring buffer
            while self.is_running and stream.is_active():
                try:
                    data = self.client_socket.recv(chunk_bytes)
                    if not data:
                        break
                    with lock:
                        buf.extend(data)
                        # Prevent buffer bloat (drop old data if > 20ms ahead)
                        max_bytes = RATE * 2 * 20 // 1000  # 20 ms
                        if len(buf) > max_bytes:
                            del buf[:len(buf) - max_bytes]
                except Exception:
                    break

            stream.stop_stream()
            stream.close()
        except Exception as e:
            if self.is_running:
                print(f"  [receive] Error: {e}")
        finally:
            self.stop()

    # ──────────────────────────────────────────────────────────────────────────
    def send_audio(self):
        """PC system audio loopback → phone speaker. Callback-driven capture."""
        if not self.default_loopback:
            print("  [send] No loopback device — SPEAKER mode skipped.")
            return

        input_idx = self.default_loopback["index"]
        chunk_bytes = CHUNK * 2
        buf = bytearray()
        lock = threading.Lock()
        sock = self.client_socket

        def capture_callback(in_data, frame_count, time_info, status):
            nonlocal buf
            data = in_data
            # Convert stereo → mono (left channel only)
            if self.loopback_channels == 2:
                arr  = np.frombuffer(data, dtype=np.int16)
                data = arr[0::2].tobytes()
            # Resample if loopback native rate ≠ RATE
            if self.loopback_rate != RATE:
                arr    = np.frombuffer(data, dtype=np.int16).astype(np.float32)
                ratio  = RATE / self.loopback_rate
                new_len = int(len(arr) * ratio)
                arr    = np.interp(
                    np.linspace(0, len(arr), new_len, endpoint=False),
                    np.arange(len(arr)), arr
                ).astype(np.int16)
                data = arr.tobytes()
            with lock:
                buf.extend(data)
            return (None, pyaudio.paContinue)

        # Sender thread: drains the buffer → socket
        def sender():
            while self.is_running:
                with lock:
                    if len(buf) >= chunk_bytes:
                        data = bytes(buf[:chunk_bytes])
                        del buf[:chunk_bytes]
                    else:
                        data = None
                if data:
                    try:
                        sock.sendall(data)
                    except Exception:
                        break
                else:
                    time.sleep(0.0001)   # 0.1 ms spin-wait

        try:
            stream = self.p.open(
                format=FORMAT,
                channels=self.loopback_channels,
                rate=self.loopback_rate,
                input=True,
                input_device_index=input_idx,
                frames_per_buffer=CHUNK,
                stream_callback=capture_callback,
            )
            stream.start_stream()

            t = threading.Thread(target=sender, daemon=True)
            t.start()

            while self.is_running and stream.is_active():
                time.sleep(0.005)

            stream.stop_stream()
            stream.close()
            t.join(timeout=1)
        except Exception as e:
            if self.is_running:
                print(f"  [send] Error: {e}")
        finally:
            self.stop()

    # ──────────────────────────────────────────────────────────────────────────
    def stop(self):
        if not self.is_running:
            return
        self.is_running = False
        print("\n  Shutting down audio stream…")
        for s in (self.client_socket, self.server_socket):
            try:
                if s: s.close()
            except: pass
        try:
            self.p.terminate()
        except: pass


if __name__ == "__main__":
    elevate_process_priority()
    server = AudioServer()
    try:
        server.start_server()
    except KeyboardInterrupt:
        server.stop()
    except Exception as e:
        print(f"Fatal error: {e}")
        server.stop()
        sys.exit(1)

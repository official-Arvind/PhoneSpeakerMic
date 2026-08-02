package com.jigar.phonespeakermic;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Context;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {

    // ── Audio constants ────────────────────────────────────────────────────────
    private static final int SAMPLE_RATE     = 48000;
    private static final int CHANNEL_IN      = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_OUT     = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT    = AudioFormat.ENCODING_PCM_16BIT;
    private static final int PERMISSION_CODE = 200;

    // Mode bytes — must match pc_server.py
    private static final byte MODE_MIC     = 0x01;
    private static final byte MODE_SPEAKER = 0x02;
    private static final byte MODE_BOTH    = 0x03;

    // ── UI ─────────────────────────────────────────────────────────────────────
    private EditText etServerIp;
    private Button   btnConnect, btnUsbConnect, btnDisconnect;
    private Button   btnModeMic, btnModeSpeaker, btnModeBoth;
    private TextView tvStatus, tvMode;

    // ── Runtime state ──────────────────────────────────────────────────────────
    private Socket      socket;
    private AudioRecord audioRecord;
    private AudioTrack  audioTrack;
    private AcousticEchoCanceler aec;
    private NoiseSuppressor      ns;
    private boolean isConnected = false;
    private boolean isRecording = false;
    private Thread  sendThread, receiveThread;
    private byte    selectedMode = MODE_BOTH;

    // ──────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etServerIp     = findViewById(R.id.etServerIp);
        btnConnect     = findViewById(R.id.btnConnect);
        btnUsbConnect  = findViewById(R.id.btnUsbConnect);
        btnDisconnect  = findViewById(R.id.btnDisconnect);
        tvStatus       = findViewById(R.id.tvStatus);
        tvMode         = findViewById(R.id.tvMode);
        btnModeMic     = findViewById(R.id.btnModeMic);
        btnModeSpeaker = findViewById(R.id.btnModeSpeaker);
        btnModeBoth    = findViewById(R.id.btnModeBoth);

        btnDisconnect.setEnabled(false);
        setMode(MODE_BOTH);

        requestMicPermission();

        btnConnect   .setOnClickListener(v -> connectToServer(etServerIp.getText().toString().trim()));
        btnUsbConnect.setOnClickListener(v -> connectToServer("127.0.0.1"));
        btnDisconnect.setOnClickListener(v -> disconnect());
        btnModeMic    .setOnClickListener(v -> setMode(MODE_MIC));
        btnModeSpeaker.setOnClickListener(v -> setMode(MODE_SPEAKER));
        btnModeBoth   .setOnClickListener(v -> setMode(MODE_BOTH));
    }

    // ──────────────────────────────────────────────────────────────────────────
    private void setMode(byte mode) {
        selectedMode = mode;
        btnModeMic    .setAlpha(0.4f);
        btnModeSpeaker.setAlpha(0.4f);
        btnModeBoth   .setAlpha(0.4f);
        String label;
        if (mode == MODE_MIC) {
            btnModeMic.setAlpha(1f);
            label = "🎤  Phone mic → PC";
        } else if (mode == MODE_SPEAKER) {
            btnModeSpeaker.setAlpha(1f);
            label = "🔊  PC audio → Phone";
        } else {
            btnModeBoth.setAlpha(1f);
            label = "🎤🔊  Bidirectional";
        }
        tvMode.setText(label);
    }

    // ──────────────────────────────────────────────────────────────────────────
    private void requestMicPermission() {
        ActivityCompat.requestPermissions(this,
            new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_CODE);
    }

    // ──────────────────────────────────────────────────────────────────────────
    private void connectToServer(String ip) {
        if (ip.isEmpty()) {
            Toast.makeText(this, "Enter PC IP address", Toast.LENGTH_SHORT).show();
            return;
        }
        runOnUiThread(() -> tvStatus.setText("Connecting…"));

        new Thread(() -> {
            try {
                socket = new Socket(ip, 5000);
                socket.setTcpNoDelay(true);
                // Shrink socket buffers to match chunk size — reduces kernel queuing
                socket.setSendBufferSize(SAMPLE_RATE / 50);
                socket.setReceiveBufferSize(SAMPLE_RATE / 50);

                // Send 1-byte mode flag so server activates only needed streams
                socket.getOutputStream().write(new byte[]{selectedMode});
                socket.getOutputStream().flush();

                isConnected = true;

                // AudioManager: VoIP mode + speakerphone for lowest OS latency path
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                am.setMode(AudioManager.MODE_IN_COMMUNICATION);
                am.setSpeakerphoneOn(true);

                runOnUiThread(() -> {
                    tvStatus.setText("● Connected");
                    btnConnect    .setEnabled(false);
                    btnUsbConnect .setEnabled(false);
                    btnDisconnect .setEnabled(true);
                    btnModeMic    .setEnabled(false);
                    btnModeSpeaker.setEnabled(false);
                    btnModeBoth   .setEnabled(false);
                });

                startAudioStreaming();

            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvStatus.setText("Failed: " + e.getMessage());
                    Toast.makeText(this, "Connection failed", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    // ──────────────────────────────────────────────────────────────────────────
    private void startAudioStreaming() {

        // ── MIC thread ────────────────────────────────────────────────────────
        if (selectedMode == MODE_MIC || selectedMode == MODE_BOTH) {
            sendThread = new Thread(() -> {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
                try {
                    int minBuf  = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT);
                    int bufSize = Math.max(minBuf, 1024) * 2;

                    // Use traditional constructor — universally compatible, no Builder API issues.
                    // VOICE_COMMUNICATION source automatically enables hardware AEC on most devices.
                    audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT, bufSize
                    );

                    // Attach hardware AEC (if supported by device)
                    if (AcousticEchoCanceler.isAvailable()) {
                        aec = AcousticEchoCanceler.create(audioRecord.getAudioSessionId());
                        if (aec != null) aec.setEnabled(true);
                    }
                    // Attach hardware Noise Suppressor
                    if (NoiseSuppressor.isAvailable()) {
                        ns = NoiseSuppressor.create(audioRecord.getAudioSessionId());
                        if (ns != null) ns.setEnabled(true);
                    }

                    audioRecord.startRecording();
                    isRecording = true;

                    byte[]       buf    = new byte[bufSize];
                    OutputStream output = socket.getOutputStream();

                    while (isRecording && isConnected) {
                        int read = audioRecord.read(buf, 0, buf.length);
                        if (read > 0) {
                            output.write(buf, 0, read);
                            output.flush();
                        }
                    }
                } catch (Exception e) {
                    if (isConnected)
                        runOnUiThread(() -> tvStatus.setText("Mic error: " + e.getMessage()));
                }
            });
            sendThread.setName("MicSend");
            sendThread.start();
        }

        // ── SPEAKER thread ────────────────────────────────────────────────────
        if (selectedMode == MODE_SPEAKER || selectedMode == MODE_BOTH) {
            receiveThread = new Thread(() -> {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
                try {
                    int minBuf  = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT);
                    int bufSize = Math.max(minBuf, 1024) * 2;

                    // Use AudioAttributes constructor (API 21+, well within our minSdk 23).
                    // USAGE_VOICE_COMMUNICATION tells the OS to use the low-latency audio path.
                    AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();

                    AudioFormat fmt = new AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_OUT)
                        .build();

                    // AudioTrack(AudioAttributes, AudioFormat, int bufSize, int mode, int sessionId)
                    // This constructor was added in API 21 — safe with our minSdk 23.
                    // No setPerformanceMode() needed: USAGE_VOICE_COMMUNICATION already routes
                    // through the low-latency path on Android 5+.
                    audioTrack = new AudioTrack(
                        attrs, fmt, bufSize,
                        AudioTrack.MODE_STREAM,
                        AudioManager.AUDIO_SESSION_ID_GENERATE
                    );
                    audioTrack.play();

                    byte[]      buf   = new byte[bufSize];
                    InputStream input = socket.getInputStream();

                    while (isConnected) {
                        int read = input.read(buf, 0, buf.length);
                        if (read > 0) {
                            audioTrack.write(buf, 0, read);
                        } else if (read == -1) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    if (isConnected)
                        runOnUiThread(() -> tvStatus.setText("Speaker error: " + e.getMessage()));
                }
            });
            receiveThread.setName("SpeakerRecv");
            receiveThread.start();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    private void disconnect() {
        isConnected = false;
        isRecording = false;

        try { if (aec         != null) { aec.release();          aec         = null; } } catch (Exception ignored) {}
        try { if (ns          != null) { ns.release();           ns          = null; } } catch (Exception ignored) {}
        try { if (audioRecord != null) { audioRecord.stop();     audioRecord.release(); } } catch (Exception ignored) {}
        try { if (audioTrack  != null) { audioTrack.stop();      audioTrack.release();  } } catch (Exception ignored) {}
        try { if (socket      != null) { socket.close();                                } } catch (Exception ignored) {}

        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        am.setMode(AudioManager.MODE_NORMAL);
        am.setSpeakerphoneOn(false);

        runOnUiThread(() -> {
            tvStatus.setText("Disconnected");
            btnConnect    .setEnabled(true);
            btnUsbConnect .setEnabled(true);
            btnDisconnect .setEnabled(false);
            btnModeMic    .setEnabled(true);
            btnModeSpeaker.setEnabled(true);
            btnModeBoth   .setEnabled(true);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnect();
    }
}

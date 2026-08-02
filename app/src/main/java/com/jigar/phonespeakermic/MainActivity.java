package com.jigar.phonespeakermic;

import android.Manifest;
import android.annotation.SuppressLint;
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

    // ── Audio config ──────────────────────────────────────────────────────────
    private static final int SAMPLE_RATE        = 48000;   // 48 kHz — matches PC server
    private static final int CHANNEL_IN         = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_OUT        = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT       = AudioFormat.ENCODING_PCM_16BIT;
    private static final int PERMISSION_CODE    = 200;

    // Mode bytes (must match pc_server.py)
    private static final byte MODE_MIC          = 0x01;
    private static final byte MODE_SPEAKER      = 0x02;
    private static final byte MODE_BOTH         = 0x03;

    // ── UI ────────────────────────────────────────────────────────────────────
    private EditText etServerIp;
    private Button btnConnect, btnUsbConnect, btnDisconnect;
    private Button btnModeMic, btnModeSpeaker, btnModeBoth;
    private TextView tvStatus, tvMode;

    // ── State ─────────────────────────────────────────────────────────────────
    private Socket socket;
    private AudioRecord audioRecord;
    private AudioTrack  audioTrack;
    private AcousticEchoCanceler aec;
    private NoiseSuppressor      ns;
    private boolean isConnected = false;
    private boolean isRecording = false;
    private Thread sendThread, receiveThread;
    private byte selectedMode = MODE_BOTH;

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind views
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
        setMode(MODE_BOTH);   // default selection

        requestPermissions();

        btnConnect   .setOnClickListener(v -> connectToServer(etServerIp.getText().toString().trim()));
        btnUsbConnect.setOnClickListener(v -> connectToServer("127.0.0.1"));
        btnDisconnect.setOnClickListener(v -> disconnect());

        btnModeMic    .setOnClickListener(v -> setMode(MODE_MIC));
        btnModeSpeaker.setOnClickListener(v -> setMode(MODE_SPEAKER));
        btnModeBoth   .setOnClickListener(v -> setMode(MODE_BOTH));
    }

    // ─────────────────────────────────────────────────────────────────────────
    private void setMode(byte mode) {
        selectedMode = mode;
        // Reset all button appearances
        btnModeMic    .setAlpha(0.45f);
        btnModeSpeaker.setAlpha(0.45f);
        btnModeBoth   .setAlpha(0.45f);

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

    // ─────────────────────────────────────────────────────────────────────────
    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
            new String[]{Manifest.permission.RECORD_AUDIO},
            PERMISSION_CODE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    private void connectToServer(String serverIp) {
        if (serverIp.isEmpty()) {
            Toast.makeText(this, "Enter PC IP address", Toast.LENGTH_SHORT).show();
            return;
        }

        runOnUiThread(() -> tvStatus.setText("Connecting…"));

        new Thread(() -> {
            try {
                socket = new Socket(serverIp, 5000);
                socket.setTcpNoDelay(true);
                // Shrink socket buffers to reduce kernel-level queuing
                socket.setSendBufferSize(SAMPLE_RATE / 50);   // ~20 ms worth
                socket.setReceiveBufferSize(SAMPLE_RATE / 50);

                // ── Send 1-byte mode flag immediately ──
                socket.getOutputStream().write(new byte[]{selectedMode});
                socket.getOutputStream().flush();

                isConnected = true;

                runOnUiThread(() -> {
                    tvStatus.setText("● Connected");
                    btnConnect   .setEnabled(false);
                    btnUsbConnect.setEnabled(false);
                    btnDisconnect.setEnabled(true);
                    btnModeMic   .setEnabled(false);
                    btnModeSpeaker.setEnabled(false);
                    btnModeBoth  .setEnabled(false);
                });

                // Set audio mode for lowest latency + hardware AEC
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                am.setMode(AudioManager.MODE_IN_COMMUNICATION);
                am.setSpeakerphoneOn(true);

                startAudioStreaming();

            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvStatus.setText("Connection failed: " + e.getMessage());
                    Toast.makeText(this, "Failed to connect", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    private void startAudioStreaming() {
        // ── MIC thread: phone mic → PC ────────────────────────────────────
        if (selectedMode == MODE_MIC || selectedMode == MODE_BOTH) {
            sendThread = new Thread(() -> {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
                try {
                    int minBuf    = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT);
                    int bufSize   = Math.max(minBuf, 512) * 2;  // smallest safe buffer

                    AudioRecord.Builder recBuilder = new AudioRecord.Builder()
                        .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                        .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AUDIO_FORMAT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_IN)
                            .build())
                        .setBufferSizeInBytes(bufSize);

                    // Request low-latency performance mode (Android 10+ / API 29+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        recBuilder.setPerformanceMode(AudioRecord.PERFORMANCE_MODE_LOW_LATENCY);
                    }

                    audioRecord = recBuilder.build();

                    // Hardware Acoustic Echo Cancellation
                    if (AcousticEchoCanceler.isAvailable()) {
                        aec = AcousticEchoCanceler.create(audioRecord.getAudioSessionId());
                        if (aec != null) aec.setEnabled(true);
                    }
                    // Hardware Noise Suppression
                    if (NoiseSuppressor.isAvailable()) {
                        ns = NoiseSuppressor.create(audioRecord.getAudioSessionId());
                        if (ns != null) ns.setEnabled(true);
                    }

                    audioRecord.startRecording();
                    isRecording = true;

                    byte[] buffer       = new byte[bufSize];
                    OutputStream output = socket.getOutputStream();

                    while (isRecording && isConnected) {
                        int read = audioRecord.read(buffer, 0, buffer.length);
                        if (read > 0) {
                            output.write(buffer, 0, read);
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

        // ── SPEAKER thread: PC audio → phone ──────────────────────────────
        if (selectedMode == MODE_SPEAKER || selectedMode == MODE_BOTH) {
            receiveThread = new Thread(() -> {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
                try {
                    int minBuf  = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT);
                    int bufSize = Math.max(minBuf, 512) * 2;

                    AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();

                    AudioFormat fmt = new AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_OUT)
                        .build();

                    AudioTrack.Builder trackBuilder = new AudioTrack.Builder()
                        .setAudioAttributes(attrs)
                        .setAudioFormat(fmt)
                        .setBufferSizeInBytes(bufSize)
                        .setTransferMode(AudioTrack.MODE_STREAM);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY);
                    }

                    audioTrack = trackBuilder.build();
                    audioTrack.play();

                    byte[] buffer      = new byte[bufSize];
                    InputStream input  = socket.getInputStream();

                    while (isConnected) {
                        int read = input.read(buffer, 0, buffer.length);
                        if (read > 0) {
                            audioTrack.write(buffer, 0, read);
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

    // ─────────────────────────────────────────────────────────────────────────
    private void disconnect() {
        isConnected = false;
        isRecording = false;

        try { if (aec != null) { aec.release(); aec = null; } } catch (Exception ignored) {}
        try { if (ns  != null) { ns.release();  ns  = null; } } catch (Exception ignored) {}
        try { if (audioRecord != null) { audioRecord.stop(); audioRecord.release(); } } catch (Exception ignored) {}
        try { if (audioTrack  != null) { audioTrack.stop();  audioTrack.release();  } } catch (Exception ignored) {}
        try { if (socket      != null) { socket.close();                             } } catch (Exception ignored) {}

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

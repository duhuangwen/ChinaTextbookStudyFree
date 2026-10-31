package org.chinatextbook.study;

import android.annotation.SuppressLint;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Records 16 kHz mono PCM with the VOICE_RECOGNITION source. WebView's getUserMedia uses MIC or
 * VOICE_COMMUNICATION, which TV firmwares do not route to Bluetooth remote microphones; voice-search
 * capture does reach them. Not thread-safe beyond the synchronized start/stop pair.
 */
final class NativeRecorder {
    static final int SAMPLE_RATE = 16000;
    private static final int MAX_SECONDS = 15;
    private static final int TYPE_BLE_HEADSET = 26;

    private AudioRecord record;
    private Thread reader;
    private ByteArrayOutputStream pcm;
    private volatile boolean running;

    /** Returns null on success, otherwise a short reason for the page. */
    @SuppressLint("MissingPermission")
    synchronized String start(AudioManager audio) {
        if (running) return null;
        int min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) return "unsupported";
        AudioRecord candidate;
        try {
            candidate = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, SAMPLE_RATE));
        } catch (RuntimeException e) {
            return "init:" + e.getMessage();
        }
        if (candidate.getState() != AudioRecord.STATE_INITIALIZED) {
            candidate.release();
            return "no-input";
        }
        AudioDeviceInfo preferred = preferredInput(audio);
        if (preferred != null) candidate.setPreferredDevice(preferred);
        try {
            candidate.startRecording();
        } catch (RuntimeException e) {
            candidate.release();
            return "start:" + e.getMessage();
        }
        if (candidate.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            candidate.release();
            return "busy";
        }
        record = candidate;
        pcm = new ByteArrayOutputStream();
        running = true;
        final AudioRecord source = candidate;
        final ByteArrayOutputStream sink = pcm;
        reader = new Thread(() -> {
            byte[] buffer = new byte[Math.max(min, 3200)];
            int limit = SAMPLE_RATE * 2 * MAX_SECONDS;
            while (running && sink.size() < limit) {
                int n = source.read(buffer, 0, buffer.length);
                if (n < 0) break;
                if (n > 0) sink.write(buffer, 0, n);
            }
        }, "cstf-recorder");
        reader.start();
        return null;
    }

    /** Stops recording and returns a WAV data URL, or an empty string when nothing was captured. */
    synchronized String stop() {
        if (record == null) return "";
        running = false;
        try {
            reader.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            record.stop();
        } catch (IllegalStateException ignored) {
            // Already stopped by the system, e.g. the remote disconnected.
        }
        record.release();
        record = null;
        byte[] samples = pcm.toByteArray();
        pcm = null;
        if (samples.length == 0) return "";
        return "data:audio/wav;base64," + Base64.encodeToString(wav(samples, SAMPLE_RATE), Base64.NO_WRAP);
    }

    static AudioDeviceInfo preferredInput(AudioManager audio) {
        AudioDeviceInfo best = null;
        int bestRank = 0;
        for (AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
            int rank = inputRank(device.getType());
            if (rank > bestRank) {
                best = device;
                bestRank = rank;
            }
        }
        return best;
    }

    /** Higher wins; 0 means leave routing to the platform (built-in or vendor remote paths). */
    static int inputRank(int type) {
        switch (type) {
            case TYPE_BLE_HEADSET: return 4;
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: return 3;
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_HEADSET:
            case AudioDeviceInfo.TYPE_WIRED_HEADSET: return 2;
            default: return 0;
        }
    }

    static List<String> describeInputs(AudioManager audio) {
        List<String> names = new ArrayList<>();
        for (AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
            names.add(device.getType() + ":" + device.getProductName());
        }
        return names;
    }

    static byte[] wav(byte[] samples, int sampleRate) {
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put(new byte[]{'R', 'I', 'F', 'F'}).putInt(36 + samples.length)
                .put(new byte[]{'W', 'A', 'V', 'E', 'f', 'm', 't', ' '}).putInt(16)
                .putShort((short) 1).putShort((short) 1).putInt(sampleRate).putInt(sampleRate * 2)
                .putShort((short) 2).putShort((short) 16)
                .put(new byte[]{'d', 'a', 't', 'a'}).putInt(samples.length);
        byte[] out = new byte[44 + samples.length];
        System.arraycopy(header.array(), 0, out, 0, 44);
        System.arraycopy(samples, 0, out, 44, samples.length);
        return out;
    }
}

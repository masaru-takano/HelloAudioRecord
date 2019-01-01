package com.example.helloaudiorecord;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "HelloAudioRecord";

    private static final int AUDIO_SAMPLE_RATE = 44100;

    private static final int AUDIO_SOURCE = MediaRecorder.AudioSource.MIC;

    private static final int AUDIO_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;

    private static final int AUDIO_CHANNEL_COUNT = 1;

    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private static final int AUDIO_BIT_RATE = 128 * 1000; // 128Kbps

    private static final int AUDIO_BUFFER_SIZE = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_CONFIG, AUDIO_FORMAT);

    private static final String AUDIO_MIME_TYPE = "audio/mp4a-latm";

    private AudioRecord mAudioRecord;

    private MediaCodec mAudioEncoder;

    private MediaMuxer mMediaMuxer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    private static final int REQUEST_PERMISSION = 1;

    @Override
    protected void onResume() {
        super.onResume();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_PERMISSION);
            return;
        } else {
            setupAudioSource();
            try {
                setupAudioEncoder();
            } catch (IOException e) {
                error("Failed to setup audio encoder.", e);
            }

            startRecording();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(), "Not allowed.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), "Allowed.", Toast.LENGTH_SHORT).show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopRecording();
    }
    
    private MediaCodec.Callback mAudioEncoderCallback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
            // NOP.
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
            debug("onOutputBufferAvailable: ");
        }

        @Override
        public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
            error("onError: ", e);
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
            debug("onOutputFormatChanged: ");
        }
    };

    private void setupAudioSource() {
        if (mAudioRecord == null) {
            mAudioRecord = new AudioRecord(AUDIO_SOURCE, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_CONFIG, AUDIO_FORMAT, AUDIO_BUFFER_SIZE);
        }
    }

    private void setupAudioEncoder() throws IOException {
        if (mAudioEncoder != null) {
            return;
        }
        MediaFormat format = MediaFormat.createAudioFormat(AUDIO_MIME_TYPE, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_COUNT);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);
        format.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        mAudioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
        debug("Created Audio Encoder: name=" + mAudioEncoder.getName());
        //mAudioEncoder.setCallback(mAudioEncoderCallback);
        mAudioEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    }

    private Thread mAudioRecordThread;

    private synchronized void startRecording() {
        if (mAudioRecordThread == null) {
            mAudioRecordThread = new Thread(new AudioRecordTask());
            mAudioRecordThread.start();
        }
    }

    private synchronized void stopRecording() {
        if (mAudioRecordThread != null) {
            mAudioRecordThread.interrupt();
            mAudioRecordThread = null;
        }
    }

    private static void debug(String message) {
        Log.d(TAG, message);
    }

    private static void error(String message, Throwable error) {
        Log.e(TAG, message, error);
    }

    private class AudioRecordTask implements Runnable {

        @Override
        public void run() {
            debug("Start recording.");
            mAudioEncoder.start();
            mAudioRecord.startRecording();

            final int bufferSize = 1024;
            int len;
            byte[] data = new byte[bufferSize];
            final long timeoutUs = 1000 * 1000;

            try {
                while (true) {
                    boolean interrupted = Thread.interrupted();

                    if ((len = mAudioRecord.read(data, 0, data.length)) > 0) {
                        debug("Read data: len=" + len);

                        int index = mAudioEncoder.dequeueInputBuffer(timeoutUs);
                        if (index != -1) {
                            ByteBuffer inputBuffer = mAudioEncoder.getInputBuffer(index);
                            inputBuffer.put(data, 0, len);

                            int flags = interrupted ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0;
                            mAudioEncoder.queueInputBuffer(index, 0, len, 0, flags);
                        }
                    }
                    if (interrupted) {
                        break;
                    }
                }
            } catch (IllegalStateException e) {
                error("Failed to input data to encoder.", e);
            } finally {
                mAudioRecord.stop();
                mAudioEncoder.stop();
                debug("Stopped recording.");
            }
        }
    }


}

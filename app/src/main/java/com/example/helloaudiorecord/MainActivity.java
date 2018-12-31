package com.example.helloaudiorecord;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

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

        try {
            setupAudioSource();
            setupAudioEncoder();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }
    
    private MediaCodec.Callback mAudioEncoderCallback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
            debug("onInputBufferAvailable: ");
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
        mAudioRecord = new AudioRecord(AUDIO_SOURCE, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_CONFIG, AUDIO_FORMAT, AUDIO_BUFFER_SIZE);
    }

    private void setupAudioEncoder() throws IOException {
        MediaFormat format = MediaFormat.createAudioFormat(AUDIO_MIME_TYPE, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_COUNT);
        mAudioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
        mAudioEncoder.setCallback(mAudioEncoderCallback);
        mAudioEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    }

    private void startRecording() {
        mAudioRecord.startRecording();
    }

    private static void debug(String message) {
        Log.d(TAG, message);
    }

    private static void error(String message, Throwable error) {
        Log.e(TAG, message, error);
    }

    private static class AudioRecordTask implements Runnable {

        private AudioRecord mAudioRecord;

        AudioRecordTask(final AudioRecord audioRecord) {
            mAudioRecord = audioRecord;
        }

        @Override
        public void run() {
            mAudioRecord.startRecording();

            final int bufferSize = 1024;
            int len;
            byte[] buffer = new byte[bufferSize];
            while (!Thread.interrupted()) {
               if ((len = mAudioRecord.read(buffer, 0, buffer.length)) > 0) {

                }
            }

            mAudioRecord.stop();
        }
    }
}

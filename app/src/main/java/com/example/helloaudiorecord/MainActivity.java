package com.example.helloaudiorecord;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
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

    private Thread mAudioEncoderThread;

    private Thread mAudioRecordThread;

    private File mAudioFile;

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
                != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION);
            return;
        } else {
            setupAudioSource();

            try {
                setupMediaMuxer();
            } catch (IOException e) {
                error("Failed to setup media muxer.", e);
            }

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

    private File createAudioFile(final String fileName) throws IOException {
        File file = new File(Environment.getExternalStorageDirectory(), fileName);
        if (!file.exists()) {
            if (!file.createNewFile()) {
                throw new IOException();
            }
        }
        return file;
    }

    private synchronized void setupMediaMuxer() throws IOException {
        if (mMediaMuxer == null) {
            File file = createAudioFile("test.mp4a");
            mMediaMuxer = new MediaMuxer(file.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mAudioFile = file;
        }
    }

    private synchronized void setupAudioSource() {
        if (mAudioRecord == null) {
            mAudioRecord = new AudioRecord(AUDIO_SOURCE, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_CONFIG, AUDIO_FORMAT, AUDIO_BUFFER_SIZE);
        }
    }

    private synchronized void setupAudioEncoder() throws IOException {
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

        mAudioEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    }

    private boolean mRecording;

    private synchronized void startRecording() {
        if (mRecording) {
            return;
        }
        mRecording = true;

        mAudioEncoder.start();
        if (mAudioEncoderThread == null) {
            mAudioEncoderThread = new Thread(new AudioEncoderTask());
            mAudioEncoderThread.start();
        }
        if (mAudioRecordThread == null) {
            mAudioRecordThread = new Thread(new AudioRecordTask());
            mAudioRecordThread.start();
        }
    }

    private synchronized void stopRecording() {
        if (!mRecording) {
            return;
        }
        mRecording = false;

        if (mAudioRecordThread != null) {
            mAudioRecordThread.interrupt();
            mAudioRecordThread = null;
        }
        if (mAudioEncoderThread != null) {
            mAudioEncoderThread.interrupt();
            mAudioEncoderThread = null;
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
            MediaFormat format = mAudioEncoder.getOutputFormat();
            int trackIx = mMediaMuxer.addTrack(format);
            mMediaMuxer.start();

            try {
                final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                final long timeoutUs = 1000 * 1000;

                while (mRecording) {
                    boolean interrupted = Thread.interrupted();
                    int index = mAudioEncoder.dequeueOutputBuffer(info, timeoutUs);
                    if (index > 0) {
                        ByteBuffer outputBuffer = mAudioEncoder.getOutputBuffer(index);
                        if (outputBuffer != null) {
                            mMediaMuxer.writeSampleData(trackIx, outputBuffer, info);
                            mAudioEncoder.releaseOutputBuffer(index, false);
                            debug("Write sample data: size=" + info.size);
                        }
                    }
                    if (interrupted) {
                        break;
                    }
                }

                mMediaMuxer.stop();

                registerMedia(mAudioFile);
            } catch (Throwable e) {
                error("Unexpectedly shutdown.", e);
            }
        }

        private void registerMedia(final File file) {
            if (checkFile(file)) {
                // Content Providerに登録する.
                MediaMetadataRetriever mediaMeta = new MediaMetadataRetriever();
                mediaMeta.setDataSource(file.toString());
                ContentResolver resolver = getContentResolver();
                ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.TITLE, file.getName());
                values.put(MediaStore.Video.Media.DISPLAY_NAME, file.getName());
                values.put(MediaStore.Video.Media.ARTIST, "No artist");
                values.put(MediaStore.Video.Media.MIME_TYPE, AUDIO_MIME_TYPE);
                values.put(MediaStore.Video.Media.DATA, file.toString());
                resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                debug("Registered audio file: " + mAudioFile.getAbsolutePath());
            } else {
                error("Failed to register audio file into content provider.", null);
            }
        }

        private boolean checkFile(final @NonNull File file) {
            return file.exists() && file.length() > 0;
        }
    }

    private class AudioEncoderTask implements Runnable {

        @Override
        public void run() {
            debug("Start encoding.");
            mAudioRecord.startRecording();

            final int bufferSize = 1024;
            int len;
            byte[] data = new byte[bufferSize];
            final long timeoutUs = 1000 * 1000;

            try {
                while (mRecording) {
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
                debug("Stopped encoding.");
            }
        }
    }


}

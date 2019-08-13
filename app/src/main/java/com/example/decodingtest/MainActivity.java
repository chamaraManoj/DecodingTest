package com.example.decodingtest;

import androidx.appcompat.app.AppCompatActivity;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.opengl.GLES20;
import android.os.Bundle;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import static android.media.MediaExtractor.MetricsConstants.MIME_TYPE;

public class MainActivity extends AppCompatActivity {
    private static final boolean VERBOSE = true;
    private static final boolean WORK_AROUND_BUGS = false;
    private String TAG = "dddd";

    long[] pts;
    int[] size;
    int[] flags;
    ArrayList<byte[]> frameBytes;
    private int mWidth = -1;
    private int mHeight = -1;
    // bit rate, in bits per second
    private int mBitRate = -1;

    public String FRAGMENT_SHADER;

    private static final int TEST_R0 = 0;                   // dull green background
    private static final int TEST_G0 = 136;
    private static final int TEST_B0 = 0;
    private static final int TEST_R1 = 236;                 // pink; BT.601 YUV {120,160,200}
    private static final int TEST_G1 = 50;
    private static final int TEST_B1 = 186;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pts = new long[7];
        size = new int[7];
        flags = new int[7];
        frameBytes = new ArrayList<byte[]>();

        String MIME_TYPE = "video/hevc";

        pts[0] = 48352680;
        size[0] = 221;
        flags[0] = 0;
        pts[1] = 48229800;
        size[1] = 66;
        flags[1] = 0;
        pts[2] = 48106920;
        size[2] = 39;
        flags[1] = 0;
        pts[3] = 48844200;
        size[3] = 206;
        flags[1] = 0;
        pts[4] = 48598440;
        size[4] = 107;
        flags[1] = 0;
        pts[5] = 48475560;
        size[5] = 79;
        flags[1] = 0;
        pts[6] = 48721320;
        size[6] = 41;
        flags[1] = 0;

        for (int i = 0; i < 7; i++) {
            String filePath = "frame" + String.valueOf(i + 1) + ".txt";
            File file = new File(filePath);
            byte[] bArray = readFileToByteArray(filePath, size[i]);

            frameBytes.add(bArray);

        }

        setParameters(256,180);

        FRAGMENT_SHADER =
                "#extension GL_OES_EGL_image_external : require\n" +
                        "precision mediump float;\n" +
                        "varying vec2 vTextureCoord;\n" +
                        "uniform samplerExternalOES sTexture;\n" +
                        "void main() {\n" +
                        "  gl_FragColor = texture2D(sTexture, vTextureCoord).rbga;\n" +
                        "}\n";

        editVideoFile(frameBytes,  pts,size, flags );
    }


    private byte[] readFileToByteArray(String file, int size) {
        //FileInputStream fis = null;
        byte[] bArrayt = new byte[size];
        try {
            InputStream inputStream = getAssets().open(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            int count = 0;
            while (reader.ready()) {
                String line = reader.readLine();
                int i = Integer.parseInt(line);
                Integer integerObject = new Integer(i);

                if (count < size) {
                    bArrayt[count] = integerObject.byteValue();
                    count++;
                }
            }
            bArrayt[0] = 0;

        } catch (IOException e) {
            e.printStackTrace();
        }

        return bArrayt;
    }


    private void editVideoFile(ArrayList<byte[]> inputData, long[] pts,int[] size, int[] flags ) {
        //if (VERBOSE) Log.d(TAG, "editVideoFile " + mWidth + "x" + mHeight);
        //VideoChunks outputData = new VideoChunks();
        MediaCodec decoder = null;
        MediaCodec encoder = null;
        InputSurface inputSurface = null;
        OutputSurface outputSurface = null;
        try {
            //MediaFormat inputFormat = inputData.getMediaFormat();
            MediaFormat inputFormat= MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
            // Create an encoder format that matches the input format.  (Might be able to just
            // re-use the format used to generate the video, since we want it to be the same.)
            MediaFormat outputFormat = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
            outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE,
                    inputFormat.getInteger(MediaFormat.KEY_BIT_RATE));
            outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE,
                    inputFormat.getInteger(MediaFormat.KEY_FRAME_RATE));
            outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,
                    inputFormat.getInteger(MediaFormat.KEY_I_FRAME_INTERVAL));
            //outputData.setMediaFormat(outputFormat);
            try {
                encoder = MediaCodec.createEncoderByType(MIME_TYPE);
            } catch (IOException e) {
                e.printStackTrace();
            }
            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = new InputSurface(encoder.createInputSurface());
            inputSurface.makeCurrent();
            encoder.start();
            // OutputSurface uses the EGL context created by InputSurface.
            try {
                decoder = MediaCodec.createDecoderByType(MIME_TYPE);
            } catch (IOException e) {
                e.printStackTrace();
            }
            outputSurface = new OutputSurface();
            outputSurface.changeFragmentShader(FRAGMENT_SHADER);
            decoder.configure(inputFormat, outputSurface.getSurface(), null, 0);
            decoder.start();
            editVideoData(inputData, decoder, outputSurface, inputSurface, encoder,pts,size,flags);
        } finally {
            //if (VERBOSE) Log.d(TAG, "shutting down encoder, decoder");
            if (outputSurface != null) {
                outputSurface.release();
            }
            if (inputSurface != null) {
                inputSurface.release();
            }
            if (encoder != null) {
                encoder.stop();
                encoder.release();
            }
            if (decoder != null) {
                decoder.stop();
                decoder.release();
            }
        }
        //return outputData;
    }

    private void setParameters(int width, int height) {

        mWidth = width;
        mHeight = height;

    }


    private void generateSurfaceFrame(int frameIndex) {
        frameIndex %= 8;
        int startX, startY;
        if (frameIndex < 4) {
            // (0,0) is bottom-left in GL
            startX = frameIndex * (mWidth / 4);
            startY = mHeight / 2;
        } else {
            startX = (7 - frameIndex) * (mWidth / 4);
            startY = 0;
        }
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClearColor(TEST_R0 / 255.0f, TEST_G0 / 255.0f, TEST_B0 / 255.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(startX, startY, mWidth / 4, mHeight / 2);
        GLES20.glClearColor(TEST_R1 / 255.0f, TEST_G1 / 255.0f, TEST_B1 / 255.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    }


    /**
     * Returns the first codec capable of encoding the specified MIME type, or null if no
     * match was found.
     */
    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }


    /**
     * Edits a stream of video data.
     */
    private void editVideoData(ArrayList<byte[]> inputData, MediaCodec decoder,
                               OutputSurface outputSurface, InputSurface inputSurface, MediaCodec encoder,long[] pts,int[] size, int[] flagsPara) {
        final int TIMEOUT_USEC = 10000;
        ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
        ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int inputChunk = 0;
        int outputCount = 0;
        boolean outputDone = false;
        boolean inputDone = false;
        boolean decoderDone = false;
        while (!outputDone) {
            //if (VERBOSE) Log.d(TAG, "edit loop");
            // Feed more data to the decoder.
            if (!inputDone) {
                int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (inputBufIndex >= 0) {
                    if (inputChunk == inputData.size()) {
                        // End of stream -- send empty frame with EOS flag set.
                        decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                        //if (VERBOSE) Log.d(TAG, "sent input EOS (with zero-length frame)");
                    } else {
                        // Copy a chunk of input to the decoder.  The first chunk should have
                        // the BUFFER_FLAG_CODEC_CONFIG flag set.
                        ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
                        inputBuf.clear();
                        inputBuf.put(inputData.get(inputChunk));
                        int flags = flagsPara[inputChunk];
                        long time = pts[inputChunk];
                        decoder.queueInputBuffer(inputBufIndex, 0, inputBuf.position(),
                                time, flags);
                        if (VERBOSE) {
                            Log.d(TAG, "submitted frame " + inputChunk + " to dec, size=" +
                                    inputBuf.position() + " flags=" + flags);
                        }
                        inputChunk++;
                    }
                } else {
                    if (VERBOSE) Log.d(TAG, "input buffer not available");
                }
            }
            // Assume output is available.  Loop until both assumptions are false.
            boolean decoderOutputAvailable = !decoderDone;
            boolean encoderOutputAvailable = true;
            while (decoderOutputAvailable || encoderOutputAvailable) {
                // Start by draining any pending output from the encoder.  It's important to
                // do this before we try to stuff any more data in.
                /*int encoderStatus = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    if (VERBOSE) Log.d(TAG, "no output from encoder available");
                    encoderOutputAvailable = false;
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    encoderOutputBuffers = encoder.getOutputBuffers();
                    if (VERBOSE) Log.d(TAG, "encoder output buffers changed");
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = encoder.getOutputFormat();
                    if (VERBOSE) Log.d(TAG, "encoder output format changed: " + newFormat);
                } else if (encoderStatus < 0) {
                    fail("unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                } else { // encoderStatus >= 0
                    ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                    if (encodedData == null) {
                        fail("encoderOutputBuffer " + encoderStatus + " was null");
                    }
                    // Write the data to the output "file".
                    if (info.size != 0) {
                        encodedData.position(info.offset);
                        encodedData.limit(info.offset + info.size);
                        outputData.addChunk(encodedData, info.flags, info.presentationTimeUs);
                        outputCount++;
                        if (VERBOSE) Log.d(TAG, "encoder output " + info.size + " bytes");
                    }
                    outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    encoder.releaseOutputBuffer(encoderStatus, false);
                }
                if (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // Continue attempts to drain output.
                    continue;
                }*/
                // Encoder is drained, check to see if we've got a new frame of output from
                // the decoder.  (The output is going to a Surface, rather than a ByteBuffer,
                // but we still get information through BufferInfo.)
                if (!decoderDone) {
                    int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                    if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // no output available yet
                        if (VERBOSE) Log.d(TAG, "no output from decoder available");
                        decoderOutputAvailable = false;
                    } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        //decoderOutputBuffers = decoder.getOutputBuffers();
                        if (VERBOSE) Log.d(TAG, "decoder output buffers changed (we don't care)");
                    } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // expected before first buffer of data
                        MediaFormat newFormat = decoder.getOutputFormat();
                        if (VERBOSE) Log.d(TAG, "decoder output format changed: " + newFormat);
                    } else if (decoderStatus < 0) {
                        //fail("unexpected result from decoder.dequeueOutputBuffer: "+decoderStatus);
                        Log.e(TAG,"unexpected result from decoder.dequeueOutputBuffer: "+decoderStatus);
                    } else { // decoderStatus >= 0
                        if (VERBOSE) Log.d(TAG, "surface decoder given buffer "
                                + decoderStatus + " (size=" + info.size + ")");
                        // The ByteBuffers are null references, but we still get a nonzero
                        // size for the decoded data.
                        boolean doRender = (info.size != 0);
                        // As soon as we call releaseOutputBuffer, the buffer will be forwarded
                        // to SurfaceTexture to convert to a texture.  The API doesn't
                        // guarantee that the texture will be available before the call
                        // returns, so we need to wait for the onFrameAvailable callback to
                        // fire.  If we don't wait, we risk rendering from the previous frame.
                        decoder.releaseOutputBuffer(decoderStatus, doRender);
                        if (doRender) {
                            // This waits for the image and renders it after it arrives.
                            if (VERBOSE) Log.d(TAG, "awaiting frame");
                            outputSurface.awaitNewImage();
                            outputSurface.drawImage();
                            // Send it to the encoder.
                            inputSurface.setPresentationTime(info.presentationTimeUs * 1000);
                            if (VERBOSE) Log.d(TAG, "swapBuffers");
                            inputSurface.swapBuffers();
                        }
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            // forward decoder EOS to encoder
                            if (VERBOSE) Log.d(TAG, "signaling input EOS");
                            if (WORK_AROUND_BUGS) {
                                // Bail early, possibly dropping a frame.
                                return;
                            } else {
                                encoder.signalEndOfInputStream();
                            }
                        }
                    }
                }
            }
        }
        if (inputChunk != outputCount) {
            throw new RuntimeException("frame lost: " + inputChunk + " in, " +
                    outputCount + " out");
        }
    }



}

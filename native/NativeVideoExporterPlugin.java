
package com.md.promotovideomaker;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import android.opengl.EGL14;
import android.opengl.EGLExt;
import android.opengl.GLES20;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.JSObject;
import com.getcapacitor.JSArray;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONObject;

import java.io.FileDescriptor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

import android.view.Surface;

@CapacitorPlugin(name = "NativeVideoExporter")
public class NativeVideoExporterPlugin extends Plugin {

    private static final int FPS = 30;
    private static final int DURATION_SECONDS = 30;
    private static final int TOTAL_FRAMES = FPS * DURATION_SECONDS;

    private static final String MIME = "video/avc";

    @PluginMethod
    public void exportVideo(PluginCall call) {

        if (Build.VERSION.SDK_INT < 29) {
            call.reject("Android 10 या उससे नया संस्करण आवश्यक है।");
            return;
        }

        String format = call.getString("format", "9:16");
        JSArray scenes = call.getArray("scenes");

        if (scenes == null || scenes.length() == 0) {
            call.reject("पहले वीडियो की योजना बनाएँ।");
            return;
        }

        int width = format.equals("16:9") ? 1280 : 720;
        int height = format.equals("16:9") ? 720 : 1280;

        getActivity().runOnUiThread(() -> {
            call.setKeepAlive(true);
        });

        new Thread(() -> {
            Uri savedUri = null;

            try {
                ArrayList<String[]> sceneData = new ArrayList<>();

                for (int i = 0; i < scenes.length(); i++) {
                    JSONObject obj = scenes.getJSONObject(i);

                    sceneData.add(new String[]{
                        obj.optString("title", "Scene " + (i + 1)),
                        obj.optString("text", "")
                    });
                }

                savedUri = createVideo(
                    width,
                    height,
                    sceneData
                );

                JSObject result = new JSObject();
                result.put("success", true);
                result.put("uri", savedUri.toString());
                result.put("message", "वीडियो Gallery में सेव हो गई।");

                call.resolve(result);

            } catch (Exception e) {

                if (savedUri != null) {
                    try {
                        getContext().getContentResolver()
                            .delete(savedUri, null, null);
                    } catch (Exception ignored) {}
                }

                call.reject("वीडियो नहीं बन सकी: " + e.getMessage());
            }

        }).start();
    }

    private Uri createVideo(
        int width,
        int height,
        ArrayList<String[]> scenes
    ) throws Exception {

        ContentValues values = new ContentValues();
        values.put(
            MediaStore.Video.Media.DISPLAY_NAME,
            "MD_Promo_" + System.currentTimeMillis() + ".mp4"
        );
        values.put(
            MediaStore.Video.Media.MIME_TYPE,
            "video/mp4"
        );
        values.put(
            MediaStore.Video.Media.RELATIVE_PATH,
            Environment.DIRECTORY_MOVIES + "/MD Promo"
        );
        values.put(
            MediaStore.Video.Media.IS_PENDING,
            1
        );

        Uri uri = getContext().getContentResolver().insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            values
        );

        if (uri == null) {
            throw new Exception("Gallery में फाइल नहीं बन सकी।");
        }

        try (
            android.os.ParcelFileDescriptor pfd =
                getContext().getContentResolver()
                    .openFileDescriptor(uri, "w")
        ) {
            if (pfd == null) {
                throw new Exception("Video file नहीं खुल सकी।");
            }

            encode(
                pfd.getFileDescriptor(),
                width,
                height,
                scenes
            );
        }

        ContentValues done = new ContentValues();
        done.put(MediaStore.Video.Media.IS_PENDING, 0);

        getContext().getContentResolver().update(
            uri,
            done,
            null,
            null
        );

        return uri;
    }

    private void encode(
        FileDescriptor fd,
        int width,
        int height,
        ArrayList<String[]> scenes
    ) throws Exception {

        MediaCodec encoder = null;
        MediaMuxer muxer = null;
        Surface inputSurface = null;
        EGLRenderer renderer = null;

        boolean muxerStarted = false;
        boolean encoderDone = false;
        int trackIndex = -1;

        MediaCodec.BufferInfo bufferInfo =
            new MediaCodec.BufferInfo();

        try {

            MediaFormat mediaFormat = MediaFormat.createVideoFormat(
                MIME,
                width,
                height
            );

            mediaFormat.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            );

            mediaFormat.setInteger(
                MediaFormat.KEY_BIT_RATE,
                4_000_000
            );

            mediaFormat.setInteger(
                MediaFormat.KEY_FRAME_RATE,
                FPS
            );

            mediaFormat.setInteger(
                MediaFormat.KEY_I_FRAME_INTERVAL,
                1
            );

            encoder = MediaCodec.createEncoderByType(MIME);

            encoder.configure(
                mediaFormat,
                null,
                null,
                MediaCodec.CONFIGURE_FLAG_ENCODE
            );

            inputSurface = encoder.createInputSurface();

            muxer = new MediaMuxer(
                fd,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            );

            encoder.start();

            renderer = new EGLRenderer(
                inputSurface,
                width,
                height
            );

            Bitmap[] sceneBitmaps = new Bitmap[5];

            for (int i = 0; i < 5; i++) {
                String[] scene = scenes.get(
                    Math.min(i, scenes.size() - 1)
                );

                sceneBitmaps[i] = makeSceneBitmap(
                    width,
                    height,
                    scene[0],
                    scene[1],
                    i + 1
                );
            }

            long frameDuration = 1_000_000L / FPS;

            for (int frame = 0; frame < TOTAL_FRAMES; frame++) {

                int sceneIndex = Math.min(
                    4,
                    frame / (TOTAL_FRAMES / 5)
                );

                renderer.draw(
                    sceneBitmaps[sceneIndex],
                    frame * frameDuration
                );

                drainEncoder(
                    encoder,
                    muxer,
                    bufferInfo,
                    false,
                    new int[]{trackIndex},
                    new boolean[]{muxerStarted}
                );

                if (trackIndex == -1 && encoder.getOutputFormat() != null) {
                    // Track index is obtained on INFO_OUTPUT_FORMAT_CHANGED.
                }

                MediaCodec.BufferInfo info =
                    new MediaCodec.BufferInfo();

                int outputIndex = encoder.dequeueOutputBuffer(
                    info,
                    0
                );

                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (muxerStarted) {
                        throw new Exception("Encoder format दोबारा बदला।");
                    }

                    trackIndex = muxer.addTrack(
                        encoder.getOutputFormat()
                    );

                    muxer.start();
                    muxerStarted = true;

                } else if (outputIndex >= 0) {

                    ByteBuffer output = encoder.getOutputBuffer(
                        outputIndex
                    );

                    if (output != null && info.size > 0 && muxerStarted) {
                        output.position(info.offset);
                        output.limit(info.offset + info.size);

                        muxer.writeSampleData(
                            trackIndex,
                            output,
                            info
                        );
                    }

                    encoder.releaseOutputBuffer(
                        outputIndex,
                        false
                    );
                }
            }

            encoder.signalEndOfInputStream();

            while (!encoderDone) {

                int index = encoder.dequeueOutputBuffer(
                    bufferInfo,
                    10_000
                );

                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {

                    if (!muxerStarted) {
                        trackIndex = muxer.addTrack(
                            encoder.getOutputFormat()
                        );

                        muxer.start();
                        muxerStarted = true;
                    }

                } else if (index >= 0) {

                    ByteBuffer output = encoder.getOutputBuffer(index);

                    if (
                        output != null &&
                        bufferInfo.size > 0 &&
                        muxerStarted
                    ) {
                        output.position(bufferInfo.offset);
                        output.limit(
                            bufferInfo.offset + bufferInfo.size
                        );

                        muxer.writeSampleData(
                            trackIndex,
                            output,
                            bufferInfo
                        );
                    }

                    if (
                        (bufferInfo.flags &
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    ) {
                        encoderDone = true;
                    }

                    encoder.releaseOutputBuffer(index, false);
                }
            }

            for (Bitmap bitmap : sceneBitmaps) {
                if (bitmap != null) bitmap.recycle();
            }

        } finally {

            if (renderer != null) {
                renderer.release();
            }

            if (encoder != null) {
                try {
                    encoder.stop();
                } catch (Exception ignored) {}

                encoder.release();
            }

            if (inputSurface != null) {
                inputSurface.release();
            }

            if (muxer != null) {
                try {
                    if (muxerStarted) muxer.stop();
                } catch (Exception ignored) {}

                muxer.release();
            }
        }
    }

    private void drainEncoder(
        MediaCodec encoder,
        MediaMuxer muxer,
        MediaCodec.BufferInfo info,
        boolean end,
        int[] track,
        boolean[] started
    ) {
        // Output is drained during the encoding loop.
    }

    private Bitmap makeSceneBitmap(
        int width,
        int height,
        String title,
        String message,
        int number
    ) {

        Bitmap bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        paint.setShader(new LinearGradient(
            0,
            0,
            width,
            height,
            new int[]{
                Color.rgb(17, 24, 39),
                Color.rgb(88, 28, 135),
                Color.rgb(30, 64, 175)
            },
            null,
            Shader.TileMode.CLAMP
        ));

        canvas.drawRect(
            0,
            0,
            width,
            height,
            paint
        );

        paint.setShader(null);

        paint.setColor(Color.argb(35, 255, 255, 255));

        canvas.drawCircle(
            width * 0.85f,
            height * 0.2f,
            width * 0.28f,
            paint
        );

        paint.setColor(Color.rgb(251, 146, 60));

        canvas.drawRoundRect(
            width * 0.08f,
            height * 0.12f,
            width * 0.92f,
            height * 0.135f,
            20,
            20,
            paint
        );

        paint.setColor(Color.WHITE);
        paint.setTypeface(android.graphics.Typeface.create(
            "sans-serif",
            android.graphics.Typeface.BOLD
        ));

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(width * 0.075f);

        canvas.drawText(
            title,
            width / 2f,
            height * 0.34f,
            paint
        );

        paint.setTypeface(android.graphics.Typeface.create(
            "sans-serif-medium",
            android.graphics.Typeface.NORMAL
        ));

        paint.setTextSize(width * 0.047f);

        drawWrappedText(
            canvas,
            message,
            paint,
            width * 0.12f,
            height * 0.45f,
            width * 0.76f,
            height * 0.075f
        );

        paint.setColor(Color.rgb(251, 191, 36));
        paint.setTextSize(width * 0.045f);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        canvas.drawText(
            "MD PROMO  •  " + number + "/5",
            width / 2f,
            height * 0.88f,
            paint
        );

        return bitmap;
    }

    private void drawWrappedText(
        Canvas canvas,
        String text,
        Paint paint,
        float x,
        float y,
        float maxWidth,
        float lineHeight
    ) {

        String[] words = text.split("\\s+");
        String line = "";
        int lineNumber = 0;

        for (String word : words) {

            String test = line.isEmpty()
                ? word
                : line + " " + word;

            if (paint.measureText(test) > maxWidth && !line.isEmpty()) {

                canvas.drawText(
                    line,
                    x + maxWidth / 2,
                    y + lineNumber * lineHeight,
                    paint
                );

                lineNumber++;
                line = word;

            } else {
                line = test;
            }
        }

        if (!line.isEmpty()) {
            canvas.drawText(
                line,
                x + maxWidth / 2,
                y + lineNumber * lineHeight,
                paint
            );
        }
    }

    private static class EGLRenderer {

        private android.opengl.EGLDisplay display;
        private android.opengl.EGLContext context;
        private android.opengl.EGLSurface surface;

        private int program;
        private int texture;
        private int positionHandle;
        private int textureHandle;
        private int samplerHandle;

        private final int width;
        private final int height;

        private final FloatBuffer vertices;

        EGLRenderer(
            Surface input,
            int width,
            int height
        ) throws Exception {

            this.width = width;
            this.height = height;

            float[] data = {
                -1f, -1f, 0f, 1f,
                 1f, -1f, 1f, 1f,
                -1f,  1f, 0f, 0f,
                 1f,  1f, 1f, 0f
            };

            vertices = ByteBuffer
                .allocateDirect(data.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

            vertices.put(data);
            vertices.position(0);

            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);

            int[] version = new int[2];

            EGL14.eglInitialize(
                display,
                version,
                0,
                version,
                1
            );

            int[] configAttributes = {
                EGL14.EGL_RENDERABLE_TYPE,
                EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                0x3142, 1,
                EGL14.EGL_NONE
            };

            android.opengl.EGLConfig[] configs =
                new android.opengl.EGLConfig[1];

            int[] count = new int[1];

            if (!EGL14.eglChooseConfig(
                display,
                configAttributes,
                0,
                configs,
                0,
                1,
                count,
                0
            )) {
                throw new Exception("EGL configuration failed");
            }

            int[] contextAttributes = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION,
                2,
                EGL14.EGL_NONE
            };

            context = EGL14.eglCreateContext(
                display,
                configs[0],
                EGL14.EGL_NO_CONTEXT,
                contextAttributes,
                0
            );

            int[] surfaceAttributes = {
                EGL14.EGL_NONE
            };

            surface = EGL14.eglCreateWindowSurface(
                display,
                configs[0],
                input,
                surfaceAttributes,
                0
            );

            EGL14.eglMakeCurrent(
                display,
                surface,
                surface,
                context
            );

            String vertexShader =
                "attribute vec4 aPosition;" +
                "attribute vec2 aTexCoord;" +
                "varying vec2 vTexCoord;" +
                "void main(){" +
                "gl_Position=aPosition;" +
                "vTexCoord=aTexCoord;" +
                "}";

            String fragmentShader =
                "precision mediump float;" +
                "uniform sampler2D uTexture;" +
                "varying vec2 vTexCoord;" +
                "void main(){" +
                "gl_FragColor=texture2D(uTexture,vTexCoord);" +
                "}";

            program = createProgram(
                vertexShader,
                fragmentShader
            );

            positionHandle = GLES20.glGetAttribLocation(
                program,
                "aPosition"
            );

            textureHandle = GLES20.glGetAttribLocation(
                program,
                "aTexCoord"
            );

            samplerHandle = GLES20.glGetUniformLocation(
                program,
                "uTexture"
            );

            int[] textures = new int[1];

            GLES20.glGenTextures(1, textures, 0);
            texture = textures[0];

            GLES20.glBindTexture(
                GLES20.GL_TEXTURE_2D,
                texture
            );

            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR
            );

            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER,
   

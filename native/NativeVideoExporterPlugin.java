
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
import android.view.Surface;

import android.opengl.EGL14;
import android.opengl.EGLExt;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONObject;

import java.io.FileDescriptor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

@CapacitorPlugin(name = "NativeVideoExporter")
public class NativeVideoExporterPlugin extends Plugin {

    private static final String MIME = "video/avc";
    private static final int FPS = 30;
    private static final int TOTAL_FRAMES = 900;

    private static class MuxState {
        int track = -1;
        boolean started = false;
    }

    @PluginMethod
    public void exportVideo(PluginCall call) {

        if (Build.VERSION.SDK_INT < 29) {
            call.reject("Android 10 या नया संस्करण आवश्यक है।");
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

        new Thread(() -> {
            Uri uri = null;

            try {
                ArrayList<String[]> data = new ArrayList<>();

                for (int i = 0; i < scenes.length(); i++) {
                    JSONObject obj = scenes.getJSONObject(i);

                    data.add(new String[]{
                        obj.optString("title", "Scene " + (i + 1)),
                        obj.optString("text", "")
                    });
                }

                uri = createVideo(width, height, data);

                JSObject result = new JSObject();
                result.put("success", true);
                result.put("uri", uri.toString());
                result.put("message", "वीडियो Gallery में सेव हो गई।");

                call.resolve(result);

            } catch (Exception e) {

                if (uri != null) {
                    try {
                        getContext().getContentResolver()
                            .delete(uri, null, null);
                    } catch (Exception ignored) {}
                }

                call.reject("वीडियो Export असफल: " + e.getMessage());
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

        values.put(MediaStore.Video.Media.IS_PENDING, 1);

        Uri uri = getContext().getContentResolver().insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            values
        );

        if (uri == null) {
            throw new Exception("Gallery में फाइल नहीं बन सकी।");
        }

        try {
            android.os.ParcelFileDescriptor pfd =
                getContext().getContentResolver()
                    .openFileDescriptor(uri, "w");

            if (pfd == null) {
                throw new Exception("Video file नहीं खुली।");
            }

            try {
                encode(
                    pfd.getFileDescriptor(),
                    width,
                    height,
                    scenes
                );
            } finally {
                pfd.close();
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

        } catch (Exception e) {
            getContext().getContentResolver().delete(
                uri,
                null,
                null
            );
            throw e;
        }
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

        MuxState state = new MuxState();

        MediaCodec.BufferInfo info =
            new MediaCodec.BufferInfo();

        Bitmap[] bitmaps = new Bitmap[5];

        try {

            MediaFormat mediaFormat =
                MediaFormat.createVideoFormat(
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

            for (int i = 0; i < 5; i++) {

                String[] scene = scenes.get(
                    Math.min(i, scenes.size() - 1)
                );

                bitmaps[i] = makeSceneBitmap(
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
                    frame / 180
                );

                long presentationTime =
                    frame * frameDuration;

                renderer.draw(
                    bitmaps[sceneIndex],
                    presentationTime * 1000L
                );

                drainEncoder(
                    encoder,
                    muxer,
                    info,
                    state,
                    false
                );
            }

            encoder.signalEndOfInputStream();

            boolean endReached = false;

            while (!endReached) {
                endReached = drainEncoder(
                    encoder,
                    muxer,
                    info,
                    state,
                    true
                );
            }

            if (!state.started) {
                throw new Exception(
                    "Encoder ने कोई वीडियो Track नहीं बनाया।"
                );
            }

        } finally {

            for (Bitmap bitmap : bitmaps) {
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }

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
                    if (state.started) {
                        muxer.stop();
                    }
                } catch (Exception ignored) {}

                muxer.release();
            }
        }
    }

    private boolean drainEncoder(
        MediaCodec encoder,
        MediaMuxer muxer,
        MediaCodec.BufferInfo info,
        MuxState state,
        boolean waitForEnd
    ) throws Exception {

        while (true) {

            int index = encoder.dequeueOutputBuffer(
                info,
                waitForEnd ? 10000 : 0
            );

            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                return false;
            }

            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {

                if (state.started) {
                    throw new Exception(
                        "Encoder output format दोबारा बदला।"
                    );
                }

                state.track = muxer.addTrack(
                    encoder.getOutputFormat()
                );

                muxer.start();
                state.started = true;

                continue;
            }

            if (index >= 0) {

                ByteBuffer output =
                    encoder.getOutputBuffer(index);

                boolean isEnd =
                    (info.flags &
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

                try {

                    if (
                        output != null &&
                        info.size > 0 &&
                        state.started &&
                        (info.flags &
                        MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                    ) {

                        output.position(info.offset);
                        output.limit(info.offset + info.size);

                        muxer.writeSampleData(
                            state.track,
                            output,
                            info
                        );
                    }

                } finally {
                    encoder.releaseOutputBuffer(index, false);
                }

                if (isEnd) {
                    return true;
                }
            }
        }
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
        paint.setTextAlign(Paint.Align.CENTER);

        paint.setTypeface(
            android.graphics.Typeface.create(
                "sans-serif",
                android.graphics.Typeface.BOLD
            )
        );

        paint.setTextSize(width * 0.075f);

        canvas.drawText(
            title,
            width / 2f,
            height * 0.34f,
            paint
        );

        paint.setTypeface(
            android.graphics.Typeface.create(
                "sans-serif-medium",
                android.graphics.Typeface.NORMAL
            )
        );

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
        paint.setTypeface(
            android.graphics.Typeface.DEFAULT_BOLD
        );

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

            if (
                paint.measureText(test) > maxWidth &&
                !line.isEmpty()
            ) {

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

        private boolean textureInitialized = false;

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

            display = EGL14.eglGetDisplay(
                EGL14.EGL_DEFAULT_DISPLAY
            );

            int[] version = new int[2];

            if (!EGL14.eglInitialize(
                display,
                version,
                0,
                version,
                1
            )) {
                throw new Exception("EGL initialize failed");
            }

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
            ) || count[0] == 0) {
                throw new Exception("EGL config failed");
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

            if (
                context == null ||
                surface == null ||
                context == EGL14.EGL_NO_CONTEXT ||
                surface == EGL14.EGL_NO_SURFACE
            ) {
                throw new Exception("EGL surface creation failed");
            }

            if (!EGL14.eglMakeCurrent(
                display,
                surface,
                surface,
                context
            )) {
                throw new Exception("EGL make current failed");
            }

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
    GLES20.GL_LINEAR
);
            GLES20.glBindTexture(
                GLES20.GL_TEXTURE_2D,
                0
            );
        }

        void draw(
            Bitmap bitmap,
            long presentationTimeNs
        ) throws Exception {

            EGL14.eglMakeCurrent(
                display,
                surface,
                surface,
                context
            );

            GLES20.glViewport(0, 0, width, height);
            GLES20.glUseProgram(program);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

            GLES20.glBindTexture(
                GLES20.GL_TEXTURE_2D,
                texture
            );

            if (!textureInitialized) {
                GLUtils.texImage2D(
                    GLES20.GL_TEXTURE_2D,
                    0,
                    bitmap,
                    0
                );
                textureInitialized = true;
            } else {
                GLUtils.texSubImage2D(
                    GLES20.GL_TEXTURE_2D,
                    0,
                    0,
                    0,
                    bitmap
                );
            }

            GLES20.glUniform1i(samplerHandle, 0);

            vertices.position(0);
            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glVertexAttribPointer(
                positionHandle, 2, GLES20.GL_FLOAT,
                false, 16, vertices
            );

            vertices.position(2);
            GLES20.glEnableVertexAttribArray(textureHandle);
            GLES20.glVertexAttribPointer(
                textureHandle, 2, GLES20.GL_FLOAT,
                false, 16, vertices
            );

            GLES20.glDrawArrays(
                GLES20.GL_TRIANGLE_STRIP, 0, 4
            );

            GLES20.glDisableVertexAttribArray(positionHandle);
            GLES20.glDisableVertexAttribArray(textureHandle);

            EGLExt.eglPresentationTimeANDROID(
                display,
                surface,
                presentationTimeNs
            );

            EGL14.eglSwapBuffers(display, surface);
        }

        private int createProgram(
            String vertexSource,
            String fragmentSource
        ) throws Exception {

            int vertexShader = createShader(
                GLES20.GL_VERTEX_SHADER,
                vertexSource
            );

            int fragmentShader = createShader(
                GLES20.GL_FRAGMENT_SHADER,
                fragmentSource
            );

            int result = GLES20.glCreateProgram();

            GLES20.glAttachShader(result, vertexShader);
            GLES20.glAttachShader(result, fragmentShader);
            GLES20.glLinkProgram(result);

            int[] status = new int[1];

            GLES20.glGetProgramiv(
                result,
                GLES20.GL_LINK_STATUS,
                status,
                0
            );

            GLES20.glDeleteShader(vertexShader);
            GLES20.glDeleteShader(fragmentShader);

            if (status[0] == 0) {
                String error = GLES20.glGetProgramInfoLog(result);
                GLES20.glDeleteProgram(result);
                throw new Exception(error);
            }

            return result;
        }

        private int createShader(
            int type,
            String source
        ) throws Exception {

            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);

            int[] status = new int[1];

            GLES20.glGetShaderiv(
                shader,
                GLES20.GL_COMPILE_STATUS,
                status,
                0
            );

            if (status[0] == 0) {
                String error = GLES20.glGetShaderInfoLog(shader);
                GLES20.glDeleteShader(shader);
                throw new Exception(error);
            }

            return shader;
        }

        void release() {

            try {
                EGL14.eglMakeCurrent(
                    display,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT
                );

                if (texture != 0) {
                    GLES20.glDeleteTextures(
                        1,
                        new int[]{texture},
                        0
                    );
                }

                if (program != 0) {
                    GLES20.glDeleteProgram(program);
                }

                EGL14.eglDestroySurface(display, surface);
                EGL14.eglDestroyContext(display, context);
                EGL14.eglTerminate(display);

            } catch (Exception ignored) {
            }
        }
    }
}

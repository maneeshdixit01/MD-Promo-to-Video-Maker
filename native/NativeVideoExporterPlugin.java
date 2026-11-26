
package com.md.promotovideomaker;

import android.content.ContentValues;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.provider.MediaStore;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.IOException;
import java.nio.ByteBuffer;

@CapacitorPlugin(name = "NativeVideoExporter")
public class NativeVideoExporterPlugin extends Plugin {

    private static final String MIME_TYPE = "video/avc";
    private static final int WIDTH = 720;
    private static final int HEIGHT = 1280;
    private static final int FRAME_RATE = 30;
    private static final int BIT_RATE = 4_000_000;
    private static final int I_FRAME_INTERVAL = 1;

    @PluginMethod
    public void exportVideo(PluginCall call) {
        call.reject("Video exporter is not yet connected. The rendering engine must be added before MP4 export can work.");
    }
}

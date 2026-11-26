
package com.md.promotovideomaker;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(NativeVideoExporterPlugin.class);
        super.onCreate(savedInstanceState);
    }
}

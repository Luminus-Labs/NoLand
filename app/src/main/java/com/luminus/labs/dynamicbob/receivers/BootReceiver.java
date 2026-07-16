package com.luminus.labs.dynamicbob.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.luminus.labs.dynamicbob.services.OverlayService;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Accessibility Service is usually started by the system, 
            // but we can try to start it explicitly if needed, 
            // though for AccessibilityService it's better to let the system handle it.
            // However, we can start our other services if any.
        }
    }
}

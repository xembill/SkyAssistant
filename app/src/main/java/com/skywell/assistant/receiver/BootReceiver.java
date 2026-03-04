package com.skywell.assistant.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.skywell.assistant.service.AssistantService;
import com.skywell.assistant.service.FloatingButtonService;

/** Araç açıldığında servisleri otomatik başlat. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        Log.i("SkyAssistant", "Boot/update alındı: " + intent.getAction());

        startService(context, AssistantService.class);
        startService(context, FloatingButtonService.class);
    }

    private void startService(Context ctx, Class<?> cls) {
        Intent i = new Intent(ctx, cls);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(i);
        } else {
            ctx.startService(i);
        }
    }
}

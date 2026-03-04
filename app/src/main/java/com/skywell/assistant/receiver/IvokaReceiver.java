package com.skywell.assistant.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.skywell.assistant.service.AssistantService;

/**
 * Direksiyon simidi tuşundan gelen IVOKA broadcast'ini yakalar
 * ve aktif dinlemeyi başlatır.
 */
public class IvokaReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Log.i("SkyAssistant", "IvokaReceiver: " + action);

        // Direksiyon tuşu veya manuel trigger → dinlemeyi başlat
        Intent serviceIntent = new Intent(context, AssistantService.class);
        serviceIntent.setAction(AssistantService.ACTION_START_LISTENING);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}

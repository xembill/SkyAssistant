package com.skywell.assistant.service;

import android.animation.ObjectAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

import com.skywell.assistant.R;

/**
 * Ekranda her zaman görünen yüzen XSky butonu.
 * Dinlerken animasyonla büyür, durduğunda küçülür.
 */
public class FloatingButtonService extends Service {

    public static final String ACTION_LISTENING_START = "com.skywell.assistant.LISTENING_START";
    public static final String ACTION_LISTENING_STOP  = "com.skywell.assistant.LISTENING_STOP";

    private static final String NOTIF_CHANNEL = "sky_floating";
    private static final int NOTIF_ID = 1002;

    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams params;

    private BroadcastReceiver listeningReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        showFloatingButton();
        registerListeningReceiver();
        startForeground(NOTIF_ID, buildNotification());
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        if (listeningReceiver != null) unregisterReceiver(listeningReceiver);
        if (floatingView != null) windowManager.removeView(floatingView);
        super.onDestroy();
    }

    private void registerListeningReceiver() {
        listeningReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (floatingView == null) return;
                String action = intent.getAction();
                if (ACTION_LISTENING_START.equals(action)) {
                    animateScale(1.0f, 1.5f, true);
                } else if (ACTION_LISTENING_STOP.equals(action)) {
                    animateScale(1.5f, 1.0f, false);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_LISTENING_START);
        filter.addAction(ACTION_LISTENING_STOP);
        registerReceiver(listeningReceiver, filter);
    }

    /** Yüzen butonu büyüt/küçült + renk/ikon değiştir */
    private void animateScale(float from, float to, boolean listening) {
        TextView btn = floatingView.findViewById(R.id.btnMic);
        if (btn == null) return;

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(floatingView, "scaleX", from, to);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(floatingView, "scaleY", from, to);
        scaleX.setDuration(300);
        scaleY.setDuration(300);
        scaleX.start();
        scaleY.start();

        btn.setTextColor(listening ? 0xFFFF4444 : 0xFFFFFFFF);
        btn.setText(listening ? "●" : "XSky");
    }

    private void showFloatingButton() {
        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.END;
        params.x = 20;
        params.y = 200;

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_button, null);
        TextView btn = floatingView.findViewById(R.id.btnMic);

        btn.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX - (int)(event.getRawX() - initialTouchX);
                        params.y = initialY + (int)(event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        float dx = Math.abs(event.getRawX() - initialTouchX);
                        float dy = Math.abs(event.getRawY() - initialTouchY);
                        if (dx < 20 && dy < 20) {
                            Intent startIntent = new Intent(FloatingButtonService.this, AssistantService.class);
                            startIntent.setAction(AssistantService.ACTION_START_LISTENING);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(startIntent);
                            } else {
                                startService(startIntent);
                            }
                        }
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(floatingView, params);
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    NOTIF_CHANNEL, "SkyAssistant Buton",
                    NotificationManager.IMPORTANCE_MIN);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
        return new NotificationCompat.Builder(this, NOTIF_CHANNEL)
                .setContentTitle("SkyAssistant")
                .setContentText("XSky aktif")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();
    }
}

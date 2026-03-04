package com.skywell.assistant.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.skywell.assistant.R;

/**
 * Dinleme sırasında ekranın üzerinde gösterilen şeffaf overlay.
 * Tanınan metni ve durumu gösterir.
 * addView/removeView ile yönetilir — gizliyken hiçbir pencere yok.
 */
public class AssistantOverlay {

    private final Context context;
    private final WindowManager windowManager;
    private final WindowManager.LayoutParams params;
    private View overlayView;
    private TextView tvStatus;
    private boolean attached = false;

    public AssistantOverlay(Context context) {
        this.context = context;
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.y = 200; // navigasyon/gesture bar'ının üstünde

        inflateView();
    }

    private void inflateView() {
        overlayView = LayoutInflater.from(context).inflate(R.layout.overlay_assistant, null);
        tvStatus = overlayView.findViewById(R.id.tvAssistantText);
    }

    public void showListening() {
        SharedPreferences prefs = context.getSharedPreferences(
                com.skywell.assistant.ui.AssistantActivity.PREFS_NAME, Context.MODE_PRIVATE);
        String name = prefs.getString(com.skywell.assistant.ui.AssistantActivity.KEY_USER_NAME, "");
        String greeting = name.isEmpty() ? "🎤 Dinliyorum..." : "🎤 " + name + ", dinliyorum...";
        overlayView.post(() -> {
            tvStatus.setText(greeting);
            attach();
        });
    }

    public void updateText(String text) {
        overlayView.post(() -> {
            tvStatus.setText(text);
            attach();
        });
    }

    public void hide() {
        overlayView.post(this::detach);
    }

    private void attach() {
        if (!attached) {
            try {
                windowManager.addView(overlayView, params);
                attached = true;
            } catch (Exception ignored) {}
        }
    }

    private void detach() {
        if (attached) {
            try {
                windowManager.removeView(overlayView);
                attached = false;
                // Yeni gösterim için view'ı yeniden inflate et
                inflateView();
            } catch (Exception ignored) {}
        }
    }

    public boolean isVisible() { return attached; }
}

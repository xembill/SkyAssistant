package com.skywell.assistant.ui;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.skywell.assistant.R;
import com.skywell.assistant.service.AssistantService;
import com.skywell.assistant.service.FloatingButtonService;

/**
 * İlk açılış: izin iste → isim sor (custom klavye, IME yok) → servisleri başlat.
 */
public class AssistantActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "sky_prefs";
    public static final String KEY_USER_NAME = "user_name";

    private static final int PERM_REQUEST = 100;

    private StringBuilder nameBuffer = new StringBuilder();
    private TextView tvNameDisplay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String action = getIntent() != null ? getIntent().getAction() : null;
        if (Intent.ACTION_ASSIST.equals(action)) {
            triggerListening();
            finish();
            return;
        }

        if (hasAllPermissions() && hasSavedName()) {
            startServices();
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        LinearLayout layoutPerms = findViewById(R.id.layoutPermissions);
        LinearLayout layoutName  = findViewById(R.id.layoutName);
        tvNameDisplay = findViewById(R.id.tvNameDisplay);

        if (hasAllPermissions()) {
            layoutPerms.setVisibility(View.GONE);
            layoutName.setVisibility(View.VISIBLE);
        }

        Button btnPerms = findViewById(R.id.btnPermissions);
        Button btnStart = findViewById(R.id.btnStart);
        btnPerms.setOnClickListener(v -> requestAllPermissions());
        btnStart.setOnClickListener(v -> {
            if (hasAllPermissions()) {
                layoutPerms.setVisibility(View.GONE);
                layoutName.setVisibility(View.VISIBLE);
            } else {
                requestAllPermissions();
            }
        });

        setupKeyboard();

        Button btnSave = findViewById(R.id.btnSaveName);
        btnSave.setOnClickListener(v -> {
            String name = nameBuffer.toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Lütfen bir isim gir!", Toast.LENGTH_SHORT).show();
                return;
            }
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putString(KEY_USER_NAME, name).apply();
            startServices();
            finish();
        });
    }

    private void setupKeyboard() {
        // harf → id eşleştirme tablosu
        int[] ids = {
            R.id.kQ, R.id.kW, R.id.kE, R.id.kR, R.id.kT,
            R.id.kY, R.id.kU, R.id.kI, R.id.kO, R.id.kP,
            R.id.kA, R.id.kS, R.id.kD, R.id.kF, R.id.kG,
            R.id.kH, R.id.kJ, R.id.kK, R.id.kL,
            R.id.kZ, R.id.kX, R.id.kC, R.id.kV, R.id.kB,
            R.id.kN, R.id.kM,
        };
        String letters = "QWERTYUIOP" + "ASDFGHJKL" + "ZXCVBNM";

        for (int i = 0; i < ids.length && i < letters.length(); i++) {
            final char ch = letters.charAt(i);
            Button btn = findViewById(ids[i]);
            if (btn != null) btn.setOnClickListener(v -> appendChar(ch));
        }

        // Türkçe özel karakterler
        int[] trIds = { R.id.kII, R.id.kSS, R.id.kOO, R.id.kUU, R.id.kCC, R.id.kGG };
        String trChars = "İŞÖÜÇĞ";
        for (int i = 0; i < trIds.length && i < trChars.length(); i++) {
            final char ch = trChars.charAt(i);
            Button btn = findViewById(trIds[i]);
            if (btn != null) btn.setOnClickListener(v -> appendChar(ch));
        }

        Button kSpace = findViewById(R.id.kSpace);
        if (kSpace != null) kSpace.setOnClickListener(v -> appendChar(' '));

        Button kBack = findViewById(R.id.kBack);
        if (kBack != null) kBack.setOnClickListener(v -> {
            if (nameBuffer.length() > 0) {
                nameBuffer.deleteCharAt(nameBuffer.length() - 1);
                updateDisplay();
            }
        });
    }

    private void appendChar(char ch) {
        nameBuffer.append(ch);
        updateDisplay();
    }

    private void updateDisplay() {
        if (tvNameDisplay != null) tvNameDisplay.setText(nameBuffer.toString());
    }

    private boolean hasSavedName() {
        String name = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_USER_NAME, "");
        return !TextUtils.isEmpty(name);
    }

    private void startServices() {
        Intent ai = new Intent(this, AssistantService.class);
        Intent fi = new Intent(this, FloatingButtonService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(ai);
            startForegroundService(fi);
        } else {
            startService(ai);
            startService(fi);
        }
    }

    private void triggerListening() {
        Intent i = new Intent(this, AssistantService.class);
        i.setAction(AssistantService.ACTION_START_LISTENING);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
    }

    private boolean hasAllPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
                && Settings.canDrawOverlays(this);
    }

    private void requestAllPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{ Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.CALL_PHONE,
                            Manifest.permission.READ_CONTACTS },
                    PERM_REQUEST);
        }
        if (!Settings.canDrawOverlays(this)) {
            Intent overlayIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(overlayIntent, PERM_REQUEST + 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (hasAllPermissions()) {
            LinearLayout layoutPerms = findViewById(R.id.layoutPermissions);
            LinearLayout layoutName  = findViewById(R.id.layoutName);
            if (layoutPerms != null) layoutPerms.setVisibility(View.GONE);
            if (layoutName  != null) layoutName.setVisibility(View.VISIBLE);
        } else {
            Toast.makeText(this, "Mikrofon izni gerekli!", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PERM_REQUEST + 1 && Settings.canDrawOverlays(this)) {
            LinearLayout layoutPerms = findViewById(R.id.layoutPermissions);
            LinearLayout layoutName  = findViewById(R.id.layoutName);
            if (layoutPerms != null) layoutPerms.setVisibility(View.GONE);
            if (layoutName  != null) layoutName.setVisibility(View.VISIBLE);
        }
    }
}

package com.luminus.labs.dynamicbob.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.luminus.labs.dynamicbob.BuildConfig;
import com.luminus.labs.dynamicbob.R;
import com.luminus.labs.dynamicbob.plugins.AppShortcut.AppShortcutPickerActivity;
import com.luminus.labs.dynamicbob.plugins.ExportedPlugins;
import com.luminus.labs.dynamicbob.services.UpdaterService;
import com.luminus.labs.dynamicbob.utils.adapters.RecylerViewSettingsAdapter;
import com.luminus.labs.dynamicbob.utils.SettingStruct;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private SharedPreferences sharedPreferences;
    private final ArrayList<SettingStruct> settings = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            if (sharedPreferences.getBoolean("clip_copy_enabled", true)) {
                ClipboardManager clipboard = (ClipboardManager)
                        getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Dynamic Bob error log", throwable.getMessage() + " : " + Arrays.toString(throwable.getStackTrace()));
                clipboard.setPrimaryClip(clip);
                sendCrashNotification();
            }
            Runtime.getRuntime().exit(0);
        });
        init();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        CollapsingToolbarLayout collapsingToolbarLayout = findViewById(R.id.collapsing_toolbar);
        if (collapsingToolbarLayout != null) {
            collapsingToolbarLayout.setExpandedTitleTypeface(ResourcesCompat.getFont(this, R.font.ndot));
            collapsingToolbarLayout.setCollapsedTitleTypeface(ResourcesCompat.getFont(this, R.font.ndot));
        }

        if ((Settings.Secure.getString(this.getContentResolver(), "enabled_notification_listeners") != null && !Settings.Secure.getString(this.getContentResolver(), "enabled_notification_listeners").contains(getApplicationContext().getPackageName())
        ) || ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            startActivity(new Intent(this, PermissionActivity.class));
        }
        MaterialCardView enable_btn = findViewById(R.id.enable_switch);
        enable_btn.setOnClickListener(l -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(this, "Installed Apps -> Dynamic Bob", Toast.LENGTH_SHORT).show();
        });

        settings.add(new SettingStruct("Disable Battery Optimization", "Prevent the system from killing the app (Recommended)", "Stability Settings", SettingStruct.TYPE_TOGGLE) {
            @Override
            public boolean onAttach(Context ctx) {
                return isIgnoringBatteryOptimizations();
            }

            @Override
            public void onCheckChanged(boolean checked, Context ctx) {
                if (checked && !isIgnoringBatteryOptimizations()) {
                    requestBatteryOptimizationExemption();
                } else if (!checked && isIgnoringBatteryOptimizations()) {
                    // Cannot easily undo from here, just show message
                    Toast.makeText(ctx, "Please enable battery optimization in system settings if desired.", Toast.LENGTH_LONG).show();
                    // Re-check to reflect reality
                    sharedPreferences.edit().putBoolean("refresh_settings", true).apply();
                }
            }
        });

        settings.add(new SettingStruct("Persistent Service", "Keep the island running more reliably in the background", "Stability Settings", SettingStruct.TYPE_TOGGLE) {
            @Override
            public boolean onAttach(Context ctx) {
                return sharedPreferences.getBoolean("persistent_service", true);
            }

            @Override
            public void onCheckChanged(boolean checked, Context ctx) {
                sharedPreferences.edit().putBoolean("persistent_service", checked).apply();
            }
        });

        settings.add(null);

        settings.add(new SettingStruct("Configure App Shortcuts", "Select apps to show in the island", "App Settings", SettingStruct.TYPE_CUSTOM) {
            @Override
            public void onClick(Context c) {
                startActivity(new Intent(MainActivity.this, AppShortcutPickerActivity.class));
            }
        });

        settings.add(new SettingStruct("Manage Overlay Layout", "Adjust the position and size of the Smart Edge", "App Settings", SettingStruct.TYPE_CUSTOM) {
            @Override
            public void onClick(Context c) {
                startActivity(new Intent(MainActivity.this, OverlayLayoutSettingActivity.class));
            }
        });

        settings.add(new SettingStruct("Appearance", "Customize colors and background image", "App Settings", SettingStruct.TYPE_CUSTOM) {
            @Override
            public void onClick(Context ctx) {
                startActivity(new Intent(MainActivity.this, AppearanceActivity.class));
            }
        });

        if (BuildConfig.AUTO_UPDATE)
            settings.add(new SettingStruct("Auto update checking", "Keep the app up to date automatically", "App Settings", SettingStruct.TYPE_TOGGLE) {
                @Override
                public boolean onAttach(Context ctx) {
                    return sharedPreferences.getBoolean("update_enabled", true);
                }

                @Override
                public void onCheckChanged(boolean checked, Context ctx) {
                    sharedPreferences.edit().putBoolean("update_enabled", checked).apply();
                }
            });

        settings.add(new SettingStruct("Invert gestures", "Swap long press and click functions", "App Settings", SettingStruct.TYPE_TOGGLE) {
            @Override
            public void onCheckChanged(boolean checked, Context ctx) {
                sharedPreferences.edit().putBoolean("invert_click", checked).apply();
            }

            @Override
            public boolean onAttach(Context ctx) {
                return sharedPreferences.getBoolean("invert_click", false);
            }
        });

        settings.add(new SettingStruct("Enable on lockscreen", "Show the island even when the device is locked", "App Settings", SettingStruct.TYPE_TOGGLE) {
            @Override
            public boolean onAttach(Context ctx) {
                return sharedPreferences.getBoolean("enable_on_lockscreen", false);
            }

            @Override
            public void onCheckChanged(boolean checked, Context ctx) {
                sharedPreferences.edit().putBoolean("enable_on_lockscreen", checked).apply();
            }
        });

        settings.add(new SettingStruct("Copy crash logs", "Automatically copy errors to clipboard", "App Settings") {
            @Override
            public boolean onAttach(Context ctx) {
                return sharedPreferences.getBoolean("clip_copy_enabled", true);
            }

            @Override
            public void onCheckChanged(boolean checked, Context ctx) {
                sharedPreferences.edit().putBoolean("clip_copy_enabled", checked).apply();
            }
        });

        ExportedPlugins.getPlugins().forEach(x -> {
            settings.add(null);
            settings.add(new SettingStruct("Enable " + x.getName() + " Plugin", "Toggle " + x.getName() + " functionality", x.getName() + " Plugin Settings") {
                             @Override
                             public boolean onAttach(Context ctx) {
                                 return sharedPreferences.getBoolean(x.getID() + "_enabled", true);
                             }

                             @Override
                             public void onCheckChanged(boolean checked, Context ctx) {
                                 sharedPreferences.edit().putBoolean(x.getID() + "_enabled", checked).apply();
                             }
                         }
            );
            if (x.getSettings() != null) {
                settings.addAll(x.getSettings());
            }
        });
        RecylerViewSettingsAdapter adapter = new RecylerViewSettingsAdapter(this, settings);
        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(new ItemDecoration());
        recyclerView.invalidateItemDecorations();
        if (sharedPreferences.getBoolean("update_enabled", true) && BuildConfig.AUTO_UPDATE)
            startService(new Intent(this, UpdaterService.class));
        if (BuildConfig.AUTO_UPDATE) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(broadcastReceiver, new IntentFilter(getPackageName() + ".UPDATE_AVAIL"), Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(broadcastReceiver, new IntentFilter(getPackageName() + ".UPDATE_AVAIL"));
            }
        }

    }

    private RecyclerView recyclerView;

    @Override
    protected void onResume() {
        super.onResume();
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        if (recyclerView != null && recyclerView.getAdapter() != null) {
            recyclerView.getAdapter().notifyDataSetChanged();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);

    }

    private void init() {
        if (sharedPreferences == null) {

            sharedPreferences = getSharedPreferences(getPackageName(), MODE_PRIVATE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        try {
            unregisterReceiver(broadcastReceiver);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        Intent intent = new Intent(getPackageName() + ".SETTINGS_CHANGED");
        Bundle b = new Bundle();
        sharedPreferences.getAll().forEach((key, value) -> {
            if (value instanceof Boolean) {
                b.putBoolean(key, (boolean) value);
            } else if (value instanceof Float) {
                b.putFloat(key, (float) value);
            } else if (value instanceof Integer) {
                b.putInt(key, (int) value);
            } else if (value instanceof String) {
                b.putString(key, (String) value);
            }
        });
        intent.putExtra("settings", b);
        sendBroadcast(intent);
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(getPackageName() + ".UPDATE_AVAIL")) {
                new MaterialAlertDialogBuilder(MainActivity.this).setTitle("New Update Available")
                        .setMessage("We would like to update this app from " + BuildConfig.VERSION_NAME + " --> " + intent.getExtras().getString("version") +
                                ".\n\nUpdating app generally means better and more stable experience.")
                        .setCancelable(false)
                        .setNegativeButton("Later", (dialogInterface, i) -> {
                            dialogInterface.dismiss();
                        })
                        .setPositiveButton("Update Now", ((dialogInterface, i) -> {
                            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                                MainActivity.this.sendBroadcast(new Intent(getPackageName() + ".START_UPDATE"));
                                Toast.makeText(MainActivity.this, "Updating in background! Please don't kill the app", Toast.LENGTH_SHORT).show();
                            } else
                                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 102);
                            dialogInterface.dismiss();
                            if (!getPackageManager().canRequestPackageInstalls()) {
                                startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                                        .setData(Uri.parse(String.format("package:%s", getPackageName()))), 103);
                                Toast.makeText(MainActivity.this, "Please provide install access to update the application.", Toast.LENGTH_SHORT).show();
                            }
                        }))
                        .show();
            }
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 102 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            MainActivity.this.sendBroadcast(new Intent(getPackageName() + ".START_UPDATE"));
            Toast.makeText(MainActivity.this, "Updating in background! Please don't kill the app", Toast.LENGTH_SHORT).show();
        }
    }

    private int dpToInt(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        return true;
    }

    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } else {
                Toast.makeText(this, "Battery optimization is already disabled", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public class ItemDecoration extends RecyclerView.ItemDecoration {
        @SuppressLint("UseCompatLoadingForDrawables")
        @Override
        public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int backgroundColor = MaterialColors.getColor(MainActivity.this, com.google.android.material.R.attr.colorSurfaceVariant, getColor(R.color.md_theme_dark_surfaceVariant));
            int dividerColor = MaterialColors.getColor(MainActivity.this, com.google.android.material.R.attr.colorOutline, Color.GRAY);
            
            int childCount = parent.getChildCount();
            if (childCount == 0) return;

            Paint bgPaint = new Paint();
            bgPaint.setColor(backgroundColor);
            bgPaint.setAntiAlias(true);
            
            Paint dividerPaint = new Paint();
            dividerPaint.setColor(dividerColor);
            dividerPaint.setStrokeWidth(dpToInt(1));
            dividerPaint.setAlpha(40); // Subtle divider

            for (int i = 0; i < childCount; i++) {
                View child = parent.getChildAt(i);
                RecylerViewSettingsAdapter.ViewHolder viewHolder = (RecylerViewSettingsAdapter.ViewHolder) parent.getChildViewHolder(child);
                int position = parent.getChildAdapterPosition(child);

                if (viewHolder == null || !viewHolder.isItem) continue;

                int left = child.getLeft();
                int right = child.getRight();
                int top = child.getTop();
                int bottom = child.getBottom();

                // Draw main background rect
                c.drawRect(left, top, right, bottom, bgPaint);

                // Rounded caps logic
                boolean isFirstInGroup = (position == 0 || settings.get(position - 1) == null);
                boolean isLastInGroup = (position == settings.size() - 1 || settings.get(position + 1) == null);

                if (isFirstInGroup) {
                    Drawable roundedTop = getDrawable(R.drawable.rounded_corner_setting_top);
                    if (roundedTop != null) {
                        // Draw the rounded cap with height matching the item vertical padding (12dp)
                        roundedTop.setBounds(left, top - dpToInt(12), right, top + dpToInt(1));
                        roundedTop.draw(c);
                    }
                }
                
                if (isLastInGroup) {
                    Drawable roundedBottom = getDrawable(R.drawable.rounded_corner_setting_bottom);
                    if (roundedBottom != null) {
                        roundedBottom.setBounds(left, bottom - dpToInt(1), right, bottom + dpToInt(12));
                        roundedBottom.draw(c);
                    }
                } else {
                    // Draw divider if not last
                    float margin = dpToInt(20);
                    c.drawLine(left + margin, bottom, right - margin, bottom, dividerPaint);
                }
            }
        }
    }

    private void sendCrashNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        final String NOTIFICATION_CHANNEL_ID = getPackageName() + ".updater_channel";
        String channelName = "Updater Service";
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_MIN);
        manager.createNotificationChannel(chan);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(false)
                .setContentTitle("Dynamic Bob Crashed")
                .setContentText("Crash Log copied to clipboard")
                .setSmallIcon(R.drawable.launcher_foreground)
                .setPriority(NotificationManager.IMPORTANCE_MAX)
                .setCategory(Notification.CATEGORY_ERROR)
                .build();
        manager.notify(100, notification);
    }
}

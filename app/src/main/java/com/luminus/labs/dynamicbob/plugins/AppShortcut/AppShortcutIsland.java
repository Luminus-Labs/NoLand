package com.luminus.labs.dynamicbob.plugins.AppShortcut;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.luminus.labs.dynamicbob.R;
import com.luminus.labs.dynamicbob.plugins.BasePlugin;
import com.luminus.labs.dynamicbob.services.OverlayService;
import com.luminus.labs.dynamicbob.utils.CallBack;
import com.luminus.labs.dynamicbob.utils.SettingStruct;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppShortcutIsland extends BasePlugin {
    private OverlayService overlayService;
    private SharedPreferences sharedPreferences;
    private LinearLayout appRow;
    private List<AppInfo> selectedApps;
    private ConstraintLayout containerView;
    private static final String PREF_SELECTED_APPS = "app_shortcut_selected_apps";
    private static final int MAX_SHORTCUTS = 6;

    @Override
    public String getID() {
        return "app_shortcut_island";
    }

    @Override
    public String getName() {
        return "App Shortcuts";
    }

    @Override
    public void onCreate(OverlayService context) {
        this.overlayService = context;
        this.sharedPreferences = context.getSharedPreferences(
                context.getPackageName(), Context.MODE_PRIVATE);
        selectedApps = new ArrayList<>();
        loadSelectedApps();
    }

    @Override
    public View onBind() {
        LayoutInflater inflater = LayoutInflater.from(overlayService);
        containerView = (ConstraintLayout) inflater.inflate(
                R.layout.app_shortcut_island_layout, null);
        containerView.setId(R.id.binded);
        
        appRow = containerView.findViewById(R.id.app_row);
        
        if (selectedApps.isEmpty()) {
            selectedApps = getDefaultApps();
        }

        for (AppInfo app : selectedApps) {
            View itemView = inflater.inflate(R.layout.app_shortcut_grid_item, appRow, false);
            ((ImageView) itemView.findViewById(R.id.app_icon)).setImageDrawable(app.icon);
            ((TextView) itemView.findViewById(R.id.app_label)).setText(app.appName);
            itemView.setOnClickListener(v -> {
                launchApp(app.packageName);
                overlayService.dequeue(this);
            });
            itemView.setAlpha(0);
            appRow.addView(itemView);
        }
        
        return containerView;
    }

    @Override
    public void onUnbind() {
        if (appRow != null) {
            appRow.removeAllViews();
        }
    }

    @Override
    public void onDestroy() {
        // Cleanup
    }

    @Override
    public void onBindComplete() {
        super.onBindComplete();
        
        containerView.post(() -> {
            // Measure the target size required for the grid
            int widthSpec = View.MeasureSpec.makeMeasureSpec(overlayService.metrics.widthPixels, View.MeasureSpec.AT_MOST);
            int heightSpec = View.MeasureSpec.makeMeasureSpec(overlayService.metrics.heightPixels, View.MeasureSpec.AT_MOST);
            containerView.measure(widthSpec, heightSpec);
            
            int targetW = containerView.getMeasuredWidth();
            int targetH = containerView.getMeasuredHeight();

            // Animate from small to the measured size
            overlayService.animateOverlay(
                    targetH,
                    targetW,
                    true, // Set to true to center the expansion
                    new CallBack(),
                    new CallBack() {
                        @Override
                        public void onFinish() {
                            for (int i = 0; i < appRow.getChildCount(); i++) {
                                View child = appRow.getChildAt(i);
                                child.animate().alpha(1).setDuration(200).setStartDelay(i * 40L).start();
                            }
                            // After animation, allow it to be wrap_content for dynamic updates
                            WindowManager.LayoutParams params = (WindowManager.LayoutParams) overlayService.mView.getLayoutParams();
                            params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                            overlayService.mWindowManager.updateViewLayout(overlayService.mView, params);
                        }
                    },
                    false
            );
        });
    }

    @Override
    public void onExpand() {
        openAppPicker(overlayService);
    }

    @Override
    public void onCollapse() {
        overlayService.dequeue(this);
    }

    @Override
    public void onClick() {
        // Single click opens app picker
        openAppPicker(overlayService);
    }

    @Override
    public String[] permissionsRequired() {
        return new String[]{
                "android.permission.QUERY_ALL_PACKAGES"
        };
    }

    @Override
    public ArrayList<SettingStruct> getSettings() {
        ArrayList<SettingStruct> settings = new ArrayList<>();
        settings.add(new SettingStruct("Configure App Shortcuts", "App Shortcuts", SettingStruct.TYPE_CUSTOM) {
            @Override
            public void onClick(Context c) {
                openAppPicker(c);
            }
        });
        return settings;
    }

    private void openAppPicker(Context context) {
        Intent intent = new Intent(context, AppShortcutPickerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private void launchApp(String packageName) {
        try {
            Intent intent = overlayService.getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                overlayService.startActivity(intent);
            } else {
                Toast.makeText(overlayService, "Cannot launch app", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(overlayService, "Error launching app", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void loadSelectedApps() {
        Set<String> savedApps = sharedPreferences.getStringSet(PREF_SELECTED_APPS, new HashSet<>());
        PackageManager pm = overlayService.getPackageManager();
        
        selectedApps.clear();
        for (String packageName : savedApps) {
            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                String appName = pm.getApplicationLabel(appInfo).toString();
                Drawable icon = pm.getApplicationIcon(appInfo);
                selectedApps.add(new AppInfo(appName, packageName, icon));
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private List<AppInfo> getDefaultApps() {
        List<AppInfo> defaultApps = new ArrayList<>();
        PackageManager pm = overlayService.getPackageManager();
        
        // Add some common default apps if available
        String[] defaultPackages = {
                "com.android.chrome",
                "com.whatsapp",
                "com.google.android.apps.messaging",
                "com.spotify.music",
                "com.google.android.gms",
                "com.google.android.apps.maps"
        };
        
        for (String packageName : defaultPackages) {
            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                String appName = pm.getApplicationLabel(appInfo).toString();
                Drawable icon = pm.getApplicationIcon(appInfo);
                defaultApps.add(new AppInfo(appName, packageName, icon));
                if (defaultApps.size() >= MAX_SHORTCUTS) break;
            } catch (PackageManager.NameNotFoundException e) {
                // App not installed
            }
        }
        
        return defaultApps;
    }

    public static class AppInfo {
        public String appName;
        public String packageName;
        public Drawable icon;

        public AppInfo(String appName, String packageName, Drawable icon) {
            this.appName = appName;
            this.packageName = packageName;
            this.icon = icon;
        }
    }
}

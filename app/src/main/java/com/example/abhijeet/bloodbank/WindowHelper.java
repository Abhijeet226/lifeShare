package com.example.abhijeet.bloodbank;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class WindowHelper {

    public static void applyEdgeToEdge(Activity activity) {
        if (activity == null) return;
        Window window = activity.getWindow();
        if (window == null) return;

        // 1. Enable true edge-to-edge window drawing
        WindowCompat.setDecorFitsSystemWindows(window, false);

        // 2. Set fully transparent status and navigation bars
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        // 3. Disable OS scrims / contrast enforcement on Android 10+ (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }

        boolean isDarkMode = (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;

        // 4. Status and Navigation bar icons contrast: dark icons in Light Mode, light icons in Dark Mode
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(window, window.getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(!isDarkMode);
            insetsController.setAppearanceLightNavigationBars(!isDarkMode);
        }
    }

    public static void applySystemBarInsets(View view) {
        if (view == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            Insets navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars());

            v.setPadding(
                    v.getPaddingLeft(),
                    statusBarInsets.top,
                    v.getPaddingRight(),
                    navBarInsets.bottom
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    public static void updateStatusBarForTab(Activity activity, int tab) {
        if (activity == null) return;
        Window window = activity.getWindow();
        if (window == null) return;

        boolean isDarkMode = (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;

        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(window, window.getDecorView());
        if (insetsController == null) return;

        // Keep system bars transparent for true edge-to-edge
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }

        if (tab == 0 /* TAB_HOME */) {
            // Home hero banner: Light icons on red hero in light mode, light icons in dark mode
            insetsController.setAppearanceLightStatusBars(false);
        } else {
            // Search, Emergency, Profile: Dark icons in Light Mode, light icons in Dark Mode
            insetsController.setAppearanceLightStatusBars(!isDarkMode);
        }
        insetsController.setAppearanceLightNavigationBars(!isDarkMode);
    }
}

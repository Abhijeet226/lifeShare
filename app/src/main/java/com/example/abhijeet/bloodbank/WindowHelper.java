package com.example.abhijeet.bloodbank;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
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

        // 1. Enable modern edge-to-edge window drawing
        WindowCompat.setDecorFitsSystemWindows(window, false);

        // 2. Set fully transparent status and navigation bars
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        boolean isDarkMode = (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;

        // 3. Status bar icons contrast: dark icons in Light Mode, light icons in Dark Mode
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(window, window.getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(!isDarkMode);
            insetsController.setAppearanceLightNavigationBars(!isDarkMode);
        }

        // 4. Dynamic Window Insets Handling on Activity content root (Single Source of Truth)
        View contentView = window.findViewById(android.R.id.content);
        if (contentView != null) {
            applySystemBarInsets(contentView);
        }
    }

    public static void applySystemBarInsets(View view) {
        if (view == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            Insets navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars());

            // Apply status bar height as top padding so content never clips under the notch/status bar
            // Apply navigation bar height as bottom padding so bottom docks stay above gesture bar
            v.setPadding(0, statusBarInsets.top, 0, navBarInsets.bottom);
            return WindowInsetsCompat.CONSUMED;
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

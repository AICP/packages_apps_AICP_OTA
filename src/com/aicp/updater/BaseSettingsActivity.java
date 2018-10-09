package com.aicp.updater;

import android.content.res.TypedArray;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.provider.Settings;
import android.view.View;

public class BaseSettingsActivity extends PreferenceActivity {

    private int mThemeRes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mThemeRes = getThemeRes();
        setTheme(mThemeRes);
        super.onCreate(savedInstanceState);
        fixStatusBarFg();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mThemeRes != getThemeRes()) {
            recreate();
        }
    }

    protected int getThemeRes() {
        int pref = Settings.System.getInt(getContentResolver(), Settings.System.AE_THEME, 0);
        switch (pref) {
            /*
            case 1:
                return R.style.AppTheme_DarkAmber;
            */
            case 2:
            case 4:
                return R.style.AppTheme_Light;
            case 3:
            case 5:
                return R.style.AppTheme_Dark;
            default:
                return R.style.AppTheme_Default;
        }
    }

    /**
     * When changing from a theme with light to one with dark status bar, recreating
     * the activity seems to be not enough to update status bar foreground color,
     * so it's black on black.
     * This is a workaround for that, basically adapted from Launcher3's dynamic
     * status bar color (fg color changing when opening/closing drawer).
     */
    private void fixStatusBarFg() {
        int oldSystemUiFlags = getWindow().getDecorView().getSystemUiVisibility();
        int newSystemUiFlags = oldSystemUiFlags;
        int[] attrs = new int[] {
                android.R.attr.windowLightStatusBar,
                android.R.attr.windowLightNavigationBar,
        };
        TypedArray ta = getTheme().obtainStyledAttributes(attrs);
        boolean lightStatusBar = ta.getBoolean(0, false);
        boolean lightNavigationBar = ta.getBoolean(1, false);
        ta.recycle();
        if (lightStatusBar) {
            newSystemUiFlags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        } else {
            newSystemUiFlags &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        if (lightNavigationBar) {
            newSystemUiFlags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            newSystemUiFlags &= ~(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }

        if (newSystemUiFlags != oldSystemUiFlags) {
            getWindow().getDecorView().setSystemUiVisibility(newSystemUiFlags);
        }
    }
}

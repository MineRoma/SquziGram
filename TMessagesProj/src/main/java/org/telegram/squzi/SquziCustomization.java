package org.telegram.squzi;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.ActionBar.Theme;

// SquziGram: кастомизация оформления.
// Акцент — лёгкий слой поверх Theme.getColor: подменяет curated-набор
// акцентных ключей любым цветом, применяется вживую через
// needSetDayNightTheme. Аватарки — перехват BackupImageView.setRoundRadius.
public class SquziCustomization {

    private static final String PREFS = "squzi_custom";

    private static final String KEY_ACCENT_ON = "accent_on";
    private static final String KEY_ACCENT_COLOR = "accent_color";
    private static final String KEY_AVATAR_MODE = "avatar_mode";
    private static final String KEY_AVATAR_RADIUS = "avatar_radius_dp";

    public static final int DEFAULT_ACCENT = 0xFF3390EC;

    public static final int AVATAR_SYSTEM = 0;
    public static final int AVATAR_SQUARE = 1;
    public static final int AVATAR_CIRCLE = 2;
    public static final int AVATAR_CUSTOM = 3;

    // Ключи, которые красит кастомный акцент.
    public static final int[] ACCENT_KEYS = {
            Theme.key_dialogButton,
            Theme.key_progressCircle,
            Theme.key_windowBackgroundWhiteBlueText,
            Theme.key_windowBackgroundWhiteBlueButton,
            Theme.key_windowBackgroundWhiteBlueIcon,
            Theme.key_windowBackgroundWhiteBlueHeader,
            Theme.key_switchTrackChecked,
            Theme.key_checkbox,
            Theme.key_checkboxCheck,
            Theme.key_chat_messagePanelSend,
            Theme.key_featuredStickers_addButton,
            Theme.key_featuredStickers_addButtonPressed,
    };

    public static final int[] PRESET_COLORS = {
            0xFF3390EC, 0xFF7B61FF, 0xFF4CAF50, 0xFFF44336,
            0xFFFF9800, 0xFFE91E63, 0xFF009688, 0xFF00BCD4,
            0xFFFFC107, 0xFF9C27B0, 0xFF795548, 0xFF607D8B,
            0xFF212121, 0xFF03A9F4, 0xFF8BC34A, 0xFFFF5722,
    };

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // -- акцент -----------------------------------------------------------

    public static boolean isAccentOn() {
        try {
            return prefs().getBoolean(KEY_ACCENT_ON, false);
        } catch (Throwable t) {
            return false;
        }
    }

    public static int getAccentColor() {
        try {
            return prefs().getInt(KEY_ACCENT_COLOR, DEFAULT_ACCENT);
        } catch (Throwable t) {
            return DEFAULT_ACCENT;
        }
    }

    public static void setAccent(boolean on, int color) {
        try {
            prefs().edit().putBoolean(KEY_ACCENT_ON, on).putInt(KEY_ACCENT_COLOR, color).apply();
        } catch (Throwable t) {
            FileLog.e(t);
        }
        refreshTheme();
    }

    // Null = без подмены. Вызывается из Theme.getColor.
    public static Integer overrideColor(int key) {
        if (!isAccentOn()) {
            return null;
        }
        int accent = getAccentColor();
        for (int accentKey : ACCENT_KEYS) {
            if (accentKey == key) {
                if (key == Theme.key_featuredStickers_addButtonPressed) {
                    return darken(accent);
                }
                return accent;
            }
        }
        return null;
    }

    private static int darken(int color) {
        int a = (color >> 24) & 0xFF;
        int r = (int) ((((color >> 16) & 0xFF)) * 0.85f);
        int g = (int) ((((color >> 8) & 0xFF)) * 0.85f);
        int b = (int) ((color & 0xFF) * 0.85f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static void refreshTheme() {
        try {
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    Theme.refreshThemeColors();
                } catch (Throwable ignored) {
                }
                try {
                    NotificationCenter.getGlobalInstance().postNotificationName(
                            NotificationCenter.needSetDayNightTheme,
                            Theme.getActiveTheme(), Theme.isCurrentThemeDark(), null, -1);
                } catch (Throwable ignored) {
                }
                try {
                    NotificationCenter.getGlobalInstance().postNotificationName(
                            NotificationCenter.didSetNewTheme, false, true);
                } catch (Throwable ignored) {
                }
                try {
                    NotificationCenter.getGlobalInstance().postNotificationName(
                            NotificationCenter.dialogsNeedReload);
                } catch (Throwable ignored) {
                }
                try {
                    if (org.telegram.ui.LaunchActivity.instance != null) {
                        org.telegram.ui.LaunchActivity.instance.rebuildAllFragments(true);
                        org.telegram.ui.LaunchActivity.instance.checkSystemBarColors(false, true, true);
                    }
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public static String colorToHex(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }

    public static Integer parseHex(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        if (t.startsWith("#")) {
            t = t.substring(1);
        }
        if (t.length() != 6 && t.length() != 8) {
            return null;
        }
        try {
            long v = Long.parseLong(t, 16);
            if (t.length() == 6) {
                v |= 0xFF000000L;
            }
            return (int) v;
        } catch (Throwable e) {
            return null;
        }
    }

    // -- аватарки ---------------------------------------------------------

    public static int avatarMode() {
        try {
            return prefs().getInt(KEY_AVATAR_MODE, AVATAR_SYSTEM);
        } catch (Throwable t) {
            return AVATAR_SYSTEM;
        }
    }

    public static int avatarRadiusDp() {
        try {
            return prefs().getInt(KEY_AVATAR_RADIUS, 8);
        } catch (Throwable t) {
            return 8;
        }
    }

    public static void setAvatar(int mode, int radiusDp) {
        try {
            prefs().edit().putInt(KEY_AVATAR_MODE, mode)
                    .putInt(KEY_AVATAR_RADIUS, Math.max(0, Math.min(30, radiusDp))).apply();
        } catch (Throwable t) {
            FileLog.e(t);
        }
        refreshLists();
    }

    public static String avatarModeName(int mode) {
        switch (mode) {
            case AVATAR_SQUARE:
                return "Квадратные";
            case AVATAR_CIRCLE:
                return "Круглые";
            case AVATAR_CUSTOM:
                return "Свой радиус (" + avatarRadiusDp() + "dp)";
            default:
                return "Как в Telegram";
        }
    }

    // Вызывается из BackupImageView.setRoundRadius.
    public static int avatarRadius(int originalPx) {
        int mode = avatarMode();
        try {
            if (mode == AVATAR_SQUARE) {
                return AndroidUtilities.dp(6);
            } else if (mode == AVATAR_CIRCLE) {
                return 100000;
            } else if (mode == AVATAR_CUSTOM) {
                return AndroidUtilities.dp(Math.max(0, Math.min(30, avatarRadiusDp())));
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return originalPx;
    }

    public static void refreshLists() {
        try {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }
}

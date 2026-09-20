package org.telegram.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.squzi.SquziCustomization;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

// SquziGram: диалог выбора цветового акцента и скругления аватарок.
public class SquziAppearance {

    public static void showAccentDialog(final BaseFragment fragment, final Runnable onDone) {
        final Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8), AndroidUtilities.dp(20), AndroidUtilities.dp(4));

        int[] presets = SquziCustomization.PRESET_COLORS;
        LinearLayout row = null;
        for (int i = 0; i < presets.length; i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_HORIZONTAL);
                content.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
            }
            final int color = presets[i];
            FrameLayout swatch = new FrameLayout(context);
            swatch.setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(44), color));
            swatch.setOnClickListener(v -> {
                SquziCustomization.setAccent(true, color);
                if (onDone != null) {
                    try {
                        onDone.run();
                    } catch (Throwable ignored) {
                    }
                }
                try {
                    fragment.dismissCurrentDialog();
                } catch (Throwable ignored) {
                }
            });
            row.addView(swatch, LayoutHelper.createLinear(44, 44, 0, 0, 12, 0));
        }

        LinearLayout hexRow = new LinearLayout(context);
        hexRow.setOrientation(LinearLayout.HORIZONTAL);
        hexRow.setGravity(Gravity.CENTER_VERTICAL);
        final EditText hexInput = new EditText(context);
        hexInput.setHint("#RRGGBB");
        hexInput.setText(SquziCustomization.colorToHex(SquziCustomization.getAccentColor()));
        hexInput.setTextSize(15);
        hexInput.setSingleLine(true);
        hexInput.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, fragment.getResourceProvider()));
        hexRow.addView(hexInput, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 8, 0));
        TextView applyHex = new TextView(context);
        applyHex.setText("OK");
        applyHex.setTextSize(15);
        applyHex.setTypeface(AndroidUtilities.bold());
        applyHex.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, fragment.getResourceProvider()));
        applyHex.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8));
        applyHex.setOnClickListener(v -> {
            Integer parsed = SquziCustomization.parseHex(hexInput.getText().toString());
            if (parsed == null) {
                try {
                    Toast.makeText(context, "Формат: #RRGGBB", Toast.LENGTH_SHORT).show();
                } catch (Throwable ignored) {
                }
                return;
            }
            SquziCustomization.setAccent(true, parsed);
            if (onDone != null) {
                try {
                    onDone.run();
                } catch (Throwable ignored) {
                }
            }
            try {
                fragment.dismissCurrentDialog();
            } catch (Throwable ignored) {
            }
        });
        hexRow.addView(applyHex, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8, 0, 0, 0));
        content.addView(hexRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

        AlertDialog.Builder builder = new AlertDialog.Builder(context, fragment.getResourceProvider());
        builder.setTitle("Цветовой акцент");
        builder.setView(content);
        builder.setPositiveButton("Готово", null);
        builder.setNegativeButton("Выключить", (dialog, which) -> {
            SquziCustomization.setAccent(false, SquziCustomization.getAccentColor());
            if (onDone != null) {
                try {
                    onDone.run();
                } catch (Throwable ignored) {
                }
            }
        });
        try {
            fragment.showDialog(builder.create());
        } catch (Throwable ignored) {
        }
    }

    public static void showAvatarDialog(final BaseFragment fragment, final Runnable onDone) {
        final Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final CharSequence[] items = new CharSequence[]{
                "Как в Telegram",
                "Квадратные",
                "Круглые",
                "Свой радиус…"
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(context, fragment.getResourceProvider());
        builder.setTitle("Скругление аватарок");
        builder.setItems(items, (dialog, which) -> {
            if (which == 3) {
                showCustomRadiusDialog(fragment, onDone);
                return;
            }
            SquziCustomization.setAvatar(which, SquziCustomization.avatarRadiusDp());
            if (onDone != null) {
                onDone.run();
            }
            try {
                Toast.makeText(context, "Полностью применится после перезапуска", Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {
            }
        });
        try {
            fragment.showDialog(builder.create());
        } catch (Throwable ignored) {
        }
    }

    private static void showCustomRadiusDialog(final BaseFragment fragment, final Runnable onDone) {
        final Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        FrameLayout wrapper = new FrameLayout(context);
        wrapper.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));
        final EditText edit = new EditText(context);
        edit.setText(String.valueOf(SquziCustomization.avatarRadiusDp()));
        edit.setHint("0–30 dp");
        edit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        edit.setTextSize(16);
        edit.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, fragment.getResourceProvider()));
        wrapper.addView(edit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(context, fragment.getResourceProvider());
        builder.setTitle("Свой радиус");
        builder.setView(wrapper);
        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            int radius = SquziCustomization.avatarRadiusDp();
            try {
                String t = edit.getText().toString().trim();
                if (!TextUtils.isEmpty(t)) {
                    radius = Math.max(0, Math.min(30, Integer.parseInt(t)));
                }
            } catch (Throwable ignored) {
            }
            SquziCustomization.setAvatar(SquziCustomization.AVATAR_CUSTOM, radius);
            if (onDone != null) {
                onDone.run();
            }
        });
        builder.setNegativeButton("Отмена", null);
        try {
            fragment.showDialog(builder.create());
        } catch (Throwable ignored) {
        }
    }
}

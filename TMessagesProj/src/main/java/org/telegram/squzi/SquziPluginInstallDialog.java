package org.telegram.squzi;

import android.app.Activity;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;

// SquziGram: окно установки плагина. Открывается по тапу на .plugin-файл
// в чате (перехват в AndroidUtilities.openForView). Показывает иконку,
// название, описание, автора и версию + кнопку установки — как в exteraGram.
public class SquziPluginInstallDialog {

    // Вызывается из AndroidUtilities.openForView. Возвращает true, если файл
    // обработан нами (диалог показан), тогда стандартное открытие не нужно.
    public static boolean handleFile(Activity activity, Theme.ResourcesProvider resourcesProvider, File file, String fileName) {
        if (activity == null || activity.isFinishing() || file == null || !file.exists()) {
            return false;
        }
        if (fileName == null || !fileName.toLowerCase().endsWith(".plugin")) {
            return false;
        }
        try {
            SquziPlugin meta = SquziPlugin.parse(file);
            show(activity, resourcesProvider, file, meta);
            return true;
        } catch (Throwable t) {
            FileLog.e(t);
            return false;
        }
    }

    private static void show(final Activity activity, Theme.ResourcesProvider resourcesProvider, final File file, final SquziPlugin meta) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(16), dp(24), dp(8));

        ImageView iconView = new ImageView(activity);
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iconView.setImageResource(R.drawable.settings_features);
        iconView.setBackground(Theme.createCircleDrawable(dp(64), 0xFF7B61FF));
        iconView.setPadding(dp(14), dp(14), dp(14), dp(14));
        content.addView(iconView, LayoutHelper.createLinear(64, 64, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));

        TextView nameView = new TextView(activity);
        nameView.setText(meta.name);
        nameView.setTextSize(17);
        nameView.setGravity(Gravity.CENTER);
        nameView.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
        nameView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        content.addView(nameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0));

        StringBuilder info = new StringBuilder();
        if (!TextUtils.isEmpty(meta.author)) {
            info.append(meta.author);
        }
        if (!TextUtils.isEmpty(meta.version)) {
            if (info.length() > 0) {
                info.append(" • ");
            }
            info.append("v").append(meta.version);
        }
        if (info.length() > 0) {
            TextView infoView = new TextView(activity);
            infoView.setText(info.toString());
            infoView.setTextSize(13);
            infoView.setGravity(Gravity.CENTER);
            infoView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider));
            content.addView(infoView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));
        }

        if (!TextUtils.isEmpty(meta.description)) {
            TextView descView = new TextView(activity);
            descView.setText(meta.description);
            descView.setTextSize(14);
            descView.setGravity(Gravity.CENTER);
            descView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            content.addView(descView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));
        }

        String warning = versionWarning(meta);
        if (warning != null) {
            TextView warnView = new TextView(activity);
            warnView.setText(warning);
            warnView.setTextSize(12);
            warnView.setGravity(Gravity.CENTER);
            warnView.setTextColor(0xFFE77512);
            content.addView(warnView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));
        }

        final boolean alreadyInstalled = SquziPluginsController.isInstalled(meta.id);
        if (alreadyInstalled) {
            TextView installedView = new TextView(activity);
            installedView.setText("Уже установлен — повторная установка обновит файл");
            installedView.setTextSize(12);
            installedView.setGravity(Gravity.CENTER);
            installedView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider));
            content.addView(installedView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity, resourcesProvider);
        builder.setTitle("Установка плагина");
        builder.setView(content);
        builder.setPositiveButton(alreadyInstalled ? "Обновить" : "Установить", (dialog, which) -> {
            String id = SquziPluginsController.install(file, meta);
            if (id != null) {
                Toast.makeText(activity, "Плагин «" + meta.name + "» установлен", Toast.LENGTH_SHORT).show();
                try {
                    SquziPluginRuntime.onPluginInstalled(id);
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            } else {
                Toast.makeText(activity, "Не удалось установить плагин", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Отмена", null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private static int dp(float value) {
        return org.telegram.messenger.AndroidUtilities.dp(value);
    }

    private static String versionWarning(SquziPlugin meta) {
        try {
            if (!TextUtils.isEmpty(meta.appVersion)
                    && !SquziPlugin.checkVersion(meta.appVersion, org.telegram.messenger.BuildVars.BUILD_VERSION_STRING)) {
                return "Внимание: требует app " + meta.appVersion + " (у нас " + org.telegram.messenger.BuildVars.BUILD_VERSION_STRING + ")";
            }
            if (!TextUtils.isEmpty(meta.requirements)) {
                return "Зависимости pip: " + meta.requirements + " (ставятся вручную)";
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // Заглушка для иконки из __icon__ (формат exteraGram "PackShortName/index"):
    // грузить стикеры из сети на этом этапе не будем, показываем стандартную иконку.
    // Подхват живых иконок — следующим этапом вместе с движком.
    @SuppressWarnings("unused")
    private static FrameLayout.LayoutParams iconParams() {
        return LayoutHelper.createFrame(64, 64, Gravity.CENTER_HORIZONTAL);
    }
}

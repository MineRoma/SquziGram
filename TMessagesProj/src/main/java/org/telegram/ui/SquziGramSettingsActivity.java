package org.telegram.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.squzi.SquziPlugin;
import org.telegram.squzi.SquziPluginsController;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.List;

// SquziGram: отдельный экран настроек + менеджер плагинов (формат exteraGram).
// Тап по плагину — вкл/выкл, долгое нажатие — удалить.
public class SquziGramSettingsActivity extends BaseFragment {

    private LinearLayout pluginsContainer;
    private LinearLayout appearanceContainer;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("SquziGram");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        ScrollView scrollView = new ScrollView(context);
        contentView.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView title = new TextView(context);
        title.setText("SquziGram");
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        container.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 16, 32, 16, 0));

        TextView header = new TextView(context);
        header.setText("Оформление");
        header.setTextSize(14);
        header.setTypeface(AndroidUtilities.bold());
        header.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        container.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 24, 20, 8));

        appearanceContainer = new LinearLayout(context);
        appearanceContainer.setOrientation(LinearLayout.VERTICAL);
        container.addView(appearanceContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

        TextView pluginsHeader = new TextView(context);
        pluginsHeader.setText("Плагины");
        pluginsHeader.setTextSize(14);
        pluginsHeader.setTypeface(AndroidUtilities.bold());
        pluginsHeader.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        container.addView(pluginsHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 24, 20, 8));

        pluginsContainer = new LinearLayout(context);
        pluginsContainer.setOrientation(LinearLayout.VERTICAL);
        container.addView(pluginsContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

        TextView hint = new TextView(context);
        hint.setText("Чтобы установить плагин: отправь .plugin-файл себе в Избранное и нажми на него.\nТап по плагину — детали, настройки, вкл/выкл и удаление.");
        hint.setTextSize(13);
        hint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        container.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 12, 20, 32));

        fillPlugins();
        fillAppearance();

        fragmentView = contentView;
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        fillPlugins();
        fillAppearance();
    }

    private void fillPlugins() {
        if (pluginsContainer == null) {
            return;
        }
        Context context = pluginsContainer.getContext();
        pluginsContainer.removeAllViews();
        List<SquziPlugin> plugins;
        try {
            plugins = SquziPluginsController.getInstalled();
        } catch (Throwable t) {
            plugins = null;
        }
        if (plugins == null || plugins.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("Плагинов пока нет");
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            empty.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            empty.setPadding(0, AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14));
            pluginsContainer.addView(empty, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 0, 12, 0));
            return;
        }
        for (int i = 0; i < plugins.size(); i++) {
            final SquziPlugin plugin = plugins.get(i);
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));

            TextView nameView = new TextView(context);
            StringBuilder title = new StringBuilder(plugin.name);
            if (!TextUtils.isEmpty(plugin.version)) {
                title.append("  v").append(plugin.version);
            }
            nameView.setText(title.toString());
            nameView.setTextSize(16);
            nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            row.addView(nameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            StringBuilder sub = new StringBuilder(plugin.enabled ? "Включён" : "Выключен");
            if (!TextUtils.isEmpty(plugin.author)) {
                sub.append(" • ").append(plugin.author);
            }
            if (!TextUtils.isEmpty(plugin.description)) {
                sub.append("\n").append(plugin.description);
            }
            TextView subView = new TextView(context);
            subView.setText(sub.toString());
            subView.setTextSize(13);
            subView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            row.addView(subView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

            row.setOnClickListener(v -> {
                try {
                    presentFragment(SquziPluginDetailActivity.create(plugin.id));
                } catch (Throwable ignored) {
                }
            });
            row.setOnLongClickListener(v -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), resourceProvider);
                builder.setTitle("Удалить плагин?");
                builder.setMessage("«" + plugin.name + "» будет удалён из SquziGram");
                builder.setPositiveButton("Удалить", (dialog, which) -> {
                    SquziPluginsController.delete(plugin.id);
                    try {
                        org.telegram.squzi.SquziPluginRuntime.onPluginDeleted(plugin.id);
                    } catch (Throwable ignored) {
                    }
                    fillPlugins();
                    try {
                        Toast.makeText(getParentActivity(), "Плагин удалён", Toast.LENGTH_SHORT).show();
                    } catch (Throwable ignored) {
                    }
                });
                builder.setNegativeButton("Отмена", null);
                try {
                    builder.create().show();
                } catch (Throwable ignored) {
                }
                return true;
            });

            pluginsContainer.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 0, 12, 1));
        }
    }

    private void fillAppearance() {
        if (appearanceContainer == null) {
            return;
        }
        Context context = appearanceContainer.getContext();
        appearanceContainer.removeAllViews();

        String accentValue;
        try {
            accentValue = org.telegram.squzi.SquziCustomization.isAccentOn()
                    ? org.telegram.squzi.SquziCustomization.colorToHex(org.telegram.squzi.SquziCustomization.getAccentColor())
                    : "Выкл";
        } catch (Throwable t) {
            accentValue = "Выкл";
        }
        LinearLayout accentRow = makeSimpleRow(context, "Цветовой акцент", accentValue);
        accentRow.setOnClickListener(v -> {
            SquziAppearance.showAccentDialog(SquziGramSettingsActivity.this);
            fillAppearance();
        });
        appearanceContainer.addView(accentRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 0, 12, 1));

        String avatarValue;
        try {
            avatarValue = org.telegram.squzi.SquziCustomization.avatarModeName(
                    org.telegram.squzi.SquziCustomization.avatarMode());
        } catch (Throwable t) {
            avatarValue = "Как в Telegram";
        }
        LinearLayout avatarRow = makeSimpleRow(context, "Скругление аватарок", avatarValue);
        avatarRow.setOnClickListener(v -> SquziAppearance.showAvatarDialog(SquziGramSettingsActivity.this, this::fillAppearance));
        appearanceContainer.addView(avatarRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 0, 12, 1));
    }

    private LinearLayout makeSimpleRow(Context context, String title, String value) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        row.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView valueView = new TextView(context);
        valueView.setText(value);
        valueView.setTextSize(13);
        valueView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        row.addView(valueView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        return row;
    }
}

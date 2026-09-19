package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.squzi.SquziPlugin;
import org.telegram.squzi.SquziPluginRuntime;
import org.telegram.squzi.SquziPluginsController;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

// SquziGram: детали плагина — описание, вкл/выкл, настройки плагина
// (create_settings), удаление.
public class SquziPluginDetailActivity extends BaseFragment {

    private String pluginId;
    private LinearLayout container;

    public static SquziPluginDetailActivity create(String pluginId) {
        SquziPluginDetailActivity fragment = new SquziPluginDetailActivity();
        Bundle args = new Bundle();
        args.putString("plugin_id", pluginId);
        fragment.setArguments(args);
        return fragment;
    }

    private SquziPlugin findPlugin() {
        if (pluginId == null) {
            return null;
        }
        try {
            for (SquziPlugin plugin : SquziPluginsController.getInstalled()) {
                if (pluginId.equals(plugin.id)) {
                    return plugin;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Override
    public View createView(Context context) {
        Bundle args = getArguments();
        pluginId = args != null ? args.getString("plugin_id") : null;

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Плагин");
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

        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        fill();

        fragmentView = contentView;
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        fill();
    }

    private void fill() {
        if (container == null) {
            return;
        }
        Context context = container.getContext();
        container.removeAllViews();

        SquziPlugin plugin = findPlugin();
        if (plugin == null) {
            try {
                Toast.makeText(getParentActivity(), "Плагин не найден", Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {
            }
            finishFragment();
            return;
        }

        TextView nameView = new TextView(context);
        nameView.setText(plugin.name);
        nameView.setTextSize(20);
        nameView.setGravity(Gravity.CENTER);
        nameView.setTypeface(AndroidUtilities.bold());
        nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        container.addView(nameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 16, 32, 16, 0));

        StringBuilder info = new StringBuilder();
        if (!TextUtils.isEmpty(plugin.author)) {
            info.append(plugin.author);
        }
        if (!TextUtils.isEmpty(plugin.version)) {
            if (info.length() > 0) {
                info.append(" • ");
            }
            info.append("v").append(plugin.version);
        }
        if (info.length() > 0) {
            TextView infoView = new TextView(context);
            infoView.setText(info.toString());
            infoView.setTextSize(13);
            infoView.setGravity(Gravity.CENTER);
            infoView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            container.addView(infoView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 16, 4, 16, 0));
        }

        if (!TextUtils.isEmpty(plugin.description)) {
            TextView descView = new TextView(context);
            descView.setText(plugin.description);
            descView.setTextSize(14);
            descView.setGravity(Gravity.CENTER);
            descView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            descView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            descView.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
            container.addView(descView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 16, 12, 0));
        }

        final boolean enabled = SquziPluginsController.isEnabled(plugin.id);
        LinearLayout enableRow = makeRow(context, enabled ? "Включён" : "Выключен", "Нажми чтобы " + (enabled ? "выключить" : "включить"));
        enableRow.setOnClickListener(v -> {
            boolean next = !SquziPluginsController.isEnabled(pluginId);
            SquziPluginsController.setEnabled(pluginId, next);
            try {
                if (next) {
                    SquziPluginRuntime.onPluginEnabled(pluginId);
                } else {
                    SquziPluginRuntime.onPluginDisabled(pluginId);
                }
            } catch (Throwable ignored) {
            }
            fill();
        });
        container.addView(enableRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 12, 12, 0));

        if (enabled) {
            addSettingsRows(context);
        } else {
            TextView offHint = new TextView(context);
            offHint.setText("Включи плагин чтобы увидеть его настройки");
            offHint.setTextSize(13);
            offHint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            container.addView(offHint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 12, 20, 0));
        }

        TextView deleteRow = new TextView(context);
        deleteRow.setText("Удалить плагин");
        deleteRow.setTextSize(16);
        deleteRow.setGravity(Gravity.CENTER);
        deleteRow.setTextColor(0xFFF45255);
        deleteRow.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        deleteRow.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
        deleteRow.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), resourceProvider);
            builder.setTitle("Удалить плагин?");
            builder.setMessage("«" + plugin.name + "» будет удалён из SquziGram");
            builder.setPositiveButton("Удалить", (dialog, which) -> {
                SquziPluginsController.delete(pluginId);
                try {
                    SquziPluginRuntime.onPluginDeleted(pluginId);
                } catch (Throwable ignored) {
                }
                finishFragment();
            });
            builder.setNegativeButton("Отмена", null);
            try {
                builder.create().show();
            } catch (Throwable ignored) {
            }
        });
        container.addView(deleteRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 16, 12, 32));
    }

    private LinearLayout makeRow(Context context, String title, String subtitle) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        row.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (!TextUtils.isEmpty(subtitle)) {
            TextView subView = new TextView(context);
            subView.setText(subtitle);
            subView.setTextSize(13);
            subView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            row.addView(subView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        }
        return row;
    }

    private void addSettingsRows(Context context) {
        String json = null;
        try {
            json = SquziPluginRuntime.getSettingsJson(pluginId);
        } catch (Throwable ignored) {
        }
        if (json == null) {
            return;
        }
        try {
            JSONObject root = new JSONObject(json);
            JSONArray rows = root.optJSONArray("rows");
            if (rows == null || rows.length() == 0) {
                return;
            }
            TextView header = new TextView(context);
            header.setText("Настройки плагина");
            header.setTextSize(14);
            header.setTypeface(AndroidUtilities.bold());
            header.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
            container.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 20, 20, 8));

            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) {
                    continue;
                }
                String type = row.optString("type", "");
                if ("header".equals(type)) {
                    TextView h = new TextView(context);
                    h.setText(row.optString("text", ""));
                    h.setTextSize(14);
                    h.setTypeface(AndroidUtilities.bold());
                    h.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
                    container.addView(h, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 8, 20, 4));
                } else if ("text".equals(type)) {
                    String sub = row.optString("subtext", "");
                    LinearLayout r = makeRow(context, row.optString("text", ""), sub.isEmpty() ? null : sub);
                    container.addView(r, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 0, 12, 1));
                } else if ("input".equals(type)) {
                    final String key = row.optString("key", "");
                    final String value = row.optString("value", "");
                    LinearLayout r = makeRow(context, row.optString("text", key), value);
                    r.setOnClickListener(v -> showInputDialog(key, row.optString("text", key), value));
                    container.addView(r, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 0, 12, 1));
                } else if ("switch".equals(type)) {
                    final String key = row.optString("key", "");
                    final boolean value = row.optBoolean("value", false);
                    String sub = row.optString("subtext", "");
                    LinearLayout r = makeRow(context, row.optString("text", key),
                            (value ? "Вкл" : "Выкл") + (sub.isEmpty() ? "" : " • " + sub));
                    r.setOnClickListener(v -> {
                        try {
                            SquziPluginRuntime.setSetting(pluginId, key, value ? "false" : "true");
                        } catch (Throwable ignored) {
                        }
                        fill();
                    });
                    container.addView(r, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 0, 12, 1));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void showInputDialog(String key, String title, String current) {
        Context context = getParentActivity();
        if (context == null || key == null) {
            return;
        }
        FrameLayout wrapper = new FrameLayout(context);
        wrapper.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));
        final EditText edit = new EditText(context);
        edit.setText(current != null ? current : "");
        edit.setHint(title);
        edit.setTextSize(16);
        edit.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        wrapper.addView(edit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourceProvider);
        builder.setTitle(title);
        builder.setView(wrapper);
        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            try {
                SquziPluginRuntime.setSetting(pluginId, key,
                        JSONObject.quote(edit.getText().toString()));
            } catch (Throwable ignored) {
            }
            fill();
        });
        builder.setNegativeButton("Отмена", null);
        try {
            builder.create().show();
        } catch (Throwable ignored) {
        }
    }
}

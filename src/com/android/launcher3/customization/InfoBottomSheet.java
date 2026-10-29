package com.android.launcher3.customization;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.THREAD_POOL_EXECUTOR;

import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.widget.TextView;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseActivity;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.settings.preference.IconPackPrefSetter;
import com.android.launcher3.settings.preference.ReloadingListPreference;
import com.android.launcher3.util.AppReloader;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.views.AbstractSlideInView;

/** App details owned by the floating view, with no fragment to restore after it is dismissed. */
public class InfoBottomSheet extends AbstractSlideInView<BaseActivity> {
    private static final int DEFAULT_CLOSE_DURATION = 200;
    private PreferenceScreen mPreferences;
    private RecyclerView mList;
    private AlertDialog mDialog;
    private Rect mSourceBounds = new Rect();
    private Context mSourceContext;
    private boolean mPreferencesAttached;

    public InfoBottomSheet(Context context) {
        this(context, null);
    }

    public InfoBottomSheet(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public InfoBottomSheet(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContent = findViewById(R.id.app_info_content);
        setContentBackgroundWithParent(
                getContext().getDrawable(R.drawable.bg_rounded_corner_bottom_sheet), mContent);
        mList = new RecyclerView(getContext());
        mList.setLayoutManager(new LinearLayoutManager(getContext()));
        mList.setOverScrollMode(View.OVER_SCROLL_NEVER);
        ((ViewGroup) findViewById(R.id.sheet_prefs)).addView(mList,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public void configureBottomSheet(Rect sourceBounds, Context context) {
        mSourceBounds.set(sourceBounds);
        mSourceContext = context;
    }

    public void populateAndShow(ItemInfo itemInfo) {
        ComponentKey key = new ComponentKey(itemInfo.getTargetComponent(), itemInfo.user);
        Launcher launcher = Launcher.getLauncher(getContext());
        ((TextView) findViewById(R.id.title)).setText(
                itemInfo.title == null || itemInfo.title.isEmpty()
                        ? getContext().getString(R.string.app_info_title) : itemInfo.title);

        PreferenceManager manager = new PreferenceManager(getContext());
        mPreferences = manager.inflateFromResource(getContext(), R.xml.app_info_preferences, null);
        manager.setPreferences(mPreferences);
        manager.setOnDisplayPreferenceDialogListener(preference -> {
            if (preference instanceof ListPreference list) showListDialog(list);
        });
        ReloadingListPreference icons = mPreferences.findPreference("pref_app_info_icon_pack");
        icons.setValue(IconDatabase.hasOverrideForComponent(getContext(), key)
                ? IconDatabase.getByComponent(getContext(), key) : IconDatabase.VALUE_INHERIT);
        icons.setOnReloadListener(ctx -> new IconPackPrefSetter(ctx, key.componentName));
        icons.setOnPreferenceChangeListener((preference, value) -> {
            if (IconDatabase.VALUE_INHERIT.equals(value)) {
                IconDatabase.resetForComponent(getContext(), key);
            } else {
                IconDatabase.setForComponent(getContext(), key, (String) value);
            }
            AppReloader.get(getContext()).reload(key);
            return true;
        });

        Preference customIcon = mPreferences.findPreference("pref_app_info_custom_icon");
        customIcon.setOnPreferenceClickListener(preference -> {
            IconPickerBottomSheet picker = (IconPickerBottomSheet) launcher.getLayoutInflater()
                    .inflate(R.layout.icon_picker_bottom_sheet, launcher.getDragLayer(), false);
            picker.show(key, () -> {
                icons.setValue(IconDatabase.hasOverrideForComponent(getContext(), key)
                        ? IconDatabase.getByComponent(getContext(), key)
                        : IconDatabase.VALUE_INHERIT);
                AppReloader.get(getContext()).reload(key);
            });
            return true;
        });
        Preference more = mPreferences.findPreference("pref_app_info_more");
        more.setOnPreferenceClickListener(preference -> {
            PackageManagerHelper.startDetailsActivityForInfo(
                    mSourceContext == null ? getContext() : mSourceContext, itemInfo,
                    mSourceBounds, ActivityOptions.makeBasic().toBundle());
            return true;
        });

        mList.setAdapter(new PreferenceGroupAdapter(mPreferences));
        attachToContainer();
        mPreferences.onAttached();
        mPreferencesAttached = true;
        mIsOpen = true;
        setUpDefaultOpenAnimation().start();

        Context context = getContext().getApplicationContext();
        THREAD_POOL_EXECUTOR.execute(() -> {
            MetadataExtractor extractor = new MetadataExtractor(context, key);
            CharSequence source = extractor.getSource();
            CharSequence lastUpdate = extractor.getLastUpdate();
            String version = context.getString(R.string.app_info_version_value,
                    extractor.getVersionName(), extractor.getVersionCode());
            MAIN_EXECUTOR.execute(() -> {
                if (!isAttachedToWindow() || !mIsOpen) return;
                Preference sourcePref = mPreferences.findPreference("pref_app_info_source");
                sourcePref.setSummary(source);
                mPreferences.findPreference("pref_app_info_last_update").setSummary(lastUpdate);
                mPreferences.findPreference("pref_app_info_version")
                        .setSummary(key.componentName.getPackageName() + " " + version);
                if (extractor.getMarketIntent() != null) {
                    sourcePref.setOnPreferenceClickListener(preference -> {
                        launcher.startActivitySafely(mList, extractor.getMarketIntent(), itemInfo);
                        return true;
                    });
                }
            });
        });
    }

    private void showListDialog(ListPreference preference) {
        mDialog = new AlertDialog.Builder(getContext())
                .setTitle(preference.getDialogTitle())
                .setSingleChoiceItems(preference.getEntries(),
                        preference.findIndexOfValue(preference.getValue()), (dialog, which) -> {
                            String value = preference.getEntryValues()[which].toString();
                            if (preference.callChangeListener(value)) preference.setValue(value);
                            dialog.dismiss();
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        mDialog.show();
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mNoIntercept = mActivityContext.getDragLayer().isEventOverView(mList, event)
                    && mList.canScrollVertically(-1);
        }
        return super.onControllerInterceptTouchEvent(event);
    }

    @Override
    protected void handleClose(boolean animate) {
        if (mDialog != null) mDialog.dismiss();
        handleClose(animate, DEFAULT_CLOSE_DURATION);
    }

    @Override
    protected boolean isOfType(@AbstractFloatingView.FloatingViewType int type) {
        return (type & TYPE_APP_INFO_BOTTOM_SHEET) != 0;
    }

    @Override
    protected Pair<View, String> getAccessibilityTarget() {
        return Pair.create(findViewById(R.id.title), getContext().getString(R.string.app_info_title));
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mDialog != null) mDialog.dismiss();
        mList.setAdapter(null);
        if (mPreferencesAttached) {
            mPreferences.onDetached();
            mPreferencesAttached = false;
        }
        super.onDetachedFromWindow();
    }
}

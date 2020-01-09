package com.android.launcher3.customization;

import android.app.ActivityOptions;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.recyclerview.widget.RecyclerView;
import android.graphics.Rect;
import android.util.Pair;

import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseActivity;
import com.android.launcher3.R;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.views.AbstractSlideInView;

import com.android.launcher3.settings.preference.IconPackPrefSetter;
import com.android.launcher3.settings.preference.ReloadingListPreference;
import com.android.launcher3.util.AppReloader;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.THREAD_POOL_EXECUTOR;

public class InfoBottomSheet extends AbstractSlideInView<BaseActivity> {
    private static final int DEFAULT_CLOSE_DURATION = 200;
    private static final String PREFS_FRAGMENT_TAG = "app_info_preferences";
    private final FragmentManager mFragmentManager;
    private Rect mSourceBounds;
    private Context mSourceContext;

    public InfoBottomSheet(Context context) {
        this(context, null);
    }

    public InfoBottomSheet(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public InfoBottomSheet(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mFragmentManager = QuickstepLauncher.getLauncher(context).getFragmentManager();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContent = findViewById(R.id.app_info_content);
        setContentBackgroundWithParent(
                getContext().getDrawable(R.drawable.bg_rounded_corner_bottom_sheet), mContent);
    }

    public void configureBottomSheet(Rect sourceBounds, Context context) {
        mSourceBounds = new Rect(sourceBounds);
        mSourceContext = context;
    }

    public void populateAndShow(ItemInfo itemInfo) {
        TextView title = findViewById(R.id.title);
        title.setText((itemInfo.title == null || itemInfo.title.isEmpty()) 
            ? getContext().getResources().getString(R.string.app_info_title) 
            : itemInfo.title);

        PrefsFragment fragment = (PrefsFragment)
                mFragmentManager.findFragmentByTag(PREFS_FRAGMENT_TAG);
        if (fragment == null) {
            fragment = new PrefsFragment();
            mFragmentManager.beginTransaction()
                    .replace(R.id.sheet_prefs, fragment, PREFS_FRAGMENT_TAG)
                    .commitNow();
        }
        fragment.loadForApp(itemInfo, mSourceBounds, mSourceContext);

        attachToContainer();
        mIsOpen = false;
        animateOpen();
    }

    private void animateOpen() {
        if (mIsOpen || mOpenCloseAnimation.getAnimationPlayer().isRunning()) {
            return;
        }
        mIsOpen = true;
        setUpDefaultOpenAnimation().start();
    }

    @Override
    protected void handleClose(boolean animate) {
        handleClose(animate, DEFAULT_CLOSE_DURATION);
    }

    @Override
    protected boolean isOfType(@AbstractFloatingView.FloatingViewType int type) {
        return (type & AbstractFloatingView.TYPE_APP_INFO_BOTTOM_SHEET) != 0;
    }

    @Override
    protected Pair<View, String> getAccessibilityTarget() {
        return Pair.create(findViewById(R.id.title), getContext().getString(R.string.app_info_title));
    }

    @Override
    public void onDetachedFromWindow() {
        Fragment pf = mFragmentManager.findFragmentByTag(PREFS_FRAGMENT_TAG);
        if (pf != null) {
            mFragmentManager.beginTransaction()
                    .remove(pf)
                    .commitNowAllowingStateLoss();
        }
        super.onDetachedFromWindow();
    }

    public static class PrefsFragment extends PreferenceFragment
            implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {
        private static final String KEY_ICON_PACK = "pref_app_info_icon_pack";
        private static final String KEY_CUSTOM_ICON = "pref_app_info_custom_icon";
        private static final String KEY_SOURCE = "pref_app_info_source";
        private static final String KEY_LAST_UPDATE = "pref_app_info_last_update";
        private static final String KEY_VERSION = "pref_app_info_version";
        private static final String KEY_MORE = "pref_app_info_more";

        private Context mContext;

        private ItemInfo mItemInfo;

        private ComponentName mComponent;
        private ComponentKey mKey;
        private QuickstepLauncher mLauncher;
        private Rect mSourceBounds;
        private Context mSourceContext;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mContext = getActivity();
            mLauncher = (QuickstepLauncher) QuickstepLauncher.getLauncher(mContext);
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.app_info_preferences);
        }

        @Override
        public RecyclerView onCreateRecyclerView(LayoutInflater inflater, ViewGroup parent,
                                                 Bundle savedInstanceState) {
            RecyclerView view = super.onCreateRecyclerView(inflater, parent, savedInstanceState);
            view.setOverScrollMode(View.OVER_SCROLL_NEVER);
            return view;
        }

        public void loadForApp(ItemInfo itemInfo, Rect sourceBounds, Context sourceContext) {
            mComponent = itemInfo.getTargetComponent();
            mItemInfo = itemInfo;
            mKey = new ComponentKey(mComponent, itemInfo.user);
            mSourceBounds = new Rect(sourceBounds);
            mSourceContext = sourceContext;

            ReloadingListPreference icons = (ReloadingListPreference) findPreference(KEY_ICON_PACK);
            icons.setValue(IconDatabase.getByComponent(mContext, mKey));
            icons.setOnReloadListener(ctx -> new IconPackPrefSetter(ctx, mComponent));
            icons.setOnPreferenceChangeListener(this);

            Preference customIcon = findPreference(KEY_CUSTOM_ICON);
            if (customIcon != null) {
                customIcon.setOnPreferenceClickListener(pref -> {
                    IconPickerBottomSheet picker =
                            (IconPickerBottomSheet) mLauncher.getLayoutInflater().inflate(
                                    R.layout.icon_picker_bottom_sheet,
                                    mLauncher.getDragLayer(), false);
                    picker.show(mKey, () -> AppReloader.get(mContext).reload(mKey));
                    return true;
                });
            }

            THREAD_POOL_EXECUTOR.execute(() -> {
                MetadataExtractor extractor = new MetadataExtractor(mContext, mComponent);

                CharSequence source = extractor.getSource();
                CharSequence lastUpdate = extractor.getLastUpdate();
                CharSequence version = mContext.getString(
                        R.string.app_info_version_value,
                        extractor.getVersionName(),
                        extractor.getVersionCode());
                Intent marketIntent = extractor.getMarketIntent();

                MAIN_EXECUTOR.execute(() -> {
                    Preference sourcePref = findPreference(KEY_SOURCE);
                    Preference lastUpdatePref = findPreference(KEY_LAST_UPDATE);
                    Preference versionPref = findPreference(KEY_VERSION);
                    Preference morePref = findPreference(KEY_MORE);

                    sourcePref.setSummary(source);
                    lastUpdatePref.setSummary(lastUpdate);
                    versionPref.setSummary(mComponent.getPackageName() + " " + version);
                    morePref.setOnPreferenceClickListener(this);

                    if (marketIntent != null) {
                        sourcePref.setOnPreferenceClickListener(
                                pref -> tryStartActivity(marketIntent));
                    }
                });
            });
        }

        @Override
        public void onResume() {
            super.onResume();
            ReloadingListPreference icons = (ReloadingListPreference) findPreference(KEY_ICON_PACK);
            if (icons != null) {
                icons.refreshEntries();
            }
        }

        private boolean tryStartActivity(Intent intent) {
            Bundle opts = mLauncher
                    .getActivityLaunchOptions(getView(), mItemInfo)
                    .toBundle();
            try {
                mLauncher.startActivity(intent, opts);
            } catch (Exception ignored) {
            }
            return false;
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (newValue.equals(IconDatabase.getGlobal(mContext))) {
                IconDatabase.resetForComponent(mContext, mKey);
            } else {
                IconDatabase.setForComponent(mContext, mKey, (String) newValue);
            }
            AppReloader.get(mContext).reload(mKey);
            return true;
        }

        private void onMoreClick() {
            PackageManagerHelper.startDetailsActivityForInfo(mSourceContext, mItemInfo,
                    mSourceBounds, ActivityOptions.makeBasic().toBundle());
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            onMoreClick();
            return true;
        }
    }
}

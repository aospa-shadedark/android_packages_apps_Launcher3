package com.android.launcher3.customization;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.TextUtils;
import android.text.format.DateUtils;

import com.android.launcher3.R;
import com.android.launcher3.util.ComponentKey;


import static android.content.pm.PackageManager.GET_META_DATA;

class MetadataExtractor {
    private final Context mContext;
    private final PackageManager mPm;

    private String mSourcePkg = "";
    private Intent mMarketIntent;

    private long mLastUpdate = 0;
    private String mVersionName = "Unknown";
    private long mVersionCode = 0;
    private boolean mSystemApp;

    MetadataExtractor(Context context, ComponentKey key) {
        mContext = context;
        PackageManager pm;
        try {
            pm = context.createContextAsUser(key.user, 0).getPackageManager();
        } catch (SecurityException e) {
            pm = context.getPackageManager();
        }
        mPm = pm;
        mVersionName = context.getString(R.string.app_info_unknown);
        String pkg = key.componentName.getPackageName();

        try {
            mSourcePkg = mPm.getInstallSourceInfo(pkg).getInstallingPackageName();
            if (!TextUtils.isEmpty(mSourcePkg)) {
                mMarketIntent = new Intent(Intent.ACTION_VIEW)
                        .setData(Uri.parse("market://details?id=" + pkg))
                        .setPackage(mSourcePkg)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
        } catch (PackageManager.NameNotFoundException | IllegalArgumentException | SecurityException ignored) {
        }

        try {
            PackageInfo pi = mPm.getPackageInfo(pkg, GET_META_DATA);
            mLastUpdate = pi.lastUpdateTime;
            if (!TextUtils.isEmpty(pi.versionName)) mVersionName = pi.versionName;
            mVersionCode = pi.getLongVersionCode();
            mSystemApp = pi.applicationInfo != null
                    && (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (PackageManager.NameNotFoundException | SecurityException ignored) {
        }
    }

    CharSequence getSource() {
        if (TextUtils.isEmpty(mSourcePkg)) {
            return mContext.getString(mSystemApp
                    ? R.string.app_info_source_system : R.string.app_info_unknown);
        }
        try {
            ApplicationInfo pi = mPm.getApplicationInfo(mSourcePkg, 0);
            return pi.loadLabel(mPm);
        } catch (PackageManager.NameNotFoundException | SecurityException ignored) {
        }
        return mSourcePkg;
    }

    String getLastUpdate() {
        if (mLastUpdate == 0) return mContext.getString(R.string.app_info_unknown);
        return DateUtils.formatDateTime(mContext, mLastUpdate,
                DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE);
    }

    String getVersionName() {
        return mVersionName;
    }

    long getVersionCode() {
        return mVersionCode;
    }

    Intent getMarketIntent() {
        return mMarketIntent;
    }

}

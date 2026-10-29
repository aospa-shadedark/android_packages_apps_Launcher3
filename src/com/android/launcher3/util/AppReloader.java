package com.android.launcher3.util;

import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.os.UserHandle;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.dagger.LauncherComponentProvider;
import android.content.pm.LauncherApps;
import android.os.UserManager;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.android.launcher3.customization.IconDatabase;

import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

public class AppReloader {
    private static AppReloader sInstance;

    public static synchronized AppReloader get(Context context) {
        if (sInstance == null) {
            sInstance = new AppReloader(context.getApplicationContext());
        }
        return sInstance;
    }

    private final Context mContext;
    private final UserManager mUsers;
    private final LauncherApps mApps;

    private AppReloader(Context context) {
        mContext = context;
        mUsers = context.getSystemService(UserManager.class);
        mApps = context.getSystemService(LauncherApps.class);
    }

    public Set<ComponentKey> withIconPack(String iconPack) {
        Set<ComponentKey> reloadKeys = new HashSet<>();
        for (UserHandle user : mUsers.getUserProfiles()) {
            for (LauncherActivityInfo info : mApps.getActivityList(null, user)) {
                ComponentKey key = new ComponentKey(info.getComponentName(), info.getUser());
                if (IconDatabase.getByComponent(mContext, key).equals(iconPack)) {
                    reloadKeys.add(key);
                }
            }
        }
        return reloadKeys;
    }

    public void reload() {
        MODEL_EXECUTOR.execute(() -> {
            for (UserHandle user : mUsers.getUserProfiles()) {
                Set<String> pkgsSet = new HashSet<>();
                for (LauncherActivityInfo info : mApps.getActivityList(null, user)) {
                    pkgsSet.add(info.getComponentName().getPackageName());
                }
                for (String pkg : pkgsSet) {
                    reload(user, pkg);
                }
            }
        });
    }

    public void reload(ComponentKey key) {
        MODEL_EXECUTOR.execute(() -> reload(key.user, key.componentName.getPackageName()));
    }

    public void reload(Collection<ComponentKey> keys) {
        Set<PackageUserKey> packages = new HashSet<>();
        for (ComponentKey key : keys) {
            packages.add(new PackageUserKey(key.componentName.getPackageName(), key.user));
        }
        MODEL_EXECUTOR.execute(() -> packages.forEach(key -> reload(key.mUser, key.mPackageName)));
    }

    private void reload(UserHandle user, String pkg) {
        LauncherAppState appState = LauncherAppState.getInstance(mContext);
        appState.getIconProvider().updateSystemState();
        // ModelInitializer updates the cache and model on its model executor; RecentsModel
        // invalidates task icons from the same event. Avoid a second independent reload path.
        LauncherComponentProvider.get(mContext).getIconChangeTracker().notifyIconChanged(pkg, user);
    }
}

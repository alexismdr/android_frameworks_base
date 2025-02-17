/*
 * Copyright (C) 2022 The Pixel Experience Project
 *               2021-2022 crDroid Android Project
 *           (C) 2023 ArrowOS
 *           (C) 2023 The LibreMobileOS Foundation
 *           (C) 2024 PixelBuildsROM
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util.pb;

import android.app.ActivityTaskManager;
import android.app.Application;
import android.app.TaskStackListener;
import android.content.pm.PackageManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Build;
import android.os.Process;
import android.os.SystemProperties;
import android.util.Log;

import com.android.internal.R;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PixelPropsUtils {

    private static final String TAG = PixelPropsUtils.class.getSimpleName();
    private static final String DEVICE = SystemProperties.get("ro.build.version.device");
    private static final String MODEL = SystemProperties.get("ro.product.model", Build.MODEL);

    private static final String PACKAGE_PIF = "org.pixelbuilds.catmouse";
    private static final String PACKAGE_GMS = "com.google.android.gms";
    private static final String PACKAGE_FINSKY = "com.android.vending";
    private static final String PACKAGE_PHOTOS = "com.google.android.apps.photos";
    private static final String PACKAGE_SET_INTEL = "com.google.android.settings.intelligence";
    private static final ComponentName GMS_ADD_ACCOUNT_ACTIVITY = ComponentName.unflattenFromString(
            "com.google.android.gms/.auth.uiflows.minutemaid.MinuteMaidActivity");

    private static final boolean DEBUG = false;

    private static String[] sCertifiedProps =
    Resources.getSystem().getStringArray(R.array.config_certifiedBuildProperties);

    private static final Map<String, Object> propsToChangeGeneric;
    private static final Map<String, Object> propsToChangeNewerPixel;
    private static final Map<String, Object> propsToChangeOlderPixel;
    private static final Map<String, Object> propsToSpoofPhotos;
    private static final Map<String, ArrayList<String>> propsToKeep;

    private static final ArrayList<String> packagesToChangeNewerPixel =
    new ArrayList<String> (
        Arrays.asList(
            "com.google.android.apps.wallpaper.pixel",
            "com.google.android.apps.wallpaper",
            "com.google.pixel.livewallpaper",
            "com.google.android.apps.aiwallpapers",
            "com.google.android.apps.emojiwallpaper",
            "com.google.android.inputmethod.latin",
            "com.google.android.googlequicksearchbox",
            "com.google.android.setupwizard"
    ));

    private static final ArrayList<String> packagesToChangeOlderPixel = 
    new ArrayList<String> (
        Arrays.asList(
            "com.google.android.gms.ui",
            "com.google.android.gms.learning",
            "com.google.android.gms.persistent",
            "com.android.chrome",
            "com.breel.wallpapers20",
            "com.nhs.online.nhsonline",
            "com.nothing.smartcenter"
    ));

    // Codenames for currently supported Pixels by Google
    private static final ArrayList<String> pixelCodenames = 
    new ArrayList<String> (
        Arrays.asList(
            "comet",
            "komodo",
            "caiman",
            "tokay",
            "akita",
            "husky",
            "shiba",
            "felix",
            "tangorpro",
            "lynx",
            "cheetah",
            "panther",
            "bluejay",
            "oriole",
            "raven"
    ));

    static {
        propsToKeep = new HashMap<>();
        propsToKeep.put(PACKAGE_SET_INTEL, new ArrayList<>(Collections.singletonList("FINGERPRINT")));
        propsToChangeGeneric = new HashMap<>();
        propsToChangeGeneric.put("TYPE", "user");
        propsToChangeGeneric.put("TAGS", "release-keys");
        propsToChangeNewerPixel = new HashMap<>();
        String fingerprint_newer_pixel = "google/husky/husky:14/AP2A.240905.003/12231197:user/release-keys";
        propsToChangeNewerPixel.put("MANUFACTURER", "Google");
        propsToChangeNewerPixel.put("MODEL", "Pixel 8 Pro");
        propsToChangeNewerPixel.put("FINGERPRINT", fingerprint_newer_pixel);
        String[] fpsections_newer_pixel = fingerprint_newer_pixel.split("/");
        propsToChangeNewerPixel.put("BRAND", fpsections_newer_pixel[0]);
        propsToChangeNewerPixel.put("DEVICE", fpsections_newer_pixel[2].split(":")[0]);
        propsToChangeNewerPixel.put("PRODUCT", fpsections_newer_pixel[1]);
        propsToChangeNewerPixel.put("ID", fpsections_newer_pixel[3]);
        propsToChangeOlderPixel = new HashMap<>();
        String fingerprint_older_pixel = "google/bluejay/bluejay:14/AP1A.240505.004/11583682:user/release-keys";
        propsToChangeOlderPixel.put("MANUFACTURER", "Google");
        propsToChangeOlderPixel.put("MODEL", "Pixel 6a");
        propsToChangeOlderPixel.put("FINGERPRINT", fingerprint_older_pixel);
        String[] fpsections_older_pixel = fingerprint_older_pixel.split("/");
        propsToChangeOlderPixel.put("BRAND", fpsections_older_pixel[0]);
        propsToChangeOlderPixel.put("DEVICE", fpsections_older_pixel[2].split(":")[0]);
        propsToChangeOlderPixel.put("PRODUCT", fpsections_older_pixel[1]);
        propsToChangeOlderPixel.put("ID", fpsections_older_pixel[3]);
        propsToSpoofPhotos = new HashMap<>();
        propsToSpoofPhotos.put("BRAND", "google");
        propsToSpoofPhotos.put("MANUFACTURER", "Google");
        propsToSpoofPhotos.put("DEVICE", "marlin");
        propsToSpoofPhotos.put("PRODUCT", "marlin");
        propsToSpoofPhotos.put("MODEL", "Pixel XL");
        propsToSpoofPhotos.put("ID", "QP1A.191005.007.A3");
        propsToSpoofPhotos.put("FINGERPRINT", "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys");
    }

    private static volatile boolean sIsFinsky = false;

    private static boolean isDroidGuard() {
        return Arrays.stream(Thread.currentThread().getStackTrace())
                        .anyMatch(elem -> elem.getClassName().toLowerCase()
                            .contains("droidguard"));
    }

    private static boolean isGmsAddAccountActivityOnTop() {
        try {
            final ActivityTaskManager.RootTaskInfo focusedTask =
                    ActivityTaskManager.getService().getFocusedRootTaskInfo();
            return focusedTask != null && focusedTask.topActivity != null
                    && focusedTask.topActivity.equals(GMS_ADD_ACCOUNT_ACTIVITY);
        } catch (Exception e) {
            Log.e(TAG, "Unable to get top activity!", e);
        }
        return false;
    }
    
    private static void setPropsForGms() {
        final boolean was = isGmsAddAccountActivityOnTop();
        final TaskStackListener taskStackListener = new TaskStackListener() {
            @Override
            public void onTaskStackChanged() {
                final boolean is = isGmsAddAccountActivityOnTop();
                if (is ^ was) {
                    dlog("GmsAddAccountActivityOnTop is:" + is + " was:" + was + ", killing myself!");
                    // process will restart automatically later
                    Process.killProcess(Process.myPid());
                }
            }
        };
        try {
            ActivityTaskManager.getService().registerTaskStackListener(taskStackListener);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register task stack listener!", e);
        }
        if (was) return;

        // Give up if not appropriate props array
        if (sCertifiedProps.length != 5) {
            Log.e(TAG, "Insufficient size of the certified props array: "
                    + sCertifiedProps.length + ", required 5");
            return;
        } else {
            dlog("Spoofing build for GMS");
            setBuildField("MANUFACTURER", sCertifiedProps[0]);
            setBuildField("MODEL", sCertifiedProps[1]);
            setVersionField("SECURITY_PATCH", sCertifiedProps[2]);
            setVersionField("DEVICE_INITIAL_SDK_INT", Integer.parseInt(sCertifiedProps[3]));
            setBuildField("FINGERPRINT", sCertifiedProps[4]);
            String[] certfpsections = sCertifiedProps[4].split("/");
            setBuildField("BRAND", certfpsections[0]);
            setBuildField("DEVICE", certfpsections[2].split(":")[0]);
            setBuildField("PRODUCT", certfpsections[1]);
            setBuildField("ID", certfpsections[3]);
            setVersionField("RELEASE", certfpsections[2].split(":")[1]);
            setVersionField("INCREMENTAL", certfpsections[4].split(":")[0]);
        }
    }

    public static void setProps(Context context) {
        final String packageName = context.getPackageName();
        final String processName = Application.getProcessName();
        sIsFinsky = packageName.equals(PACKAGE_FINSKY);

        propsToChangeGeneric.forEach((k, v) -> setPropValue(k, v));
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        if (pixelCodenames.contains(DEVICE)) {
            return;
        }

        Map<String, Object> propsToChange = new HashMap<>();

        if (packagesToChangeNewerPixel.contains(packageName)
            || packagesToChangeNewerPixel.contains(processName)) {
                propsToChange.putAll(propsToChangeNewerPixel);
        } else if (packagesToChangeOlderPixel.contains(packageName)
            || packagesToChangeOlderPixel.contains(processName)) {
                propsToChange.putAll(propsToChangeOlderPixel);
        } else if (packageName.equals(PACKAGE_PHOTOS)) {
            propsToChange.putAll(propsToSpoofPhotos);
        }

        if (propsToChange.isEmpty()){
            if (DEBUG) Log.d(TAG, "Nothing to define for: " + packageName);
        } else {
            if (DEBUG) Log.d(TAG, "Defining props for: " + packageName);
            for (Map.Entry<String, Object> prop : propsToChange.entrySet()) {
                String key = prop.getKey();
                Object value = prop.getValue();
                if (propsToKeep.containsKey(packageName) && propsToKeep.get(packageName).contains(key)) {
                    if (DEBUG) Log.d(TAG, "Not defining " + key + " prop for: " + packageName);
                    continue;
                }
                if (DEBUG) Log.d(TAG, "Defining " + key + " prop for: " + packageName);
                    setPropValue(key, value);
            }
        }

        if (packageName.equals(PACKAGE_GMS)) {
            setPropValue("TIME", System.currentTimeMillis());
            if (processName.toLowerCase().contains("unstable")) {
                    try {
                        PackageManager pm = context.getPackageManager();
                        Resources resources = pm.getResourcesForApplication(PACKAGE_PIF);
                        int resourceId = resources.getIdentifier(
                            "config_certifiedBuildProperties", "array", PACKAGE_PIF);
                        String[] packageProps = resources.getStringArray(resourceId);
                        if (!Arrays.equals(sCertifiedProps, packageProps)) {
                            sCertifiedProps = packageProps;
                        }
                    } catch (PackageManager.NameNotFoundException e) {
                        if (DEBUG) Log.d(TAG, "PIF package is not found");
                    }
                    setPropsForGms();
                    return;
            }
        }

        // Set proper indexing fingerprint
        if (packageName.equals(PACKAGE_SET_INTEL)) {
            setPropValue("FINGERPRINT", Build.VERSION.INCREMENTAL);
        }
        // Show correct model name on gms services
        if ("com.google.android.gms.ui".equals(processName)) {
            setPropValue("MODEL", MODEL);
        }
    }

    private static void setPropValue(String key, Object value) {
        try {
            if (DEBUG) Log.d(TAG, "Defining prop " + key + " to " + value.toString());
            // Unlock
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);
            // Edit
            field.set(null, value);
            // Lock
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to set prop " + key, e);
        }
    }

    private static void setBuildField(String key, String value) {
        try {
            if (DEBUG) Log.d(TAG, "Defining build field " + key + " to " + value);
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Build." + key, e);
        }
    }

    private static void setVersionField(String key, Object value) {
        try {
            if (DEBUG) Log.d(TAG, "Defining version field " + key + " to " + value.toString());
            Field field = Build.VERSION.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to set version field " + key, e);
        }
    }

    public static boolean shouldBypassTaskPermission(Context context) {
        // GMS doesn't have MANAGE_ACTIVITY_TASKS permission
        final int callingUid = Binder.getCallingUid();
        final int gmsUid;
        try {
            gmsUid = context.getPackageManager().getApplicationInfo(PACKAGE_GMS, 0).uid;
            dlog("shouldBypassTaskPermission: gmsUid:" + gmsUid + " callingUid:" + callingUid);
        } catch (Exception e) {
            Log.e(TAG, "shouldBypassTaskPermission: unable to get gms uid", e);
            return false;
        }
        return gmsUid == callingUid;
    }

    public static boolean getIsKeyAttest() {
        return sIsFinsky || isDroidGuard();
    }

    public static void dlog(String msg) {
        if (DEBUG) Log.d(TAG, msg);
    }
}

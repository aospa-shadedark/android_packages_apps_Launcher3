package com.android.launcher3.icons.pack;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class IconPackParser {
    private static final String TAG = "IconPackParser";

    static IconPack.Data parsePackage(PackageManager pm, Resources res, String pkg)
            throws IOException, XmlPullParserException {
        IconPack.Data iconPack = new IconPack.Data();

        readXml(res, pkg, "appfilter", parser -> {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() != XmlPullParser.START_TAG) continue;
                switch (parser.getName()) {
                    case "item": addItem(parser, iconPack); break;
                    case "calendar": addCalendar(parser, iconPack); break;
                    case "iconback": addImgsTo(res, pkg, parser, iconPack.iconBacks); break;
                    case "iconmask": addImgsTo(res, pkg, parser, iconPack.iconMasks); break;
                    case "iconupon": addImgsTo(res, pkg, parser, iconPack.iconUpons); break;
                    case "scale": setScale(parser, iconPack); break;
                    case "dynamic-clock": addClock(res, pkg, parser, iconPack); break;
                }
            }
        });

        return iconPack;
    }

    static List<IconPack.IconEntry> parseAllEntries(PackageManager pm, Resources res, String pkg,
            android.content.pm.ApplicationInfo appInfo)
            throws IOException, XmlPullParserException {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        // The published catalog also works when a pack obfuscates APK resource paths.
        collectDrawableNames(res, pkg, "drawable", names);
        boolean hasCatalog = !names.isEmpty();
        collectDrawableNames(res, pkg, "appfilter", names);
        if (!hasCatalog) collectApkDrawableNames(appInfo, names);
        return toEntries(res, pkg, names);
    }

    private static List<IconPack.IconEntry> toEntries(
            Resources res, String pkg, LinkedHashSet<String> names) {
        List<IconPack.IconEntry> entries = new ArrayList<>(names.size());
        for (String name : names) {
            int id = res.getIdentifier(name, "drawable", pkg);
            if (id == 0) {
                id = res.getIdentifier(name, "mipmap", pkg);
            }
            if (id != 0) {
                entries.add(new IconPack.IconEntry(name, id));
            }
        }
        entries.sort(Comparator.comparing(entry -> entry.drawableName,
                String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    private interface ParserReader {
        void read(XmlPullParser parser) throws IOException, XmlPullParserException;
    }

    private static void readXml(Resources res, String pkg, String name, ParserReader reader)
            throws IOException, XmlPullParserException {
        int resId = res.getIdentifier(name, "xml", pkg);
        if (resId != 0) {
            try (XmlResourceParser parser = res.getXml(resId)) {
                reader.read(parser);
            }
        } else {
            // Many Play Store packs ship plain XML under assets instead of res/xml.
            InputStream input;
            try {
                input = res.getAssets().open(name + ".xml");
            } catch (java.io.FileNotFoundException e) {
                return;
            }
            try (InputStream stream = input) {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(stream, "UTF-8");
                reader.read(parser);
            }
        }
    }

    private static void collectDrawableNames(Resources res, String pkg, String name,
            LinkedHashSet<String> out) throws IOException, XmlPullParserException {
        readXml(res, pkg, name, parser -> {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() != XmlPullParser.START_TAG) continue;
                String drawable = parser.getAttributeValue(null, "drawable");
                if (drawable != null && !drawable.isEmpty()) out.add(drawable);
                if ("calendar".equals(parser.getName())) {
                    String prefix = parser.getAttributeValue(null, "prefix");
                    if (prefix != null) {
                        for (int day = 1; day <= 31; day++) out.add(prefix + day);
                    }
                }
            }
        });
    }

    private static void collectApkDrawableNames(
            android.content.pm.ApplicationInfo appInfo, LinkedHashSet<String> out) {
        if (appInfo.sourceDir != null) collectApkDrawableNames(appInfo.sourceDir, out);
        if (appInfo.splitSourceDirs != null) {
            for (String splitPath : appInfo.splitSourceDirs) {
                collectApkDrawableNames(splitPath, out);
            }
        }
    }

    private static void collectApkDrawableNames(String apkPath, LinkedHashSet<String> out) {
        try (ZipFile apk = new ZipFile(apkPath)) {
            Enumeration<? extends ZipEntry> entries = apk.entries();
            while (entries.hasMoreElements()) {
                String path = entries.nextElement().getName();
                if (!path.startsWith("res/")) continue;

                int directoryEnd = path.indexOf('/', 4);
                if (directoryEnd < 0 || !isDrawableDirectory(path.substring(4, directoryEnd))) {
                    continue;
                }

                String fileName = path.substring(directoryEnd + 1);
                if (fileName.indexOf('/') >= 0) continue;
                int extensionStart = fileName.lastIndexOf('.');
                if (extensionStart <= 0) continue;
                String extension = fileName.substring(extensionStart + 1).toLowerCase(Locale.ROOT);
                if (!(extension.equals("png") || extension.equals("webp")
                        || extension.equals("jpg") || extension.equals("jpeg")
                        || extension.equals("gif") || extension.equals("xml")
                        || extension.equals("avif") || extension.equals("heic"))) {
                    continue;
                }

                String resourceName = fileName.substring(0, extensionStart);
                if (resourceName.endsWith(".9")) {
                    resourceName = resourceName.substring(0, resourceName.length() - 2);
                }
                if (isValidResourceName(resourceName)) out.add(resourceName);
            }
        } catch (IOException e) {
            Log.w(TAG, "Unable to enumerate icon resources from " + apkPath, e);
        }
    }

    private static boolean isDrawableDirectory(String directory) {
        return directory.equals("drawable") || directory.startsWith("drawable-")
                || directory.equals("mipmap") || directory.startsWith("mipmap-");
    }

    private static boolean isValidResourceName(String name) {
        if (name.isEmpty() || !Character.isLowerCase(name.charAt(0))) return false;
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(Character.isLowerCase(c) || Character.isDigit(c) || c == '_')) return false;
        }
        return true;
    }

    private static void addItem(XmlPullParser parseXml,
                                IconPack.Data iconPack) {
        String component = parseXml.getAttributeValue(null, "component");
        String drawable = parseXml.getAttributeValue(null, "drawable");
        if (component != null && drawable != null) {
            ComponentName componentName = parseComponent(component);
            if (componentName != null) {
                iconPack.drawables.put(componentName, drawable);
            }
        }
    }

    private static void addCalendar(XmlPullParser parseXml, IconPack.Data iconPack) {
        String component = parseXml.getAttributeValue(null, "component");
        String prefix = parseXml.getAttributeValue(null, "prefix");
        if (component != null && prefix != null) {
            ComponentName componentName = parseComponent(component);
            if (componentName != null) {
                iconPack.calendarPrefix.put(componentName, prefix);
            }
        }
    }

    private static ComponentName parseComponent(String component) {
        if (component.startsWith("ComponentInfo{") && component.endsWith("}")) {
            component = component.substring(14, component.length() - 1);
        }
        return ComponentName.unflattenFromString(component);
    }

    private static void addImgsTo(Resources res, String pkg, XmlPullParser parseXml,
                                  List<Integer> list) {
        for (int i = 0; i < parseXml.getAttributeCount(); i++) {
            if (parseXml.getAttributeName(i).startsWith("img")) {
                int id = res.getIdentifier(parseXml.getAttributeValue(i), "drawable", pkg);
                if (id != 0) {
                    list.add(id);
                }
            }
        }
    }

    private static void setScale(XmlPullParser parseXml, IconPack.Data iconPack) {
        String factor = parseXml.getAttributeValue(null, "factor");
        if (factor != null) {
            try {
                // ToDo: Parse reference to dimens
                float scale = Float.parseFloat(factor);
                if (!Float.isNaN(scale) && !Float.isInfinite(scale) && scale > 0f) {
                    iconPack.scale = scale;
                }
            } catch (NumberFormatException e) {
                Log.d(TAG, "Invalid scale: " + factor, e);
            }
        }
    }

    private static int getInt(XmlPullParser parser, String name, int fallback) {
        String value = parser.getAttributeValue(null, name);
        try {
            return value == null ? fallback : Integer.decode(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static void addClock(Resources res, String pkg, XmlPullParser parseXml,
                                 IconPack.Data iconPack) {
        String drawable = parseXml.getAttributeValue(null, "drawable");
        if (drawable != null) {
            int drawableId = res.getIdentifier(drawable, "drawable", pkg);
            if (drawableId == 0) {
                drawableId = res.getIdentifier(drawable, "mipmap", pkg);
            }
            if (drawableId != 0) {
                iconPack.clockMetadata.put(drawableId, new IconPack.Clock(
                        getInt(parseXml, "hourLayerIndex", -1),
                        getInt(parseXml, "minuteLayerIndex", -1),
                        getInt(parseXml, "secondLayerIndex", -1),
                        getInt(parseXml, "defaultHour", 0),
                        getInt(parseXml, "defaultMinute", 0),
                        getInt(parseXml, "defaultSecond", 0)));
            }
        }
    }
}

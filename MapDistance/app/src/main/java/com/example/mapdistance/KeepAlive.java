package com.example.mapdistance;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.Process;
import android.provider.Settings;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 查这台机有没有把阿米测距放进省电 / 自启动限制。
 * 安装时系统不会告诉 App；只能打开后查。
 * 小米能读出自启动（AppOps 10008）；华为应用启动管理没有公开接口，要跳到系统页让人看。
 */
public final class KeepAlive {
    public enum Brand { XIAOMI, HUAWEI, OTHER }

    public enum Tri { YES, NO, UNKNOWN }

    public static final class Report {
        public Brand brand = Brand.OTHER;
        public String brandLabel = "这台手机";
        public boolean batteryIgnored;
        public boolean backgroundRestricted;
        public Tri autoStart = Tri.UNKNOWN;
        public boolean oemFamily;
        public boolean oemConfirmed;
        public boolean blocking;
        public boolean allClear;
        public String headline = "";
        public String settingsText = "";
        public String dialogText = "";
        public String bannerText = "";
    }

    private KeepAlive() {
    }

    public static Report inspect(Context c) {
        Report r = new Report();
        r.brand = detectBrand();
        r.brandLabel = label(r.brand);
        r.oemFamily = r.brand != Brand.OTHER;
        r.batteryIgnored = batteryIgnored(c);
        r.backgroundRestricted = backgroundRestricted(c);
        if (r.brand == Brand.XIAOMI) {
            r.autoStart = xiaomiAutoStart(c);
        }
        r.oemConfirmed = Prefs.oemKeepAliveOk(c);
        if (r.autoStart == Tri.NO) {
            r.oemConfirmed = false;
        }
        r.blocking = !r.batteryIgnored || r.backgroundRestricted || r.autoStart == Tri.NO;
        r.allClear = !r.blocking && (!r.oemFamily || r.oemConfirmed);
        fillCopy(r);
        return r;
    }

    public static boolean openBattery(Context c) {
        if (batteryIgnored(c)) {
            Intent all = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            if (start(c, all)) {
                return true;
            }
        }
        Intent req = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        req.setData(Uri.parse("package:" + c.getPackageName()));
        if (start(c, req)) {
            return true;
        }
        return start(c, new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
    }

    public static boolean openAutoStart(Context c) {
        Brand b = detectBrand();
        if (b == Brand.XIAOMI) {
            Intent action = new Intent("miui.intent.action.OP_AUTO_START");
            action.addCategory(Intent.CATEGORY_DEFAULT);
            if (start(c,
                    action,
                    component("com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity")
            )) {
                return true;
            }
        }
        if (b == Brand.HUAWEI) {
            if (openHuaweiStartup(c)) {
                return true;
            }
        }
        return openAppDetails(c);
    }

    public static boolean openOemExtra(Context c) {
        Brand b = detectBrand();
        if (b == Brand.XIAOMI) {
            Intent hide = component("com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity");
            hide.putExtra("package_name", c.getPackageName());
            hide.putExtra("package_label", "阿米测距");
            Intent list = new Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST");
            if (start(c, hide, list)) {
                return true;
            }
        }
        if (b == Brand.HUAWEI) {
            return openHuaweiStartup(c);
        }
        return openAppDetails(c);
    }

    public static boolean openNext(Context c, Report r) {
        if (r == null) {
            r = inspect(c);
        }
        if (!r.batteryIgnored) {
            return openBattery(c);
        }
        if (r.autoStart == Tri.NO) {
            return openAutoStart(c);
        }
        if (r.backgroundRestricted) {
            return openAppDetails(c);
        }
        if (r.oemFamily && !r.oemConfirmed) {
            if (r.brand == Brand.XIAOMI) {
                return openOemExtra(c);
            }
            return openAutoStart(c);
        }
        return openAppDetails(c);
    }

    private static boolean openHuaweiStartup(Context c) {
        return start(c,
                component("com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                component("com.huawei.systemmanager",
                        "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
                component("com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity"),
                action("huawei.intent.action.HSM_BOOTAPP_MANAGER"),
                component("com.hihonor.systemmanager",
                        "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                component("com.hihonor.systemmanager",
                        "com.hihonor.systemmanager.appcontrol.activity.StartupAppControlActivity")
        );
    }

    public static boolean openAppDetails(Context c) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.setData(Uri.parse("package:" + c.getPackageName()));
        return start(c, i);
    }

    private static void fillCopy(Report r) {
        List<String> lines = new ArrayList<>();
        lines.add(r.batteryIgnored ? "电池优化：已放开" : "电池优化：还在省电名单里");
        if (r.brand == Brand.XIAOMI) {
            if (r.autoStart == Tri.YES) {
                lines.add("自启动：已打开");
            } else if (r.autoStart == Tri.NO) {
                lines.add("自启动：被关掉（锁屏容易停测）");
            } else {
                lines.add("自启动：本机读不到，请打开系统页看一眼");
            }
        }
        if (r.backgroundRestricted) {
            lines.add("系统后台限制：开着，轨迹服务会被停");
        } else {
            lines.add("系统后台限制：没有");
        }
        if (r.oemFamily) {
            if (r.oemConfirmed) {
                lines.add("厂商额外设置：你已确认设好");
            } else if (r.brand == Brand.XIAOMI) {
                lines.add("还差：省电策略选「无限制」，最近任务里锁定");
            } else {
                lines.add("还差：应用启动改手动，三项全开，最近任务里锁定");
            }
        }

        r.settingsText = r.brandLabel + "\n" + join(lines);

        if (r.allClear) {
            r.headline = "后台限制已放开";
            r.bannerText = "";
            r.dialogText = "当前检测通过。锁屏后通知栏还在「走路中 / 跑步中 / 骑车中 / 自动中」就说明还在记。";
            return;
        }

        if (!r.batteryIgnored) {
            r.headline = "还在系统省电名单里";
        } else if (r.autoStart == Tri.NO) {
            r.headline = "自启动被关掉，锁屏容易停测";
        } else if (r.backgroundRestricted) {
            r.headline = "系统限制了后台运行";
        } else {
            r.headline = "还差" + r.brandLabel + "那几步";
        }
        r.bannerText = r.headline + "，点这里放开";

        StringBuilder d = new StringBuilder();
        d.append("测距靠通知栏前台服务。国内手机常把新装 App 放进限制名单，锁屏后距离就不走了。\n\n");
        d.append("本机刚查到：\n");
        d.append(join(lines));
        d.append("\n\n");
        if (r.brand == Brand.XIAOMI) {
            d.append("请按顺序做：\n");
            d.append("1. 忽略电池优化（点下面「去处理」）\n");
            d.append("2. 自启动管理里打开阿米测距\n");
            d.append("3. 省电策略选「无限制」\n");
            d.append("4. 最近任务里下拉卡片，点锁定\n\n");
            d.append("第 1、2 步本机能复查；第 3、4 步系统不给读，设完回设置页点「厂商那几项我已设好」。");
        } else if (r.brand == Brand.HUAWEI) {
            d.append("请按顺序做：\n");
            d.append("1. 忽略电池优化（点下面「去处理」）\n");
            d.append("2. 应用启动管理：关掉自动管理；自启动、关联启动、后台活动都打开\n");
            d.append("3. 最近任务里锁定阿米测距\n\n");
            d.append("华为不给读应用启动状态，设完回设置页点「厂商那几项我已设好」。");
        } else {
            d.append("点「去处理」放开电池优化即可。");
        }
        r.dialogText = d.toString();
    }

    private static Brand detectBrand() {
        String s = (safe(Build.MANUFACTURER) + " " + safe(Build.BRAND) + " "
                + safe(Build.FINGERPRINT)).toLowerCase(Locale.US);
        if (s.contains("xiaomi") || s.contains("redmi") || s.contains("poco")
                || s.contains("blackshark") || miuiProp()) {
            return Brand.XIAOMI;
        }
        if (s.contains("huawei") || s.contains("honor") || s.contains("hihonor")) {
            return Brand.HUAWEI;
        }
        return Brand.OTHER;
    }

    private static String label(Brand b) {
        if (b == Brand.XIAOMI) {
            return "小米 / 红米";
        }
        if (b == Brand.HUAWEI) {
            String brand = safe(Build.BRAND).toLowerCase(Locale.US);
            if (brand.contains("honor")) {
                return "荣耀";
            }
            return "华为";
        }
        return "这台手机";
    }

    private static boolean batteryIgnored(Context c) {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
    }

    private static boolean backgroundRestricted(Context c) {
        if (Build.VERSION.SDK_INT < 28) {
            return false;
        }
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        return am != null && am.isBackgroundRestricted();
    }

    /**
     * MIUI / 澎湃：OP_AUTO_START = 10008。0 已允许，1 被关掉。
     */
    private static Tri xiaomiAutoStart(Context c) {
        AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        if (ops == null) {
            return Tri.UNKNOWN;
        }
        try {
            Method m = ops.getClass().getMethod("checkOpNoThrow", int.class, int.class, String.class);
            Object raw = m.invoke(ops, 10008, Process.myUid(), c.getPackageName());
            if (!(raw instanceof Integer)) {
                return Tri.UNKNOWN;
            }
            int mode = (Integer) raw;
            if (mode == AppOpsManager.MODE_ALLOWED) {
                return Tri.YES;
            }
            if (mode == AppOpsManager.MODE_IGNORED || mode == AppOpsManager.MODE_ERRORED) {
                return Tri.NO;
            }
            return Tri.UNKNOWN;
        } catch (Exception e) {
            return Tri.UNKNOWN;
        }
    }

    private static boolean miuiProp() {
        String a = prop("ro.miui.ui.version.name");
        String b = prop("ro.mi.os.version.name");
        return (a != null && a.length() > 0) || (b != null && b.length() > 0);
    }

    private static String prop(String key) {
        try {
            Class<?> clz = Class.forName("android.os.SystemProperties");
            Method get = clz.getMethod("get", String.class);
            Object v = get.invoke(null, key);
            return v == null ? "" : v.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static Intent component(String pkg, String cls) {
        Intent i = new Intent();
        i.setComponent(new ComponentName(pkg, cls));
        return i;
    }

    private static Intent action(String name) {
        Intent i = new Intent(name);
        i.addCategory(Intent.CATEGORY_DEFAULT);
        return i;
    }

    private static boolean start(Context c, Intent... intents) {
        for (Intent i : intents) {
            if (i == null) {
                continue;
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                c.startActivity(i);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private static String join(List<String> lines) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                b.append('\n');
            }
            b.append("· ").append(lines.get(i));
        }
        return b.toString();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}

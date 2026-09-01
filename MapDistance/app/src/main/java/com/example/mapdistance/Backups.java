package com.example.mapdistance;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 历史轨迹 JSON 快照。写法对齐姊妹项目健康日志：离开 App 且 dirty 时写一份，
 * 另有按月/周/天的定时检查；文件可放用户选的共享文件夹，失败回落到应用目录。
 */
final class Backups {
    static final String DIR_NAME = "backups";
    private static final String PREFIX = "ami-backup-";
    private static final String SUFFIX = ".json";
    private static final Pattern NAME = Pattern.compile(
            "^ami-backup-(\\d{8})-(\\d{6})-(\\d+)\\.json$");
    private static final SimpleDateFormat STAMP =
            new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
    private static final SimpleDateFormat SHOWN =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);

    private static final String PREFS = "backup_prefs";
    private static final String K_AUTO = "auto";
    private static final String K_KEEP = "keep";
    private static final String K_FOLDER = "folder";
    private static final String K_INTERNAL = "use_internal";
    private static final String K_LAST_AT = "last_at";
    private static final String K_LAST_MSG = "last_msg";
    private static final String K_DIRTY = "dirty";
    private static final String K_PERIOD = "period_days";
    private static final String K_LAST_CHECK = "last_check";

    private static final long MIN_INTERVAL_MS = 10 * 60 * 1000L;
    private static final long DAY_MS = 24 * 60 * 60 * 1000L;
    static final int[] KEEP_OPTIONS = {3, 5, 10, 20};
    private static final int DEFAULT_KEEP = 5;

    static final int[] PERIOD_OPTIONS = {30, 7, 1, 0};
    static final String[] PERIOD_LABELS = {"每月", "每周", "每天", "关闭定时"};
    private static final int DEFAULT_PERIOD = 30;
    static final String ACTION_TICK = "com.example.mapdistance.BACKUP_TICK";

    private static final AtomicBoolean BUSY = new AtomicBoolean(false);

    private Backups() {}

    static final class Entry {
        final String name;
        final long time;
        final long size;
        final int rows;
        final File file;
        final Uri uri;

        Entry(String name, long time, long size, int rows, File file, Uri uri) {
            this.name = name;
            this.time = time;
            this.size = size;
            this.rows = rows;
            this.file = file;
            this.uri = uri;
        }

        String title() {
            return SHOWN.format(new Date(time)) + (rows > 0 ? "   " + comma(rows) + " 段" : "");
        }

        String subtitle() {
            return name + "   " + size(size);
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean autoEnabled(Context c) {
        return prefs(c).getBoolean(K_AUTO, true);
    }

    static void setAutoEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean(K_AUTO, on).apply();
        schedule(c);
    }

    static int periodDays(Context c) {
        return prefs(c).getInt(K_PERIOD, DEFAULT_PERIOD);
    }

    static void setPeriodDays(Context c, int days) {
        prefs(c).edit().putInt(K_PERIOD, days).apply();
        schedule(c);
    }

    static String periodLabel(Context c) {
        int days = periodDays(c);
        for (int i = 0; i < PERIOD_OPTIONS.length; i++) {
            if (PERIOD_OPTIONS[i] == days) {
                return PERIOD_LABELS[i];
            }
        }
        return days + " 天";
    }

    static int keep(Context c) {
        return prefs(c).getInt(K_KEEP, DEFAULT_KEEP);
    }

    static void setKeep(Context c, int n) {
        prefs(c).edit().putInt(K_KEEP, n).apply();
    }

    /**
     * 备份目录：未强制「用应用目录」时，优先本面板选过的文件夹，否则沿用「文件夹同步」已选的目录。
     */
    static Uri folder(Context c) {
        if (prefs(c).getBoolean(K_INTERNAL, false)) {
            return null;
        }
        String s = prefs(c).getString(K_FOLDER, null);
        if (s == null || s.isEmpty()) {
            s = Prefs.backupTree(c);
        }
        if (s == null || s.isEmpty()) {
            return null;
        }
        Uri uri = Uri.parse(s);
        for (UriPermission p : c.getContentResolver().getPersistedUriPermissions()) {
            if (p.getUri().equals(uri) && p.isWritePermission()) {
                return uri;
            }
        }
        if (prefs(c).contains(K_FOLDER)) {
            prefs(c).edit().remove(K_FOLDER).apply();
        }
        return null;
    }

    static void setFolder(Context c, Uri uri) {
        SharedPreferences.Editor e = prefs(c).edit();
        if (uri == null) {
            e.putBoolean(K_INTERNAL, true);
            e.remove(K_FOLDER);
        } else {
            e.putBoolean(K_INTERNAL, false);
            e.putString(K_FOLDER, uri.toString());
            Prefs.setBackupTree(c, uri.toString());
            Prefs.setBackupFolderLabel(c, SyncFolder.folderLabel(uri));
        }
        e.apply();
    }

    static File internalDir(Context c) {
        File base = c.getExternalFilesDir(null);
        if (base == null) {
            base = c.getFilesDir();
        }
        File dir = new File(base, DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    static String locationLabel(Context c) {
        Uri f = folder(c);
        if (f != null) {
            return readableTree(f);
        }
        return internalDir(c).getAbsolutePath();
    }

    private static String readableTree(Uri tree) {
        String id;
        try {
            id = DocumentsContract.getTreeDocumentId(tree);
        } catch (RuntimeException e) {
            return tree.toString();
        }
        int colon = id.indexOf(':');
        String vol = colon < 0 ? id : id.substring(0, colon);
        String path = colon < 0 ? "" : id.substring(colon + 1);
        String head = "primary".equals(vol) ? "内部存储" : vol;
        return path.isEmpty() ? head : head + "/" + path;
    }

    static String statusLine(Context c) {
        long at = prefs(c).getLong(K_LAST_AT, 0L);
        String msg = prefs(c).getString(K_LAST_MSG, null);
        StringBuilder sb = new StringBuilder();
        sb.append(at <= 0 ? "还没有备份过"
                : "上次备份 " + SHOWN.format(new Date(at)) + (msg == null ? "" : "  ·  " + msg));
        long check = prefs(c).getLong(K_LAST_CHECK, 0L);
        if (check > 0) {
            sb.append("\n上次定时检查 ").append(SHOWN.format(new Date(check)))
                    .append("（数据未变化，未新建备份）");
        }
        return sb.toString();
    }

    static String scheduleLine(Context c) {
        int days = periodDays(c);
        if (!autoEnabled(c) || days <= 0) {
            return "定时备份已关闭，只在改动前后备份";
        }
        long base = Math.max(prefs(c).getLong(K_LAST_AT, 0L), prefs(c).getLong(K_LAST_CHECK, 0L));
        if (base <= 0) {
            return periodLabel(c) + "检查一次";
        }
        return periodLabel(c) + "检查一次，下次约 " + SHOWN.format(new Date(base + days * DAY_MS));
    }

    static void markDirty(Context c) {
        prefs(c).edit().putBoolean(K_DIRTY, true).apply();
    }

    static boolean due(Context c) {
        if (!autoEnabled(c)) {
            return false;
        }
        if (!prefs(c).getBoolean(K_DIRTY, false)) {
            return false;
        }
        return System.currentTimeMillis() - prefs(c).getLong(K_LAST_AT, 0L) >= MIN_INTERVAL_MS;
    }

    static void schedule(Context c) {
        Context app = c.getApplicationContext();
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        PendingIntent pi = tick(app);
        am.cancel(pi);
        if (!autoEnabled(app) || periodDays(app) <= 0) {
            return;
        }
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + DAY_MS, AlarmManager.INTERVAL_DAY, pi);
    }

    private static PendingIntent tick(Context app) {
        Intent i = new Intent(app, BackupAlarm.class).setAction(ACTION_TICK);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(app, 1, i, flags);
    }

    static String runScheduled(Context c, TrackStore store) {
        if (!scheduledDue(c)) {
            return null;
        }
        if (!prefs(c).getBoolean(K_DIRTY, false) || store.count() == 0) {
            prefs(c).edit().putLong(K_LAST_CHECK, System.currentTimeMillis()).apply();
            return null;
        }
        return create(c, store);
    }

    static boolean scheduledDue(Context c) {
        if (!autoEnabled(c)) {
            return false;
        }
        int days = periodDays(c);
        if (days <= 0) {
            return false;
        }
        long last = Math.max(prefs(c).getLong(K_LAST_AT, 0L), prefs(c).getLong(K_LAST_CHECK, 0L));
        return last <= 0 || System.currentTimeMillis() - last >= days * DAY_MS;
    }

    static String create(Context c, TrackStore store) {
        if (!BUSY.compareAndSet(false, true)) {
            return null;
        }
        try {
            int rows = store.count();
            if (rows == 0) {
                return null;
            }
            String name = PREFIX + STAMP.format(new Date()) + "-" + rows + SUFFIX;
            Uri tree = folder(c);
            if (tree != null) {
                try {
                    writeToTree(c, store, tree, name);
                    rotate(c);
                    markClean(c, comma(rows) + " 段");
                    return "已备份 " + comma(rows) + " 段 · " + name;
                } catch (Exception e) {
                    Log.w("AmiBackup", "backup to picked folder failed", e);
                    prefs(c).edit().putBoolean(K_INTERNAL, true).remove(K_FOLDER).apply();
                }
            }
            try {
                writeToFile(c, store, name);
            } catch (Exception e) {
                Log.w("AmiBackup", "backup failed", e);
                return "自动备份失败：" + reason(e);
            }
            rotate(c);
            boolean fellBack = tree != null;
            markClean(c, comma(rows) + " 段" + (fellBack ? "（所选文件夹不可用，已改存应用目录）" : ""));
            return fellBack
                    ? "所选文件夹不可用，已备份 " + comma(rows) + " 段到应用目录"
                    : "已备份 " + comma(rows) + " 段 · " + name;
        } finally {
            BUSY.set(false);
        }
    }

    private static void markClean(Context c, String msg) {
        prefs(c).edit()
                .putBoolean(K_DIRTY, false)
                .putLong(K_LAST_AT, System.currentTimeMillis())
                .putString(K_LAST_MSG, msg)
                .remove(K_LAST_CHECK)
                .apply();
    }

    private static String reason(Exception e) {
        String m = e.getMessage();
        return m == null || m.isEmpty() ? e.getClass().getSimpleName() : m;
    }

    private static byte[] payload(Context c, TrackStore store) {
        return SyncPack.taggedJson(c, store).getBytes(StandardCharsets.UTF_8);
    }

    private static void writeToFile(Context c, TrackStore store, String name) throws IOException {
        File dir = internalDir(c);
        File tmp = new File(dir, name + ".tmp");
        File out = new File(dir, name);
        OutputStream os = new FileOutputStream(tmp);
        try {
            os.write(payload(c, store));
        } finally {
            os.close();
        }
        if (out.exists() && !out.delete()) {
            tmp.delete();
            throw new IOException("旧备份无法覆盖");
        }
        if (!tmp.renameTo(out)) {
            tmp.delete();
            throw new IOException("备份重命名失败");
        }
    }

    private static void writeToTree(Context c, TrackStore store, Uri tree, String name)
            throws IOException {
        ContentResolver cr = c.getContentResolver();
        Uri dir = DocumentsContract.buildDocumentUriUsingTree(tree,
                DocumentsContract.getTreeDocumentId(tree));
        Uri doc = DocumentsContract.createDocument(cr, dir, "application/json", name);
        if (doc == null) {
            throw new IOException("无法在所选文件夹创建文件");
        }
        try {
            OutputStream os = cr.openOutputStream(doc);
            if (os == null) {
                throw new IOException("无法写入所选文件夹");
            }
            try {
                os.write(payload(c, store));
            } finally {
                os.close();
            }
        } catch (IOException | RuntimeException e) {
            try {
                DocumentsContract.deleteDocument(cr, doc);
            } catch (Exception ignored) {
            }
            throw e instanceof IOException ? (IOException) e : new IOException(e.getMessage(), e);
        }
    }

    static List<Entry> list(Context c) {
        Uri tree = folder(c);
        List<Entry> out = tree != null ? listTree(c, tree) : listFiles(c);
        Collections.sort(out, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return Long.compare(b.time, a.time);
            }
        });
        return out;
    }

    private static List<Entry> listFiles(Context c) {
        List<Entry> out = new ArrayList<>();
        File[] files = internalDir(c).listFiles();
        if (files == null) {
            return out;
        }
        for (File f : files) {
            Matcher m = NAME.matcher(f.getName());
            if (!f.isFile() || !m.matches()) {
                continue;
            }
            out.add(new Entry(f.getName(), timeOf(m, f.lastModified()), f.length(),
                    Integer.parseInt(m.group(3)), f, null));
        }
        return out;
    }

    private static List<Entry> listTree(Context c, Uri tree) {
        List<Entry> out = new ArrayList<>();
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree,
                DocumentsContract.getTreeDocumentId(tree));
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(children, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            }, null, null, null);
            while (cur != null && cur.moveToNext()) {
                String name = cur.getString(1);
                if (name == null) {
                    continue;
                }
                Matcher m = NAME.matcher(name);
                if (!m.matches()) {
                    continue;
                }
                out.add(new Entry(name, timeOf(m, cur.isNull(3) ? 0 : cur.getLong(3)),
                        cur.isNull(2) ? 0 : cur.getLong(2), Integer.parseInt(m.group(3)), null,
                        DocumentsContract.buildDocumentUriUsingTree(tree, cur.getString(0))));
            }
        } catch (Exception e) {
            Log.w("AmiBackup", "list failed", e);
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
        return out;
    }

    private static long timeOf(Matcher m, long fallback) {
        try {
            return STAMP.parse(m.group(1) + "-" + m.group(2)).getTime();
        } catch (Exception e) {
            return fallback;
        }
    }

    static InputStream open(Context c, Entry e) throws IOException {
        if (e.file != null) {
            return new FileInputStream(e.file);
        }
        InputStream in = c.getContentResolver().openInputStream(e.uri);
        if (in == null) {
            throw new IOException("无法读取备份文件");
        }
        return in;
    }

    static String restore(Context c, TrackStore store, Entry e) throws IOException {
        InputStream in = open(c, e);
        try {
            List<TrackSession> sessions = SyncPack.parseSessions(SyncPack.decode(SyncPack.readAll(in)));
            if (sessions.isEmpty()) {
                return "备份文件里没有有效数据";
            }
            int n = store.replaceAll(sessions);
            return "已恢复 " + n + " 段";
        } finally {
            in.close();
        }
    }

    static boolean delete(Context c, Entry e) {
        try {
            if (e.file != null) {
                return e.file.delete();
            }
            return DocumentsContract.deleteDocument(c.getContentResolver(), e.uri);
        } catch (Exception ex) {
            Log.w("AmiBackup", "delete failed", ex);
            return false;
        }
    }

    private static void rotate(Context c) {
        List<Entry> all = list(c);
        for (int i = keep(c); i < all.size(); i++) {
            delete(c, all.get(i));
        }
    }

    static void rotateNow(Context c) {
        rotate(c);
    }

    static long totalSize(List<Entry> list) {
        long n = 0;
        for (Entry e : list) {
            n += e.size;
        }
        return n;
    }

    static String size(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.US, "%.0f KB", kb);
        }
        return String.format(Locale.US, "%.1f MB", kb / 1024.0);
    }

    static String comma(int n) {
        return String.format(Locale.US, "%,d", n);
    }
}

package com.example.mapdistance;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 共享文件夹读写 sync_*.json，给多机文件夹同步用。 */
public final class SyncFolder {
    private SyncFolder() {
    }

    public static final class TreeDoc {
        public final String name;
        public final Uri uri;
        public final long modified;

        TreeDoc(String name, Uri uri, long modified) {
            this.name = name;
            this.uri = uri;
            this.modified = modified;
        }
    }

    public static String folderLabel(Uri tree) {
        if (tree == null) {
            return "";
        }
        try {
            String id = DocumentsContract.getTreeDocumentId(tree);
            int colon = id.indexOf(':');
            String path = colon >= 0 ? id.substring(colon + 1) : id;
            return path == null || path.isEmpty() ? "已选文件夹" : path;
        } catch (Exception e) {
            return "已选文件夹";
        }
    }

    public static List<TreeDoc> listTree(Context ctx, String prefix, String suffix) {
        List<TreeDoc> out = new ArrayList<>();
        String saved = Prefs.backupTree(ctx);
        if (saved == null || saved.isEmpty()) {
            return out;
        }
        Cursor c = null;
        try {
            Uri tree = Uri.parse(saved);
            String docId = DocumentsContract.getTreeDocumentId(tree);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
            String[] cols = new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
            };
            try {
                c = ctx.getContentResolver().query(children, cols, null, null, null);
            } catch (Exception e) {
                cols = new String[]{
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME
                };
                c = ctx.getContentResolver().query(children, cols, null, null, null);
            }
            if (c == null) {
                return out;
            }
            while (c.moveToNext()) {
                String name = c.getString(1);
                if (name == null) {
                    continue;
                }
                if (prefix != null && !name.startsWith(prefix)) {
                    continue;
                }
                if (suffix != null && !name.endsWith(suffix)) {
                    continue;
                }
                long modified = 0L;
                if (c.getColumnCount() > 2) {
                    try {
                        modified = c.getLong(2);
                    } catch (Exception ignored) {
                        modified = 0L;
                    }
                }
                Uri file = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0));
                out.add(new TreeDoc(name, file, modified));
            }
        } catch (Exception ignored) {
            return out;
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return out;
    }

    public static byte[] readTree(Context ctx, Uri file) throws IOException {
        InputStream in = ctx.getContentResolver().openInputStream(file);
        if (in == null) {
            throw new IOException("无法读取文件");
        }
        try {
            return SyncPack.readAll(in);
        } finally {
            in.close();
        }
    }

    public static String writeToTree(Context ctx, String body, String name) {
        String saved = Prefs.backupTree(ctx);
        if (saved == null || saved.isEmpty()) {
            return "；未选择同步文件夹";
        }
        try {
            Uri tree = Uri.parse(saved);
            Uri existing = findChild(ctx, tree, name);
            Uri file = existing;
            if (file == null) {
                Uri dirUri = DocumentsContract.buildDocumentUriUsingTree(
                        tree, DocumentsContract.getTreeDocumentId(tree));
                file = DocumentsContract.createDocument(
                        ctx.getContentResolver(), dirUri, "application/json", name);
            }
            if (file == null) {
                return "；所选文件夹写入失败";
            }
            OutputStream out = ctx.getContentResolver().openOutputStream(file, "w");
            if (out == null) {
                out = ctx.getContentResolver().openOutputStream(file);
            }
            if (out == null) {
                return "；所选文件夹无法打开";
            }
            try {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            } finally {
                out.close();
            }
            return "";
        } catch (Exception e) {
            return "；外置写入失败";
        }
    }

    static Uri findChild(Context ctx, Uri tree, String name) {
        Cursor c = null;
        try {
            String docId = DocumentsContract.getTreeDocumentId(tree);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
            c = ctx.getContentResolver().query(children, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
            }, null, null, null);
            if (c == null) {
                return null;
            }
            while (c.moveToNext()) {
                if (name.equals(c.getString(1))) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0));
                }
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return null;
    }
}

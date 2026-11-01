package com.l3j3y3.codeexplainer;

import android.app.Activity;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;

import androidx.activity.result.ActivityResult;
import androidx.documentfile.provider.DocumentFile;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 让网页读手机上一个文件夹里的代码文件。
 *
 * 为什么不用 @capacitor/filesystem：Android 11+ 起 Directory.Documents 只能读
 * 本 App 自己创建的文件，读不了用户放进来的代码；Directory.ExternalStorage 更是
 * Android 9 以上就没有了（见 Capacitor 官方文档）。所以走 SAF：
 * 用户用系统选择器点头授权一个目录，我们拿持久读权限，之后想读哪个文件读哪个。
 *
 * 全程不需要 READ_EXTERNAL_STORAGE，也不往别人的文件夹里写东西 —— 只读。
 */
@CapacitorPlugin(name = "FolderSource")
public class FolderSourcePlugin extends Plugin {

    private static final String PREF = "ce";
    private static final String KEY_TREE = "treeUri";
    private static final int MAX_BYTES = 200 * 1024;

    /* ---------- 选文件夹 ---------- */

    @PluginMethod
    public void pick(PluginCall call) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(call, intent, "onPicked");
    }

    @ActivityCallback
    private void onPicked(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() != Activity.RESULT_OK
                || result.getData() == null || result.getData().getData() == null) {
            call.reject("没选文件夹");
            return;
        }
        Uri uri = result.getData().getData();
        try {
            getContext().getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
            // 有些系统/目录不给持久授权，这一次用完就算了，restore 时会自然失效
        }
        getContext().getSharedPreferences(PREF, 0).edit()
                .putString(KEY_TREE, uri.toString()).apply();
        JSObject ret = new JSObject();
        ret.put("uri", uri.toString());
        ret.put("name", displayName(uri));
        call.resolve(ret);
    }

    /* ---------- 恢复上次选的 ---------- */

    @PluginMethod
    public void restore(PluginCall call) {
        Uri uri = savedTree();
        JSObject ret = new JSObject();
        if (uri == null) {
            ret.put("uri", "");
            call.resolve(ret);
            return;
        }
        ret.put("uri", uri.toString());
        ret.put("name", displayName(uri));
        call.resolve(ret);
    }

    /* ---------- 列一层（sub 是相对根的子路径，可空） ---------- */

    @PluginMethod
    public void list(PluginCall call) {
        String sub = call.getString("sub", "");
        if (sub == null) sub = "";
        Uri tree = savedTree();
        if (tree == null) {
            call.reject("还没选文件夹，或者授权已经失效了");
            return;
        }
        DocumentFile dir = DocumentFile.fromTreeUri(getContext(), tree);
        if (dir == null || !dir.isDirectory()) {
            call.reject("这个文件夹打不开了");
            return;
        }
        try {
            if (!sub.isEmpty()) {
                for (String part : sub.split("/")) {
                    if (part.isEmpty()) continue;
                    DocumentFile next = dir.findFile(part);
                    if (next == null || !next.isDirectory()) {
                        call.reject("找不到子文件夹：" + part);
                        return;
                    }
                    dir = next;
                }
            }
            DocumentFile[] kids = dir.listFiles();
            JSArray arr = new JSArray();
            for (DocumentFile f : kids) {
                String name = f.getName();
                if (name == null || name.isEmpty() || name.startsWith(".")) continue;
                JSObject o = new JSObject();
                o.put("name", name);
                o.put("isDir", f.isDirectory());
                o.put("size", f.length());
                o.put("uri", f.getUri().toString());
                o.put("sub", sub.isEmpty() ? name : sub + "/" + name);
                arr.put(o);
            }
            JSObject ret = new JSObject();
            ret.put("items", arr);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("列不出来：" + e.getMessage());
        }
    }

    /* ---------- 读一个文件 ---------- */

    @PluginMethod
    public void read(PluginCall call) {
        String u = call.getString("uri");
        if (u == null || u.isEmpty()) {
            call.reject("没给文件");
            return;
        }
        Uri uri = Uri.parse(u);
        try (InputStream in = getContext().getContentResolver().openInputStream(uri)) {
            if (in == null) {
                call.reject("打不开这个文件");
                return;
            }
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_BYTES) {
                    call.reject("这个文件超过 200KB，先在别处切一段再试");
                    return;
                }
                bo.write(buf, 0, n);
            }
            byte[] bytes = bo.toByteArray();
            JSObject ret = new JSObject();
            ret.put("text", decodeText(bytes));
            ret.put("bytes", total);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("读不出来：" + e.getMessage());
        }
    }

    /* ---------- 内部 ---------- */

    /** UTF-8 严格解码，失败回退 GBK（国内 .cpp/.txt 常见）。 */
    private String decodeText(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (Exception e) {
            try {
                return new String(bytes, "GBK");
            } catch (Exception e2) {
                return new String(bytes, StandardCharsets.ISO_8859_1);
            }
        }
    }

    private Uri savedTree() {
        String s = getContext().getSharedPreferences(PREF, 0).getString(KEY_TREE, "");
        if (s == null || s.isEmpty()) return null;
        Uri uri = Uri.parse(s);
        // 权限可能被系统或用户在设置里收回，收回就当没选过
        for (UriPermission p : getContext().getContentResolver().getPersistedUriPermissions()) {
            if (p.getUri().equals(uri) && p.isReadPermission()) return uri;
        }
        return null;
    }

    private String displayName(Uri uri) {
        DocumentFile df = DocumentFile.fromTreeUri(getContext(), uri);
        String n = df == null ? null : df.getName();
        if (n == null || n.isEmpty()) {
            List<String> segs = uri.getPathSegments();
            n = segs.isEmpty() ? "已选文件夹" : segs.get(segs.size() - 1);
        }
        return n;
    }
}

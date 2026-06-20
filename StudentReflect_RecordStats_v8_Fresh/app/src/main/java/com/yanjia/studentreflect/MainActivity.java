package com.yanjia.studentreflect;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private WebView webView;
    private long backgroundStartMs = 0L;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onPause() {
        super.onPause();
        backgroundStartMs = System.currentTimeMillis();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (backgroundStartMs > 0 && webView != null) {
            final long start = backgroundStartMs;
            final long end = System.currentTimeMillis();
            backgroundStartMs = 0;
            webView.postDelayed(() -> {
                String js = "if(window.appOnResumeFromAndroid){window.appOnResumeFromAndroid(" + start + "," + end + ");}";
                if (Build.VERSION.SDK_INT >= 19) webView.evaluateJavascript(js, null);
                else webView.loadUrl("javascript:" + js);
            }, 250);
        }
    }

    public class Bridge {
        @JavascriptInterface
        public String getOwnPackage() { return getPackageName(); }

        @JavascriptInterface
        public boolean hasUsagePermission() {
            try {
                AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
                int mode;
                if (Build.VERSION.SDK_INT >= 29) {
                    mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName());
                } else {
                    mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName());
                }
                return mode == AppOpsManager.MODE_ALLOWED;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public void openUsageSettings() {
            try {
                Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                try {
                    Intent intent = new Intent(Settings.ACTION_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception ignored) {}
            }
        }

        @JavascriptInterface
        public String queryUsageEvents(long startMs, long endMs) {
            JSONArray arr = new JSONArray();
            if (!hasUsagePermission()) return arr.toString();
            try {
                UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
                UsageEvents events = usm.queryEvents(Math.max(0, startMs - 1000), endMs + 1000);
                UsageEvents.Event ev = new UsageEvents.Event();
                Map<String, Long> foregroundStarts = new HashMap<>();
                while (events.hasNextEvent()) {
                    events.getNextEvent(ev);
                    String pkg = ev.getPackageName();
                    if (pkg == null) continue;
                    int type = ev.getEventType();
                    boolean resume = type == UsageEvents.Event.MOVE_TO_FOREGROUND;
                    boolean pause = type == UsageEvents.Event.MOVE_TO_BACKGROUND;
                    if (Build.VERSION.SDK_INT >= 29) {
                        resume = resume || type == UsageEvents.Event.ACTIVITY_RESUMED;
                        pause = pause || type == UsageEvents.Event.ACTIVITY_PAUSED;
                    }
                    long t = ev.getTimeStamp();
                    if (resume) {
                        foregroundStarts.put(pkg, t);
                    } else if (pause) {
                        Long st = foregroundStarts.remove(pkg);
                        if (st != null) addUsage(arr, pkg, Math.max(st, startMs), Math.min(t, endMs));
                    }
                }
                long nowEnd = endMs;
                for (Map.Entry<String, Long> e : foregroundStarts.entrySet()) {
                    addUsage(arr, e.getKey(), Math.max(e.getValue(), startMs), nowEnd);
                }
            } catch (Exception e) {
                try {
                    JSONObject o = new JSONObject();
                    o.put("error", e.toString());
                    arr.put(o);
                } catch (Exception ignored) {}
            }
            return arr.toString();
        }

        private void addUsage(JSONArray arr, String pkg, long st, long en) throws Exception {
            if (en <= st) return;
            JSONObject o = new JSONObject();
            o.put("package", pkg);
            o.put("label", getAppLabel(pkg));
            o.put("start", st);
            o.put("end", en);
            o.put("duration", en - st);
            arr.put(o);
        }

        private String getAppLabel(String pkg) {
            try {
                PackageManager pm = getPackageManager();
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                CharSequence label = pm.getApplicationLabel(ai);
                return label == null ? pkg : label.toString();
            } catch (Exception e) {
                return pkg;
            }
        }

        @JavascriptInterface
        public void copyToClipboard(String text) {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("student_reflect_json", text == null ? "" : text));
                Toast.makeText(MainActivity.this, "已复制", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "复制失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }

        @JavascriptInterface
        public String saveTextToDownloads(String filename, String text) {
            if (filename == null || filename.trim().isEmpty()) filename = "student_reflect_backup.json";
            if (!filename.endsWith(".json") && !filename.endsWith(".txt")) filename += ".json";
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentResolver resolver = getContentResolver();
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/StudentReflect");
                    Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new Exception("无法创建文件");
                    try (OutputStream os = resolver.openOutputStream(uri)) {
                        if (os == null) throw new Exception("无法打开文件");
                        os.write((text == null ? "" : text).getBytes("UTF-8"));
                    }
                    return "已保存到 下载/StudentReflect/" + filename;
                } else {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "StudentReflect");
                    if (!dir.exists()) dir.mkdirs();
                    File file = new File(dir, filename);
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        fos.write((text == null ? "" : text).getBytes("UTF-8"));
                    }
                    return "已保存到 " + file.getAbsolutePath();
                }
            } catch (Exception e) {
                return "保存失败：" + e.getMessage();
            }
        }
    }
}

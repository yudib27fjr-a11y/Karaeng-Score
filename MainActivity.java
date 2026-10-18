package com.karaengfc.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String HOME = "https://" + HOST + "/assets/index.html";
    private static final int PICK_FILE = 41;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        setContentView(web);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (HOST.equals(uri.getHost())) return false;
                openExternal(uri);
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), PICK_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        if (state != null) web.restoreState(state);
        else web.loadUrl(HOME);
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) {
            toast("Tidak ada aplikasi untuk membuka tautan ini");
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_FILE && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    private String saveBytes(String name, byte[] data, String mime) throws Exception {
        boolean image = mime.startsWith("image/");
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            String folder = (image ? Environment.DIRECTORY_PICTURES : Environment.DIRECTORY_DOWNLOADS) + "/Karaeng FC";
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, folder);
            Uri target = image ? MediaStore.Images.Media.EXTERNAL_CONTENT_URI : MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            Uri uri = getContentResolver().insert(target, cv);
            if (uri == null) throw new Exception("tidak bisa membuat file");
            try (OutputStream o = getContentResolver().openOutputStream(uri)) {
                o.write(data);
            }
            return folder;
        } else {
            File dir = getExternalFilesDir(image ? Environment.DIRECTORY_PICTURES : Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) dir = getFilesDir();
            dir.mkdirs();
            try (FileOutputStream o = new FileOutputStream(new File(dir, name))) {
                o.write(data);
            }
            return dir.getAbsolutePath();
        }
    }

    private class Bridge {
        @JavascriptInterface
        public void saveFile(final String name, final String b64, final String mime) {
            runOnUiThread(() -> {
                try {
                    String where = saveBytes(name, Base64.decode(b64, Base64.DEFAULT), mime);
                    toast("Tersimpan di " + where + ": " + name);
                } catch (Exception e) {
                    toast("Gagal menyimpan: " + e.getMessage());
                }
            });
        }

        @JavascriptInterface
        public void shareFile(final String name, final String b64, final String mime, final String text) {
            runOnUiThread(() -> {
                try {
                    File dir = new File(getCacheDir(), "shared");
                    dir.mkdirs();
                    File f = new File(dir, name);
                    try (FileOutputStream o = new FileOutputStream(f)) {
                        o.write(Base64.decode(b64, Base64.DEFAULT));
                    }
                    Uri uri = FileProvider.getUriForFile(MainActivity.this, "com.karaengfc.app.files", f);
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType(mime);
                    i.putExtra(Intent.EXTRA_STREAM, uri);
                    if (text != null && !text.isEmpty()) i.putExtra(Intent.EXTRA_TEXT, text);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(i, "Bagikan"));
                } catch (Exception e) {
                    toast("Gagal membagikan: " + e.getMessage());
                }
            });
        }

        @JavascriptInterface
        public void copyText(final String text) {
            runOnUiThread(() -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Karaeng FC", text));
            });
        }
    }
}

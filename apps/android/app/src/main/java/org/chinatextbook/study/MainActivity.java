package org.chinatextbook.study;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.UiModeManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.webkit.WebViewAssetLoader;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.JavascriptInterface;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Embedded Android/Android TV client with optional USB media import. */
public class MainActivity extends Activity {
    private static final int MICROPHONE = 10;
    private static final int PICK_FILE = 11;
    private static final int PICK_MEDIA = 12;
    private static final int STORAGE = 13;
    private final OriginPolicy policy = new OriginPolicy(BuildConfig.WEB_APP_URL);
    private WebViewAssetLoader assetLoader;
    private WebView web;
    private ProgressBar loading;
    private LinearLayout errorPanel;
    private File mediaRoot;
    private PermissionRequest microphoneRequest;
    private ValueCallback<Uri[]> fileCallback;
    private boolean resumed;
    private boolean awaitingRuntimePermission;
    private boolean television;
    private boolean importingMedia;
    private boolean pendingUsbScan;
    private TextToSpeech speech;
    private boolean speechReady;
    private String pendingSpeech;
    private String retryUrl = BuildConfig.WEB_APP_URL;

    @Override @SuppressLint("SetJavaScriptEnabled")
    public void onCreate(Bundle state) {
        super.onCreate(state);
        UiModeManager uiMode = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        int uiModeType = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_TYPE_MASK);
        television = uiModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
                (uiMode != null && uiMode.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        web = new WebView(this);
        mediaRoot = MediaLibrary.root(getFilesDir());
        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/", new AssetsSitePathHandler(getAssets(), mediaRoot))
                .build();
        initializeSpeech();
        web.addJavascriptInterface(new TtsBridge(), "AndroidTts");
        web.addJavascriptInterface(new MediaImportBridge(), "MediaImport");
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        loading = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        root.addView(loading, new FrameLayout.LayoutParams(-1, dp(3), Gravity.TOP));
        errorPanel = new LinearLayout(this);
        errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setGravity(Gravity.CENTER);
        errorPanel.setPadding(dp(24), dp(24), dp(24), dp(24));
        errorPanel.setBackgroundColor(Color.WHITE);
        TextView message = new TextView(this);
        message.setText(R.string.connection_failed);
        message.setTextSize(18);
        message.setTextColor(Color.DKGRAY);
        message.setGravity(Gravity.CENTER);
        errorPanel.addView(message);
        Button retry = new Button(this);
        retry.setText(R.string.retry);
        retry.setOnClickListener(v -> web.loadUrl(retryUrl));
        errorPanel.addView(retry);
        root.addView(errorPanel, new FrameLayout.LayoutParams(-1, -1));
        errorPanel.setVisibility(View.GONE);
        setContentView(root);
        // API 35 enforces edge-to-edge. Keep lesson controls out of system bars and keyboard.
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets edges = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                v.setPadding(edges.left, edges.top, edges.right, edges.bottom);
                return insets;
            });
            root.requestApplyInsets();
        }
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (television) {
            // Lets the shared web app enable its D-pad navigation without a bridge.
            settings.setUserAgentString(settings.getUserAgentString() + " ChinaStudyFreeTV");
            // Honour the page's TV viewport (width=1280) and scale it to the screen.
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
        }
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(
                    WebView view, WebResourceRequest request) {
                WebResourceResponse local = assetLoader.shouldInterceptRequest(request.getUrl());
                return local != null ? local : super.shouldInterceptRequest(view, request);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (policy.isTrusted(url)) return false;
                openExternal(url);
                return true;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (policy.isTrusted(url)) return false;
                if (request.isForMainFrame() && request.hasGesture()) openExternal(url);
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                cancelMicrophone();
                if (!policy.isTrusted(url)) { view.stopLoading(); return; }
                retryUrl = url;
                errorPanel.setVisibility(View.GONE);
                loading.setVisibility(View.VISIBLE);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showError();
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame() && response.getStatusCode() >= 400) showError();
            }
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (television) view.requestFocus(View.FOCUS_DOWN);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int progress) {
                loading.setProgress(progress);
                if (progress == 100) loading.setVisibility(View.GONE);
            }
            @Override public void onPermissionRequest(PermissionRequest request) {
                cancelMicrophone();
                boolean wantsAudio = false;
                for (String resource : request.getResources()) {
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) wantsAudio = true;
                }
                if (!resumed || !wantsAudio || !policy.isTrusted(request.getOrigin().toString())
                        || !policy.isTrusted(web.getUrl())) { request.deny(); return; }
                microphoneRequest = request;
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    completeMicrophone(true);
                } else {
                    awaitingRuntimePermission = true;
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MICROPHONE);
                }
            }
            @Override public void onPermissionRequestCanceled(PermissionRequest request) {
                if (microphoneRequest == request) microphoneRequest = null;
            }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try { startActivityForResult(params.createIntent(), PICK_FILE); }
                catch (ActivityNotFoundException e) {
                    fileCallback.onReceiveValue(null);
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, R.string.file_picker_missing, Toast.LENGTH_LONG).show();
                }
                return true;
            }
        });
        web.setDownloadListener((url, agent, disposition, mime, length) -> {
            if (policy.isTrusted(url)) openExternal(url);
            else Toast.makeText(this, R.string.download_in_browser, Toast.LENGTH_LONG).show();
        });
        if (state == null || web.restoreState(state) == null || !policy.isTrusted(web.getUrl())) {
            web.loadUrl(BuildConfig.WEB_APP_URL);
        } else retryUrl = web.getUrl();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void initializeSpeech() {
        speech = new TextToSpeech(getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                int language = speech.setLanguage(Locale.SIMPLIFIED_CHINESE);
                speechReady = language != TextToSpeech.LANG_MISSING_DATA
                        && language != TextToSpeech.LANG_NOT_SUPPORTED;
                speech.setSpeechRate(0.9f);
            }
            if (speechReady && pendingSpeech != null) {
                String text = pendingSpeech;
                pendingSpeech = null;
                speakText(text);
            }
        });
    }
    private void speakText(String text) {
        if (!speechReady || speech == null) {
            pendingSpeech = text;
            return;
        }
        speech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cstf-" + System.nanoTime());
    }
    private final class TtsBridge {
        @JavascriptInterface public boolean speak(String text) {
            String value = text == null ? "" : text.trim();
            if (value.isEmpty()) return false;
            runOnUiThread(() -> speakText(value));
            return true;
        }
        @JavascriptInterface public void stop() {
            runOnUiThread(() -> {
                pendingSpeech = null;
                if (speech != null) speech.stop();
            });
        }
    }

    private final class MediaImportBridge {
        @JavascriptInterface public boolean open() {
            runOnUiThread(MainActivity.this::openMediaPicker);
            return true;
        }
        @JavascriptInterface public String status() {
            return MediaLibrary.statusJson(getFilesDir());
        }
        @JavascriptInterface public boolean clear() {
            runOnUiThread(MainActivity.this::confirmClearMedia);
            return true;
        }
    }

    private void openMediaPicker() {
        if (importingMedia) {
            Toast.makeText(this, R.string.media_import_running, Toast.LENGTH_SHORT).show();
            return;
        }
        // Many TV firmwares strip DocumentsUI, and the system then shows an empty "no app" chooser.
        if (television || !startSystemPicker()) openUsbArchives();
    }

    private void openSystemPicker() {
        if (!startSystemPicker()) Toast.makeText(this, R.string.file_picker_missing, Toast.LENGTH_LONG).show();
    }

    private boolean startSystemPicker() {
        return startPicker(Intent.ACTION_OPEN_DOCUMENT) || startPicker(Intent.ACTION_GET_CONTENT);
    }

    private boolean startPicker(String action) {
        Intent intent = new Intent(action);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isEmpty()) {
            return false;
        }
        try {
            startActivityForResult(intent, PICK_MEDIA);
            return true;
        } catch (ActivityNotFoundException | SecurityException error) {
            return false;
        }
    }

    private void openUsbArchives() {
        if (!hasStorageAccess()) {
            explainStorageAccess();
            return;
        }
        showArchiveChooser(UsbArchiveScanner.find(storageRoots()));
    }

    private boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void explainStorageAccess() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.usb_access_title)
                .setMessage(Build.VERSION.SDK_INT >= 30
                        ? R.string.usb_access_message_settings : R.string.usb_access_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.usb_use_system_picker, (dialog, which) -> openSystemPicker())
                .setPositiveButton(R.string.usb_access_grant, (dialog, which) -> requestStorageAccess())
                .show();
    }

    private void requestStorageAccess() {
        if (Build.VERSION.SDK_INT < 30) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE);
            return;
        }
        Uri app = Uri.fromParts("package", getPackageName(), null);
        pendingUsbScan = startSettings(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, app))
                || startSettings(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                || startSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app));
        if (!pendingUsbScan) Toast.makeText(this, R.string.usb_access_settings_missing, Toast.LENGTH_LONG).show();
    }

    private boolean startSettings(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException error) {
            return false;
        }
    }

    private List<File> storageRoots() {
        List<File> roots = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 30) {
            StorageManager storage = getSystemService(StorageManager.class);
            if (storage != null) {
                for (StorageVolume volume : storage.getStorageVolumes()) addRoot(roots, volume.getDirectory());
            }
        }
        for (File dir : getExternalFilesDirs(null)) addRoot(roots, UsbArchiveScanner.volumeRoot(dir));
        // Vendor TV images often mount USB drives outside StorageManager's public volumes.
        for (String parent : new String[]{"/storage", "/mnt/usb", "/mnt/usb_storage"}) {
            File[] children = new File(parent).listFiles();
            if (children == null) continue;
            for (File child : children) {
                String name = child.getName();
                if (!name.equals("emulated") && !name.equals("self")) addRoot(roots, child);
            }
        }
        return roots;
    }

    private static void addRoot(List<File> roots, File root) {
        if (root != null && !roots.contains(root)) roots.add(root);
    }

    private void showArchiveChooser(List<File> archives) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.usb_pick_title)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.usb_use_system_picker, (dialog, which) -> openSystemPicker());
        if (archives.isEmpty()) {
            builder.setMessage(R.string.usb_none_found)
                    .setPositiveButton(R.string.usb_rescan, (dialog, which) -> openUsbArchives())
                    .show();
            return;
        }
        String[] labels = new String[archives.size()];
        boolean[] checked = new boolean[archives.size()];
        for (int i = 0; i < labels.length; i++) {
            File archive = archives.get(i);
            labels[i] = getString(R.string.usb_archive_item, archive.getName(),
                    Math.max(1, archive.length() / (1024 * 1024)), archive.getParent());
            checked[i] = MediaArchiveImporter.expectedSha256(archive.getName()) != null;
        }
        builder.setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.usb_import, (dialog, which) -> {
                    List<Uri> uris = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) uris.add(Uri.fromFile(archives.get(i)));
                    }
                    if (uris.isEmpty()) Toast.makeText(this, R.string.usb_none_selected, Toast.LENGTH_SHORT).show();
                    else importMedia(uris);
                })
                .show();
    }

    private void importMedia(List<Uri> uris) {
        if (importingMedia || uris.isEmpty()) return;
        importingMedia = true;
        loading.setIndeterminate(true);
        loading.setVisibility(View.VISIBLE);
        Toast.makeText(this, R.string.media_import_started, Toast.LENGTH_LONG).show();
        new Thread(() -> {
            int files = 0;
            long bytes = 0;
            String failure = null;
            for (Uri uri : uris) {
                String name = mediaDisplayName(uri);
                try {
                    String expected = MediaArchiveImporter.expectedSha256(name);
                    if (expected != null) {
                        InputStream verify = getContentResolver().openInputStream(uri);
                        if (verify == null) throw new IOException("Cannot open " + name);
                        if (!expected.equalsIgnoreCase(MediaArchiveImporter.sha256(verify))) {
                            throw new IOException("SHA-256 mismatch");
                        }
                    }
                    MediaArchiveImporter.Result result;
                    try (InputStream input = getContentResolver().openInputStream(uri)) {
                        if (input == null) throw new IOException("Cannot open " + name);
                        result = MediaArchiveImporter.importArchive(mediaRoot, input, null);
                    }
                    if (result.files == 0) throw new IOException("No media found");
                    files += result.files;
                    bytes += result.bytes;
                } catch (Exception error) {
                    failure = name + ": " + error.getMessage();
                    break;
                }
            }
            final int importedFiles = files;
            final long importedBytes = bytes;
            final String importFailure = failure;
            runOnUiThread(() -> {
                importingMedia = false;
                loading.setIndeterminate(false);
                loading.setVisibility(View.GONE);
                if (importFailure != null) {
                    Toast.makeText(this,
                            getString(R.string.media_import_failed, importFailure),
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this,
                            getString(R.string.media_import_done, importedFiles,
                                    importedBytes / (1024 * 1024)),
                            Toast.LENGTH_LONG).show();
                }
                if (web != null) web.reload();
            });
        }, "cstf-media-import").start();
    }

    private String mediaDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (RuntimeException ignored) {}
        String fallback = uri.getLastPathSegment();
        return fallback == null ? "media archive" : fallback;
    }

    private void confirmClearMedia() {
        if (importingMedia) {
            Toast.makeText(this, R.string.media_import_running, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.media_clear_title)
                .setMessage(R.string.media_clear_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.media_clear_confirm, (dialog, which) -> {
                    MediaLibrary.deleteTree(mediaRoot);
                    Toast.makeText(this, R.string.media_clear_done, Toast.LENGTH_SHORT).show();
                    if (web != null) web.reload();
                })
                .show();
    }
    private void showError() { loading.setVisibility(View.GONE); errorPanel.setVisibility(View.VISIBLE); }
    private void openExternal(String url) {
        if (!OriginPolicy.isExternalHttps(url)) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (ActivityNotFoundException ignored) { /* No browser installed. */ }
    }
    private void completeMicrophone(boolean granted) {
        PermissionRequest request = microphoneRequest;
        microphoneRequest = null;
        if (request == null) return;
        if (granted && resumed && policy.isTrusted(web.getUrl()) && policy.isTrusted(request.getOrigin().toString())) {
            request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
        } else request.deny();
    }
    private void cancelMicrophone() { completeMicrophone(false); }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == MICROPHONE) {
            awaitingRuntimePermission = false;
            boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            // Permission dialogs can temporarily pause the Activity. Finish on resume in that case.
            if (!granted || resumed) completeMicrophone(granted);
        } else if (code == STORAGE) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) openUsbArchives();
            else Toast.makeText(this, R.string.usb_access_denied, Toast.LENGTH_LONG).show();
        }
    }
    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == PICK_MEDIA && result == RESULT_OK && data != null) {
            List<Uri> uris = new ArrayList<>();
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) {
                    uris.add(clip.getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            importMedia(uris);
            return;
        }
        if (code == PICK_FILE && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            fileCallback = null;
        }
    }
    @Override protected void onSaveInstanceState(Bundle state) { web.saveState(state); super.onSaveInstanceState(state); }
    @Override protected void onResume() {
        super.onResume(); resumed = true;
        if (web != null) web.onResume();
        if (microphoneRequest != null && !awaitingRuntimePermission) {
            completeMicrophone(checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
        }
        if (pendingUsbScan) {
            pendingUsbScan = false;
            if (hasStorageAccess()) openUsbArchives();
            else Toast.makeText(this, R.string.usb_access_denied, Toast.LENGTH_LONG).show();
        }
    }
    @Override protected void onPause() {
        resumed = false;
        if (!awaitingRuntimePermission) cancelMicrophone();
        if (web != null) web.onPause();
        super.onPause();
    }
    @Override public void onBackPressed() {
        if (importingMedia) {
            Toast.makeText(this, R.string.media_import_running, Toast.LENGTH_SHORT).show();
            return;
        }
        // Back never reaches the page as a key event; let an open web dialog close first.
        web.evaluateJavascript("!!(window.__cstfHandleBack && window.__cstfHandleBack())", handled -> {
            if ("true".equals(handled)) return;
            if (web.canGoBack()) web.goBack(); else MainActivity.super.onBackPressed();
        });
    }
    @Override protected void onDestroy() {
        cancelMicrophone();
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        if (speech != null) {
            speech.stop();
            speech.shutdown();
            speech = null;
        }
        if (web != null) { web.stopLoading(); web.destroy(); }
        super.onDestroy();
    }
}

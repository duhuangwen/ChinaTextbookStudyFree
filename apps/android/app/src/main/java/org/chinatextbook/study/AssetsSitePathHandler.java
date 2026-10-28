package org.chinatextbook.study;

import android.content.res.AssetManager;
import android.webkit.WebResourceResponse;
import androidx.webkit.WebViewAssetLoader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/** Serves the exported Next.js site from assets/www through an HTTPS origin. */
final class AssetsSitePathHandler implements WebViewAssetLoader.PathHandler {
    private final AssetManager assets;
    private final File mediaRoot;

    AssetsSitePathHandler(AssetManager assets, File mediaRoot) {
        this.assets = assets;
        this.mediaRoot = mediaRoot;
    }

    @Override
    public WebResourceResponse handle(String path) {
        String assetPath = EmbeddedAssetPath.resolve(path);
        if (assetPath == null) return notFound();
        if (assetPath.startsWith("audio/") || assetPath.startsWith("story-images/")
                || assetPath.startsWith("textbook-pages/")) {
            WebResourceResponse media = openMedia(assetPath);
            if (media != null) return media;
        }
        try {
            InputStream stream = assets.open("www/" + assetPath, AssetManager.ACCESS_STREAMING);
            String mime = EmbeddedAssetPath.mimeType(assetPath);
            String encoding = mime.startsWith("text/") || mime.contains("json")
                    || mime.contains("javascript") || mime.contains("svg") ? "UTF-8" : null;
            return new WebResourceResponse(mime, encoding, 200, "OK",
                    Collections.singletonMap("Cache-Control", "no-cache"), stream);
        } catch (IOException error) {
            return notFound();
        }
    }

    private WebResourceResponse openMedia(String relative) {
        try {
            File root = mediaRoot.getCanonicalFile();
            File file = new File(root, relative).getCanonicalFile();
            if (!file.getPath().startsWith(root.getPath() + File.separator) || !file.isFile()) {
                return null;
            }
            String mime = EmbeddedAssetPath.mimeType(relative);
            return new WebResourceResponse(mime, null, 200, "OK",
                    Collections.singletonMap("Cache-Control", "max-age=3600"),
                    new FileInputStream(file));
        } catch (IOException error) {
            return null;
        }
    }

    private WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
                Collections.emptyMap(),
                new ByteArrayInputStream("Not found".getBytes(StandardCharsets.UTF_8)));
    }
}

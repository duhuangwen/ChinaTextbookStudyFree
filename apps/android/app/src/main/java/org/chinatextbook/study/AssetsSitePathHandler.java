package org.chinatextbook.study;

import android.content.res.AssetManager;
import android.webkit.WebResourceResponse;
import androidx.webkit.WebViewAssetLoader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/** Serves the exported Next.js site from assets/www through an HTTPS origin. */
final class AssetsSitePathHandler implements WebViewAssetLoader.PathHandler {
    private final AssetManager assets;

    AssetsSitePathHandler(AssetManager assets) {
        this.assets = assets;
    }

    @Override
    public WebResourceResponse handle(String path) {
        String assetPath = EmbeddedAssetPath.resolve(path);
        if (assetPath == null) return notFound();
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

    private WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
                Collections.emptyMap(),
                new ByteArrayInputStream("Not found".getBytes(StandardCharsets.UTF_8)));
    }
}

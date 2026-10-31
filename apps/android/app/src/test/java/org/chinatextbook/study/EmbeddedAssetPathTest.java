package org.chinatextbook.study;
import org.junit.Test;
import static org.junit.Assert.*;

public class EmbeddedAssetPathTest {
    @Test public void mapsCleanRoutesToIndexFiles() {
        assertEquals("index.html", EmbeddedAssetPath.resolve("/"));
        assertEquals("grade/1/index.html", EmbeddedAssetPath.resolve("/grade/1/"));
        assertEquals("lesson/g1up/lesson/index.html",
                EmbeddedAssetPath.resolve("/lesson/g1up/lesson"));
    }
    @Test public void keepsHashedAssetsAndTypes() {
        assertEquals("next-static/static/chunks/app.js",
                EmbeddedAssetPath.resolveAsset("/_next/static/chunks/app.js"));
        assertEquals("text/javascript", EmbeddedAssetPath.mimeType("app.js"));
        assertEquals("image/png", EmbeddedAssetPath.mimeType("icon.png"));
    }
    @Test public void fallsBackFromWebMp3ToImportedOpus() {
        assertEquals("audio/cc/abc.opus", EmbeddedAssetPath.opusFallback("audio/cc/abc.mp3"));
        assertNull(EmbeddedAssetPath.opusFallback("audio/cc/abc.opus"));
        assertNull(EmbeddedAssetPath.opusFallback("story-images/a.mp3"));
    }
    @Test public void rejectsTraversal() {
        assertNull(EmbeddedAssetPath.resolve("/../secret"));
        assertNull(EmbeddedAssetPath.resolve("/safe/../../secret"));
    }
}

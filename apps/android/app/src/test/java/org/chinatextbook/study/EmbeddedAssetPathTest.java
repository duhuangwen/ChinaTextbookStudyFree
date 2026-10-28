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
        assertEquals("_next/static/chunks/app.js",
                EmbeddedAssetPath.resolve("/_next/static/chunks/app.js"));
        assertEquals("text/javascript", EmbeddedAssetPath.mimeType("app.js"));
        assertEquals("image/png", EmbeddedAssetPath.mimeType("icon.png"));
    }
    @Test public void rejectsTraversal() {
        assertNull(EmbeddedAssetPath.resolve("/../secret"));
        assertNull(EmbeddedAssetPath.resolve("/safe/../../secret"));
    }
}

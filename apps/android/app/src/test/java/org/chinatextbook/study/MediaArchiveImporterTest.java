package org.chinatextbook.study;
import org.junit.Test;
import static org.junit.Assert.*;

public class MediaArchiveImporterTest {
    @Test public void acceptsOfficialMediaRoots() {
        assertEquals("audio/ab/hash.opus", MediaArchiveImporter.normalizeEntryName("audio/ab/hash.opus"));
        assertEquals("story-images/book/story.jpg",
                MediaArchiveImporter.normalizeEntryName("ChinaStudyFree/story-images/book/story.jpg"));
        assertEquals("textbook-pages/book/p1.jpg",
                MediaArchiveImporter.normalizeEntryName("apps/web/public/textbook-pages/book/p1.jpg"));
    }
    @Test public void ignoresSiteFilesAndExtensions() {
        assertNull(MediaArchiveImporter.normalizeEntryName("www/index.html"));
        assertNull(MediaArchiveImporter.normalizeEntryName("audio/readme.txt"));
    }
    @Test public void rejectsTraversal() {
        assertNull(MediaArchiveImporter.normalizeEntryName("../audio/a.opus"));
        assertNull(MediaArchiveImporter.normalizeEntryName("audio/../secret.jpg"));
        assertNull(MediaArchiveImporter.normalizeEntryName("C:/audio/a.opus"));
    }
}

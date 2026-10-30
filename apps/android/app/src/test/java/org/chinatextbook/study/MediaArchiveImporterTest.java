package org.chinatextbook.study;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class MediaArchiveImporterTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

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

    @Test public void importsUnverifiedZipAndReportsSourceProgress() throws IOException {
        File root = temp.newFolder("offline-media");
        byte[] zip = zip("story-images/b/1.jpg", "textbook-pages/b/p1.jpg", "www/index.html");
        List<long[]> progress = new ArrayList<>();
        MediaArchiveImporter.Result result = MediaArchiveImporter.importArchive(root,
                new ByteArrayInputStream(zip), null, (source, files) -> progress.add(new long[]{source, files}));
        assertEquals(2, result.files);
        assertTrue(new File(root, "story-images/b/1.jpg").isFile());
        assertTrue(new File(root, "textbook-pages/b/p1.jpg").isFile());
        long[] last = progress.get(progress.size() - 1);
        assertEquals(zip.length, last[0]);
        assertEquals(2, last[1]);
    }

    @Test public void verifiedZipReplacesOnlyTheRootsItContains() throws Exception {
        File root = temp.newFolder("offline-media");
        write(new File(root, "story-images/old/stale.jpg"));
        write(new File(root, "audio/ab/keep.opus"));
        byte[] zip = zip("story-images/b/1.jpg");
        MediaArchiveImporter.Result result = MediaArchiveImporter.importArchive(root,
                new ByteArrayInputStream(zip), sha256(zip), null);
        assertEquals(1, result.files);
        assertTrue(new File(root, "story-images/b/1.jpg").isFile());
        assertFalse(new File(root, "story-images/old/stale.jpg").exists());
        assertTrue(new File(root, "audio/ab/keep.opus").isFile());
        assertNoStaging(root);
    }

    @Test public void verifiedTarGzImportsInOnePass() throws Exception {
        File root = temp.newFolder("offline-media");
        byte[] tgz = tarGz("audio/ab/1.opus", "audio/cd/2.opus");
        MediaArchiveImporter.Result result = MediaArchiveImporter.importArchive(root,
                new ByteArrayInputStream(tgz), sha256(tgz), null);
        assertEquals(2, result.files);
        assertTrue(new File(root, "audio/cd/2.opus").isFile());
        assertNoStaging(root);
    }

    @Test public void checksumMismatchKeepsExistingMediaUntouched() throws Exception {
        File root = temp.newFolder("offline-media");
        write(new File(root, "story-images/old/stale.jpg"));
        byte[] zip = zip("story-images/b/1.jpg");
        try {
            MediaArchiveImporter.importArchive(root, new ByteArrayInputStream(zip), sha256(new byte[]{1}), null);
            fail("expected checksum failure");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("SHA-256"));
        }
        assertTrue(new File(root, "story-images/old/stale.jpg").isFile());
        assertFalse(new File(root, "story-images/b/1.jpg").exists());
        assertNoStaging(root);
    }

    private static void assertNoStaging(File root) {
        String[] names = root.list();
        assertNotNull(names);
        for (String name : names) assertFalse(name, name.startsWith("."));
    }

    private static byte[] zip(String... names) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String name : names) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(name.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] tarGz(String... names) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new GZIPOutputStream(bytes))) {
            for (String name : names) {
                byte[] body = name.getBytes(StandardCharsets.UTF_8);
                TarArchiveEntry entry = new TarArchiveEntry(name);
                entry.setSize(body.length);
                tar.putArchiveEntry(entry);
                tar.write(body);
                tar.closeArchiveEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static void write(File file) throws IOException {
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        Files.write(file.toPath(), new byte[]{1});
    }

    private static String sha256(byte[] data) throws Exception {
        StringBuilder hex = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(data)) {
            hex.append(String.format(Locale.US, "%02x", b & 0xff));
        }
        return hex.toString();
    }
}

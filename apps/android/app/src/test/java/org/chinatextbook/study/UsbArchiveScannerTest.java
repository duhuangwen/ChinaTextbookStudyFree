package org.chinatextbook.study;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class UsbArchiveScannerTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void findsArchivesAtRootAndNestedFolders() throws IOException {
        File usb = temp.newFolder("usb");
        touch(usb, "ChinaStudyFree-story-images.zip");
        touch(usb, "notes.txt");
        touch(new File(usb, "Download"), "ChinaStudyFree-audio.tar.gz");
        touch(new File(usb, "a/b"), "ChinaStudyFree-textbook-pages.zip");
        List<String> names = names(UsbArchiveScanner.find(Arrays.asList(usb)));
        assertEquals(Arrays.asList("ChinaStudyFree-audio.tar.gz", "ChinaStudyFree-story-images.zip",
                "ChinaStudyFree-textbook-pages.zip"), names);
    }

    @Test public void listsOfficialArchivesBeforeOtherZips() throws IOException {
        File usb = temp.newFolder("usb");
        touch(usb, "aaa-custom.zip");
        touch(usb, "ChinaStudyFree-textbook-pages.zip");
        assertEquals(Arrays.asList("ChinaStudyFree-textbook-pages.zip", "aaa-custom.zip"),
                names(UsbArchiveScanner.find(Arrays.asList(usb))));
    }

    @Test public void skipsHiddenAndAppDataFoldersAndDeepTrees() throws IOException {
        File usb = temp.newFolder("usb");
        touch(new File(usb, ".Trash"), "x.zip");
        touch(new File(usb, "Android/data"), "y.zip");
        touch(new File(usb, "a/b/c/d"), "too-deep.zip");
        assertTrue(UsbArchiveScanner.find(Arrays.asList(usb)).isEmpty());
    }

    @Test public void deduplicatesOverlappingRootsAndIgnoresMissingOnes() throws IOException {
        File usb = temp.newFolder("usb");
        touch(new File(usb, "Download"), "ChinaStudyFree-audio.tar.gz");
        List<File> found = UsbArchiveScanner.find(Arrays.asList(
                usb, new File(usb, "Download"), new File(usb, "missing"), null));
        assertEquals(1, found.size());
    }

    @Test public void derivesVolumeRootFromAppSpecificDirectory() {
        assertEquals(new File("/storage/1234-ABCD"), UsbArchiveScanner.volumeRoot(
                new File("/storage/1234-ABCD/Android/data/org.chinatextbook.study/files")));
        assertNull(UsbArchiveScanner.volumeRoot(new File("/data/user/0/app/files")));
        assertNull(UsbArchiveScanner.volumeRoot(null));
    }

    private static void touch(File dir, String name) throws IOException {
        assertTrue(dir.isDirectory() || dir.mkdirs());
        Files.write(new File(dir, name).toPath(), new byte[]{1});
    }

    private static List<String> names(List<File> files) {
        String[] out = new String[files.size()];
        for (int i = 0; i < out.length; i++) out[i] = files.get(i).getName();
        return Arrays.asList(out);
    }
}

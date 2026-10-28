package org.chinatextbook.study;

import java.io.File;
import java.util.Locale;

/** Manages the optional media directory imported from a USB drive. */
final class MediaLibrary {
    static final String[] ROOTS = {"audio", "story-images", "textbook-pages"};

    private MediaLibrary() {}

    static File root(File filesDir) {
        return new File(filesDir, "offline-media");
    }

    static String statusJson(File filesDir) {
        File root = root(filesDir);
        boolean audio = new File(root, "audio").isDirectory();
        boolean images = new File(root, "story-images").isDirectory();
        boolean pages = new File(root, "textbook-pages").isDirectory();
        return String.format(Locale.US,
                "{\"audio\":%s,\"images\":%s,\"pages\":%s,\"complete\":%s}",
                audio, images, pages, audio && images && pages);
    }

    static boolean deleteTree(File file) {
        if (file == null || !file.exists()) return true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteTree(child)) return false;
                }
            }
        }
        return file.delete();
    }
}

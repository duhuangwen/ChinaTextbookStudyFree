package org.chinatextbook.study;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Finds media archives on mounted volumes for TVs whose firmware ships without a document picker. */
final class UsbArchiveScanner {
    private static final int MAX_DEPTH = 3;
    private static final int MAX_RESULTS = 100;

    private UsbArchiveScanner() {}

    static List<File> find(List<File> roots) {
        Set<String> seen = new LinkedHashSet<>();
        List<File> found = new ArrayList<>();
        for (File root : roots) {
            if (root != null && root.isDirectory()) scan(root, 0, seen, found);
        }
        Collections.sort(found, (a, b) -> {
            boolean officialA = MediaArchiveImporter.expectedSha256(a.getName()) != null;
            boolean officialB = MediaArchiveImporter.expectedSha256(b.getName()) != null;
            if (officialA != officialB) return officialA ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        return found;
    }

    /** Maps an app-specific directory such as {@code /storage/X/Android/data/pkg/files} to {@code /storage/X}. */
    static File volumeRoot(File appSpecificDir) {
        if (appSpecificDir == null) return null;
        String path = appSpecificDir.getPath().replace('\\', '/');
        int index = path.indexOf("/Android/data/");
        return index > 0 ? new File(path.substring(0, index)) : null;
    }

    private static void scan(File dir, int depth, Set<String> seen, List<File> found) {
        if (found.size() >= MAX_RESULTS) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            String name = child.getName();
            if (name.startsWith(".")) continue;
            if (child.isDirectory()) {
                if (depth + 1 < MAX_DEPTH && !(depth == 0 && name.equals("Android"))) {
                    scan(child, depth + 1, seen, found);
                }
            } else if (isArchive(name) && seen.add(canonical(child))) {
                found.add(child);
                if (found.size() >= MAX_RESULTS) return;
            }
        }
    }

    private static boolean isArchive(String name) {
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".zip") || lower.endsWith(".tar.gz") || lower.endsWith(".tgz");
    }

    private static String canonical(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }
}

package org.chinatextbook.study;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

/** Streams official ZIP/TAR.GZ media archives into the app's private storage. */
final class MediaArchiveImporter {
    private static final long MAX_FILE_BYTES = 128L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024;

    interface Progress {
        void onProgress(int files, long bytes);
    }

    static final class Result {
        final int files;
        final long bytes;
        Result(int files, long bytes) {
            this.files = files;
            this.bytes = bytes;
        }
    }

    private MediaArchiveImporter() {}

    static String expectedSha256(String displayName) {
        if (displayName == null) return null;
        String name = displayName.toLowerCase(Locale.US);
        if (name.endsWith("audio.tar.gz")) {
            return "5f3ec18f9f63aea90215d9059bc1ef23a45478c995e1555eeb898c6f3b757269";
        }
        if (name.endsWith("story-images.zip")) {
            return "0398625e2a29b3c56cc581a8cc6570a7bec5600bddf754d6330e102d1b948a2f";
        }
        if (name.endsWith("textbook-pages.zip")) {
            return "628c8f24e8e961a708fa200e5796ec585d4abd9aaf521112951b0321d6e70474";
        }
        return null;
    }

    static String sha256(InputStream source) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[128 * 1024];
            int count;
            try (InputStream input = source) {
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            StringBuilder output = new StringBuilder(64);
            for (byte value : digest.digest()) {
                output.append(String.format(Locale.US, "%02x", value & 0xff));
            }
            return output.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 unavailable", impossible);
        }
    }

    static Result importArchive(File mediaRoot, InputStream source, Progress progress)
            throws IOException {
        BufferedInputStream input = new BufferedInputStream(source, 128 * 1024);
        input.mark(4);
        int a = input.read();
        int b = input.read();
        input.reset();
        if (a == 'P' && b == 'K') return importZip(mediaRoot, input, progress);
        if (a == 0x1f && b == 0x8b) return importTarGz(mediaRoot, input, progress);
        throw new IOException("Unsupported media archive");
    }

    private static Result importZip(File root, InputStream source, Progress progress)
            throws IOException {
        int files = 0;
        long bytes = 0;
        try (ZipInputStream zip = new ZipInputStream(source)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String safe = normalizeEntryName(entry.getName());
                if (safe == null) continue;
                long copied = copyEntry(zip, new File(root, safe));
                if (copied < 0 || bytes + copied > MAX_TOTAL_BYTES) {
                    throw new IOException("Media archive is too large");
                }
                bytes += copied;
                files++;
                if (progress != null && files % 100 == 0) progress.onProgress(files, bytes);
            }
        }
        if (progress != null) progress.onProgress(files, bytes);
        return new Result(files, bytes);
    }

    private static Result importTarGz(File root, InputStream source, Progress progress)
            throws IOException {
        int files = 0;
        long bytes = 0;
        try (GzipCompressorInputStream gzip = new GzipCompressorInputStream(source);
             TarArchiveInputStream tar = new TarArchiveInputStream(gzip)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextTarEntry()) != null) {
                if (!entry.isFile()) continue;
                String safe = normalizeEntryName(entry.getName());
                if (safe == null) continue;
                long copied = copyEntry(tar, new File(root, safe));
                if (copied < 0 || bytes + copied > MAX_TOTAL_BYTES) {
                    throw new IOException("Media archive is too large");
                }
                bytes += copied;
                files++;
                if (progress != null && files % 100 == 0) progress.onProgress(files, bytes);
            }
        }
        if (progress != null) progress.onProgress(files, bytes);
        return new Result(files, bytes);
    }

    private static long copyEntry(InputStream input, File target) throws IOException {
        File canonicalRoot = target.getParentFile();
        while (canonicalRoot != null && !canonicalRoot.getName().equals("offline-media")) {
            canonicalRoot = canonicalRoot.getParentFile();
        }
        if (canonicalRoot == null || !target.getCanonicalPath().startsWith(canonicalRoot.getCanonicalPath() + File.separator)) {
            throw new IOException("Unsafe media path");
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create media directory");
        }
        long total = 0;
        try (OutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[128 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_FILE_BYTES) throw new IOException("Media file is too large");
                output.write(buffer, 0, count);
            }
        }
        return total;
    }

    static String normalizeEntryName(String raw) {
        if (raw == null) return null;
        String name = raw.replace('\\', '/');
        while (name.startsWith("./")) name = name.substring(2);
        if (name.startsWith("/") || name.contains(":")) return null;
        if (name.startsWith("apps/web/public/")) name = name.substring("apps/web/public/".length());

        String relative = null;
        for (String root : MediaLibrary.ROOTS) {
            if (name.startsWith(root + "/")) {
                relative = name;
                break;
            }
            int marker = name.indexOf("/" + root + "/");
            if (marker >= 0) {
                relative = name.substring(marker + 1);
                break;
            }
        }
        if (relative == null) return null;
        for (String part : relative.split("/")) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) return null;
        }
        String lower = relative.toLowerCase(Locale.US);
        boolean allowed = lower.endsWith(".opus") || lower.endsWith(".mp3")
                || lower.endsWith(".ogg") || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".webp") || lower.endsWith(".json");
        return allowed ? relative : null;
    }
}

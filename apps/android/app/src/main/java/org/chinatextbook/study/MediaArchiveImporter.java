package org.chinatextbook.study;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

/** Streams official ZIP/TAR.GZ media archives into the app's private storage. */
final class MediaArchiveImporter {
    private static final long MAX_FILE_BYTES = 128L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024;
    private static final int BUFFER_BYTES = 256 * 1024;
    private static final long PROGRESS_INTERVAL_NANOS = 250_000_000L;
    private static final String STAGING = ".staging";

    interface Progress {
        void onProgress(long sourceBytes, int files);
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

    /** The official image packs are zipped from inside their folder, so their entries carry no media root. */
    static String defaultRoot(String displayName) {
        if (displayName == null) return null;
        String name = displayName.toLowerCase(Locale.US);
        if (name.endsWith("story-images.zip")) return "story-images";
        if (name.endsWith("textbook-pages.zip")) return "textbook-pages";
        return null;
    }

    /**
     * Imports an archive in a single read. With {@code expectedSha256}, entries are staged and only replace the
     * live media roots after the whole source (including trailing archive metadata) matches the checksum.
     */
    static Result importArchive(File mediaRoot, InputStream source, String expectedSha256, String defaultRoot,
                                Progress progress) throws IOException {
        MessageDigest digest = expectedSha256 == null ? null : newSha256();
        CountingInputStream counted = new CountingInputStream(
                digest == null ? source : new DigestInputStream(source, digest));
        File target = digest == null ? mediaRoot : new File(mediaRoot, STAGING);
        if (digest != null && !MediaLibrary.deleteTree(target)) {
            throw new IOException("Cannot reset staging directory");
        }
        try {
            Writer writer = new Writer(target, counted, progress);
            BufferedInputStream input = new BufferedInputStream(counted, BUFFER_BYTES);
            input.mark(4);
            int a = input.read();
            int b = input.read();
            input.reset();
            if (a == 'P' && b == 'K') importZip(input, defaultRoot, writer);
            else if (a == 0x1f && b == 0x8b) importTarGz(input, defaultRoot, writer);
            else throw new IOException("Unsupported media archive");
            drain(input, writer.buffer);
            if (digest != null) {
                if (!expectedSha256.equalsIgnoreCase(hex(digest.digest()))) throw new IOException("SHA-256 mismatch");
                promote(target, mediaRoot);
            }
            writer.report(true);
            return new Result(writer.files, writer.bytes);
        } finally {
            if (digest != null) MediaLibrary.deleteTree(target);
        }
    }

    // The entry streams' close() would close the shared source, which still has to be drained for the checksum.
    private static void importZip(InputStream source, String defaultRoot, Writer writer) throws IOException {
        ZipInputStream zip = new ZipInputStream(source);
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            if (entry.isDirectory()) continue;
            String safe = normalizeEntryName(entry.getName(), defaultRoot);
            if (safe != null) writer.write(safe, zip);
        }
    }

    private static void importTarGz(InputStream source, String defaultRoot, Writer writer) throws IOException {
        TarArchiveInputStream tar = new TarArchiveInputStream(new GzipCompressorInputStream(source));
        TarArchiveEntry entry;
        while ((entry = tar.getNextEntry()) != null) {
            if (!entry.isFile()) continue;
            String safe = normalizeEntryName(entry.getName(), defaultRoot);
            if (safe != null) writer.write(safe, tar);
        }
    }

    private static void drain(InputStream input, byte[] buffer) throws IOException {
        while (input.read(buffer) != -1) {
            // Bytes after the last entry still belong to the checksum and to the progress total.
        }
    }

    private static void promote(File staging, File mediaRoot) throws IOException {
        for (String name : MediaLibrary.ROOTS) {
            File fresh = new File(staging, name);
            if (!fresh.isDirectory()) continue;
            File live = new File(mediaRoot, name);
            if (!MediaLibrary.deleteTree(live) || !fresh.renameTo(live)) {
                throw new IOException("Cannot install " + name);
            }
        }
    }

    private static MessageDigest newSha256() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 unavailable", impossible);
        }
    }

    private static String hex(byte[] digest) {
        StringBuilder output = new StringBuilder(64);
        for (byte value : digest) output.append(String.format(Locale.US, "%02x", value & 0xff));
        return output.toString();
    }

    private static final class Writer {
        final byte[] buffer = new byte[BUFFER_BYTES];
        private final File root;
        private final String rootPrefix;
        private final CountingInputStream source;
        private final Progress progress;
        private final Set<String> checkedDirs = new HashSet<>();
        private long lastReport;
        int files;
        long bytes;

        Writer(File root, CountingInputStream source, Progress progress) throws IOException {
            if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot create media directory");
            this.root = root;
            this.rootPrefix = root.getCanonicalPath() + File.separator;
            this.source = source;
            this.progress = progress;
        }

        void write(String safe, InputStream input) throws IOException {
            int slash = safe.lastIndexOf('/');
            File dir = new File(root, safe.substring(0, slash));
            // Canonical checks per directory instead of per file: the audio pack has ~81k files in ~256 folders.
            if (checkedDirs.add(safe.substring(0, slash))) {
                if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create media directory");
                if (!(dir.getCanonicalPath() + File.separator).startsWith(rootPrefix)) {
                    throw new IOException("Unsafe media path");
                }
            }
            File target = new File(dir, safe.substring(slash + 1));
            if (target.exists() && !target.getCanonicalPath().startsWith(rootPrefix)) {
                throw new IOException("Unsafe media path");
            }
            long total = 0;
            try (OutputStream output = new FileOutputStream(target)) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_FILE_BYTES) throw new IOException("Media file is too large");
                    output.write(buffer, 0, count);
                }
            }
            if (bytes + total > MAX_TOTAL_BYTES) throw new IOException("Media archive is too large");
            bytes += total;
            files++;
            report(false);
        }

        void report(boolean force) {
            if (progress == null) return;
            long now = System.nanoTime();
            if (!force && now - lastReport < PROGRESS_INTERVAL_NANOS) return;
            lastReport = now;
            progress.onProgress(source.count, files);
        }
    }

    private static final class CountingInputStream extends FilterInputStream {
        volatile long count;

        CountingInputStream(InputStream input) {
            super(input);
        }

        @Override public int read() throws IOException {
            int value = super.read();
            if (value >= 0) count++;
            return value;
        }

        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) count += read;
            return read;
        }

        @Override public long skip(long n) throws IOException {
            long skipped = super.skip(n);
            if (skipped > 0) count += skipped;
            return skipped;
        }
    }

    static String normalizeEntryName(String raw) {
        return normalizeEntryName(raw, null);
    }

    static String normalizeEntryName(String raw, String defaultRoot) {
        if (raw == null) return null;
        String name = raw.replace('\\', '/');
        while (name.startsWith("./")) name = name.substring(2);
        if (name.startsWith("/") || name.contains(":")) return null;
        for (String part : name.split("/")) {
            if (part.equals("..")) return null;
        }
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
        if (relative == null && defaultRoot != null) relative = defaultRoot + "/" + name;
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

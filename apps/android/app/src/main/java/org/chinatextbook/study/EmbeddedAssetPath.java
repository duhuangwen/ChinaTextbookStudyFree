package org.chinatextbook.study;

/** Maps clean Web URLs to files inside assets/www. */
final class EmbeddedAssetPath {
    private EmbeddedAssetPath() {}

    static String resolve(String requestPath) {
        if (requestPath == null || requestPath.isEmpty() || "/".equals(requestPath)) {
            return "index.html";
        }
        String path = requestPath;
        if (!path.startsWith("/")) path = "/" + path;
        if (path.endsWith("/")) path += "index.html";
        else {
            String last = path.substring(path.lastIndexOf('/') + 1);
            if (!last.contains(".")) path += "/index.html";
        }
        String relative = path.substring(1);
        for (String part : relative.split("/")) {
            if (part.equals(".") || part.equals("..")) return null;
        }
        return relative;
    }

    static String resolveAsset(String requestPath) {
        String relative = resolve(requestPath);
        return relative != null && relative.startsWith("_next/")
                ? "next-static/" + relative.substring("_next/".length())
                : relative;
    }

    /**
     * The Web build transcodes narration to MP3 and some UI cues hard-code that name, but the official audio
     * pack imported from USB only carries the original Opus files.
     */
    static String opusFallback(String relative) {
        return relative.startsWith("audio/") && relative.endsWith(".mp3")
                ? relative.substring(0, relative.length() - ".mp3".length()) + ".opus"
                : null;
    }

    static String mimeType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".html")) return "text/html";
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) return "text/javascript";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".json") || lower.endsWith(".webmanifest")) return "application/json";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".woff")) return "font/woff";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".opus")) return "audio/ogg";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".xml")) return "application/xml";
        return "application/octet-stream";
    }
}

package dev.sapphire.companion;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Bounded single-file staging. No remote path is ever used as a local path. */
final class IncomingFileTransfer {
    static final int MAX_BYTES = 5 * 1024 * 1024, CHUNK_BYTES = 2048;
    static final long IDLE_MS = 60_000, LIFETIME_MS = 600_000;
    private final File root;
    private final ArrayDeque<String> recent = new ArrayDeque<>();
    private Active active;
    private boolean cleaned;
    static final class Received {
        final String id, name, sha256;
        final long size;
        Received(String id, String name, long size, String sha256) { this.id = id; this.name = name; this.size = size; this.sha256 = sha256; }
    }
    private static final class Active {
        Received offer;
        File directory;
        FileOutputStream output;
        MessageDigest digest;
        long started, updated, written;
        int next;
    }
    IncomingFileTransfer(File root) { this.root = root; }
    static boolean validID(String id) { return id != null && id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"); }
    static boolean validName(String value) {
        if (value == null || value.isEmpty() || value.equals(".") || value.equals("..") || value.getBytes(StandardCharsets.UTF_8).length > 180) return false;
        if (value.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            int category = Character.getType(value.codePointAt(i));
            if (category == Character.FORMAT || category == Character.LINE_SEPARATOR || category == Character.PARAGRAPH_SEPARATOR) return false;
            if (c == '/' || c == '\\' || Character.isISOControl(c)) return false;
            if (Character.isHighSurrogate(c)) { if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) return false; }
            else if (Character.isLowSurrogate(c)) return false;
        }
        return true;
    }
    private static Long integer(JSONObject value, String key) {
        Object input = value.opt(key);
        return input instanceof Integer || input instanceof Long ? ((Number) input).longValue() : null;
    }
    private static Received offer(JSONObject value) {
        if (!(value.opt("requestId") instanceof String) || !(value.opt("name") instanceof String) || !(value.opt("sha256") instanceof String)) return null;
        String id = value.optString("requestId"), name = value.optString("name"), hash = value.optString("sha256");
        Long size = integer(value, "size");
        if (!validID(id) || !validName(name) || size == null || size < 0 || size > MAX_BYTES || !hash.matches("[a-f0-9]{64}")) return null;
        return new Received(id, name, size, hash);
    }
    private static JSONObject result(String id, String state, int next, long bytes) {
        JSONObject result = new JSONObject();
        try { result.put("requestId", validID(id) ? id : "").put("state", state).put("nextIndex", next).put("receivedBytes", bytes); }
        catch (JSONException ignored) { }
        return result;
    }
    private boolean expired(long now) {
        return active != null && (now < active.updated || now < active.started || now - active.updated >= IDLE_MS || now - active.started >= LIFETIME_MS);
    }
    synchronized void expire(long now) { if (expired(now)) abort(); }
    synchronized void abort() {
        if (active == null) return;
        try { active.output.close(); } catch (IOException ignored) { }
        cleanup(active.directory);
        active = null;
    }
    private static void cleanup(File directory) {
        if (directory == null || Files.isSymbolicLink(directory.toPath())) return;
        new File(directory, "data").delete(); new File(directory, "metadata.json").delete(); directory.delete();
    }
    private void prepareRoot() throws IOException {
        if (Files.isSymbolicLink(root.toPath()) || (!root.isDirectory() && !root.mkdirs())) throw new IOException("Unavailable staging root");
        if (!cleaned) {
            File[] prior = root.listFiles((dir, name) -> name.matches("\\.(partial|discard)-[a-f0-9-]{36}"));
            if (prior != null) for (File directory : prior) cleanup(directory);
            cleaned = true;
        }
    }
    synchronized JSONObject handle(String type, JSONObject payload, boolean enabled, long now) {
        String id = payload.opt("requestId") instanceof String ? payload.optString("requestId") : "";
        if (!enabled) { abort(); return result(id, "disabled", 0, 0); }
        if (!validID(id) || now < 0) return result(id, "invalid", 0, 0);
        if (expired(now)) { abort(); if (!"file.offer".equals(type)) return result(id, "expired", 0, 0); }
        try {
            if ("file.offer".equals(type)) {
                Received offer = offer(payload);
                if (offer == null) return result(id, "invalid", 0, 0);
                if (active != null || new File(root, "ready").exists()) return result(id, "busy", 0, 0);
                if (recent.contains(id)) return result(id, "expired", 0, 0);
                prepareRoot();
                File directory = new File(root, ".partial-" + UUID.randomUUID());
                Files.createDirectory(directory.toPath());
                Active candidate = new Active();
                candidate.offer = offer; candidate.directory = directory;
                try {
                    candidate.output = new FileOutputStream(new File(directory, "data"));
                    candidate.digest = MessageDigest.getInstance("SHA-256");
                } catch (Exception error) { if (candidate.output != null) candidate.output.close(); cleanup(directory); throw error; }
                candidate.started = candidate.updated = now;
                active = candidate;
                recent.addLast(id); while (recent.size() > 64) recent.removeFirst();
                return result(id, "ready", 0, 0);
            }
            if (active == null || !active.offer.id.equals(id)) return result(id, "expired", 0, 0);
            if ("file.cancel".equals(type)) { abort(); return result(id, "cancelled", 0, 0); }
            if ("file.chunk".equals(type)) {
                Long index = integer(payload, "index");
                String encoded = payload.opt("data") instanceof String ? payload.optString("data") : "";
                if (index == null || index != active.next || encoded.length() > 4 * ((CHUNK_BYTES + 2) / 3)) throw new IOException("Invalid sequence");
                byte[] bytes = Base64.getDecoder().decode(encoded);
                int expected = (int) Math.min(CHUNK_BYTES, active.offer.size - active.written);
                if (expected <= 0 || bytes.length != expected || !Base64.getEncoder().encodeToString(bytes).equals(encoded)) throw new IOException("Invalid chunk");
                active.output.write(bytes); active.digest.update(bytes);
                active.written += bytes.length; active.next++; active.updated = now;
                return result(id, "chunk", active.next, active.written);
            }
            if ("file.finish".equals(type)) {
                if (active.written != active.offer.size) throw new IOException("Incomplete file");
                StringBuilder hash = new StringBuilder();
                for (byte b : active.digest.digest()) hash.append(String.format(Locale.ROOT, "%02x", b & 255));
                if (!hash.toString().equals(active.offer.sha256)) throw new IOException("Digest mismatch");
                active.output.getFD().sync(); active.output.close();
                JSONObject metadata = new JSONObject().put("requestId", id).put("name", active.offer.name).put("size", active.written).put("sha256", active.offer.sha256);
                try (FileOutputStream file = new FileOutputStream(new File(active.directory, "metadata.json"))) {
                    file.write(metadata.toString().getBytes(StandardCharsets.UTF_8)); file.getFD().sync();
                }
                // Directory publication makes content and metadata visible together.
                move(active.directory, new File(root, "ready"));
                int next = active.next; long size = active.written; active = null;
                return result(id, "complete", next, size);
            }
            return result(id, "invalid", active.next, active.written);
        } catch (Exception error) { abort(); return result(id, "error", 0, 0); }
    }
    private static void move(File source, File target) throws IOException {
        if (target.exists()) throw new IOException("Destination already exists");
        try { Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException unavailable) { Files.move(source.toPath(), target.toPath()); }
    }
    synchronized Received received() {
        File directory = new File(root, "ready"), metadata = new File(directory, "metadata.json"), data = new File(directory, "data");
        try {
            if (Files.isSymbolicLink(directory.toPath()) || !Files.isRegularFile(metadata.toPath(), LinkOption.NOFOLLOW_LINKS)
                    || !Files.isRegularFile(data.toPath(), LinkOption.NOFOLLOW_LINKS) || metadata.length() > 4096) return null;
            Received result = offer(new JSONObject(new String(Files.readAllBytes(metadata.toPath()), StandardCharsets.UTF_8)));
            return result != null && data.length() == result.size ? result : null;
        } catch (Exception unavailable) { return null; }
    }
    synchronized InputStream openReceived(String id) throws IOException {
        Received value = received();
        if (value == null || !value.id.equals(id)) throw new IOException("Received file unavailable");
        return new FileInputStream(new File(new File(root, "ready"), "data"));
    }
    static void copyVerified(InputStream input, OutputStream output, Received expected) throws IOException {
        if (expected == null || expected.size < 0 || expected.size > MAX_BYTES || !expected.sha256.matches("[a-f0-9]{64}")) throw new IOException("Invalid staged metadata");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024]; int count; long copied = 0;
            while ((count = input.read(buffer)) != -1) {
                copied += count;
                if (copied > expected.size) throw new IOException("Staged file changed");
                digest.update(buffer, 0, count); output.write(buffer, 0, count);
            }
            StringBuilder hash = new StringBuilder();
            for (byte b : digest.digest()) hash.append(String.format(Locale.ROOT, "%02x", b & 255));
            if (copied != expected.size || !hash.toString().equals(expected.sha256)) throw new IOException("Staged file failed verification");
            output.flush();
        } catch (java.security.NoSuchAlgorithmException unavailable) { throw new IOException("SHA-256 unavailable", unavailable); }
    }
    synchronized boolean hasStoredFile() { return new File(root, "ready").exists(); }
    synchronized boolean discardUnavailable() {
        if (received() != null || !hasStoredFile()) return false;
        return discardDirectory();
    }
    private boolean discardDirectory() {
        File discarded = new File(root, ".discard-" + UUID.randomUUID());
        try { move(new File(root, "ready"), discarded); cleanup(discarded); return true; }
        catch (IOException unavailable) { return false; }
    }
    synchronized boolean discard(String id) {
        Received value = received();
        if (value == null || !value.id.equals(id)) return false;
        return discardDirectory();
    }
}

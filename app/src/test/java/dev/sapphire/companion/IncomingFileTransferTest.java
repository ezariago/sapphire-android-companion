package dev.sapphire.companion;
import org.junit.Test;
import static org.junit.Assert.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

public final class IncomingFileTransferTest {
    static String hash(byte[] data) throws Exception {
        StringBuilder text = new StringBuilder(); for (byte b : MessageDigest.getInstance("SHA-256").digest(data)) text.append(String.format(Locale.ROOT, "%02x", b & 255)); return text.toString();
    }
    static JSONObject offer(String id, byte[] data) throws Exception {
        return new JSONObject().put("requestId", id).put("name", "Synthetic sample.bin").put("size", data.length).put("sha256", hash(data));
    }
    static JSONObject chunk(String id, int index, byte[] bytes) throws Exception { return new JSONObject().put("requestId", id).put("index", index).put("data", Base64.getEncoder().encodeToString(bytes)); }
    static void cleanup(Path root) throws Exception { try (java.util.stream.Stream<Path> paths = Files.walk(root)) { paths.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException e) { throw new RuntimeException(e); } }); } }
    @Test public void publishesVerifiedFileAndPreservesUntilExplicitDiscard() throws Exception {
        Path root = Files.createTempDirectory("sapphire-transfer-test-");
        try {
            IncomingFileTransfer receiver = new IncomingFileTransfer(root.toFile());
            String id = UUID.randomUUID().toString(); byte[] data = new byte[5000]; new Random(7).nextBytes(data);
            assertEquals("ready", receiver.handle("file.offer", offer(id, data), true, 0).getString("state"));
            assertNull(receiver.received());
            assertEquals("busy", receiver.handle("file.offer", offer(UUID.randomUUID().toString(), data), true, 1).getString("state"));
            int index = 0;
            for (int offset = 0; offset < data.length; offset += IncomingFileTransfer.CHUNK_BYTES) {
                JSONObject ack = receiver.handle("file.chunk", chunk(id, index, Arrays.copyOfRange(data, offset, Math.min(data.length, offset + IncomingFileTransfer.CHUNK_BYTES))), true, 100 + index);
                assertEquals("chunk", ack.getString("state")); assertEquals(++index, ack.getInt("nextIndex"));
            }
            assertNull(receiver.received());
            assertEquals("complete", receiver.handle("file.finish", new JSONObject().put("requestId", id), true, 200).getString("state"));
            assertEquals(data.length, receiver.received().size);
            receiver.abort(); // Disconnect preserves the completed file.
            IncomingFileTransfer restarted = new IncomingFileTransfer(root.toFile());
            assertEquals(id, restarted.received().id);
            try (InputStream file = restarted.openReceived(id)) { assertArrayEquals(data, file.readAllBytes()); }
            assertFalse(restarted.discard(UUID.randomUUID().toString()));
            assertFalse(restarted.discardUnavailable());
            assertEquals("busy", restarted.handle("file.offer", offer(UUID.randomUUID().toString(), data), true, 300).getString("state"));
            assertTrue(restarted.discard(id)); assertNull(restarted.received());
            assertEquals("expired", receiver.handle("file.offer", offer(id, data), true, 400).getString("state"));
        } finally { cleanup(root); }
    }
    @Test public void rejectsMalformedCorruptStaleAndDisabledTransfers() throws Exception {
        Path root = Files.createTempDirectory("sapphire-transfer-test-");
        try {
            IncomingFileTransfer receiver = new IncomingFileTransfer(root.toFile()); byte[] data = new byte[10];
            for (String name : new String[]{"../escape", "a/b", "a\\b", "..", " ", "bad\nname", "hidden\u202Eexe", "\ud800"}) {
                JSONObject request = offer(UUID.randomUUID().toString(), data).put("name", name);
                assertEquals(name, "invalid", receiver.handle("file.offer", request, true, 0).getString("state"));
            }
            for (Object size : new Object[]{-1, IncomingFileTransfer.MAX_BYTES + 1, 1.5, "10"}) {
                assertEquals("invalid", receiver.handle("file.offer", offer(UUID.randomUUID().toString(), data).put("size", size), true, 0).getString("state"));
            }
            String id = UUID.randomUUID().toString();
            assertEquals("disabled", receiver.handle("file.offer", offer(id, data), false, 0).getString("state"));
            assertEquals("ready", receiver.handle("file.offer", offer(id, data), true, 0).getString("state"));
            assertEquals("error", receiver.handle("file.chunk", chunk(id, 1, data), true, 1).getString("state"));
            assertNull(receiver.received());
            id = UUID.randomUUID().toString(); receiver.handle("file.offer", offer(id, data), true, 10);
            assertEquals("expired", receiver.handle("file.chunk", chunk(id, 0, data), true, 10 + IncomingFileTransfer.IDLE_MS).getString("state"));
            id = UUID.randomUUID().toString(); receiver.handle("file.offer", offer(id, data), true, 100);
            assertEquals("expired", receiver.handle("file.chunk", chunk(id, 0, data), true, 99).getString("state"));
            id = UUID.randomUUID().toString(); receiver.handle("file.offer", offer(id, data), true, 200);
            assertEquals("error", receiver.handle("file.finish", new JSONObject().put("requestId", id), true, 201).getString("state"));
            id = UUID.randomUUID().toString(); receiver.handle("file.offer", offer(id, data).put("sha256", "0".repeat(64)), true, 300);
            receiver.handle("file.chunk", chunk(id, 0, data), true, 301);
            assertEquals("error", receiver.handle("file.finish", new JSONObject().put("requestId", id), true, 302).getString("state"));
            assertNull(receiver.received());
            id = UUID.randomUUID().toString(); receiver.handle("file.offer", offer(id, data), true, 400);
            assertEquals("disabled", receiver.handle("file.chunk", chunk(id, 0, data), false, 401).getString("state"));
            assertEquals(0, Objects.requireNonNull(root.toFile().listFiles()).length);
            // Empty files use the same digest/atomic publication, without chunks.
            id = UUID.randomUUID().toString(); receiver.handle("file.offer", offer(id, new byte[0]), true, 500);
            assertEquals("complete", receiver.handle("file.finish", new JSONObject().put("requestId", id), true, 501).getString("state"));
            assertTrue(receiver.discard(id));
        } finally { cleanup(root); }
    }
    @Test public void cancelsAndRecoversOnlyOwnedIncompleteOrUnavailableCache() throws Exception {
        Path root = Files.createTempDirectory("sapphire-transfer-test-");
        try {
            IncomingFileTransfer receiver = new IncomingFileTransfer(root.toFile()); String id = UUID.randomUUID().toString(); byte[] data = new byte[10];
            receiver.handle("file.offer", offer(id, data), true, 0);
            assertEquals("cancelled", receiver.handle("file.cancel", new JSONObject().put("requestId", id), true, 1).getString("state"));
            assertEquals(0, Objects.requireNonNull(root.toFile().listFiles()).length);
            id = UUID.randomUUID().toString(); receiver.handle("file.offer", offer(id, data), true, 10);
            receiver.expire(10 + IncomingFileTransfer.IDLE_MS); assertNull(receiver.received());
            id = UUID.randomUUID().toString(); receiver.handle("file.offer", offer(id, new byte[0]), true, 100000);
            receiver.handle("file.finish", new JSONObject().put("requestId", id), true, 100001);
            Files.delete(root.resolve("ready/data")); // Simulate OS cache eviction, own fixture only.
            assertTrue(receiver.hasStoredFile()); assertNull(receiver.received());
            assertTrue(receiver.discardUnavailable()); assertFalse(receiver.hasStoredFile());
        } finally { cleanup(root); }
    }
    @Test public void verifiesSaveCopyAndReportsReadWriteOrDigestFailure() throws Exception {
        byte[] data = new byte[]{1, 2, 3};
        IncomingFileTransfer.Received expected = new IncomingFileTransfer.Received(UUID.randomUUID().toString(), "Fixture.bin", data.length, hash(data));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        IncomingFileTransfer.copyVerified(new ByteArrayInputStream(data), output, expected);
        assertArrayEquals(data, output.toByteArray());
        for (byte[] corrupt : new byte[][]{new byte[]{1, 2}, new byte[]{1, 2, 4}, new byte[]{1, 2, 3, 4}}) {
            try { IncomingFileTransfer.copyVerified(new ByteArrayInputStream(corrupt), new ByteArrayOutputStream(), expected); fail("Corruption accepted"); }
            catch (IOException refused) { }
        }
        try { IncomingFileTransfer.copyVerified(new ByteArrayInputStream(data), new OutputStream() { public void write(int b) throws IOException { throw new IOException("Fixture disk failure"); } }, expected); fail("Failed write accepted"); }
        catch (IOException refused) { }
    }

    @Test public void acceptsExactMaximumAndFinalAcknowledgementIndex() throws Exception {
        Path root = Files.createTempDirectory("sapphire-transfer-limit-");
        try {
            IncomingFileTransfer receiver = new IncomingFileTransfer(root.toFile());
            byte[] data = new byte[IncomingFileTransfer.MAX_BYTES];
            String id = UUID.randomUUID().toString();
            assertEquals("ready", receiver.handle("file.offer", offer(id, data), true, 0).getString("state"));
            int index = 0;
            byte[] chunk = new byte[IncomingFileTransfer.CHUNK_BYTES];
            while (index * chunk.length < data.length) {
                assertEquals("chunk", receiver.handle("file.chunk", chunk(id, index, chunk), true, index + 1).getString("state"));
                index++;
            }
            JSONObject done = receiver.handle("file.finish", new JSONObject().put("requestId", id), true, index + 1);
            assertEquals("complete", done.getString("state"));
            assertEquals(data.length / IncomingFileTransfer.CHUNK_BYTES, done.getInt("nextIndex"));
            assertEquals(data.length, done.getLong("receivedBytes"));
            assertTrue(receiver.discard(id));
        } finally { cleanup(root); }
    }

}

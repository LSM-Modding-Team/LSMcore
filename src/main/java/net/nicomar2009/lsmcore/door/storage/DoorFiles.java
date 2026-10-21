package net.nicomar2009.lsmcore.door.storage;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

final class DoorFiles {
    static final int MAX_BYTES=16*1024*1024;
    private DoorFiles() {}
    static JsonObject read(Path file) throws IOException {
        if (Files.size(file)>MAX_BYTES) throw new IOException("Door storage exceeds bound");
        return JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject();
    }
    static void write(Path file,JsonObject data) throws IOException {
        byte[] bytes=data.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length>MAX_BYTES) throw new IllegalArgumentException("storage_limit");
        Files.createDirectories(file.getParent()); Path temp=Files.createTempFile(file.getParent(),"door-",".tmp");
        try {
            try (var channel=java.nio.channels.FileChannel.open(temp,StandardOpenOption.WRITE)) {
                var buffer=java.nio.ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true);
            }
            Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
}

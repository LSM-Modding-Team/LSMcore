package net.nicomar2009.lsmcore.window.storage;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class JsonFiles {
    private JsonFiles() {}
    public static JsonObject read(Path path,long maxBytes) throws IOException {
        if (Files.size(path)>maxBytes) throw new IOException("Window data exceeds absolute file bound");
        return JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();
    }
    public static void write(Path path,JsonObject json) throws IOException {
        Files.createDirectories(path.getParent()); Path temp=Files.createTempFile(path.getParent(),"window-",".tmp");
        try {
            Files.writeString(temp,json.toString(),StandardCharsets.UTF_8);
            Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
}

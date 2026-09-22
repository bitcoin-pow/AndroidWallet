import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Removes metadata chunks without decoding/re-encoding the PNG image data. */
class StripPngMetadata {
    public static void main(String[] args) throws Exception {
        for (String name : args) {
            var file = Path.of(name);
            byte[] original = Files.readAllBytes(file);
            var before = ImageIO.read(new ByteArrayInputStream(original));
            if (before == null) throw new IOException("Not a supported PNG");
            var input = new DataInputStream(new ByteArrayInputStream(original));
            var buffer = new ByteArrayOutputStream();
            var output = new DataOutputStream(buffer);
            byte[] signature = input.readNBytes(8);
            if (!Arrays.equals(signature, new byte[]{(byte)137,80,78,71,13,10,26,10})) throw new IOException("Not PNG");
            output.write(signature);
            int removed = 0;
            while (input.available() > 0) {
                int length = input.readInt();
                if (length < 0 || length > input.available() - 8) throw new IOException("Invalid chunk");
                byte[] type = input.readNBytes(4), data = input.readNBytes(length), crc = input.readNBytes(4);
                String kind = new String(type, java.nio.charset.StandardCharsets.US_ASCII);
                if (Set.of("tEXt", "zTXt", "iTXt", "eXIf", "tIME").contains(kind)) { removed++; continue; }
                output.writeInt(length); output.write(type); output.write(data); output.write(crc);
            }
            byte[] sanitized = buffer.toByteArray();
            var after = ImageIO.read(new ByteArrayInputStream(sanitized));
            int width = before.getWidth(), height = before.getHeight();
            if (width != after.getWidth() || height != after.getHeight() ||
                !Arrays.equals(before.getRGB(0, 0, width, height, null, 0, width), after.getRGB(0, 0, width, height, null, 0, width)))
                throw new IOException("Pixel verification failed");
            if (removed > 0) Files.write(file, sanitized);
            System.out.println(file.getFileName() + ": removed " + removed + " metadata chunks; pixels unchanged");
        }
    }
}

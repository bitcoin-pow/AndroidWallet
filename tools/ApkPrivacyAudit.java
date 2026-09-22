import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import java.io.*;

/** Scans uncompressed APK entries; never prints matching personal data or secrets. */
class ApkPrivacyAudit {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: java tools/ApkPrivacyAudit.java file.apk");
        var patterns = new LinkedHashMap<String, Pattern>();
        String username = System.getProperty("user.name");
        if (username.length() >= 3) patterns.put("local username", Pattern.compile(Pattern.quote(username), Pattern.CASE_INSENSITIVE));
        patterns.put("local home directory", Pattern.compile(Pattern.quote(System.getProperty("user.home")), Pattern.CASE_INSENSITIVE));
        patterns.put("developer filesystem path", Pattern.compile("(?:/Users/|/home/|[A-Z]:\\\\Users\\\\)[^\\s\\x00/\\\\]+", Pattern.CASE_INSENSITIVE));
        patterns.put("private-key PEM", Pattern.compile("-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----"));
        patterns.put("test-only wallet fixtures", Pattern.compile("com/wallet/btcw/(?:WalletPipelineTest|SeedVaultBiometricTest|DesignPreviewTest)"));
        int entries = 0, findings = 0, pngMetadata = 0;
        byte[] archive = Files.readAllBytes(Path.of(args[0]));
        for (var check : patterns.entrySet()) {
            boolean match = false;
            for (var encoding : List.of(StandardCharsets.ISO_8859_1, StandardCharsets.UTF_16LE, StandardCharsets.UTF_16BE))
                match |= check.getValue().matcher(new String(archive, encoding)).find();
            if (match) { System.out.println("REVIEW: " + check.getKey() + " in APK container/signing data"); findings++; }
        }
        try (var zip = new ZipFile(args[0])) {
            var files = zip.entries();
            while (files.hasMoreElements()) {
                var entry = files.nextElement();
                if (entry.isDirectory()) continue;
                entries++;
                byte[] bytes;
                try (var stream = zip.getInputStream(entry)) { bytes = stream.readNBytes(100_000_001); }
                if (bytes.length > 100_000_000) throw new IOException("Entry exceeds audit size limit");
                var encodings = List.of(StandardCharsets.ISO_8859_1, StandardCharsets.UTF_16LE, StandardCharsets.UTF_16BE);
                for (var check : patterns.entrySet()) {
                    boolean match = check.getValue().matcher(entry.getName()).find();
                    for (var encoding : encodings) match |= check.getValue().matcher(new String(bytes, encoding)).find();
                    if (match) { System.out.println("REVIEW: " + check.getKey() + " in " + entry.getName()); findings++; }
                }
                if (entry.getName().matches("(?i).*(?:\\.(?:jks|keystore|p12|pfx)|local\\.properties|wallet-transactions-v1\\.json|wallet-seed-v1\\.bin|wallet-rescan-requested|seed-vault.*)$")) {
                    System.out.println("REVIEW: unexpected private/config file " + entry.getName()); findings++;
                }
                if (entry.getName().endsWith(".png") && bytes.length > 8) {
                    var data = new DataInputStream(new ByteArrayInputStream(bytes, 8, bytes.length - 8));
                    while (data.available() >= 12) {
                        int length = data.readInt();
                        String type = new String(data.readNBytes(4), StandardCharsets.US_ASCII);
                        if (length < 0 || length > data.available() - 4) throw new IOException("Invalid PNG chunk");
                        if (Set.of("tEXt", "zTXt", "iTXt", "eXIf", "tIME").contains(type)) {
                            System.out.println("REVIEW: image metadata " + type + " in " + entry.getName()); pngMetadata++;
                        }
                        data.skipNBytes(length + 4L);
                    }
                }
                if (entry.getName().endsWith(".webp") && bytes.length >= 12) {
                    var data = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                    data.position(12);
                    while (data.remaining() >= 8) {
                        byte[] tag = new byte[4]; data.get(tag);
                        String type = new String(tag, StandardCharsets.US_ASCII);
                        int length = data.getInt();
                        long padded = (long) length + (length & 1);
                        if (length < 0 || padded > data.remaining()) throw new IOException("Invalid WebP chunk");
                        if (Set.of("EXIF", "XMP ").contains(type)) {
                            System.out.println("REVIEW: image metadata " + type + " in " + entry.getName()); pngMetadata++;
                        }
                        data.position(data.position() + (int) padded);
                    }
                }
            }
        }
        System.out.println("APK entries inspected: " + entries);
        System.out.println("Personal-path/secret/test-file findings: " + findings);
        System.out.println("Image metadata chunks requiring review: " + pngMetadata);
        if (findings != 0 || pngMetadata != 0) System.exit(1);
    }
}

// SPDX-License-Identifier: GPL-2.0-or-later
package org.dolphinemu.dolphinemu.features.skylanders;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Keeps writable development figures separate from read-only import sources. */
public final class FigureCopyStore {
    public interface Source {
        InputStream open() throws IOException;
    }

    private static final int FIGURE_SIZE = 1024;
    private final File directory;

    public FigureCopyStore(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Cannot create development figure directory");
        }
        this.directory = directory.getCanonicalFile();
    }

    public synchronized File importCopy(String identity, Source source) throws IOException {
        File destination = new File(directory, hash(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + ".sky");
        if (!directory.equals(destination.getCanonicalFile().getParentFile())) {
            throw new IOException("Figure destination escapes development collection");
        }
        if (destination.exists()) {
            if (!destination.isFile() || destination.length() != FIGURE_SIZE) {
                throw new IOException("Existing development figure is invalid; refusing to overwrite it");
            }
            return destination; // Preserve progress written to the previously imported copy.
        }

        byte[] bytes = new byte[FIGURE_SIZE + 1];
        int size = 0;
        try (InputStream input = source.open()) {
            if (input == null) throw new IOException("Cannot open figure source");
            while (size < bytes.length) {
                int count = input.read(bytes, size, bytes.length - size);
                if (count < 0) break;
                if (count == 0) {
                    int next = input.read();
                    if (next < 0) break;
                    bytes[size++] = (byte) next;
                } else {
                    size += count;
                }
            }
        }
        if (size != FIGURE_SIZE) throw new IOException("A figure image must be exactly 1024 bytes");

        File temporary = File.createTempFile("import-", ".tmp", directory);
        boolean published = false;
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(bytes, 0, FIGURE_SIZE);
                output.getFD().sync();
            }
            if (destination.exists() || !temporary.renameTo(destination)) {
                throw new IOException("Cannot publish development figure copy");
            }
            published = true;
            // Retain a checksum of the original bytes without retaining a sensitive source URI.
            byte[] original = java.util.Arrays.copyOf(bytes, FIGURE_SIZE);
            try (FileOutputStream provenance = new FileOutputStream(new File(directory, destination.getName() + ".source.sha256"))) {
                provenance.write((hash(original) + "\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            }
            return destination;
        } finally {
            if (!published && temporary.exists() && !temporary.delete()) temporary.deleteOnExit();
        }
    }

    public synchronized File newFigure() throws IOException {
        // Each creation gets its own destination; no original URI or existing progress is overwritten.
        return File.createTempFile("created-", ".sky", directory);
    }

    private static String hash(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}

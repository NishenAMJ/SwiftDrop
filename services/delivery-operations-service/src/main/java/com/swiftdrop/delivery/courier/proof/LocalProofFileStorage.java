package com.swiftdrop.delivery.courier.proof;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Keeps proof files in one directory on disk (a Docker volume in compose). Each file is named by a random id the
 * server generates, so nothing the courier supplies ever becomes part of a path.
 */
@Component
public class LocalProofFileStorage implements ProofFileStorage {

    private static final Pattern KEY = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final Path directory;

    public LocalProofFileStorage(@Value("${delivery.proof.storage-dir:./data/proofs}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.directory);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create the proof storage directory " + this.directory, e);
        }
    }

    @Override
    public String save(byte[] bytes) {
        var key = UUID.randomUUID().toString();
        try {
            Files.write(locate(key), bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store the proof file", e);
        }
        return key;
    }

    @Override
    public byte[] read(String key) {
        try {
            return Files.readAllBytes(locate(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the proof file", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(locate(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not remove the proof file", e);
        }
    }

    /** Only a key in the exact shape we generate, resolved inside the storage directory, is ever opened. */
    private Path locate(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Not a proof file key");
        }
        var path = directory.resolve(key).normalize();
        if (!path.startsWith(directory)) {
            throw new IllegalArgumentException("Not a proof file key");
        }
        return path;
    }
}

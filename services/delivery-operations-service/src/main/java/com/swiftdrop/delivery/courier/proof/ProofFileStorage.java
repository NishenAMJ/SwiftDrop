package com.swiftdrop.delivery.courier.proof;

/** Where proof file bytes are kept. The rest of the service only ever holds the returned key. */
public interface ProofFileStorage {

    /** Stores the bytes under a new random key and returns it. */
    String save(byte[] bytes);

    /** The bytes for a key this storage returned earlier. */
    byte[] read(String key);

    /** Removes a stored file; a key that is already gone is not an error. */
    void delete(String key);
}

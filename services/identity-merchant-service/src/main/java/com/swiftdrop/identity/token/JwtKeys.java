package com.swiftdrop.identity.token;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.swiftdrop.identity.config.IdentityProperties;

/**
 * The RSA key that signs access tokens.
 *
 * <p>Read from {@code IDENTITY_JWT_PRIVATE_KEY_PATH} (PKCS#8 PEM). When that is not set, a
 * temporary key is generated at startup, so tokens stop working after a restart; fine for local
 * development and CI, never for a shared deployment. The key ID is the key's RFC 7638 thumbprint,
 * so the same key always gets the same ID.
 */
@Component
public class JwtKeys {

    private static final Logger log = LoggerFactory.getLogger(JwtKeys.class);

    private final RSAKey signingKey;

    public JwtKeys(IdentityProperties properties) throws Exception {
        String path = properties.jwt().privateKeyPath();
        KeyPair keyPair = StringUtils.hasText(path) ? readKeyPair(Path.of(path)) : temporaryKeyPair();

        RSAKey key = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
            .privateKey((RSAPrivateKey) keyPair.getPrivate())
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .build();
        this.signingKey = new RSAKey.Builder(key).keyID(key.computeThumbprint().toString()).build();
        log.info("Access tokens are signed with key id {}", signingKey.getKeyID());
    }

    public RSAKey signingKey() {
        return signingKey;
    }

    public String keyId() {
        return signingKey.getKeyID();
    }

    public RSAPublicKey publicKey() {
        try {
            return signingKey.toRSAPublicKey();
        }
        catch (JOSEException ex) {
            throw new IllegalStateException("Signing key has no public part", ex);
        }
    }

    /** The JWKS document: public keys only. */
    public Map<String, Object> publicJwkSet() {
        return new JWKSet(signingKey.toPublicJWK()).toJSONObject();
    }

    private static KeyPair readKeyPair(Path path) throws Exception {
        RSAPrivateKey privateKey;
        try (InputStream in = Files.newInputStream(path)) {
            privateKey = RsaKeyConverters.pkcs8().convert(in);
        }
        if (!(privateKey instanceof RSAPrivateCrtKey crt)) {
            throw new IllegalStateException("IDENTITY_JWT_PRIVATE_KEY_PATH must point to an RSA private key in PKCS#8 PEM format");
        }
        RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
            .generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
        log.info("Loaded JWT signing key from {}", path);
        return new KeyPair(publicKey, privateKey);
    }

    private static KeyPair temporaryKeyPair() throws GeneralSecurityException {
        log.warn("IDENTITY_JWT_PRIVATE_KEY_PATH is not set: using a temporary signing key. "
                + "Access tokens stop working when the service restarts.");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}

package bao.gabriel.cognia.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeeTestVerifierTest {

    @Test
    void signsTheLotNumberWithHmacSha256() {
        // Standard HMAC-SHA256 test vector.
        assertEquals("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
                GeeTestVerifier.signToken("The quick brown fox jumps over the lazy dog", "key"));
    }

    @Test
    void producesLowerCaseHex() {
        String signature = GeeTestVerifier.signToken("4dc3cfc2cdff448cad8d13107198d473",
                "b09a7aafbfd83f73b35a9b530d0337bf");

        assertEquals(64, signature.length());
        assertEquals(signature.toLowerCase(), signature);
    }
}

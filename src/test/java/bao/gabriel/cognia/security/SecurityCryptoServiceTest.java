package bao.gabriel.cognia.security;

import bao.gabriel.cognia.config.properties.SettingsProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityCryptoServiceTest {

    private SecurityCryptoService service(String secret) {
        SettingsProperties properties = new SettingsProperties();
        properties.setSecret(secret);
        return new SecurityCryptoService(properties);
    }

    @Test
    void encryptsAndDecryptsSmtpPassword() {
        SecurityCryptoService crypto = service("0123456789abcdef0123456789abcdef");
        String ciphertext = crypto.encrypt("smtp-secret-value");

        assertNotNull(ciphertext);
        assertTrue(ciphertext.length() > 16);
        assertEquals("smtp-secret-value", crypto.decrypt(ciphertext));
    }

    @Test
    void returnsNullWhenTheKeyChanged() {
        SecurityCryptoService first = service("0123456789abcdef0123456789abcdef");
        SecurityCryptoService second = service("fedcba9876543210fedcba9876543210");
        String ciphertext = first.encrypt("smtp-secret-value");

        assertNull(second.decrypt(ciphertext));
    }

    @Test
    void rejectsShortSecret() {
        assertThrows(IllegalStateException.class, () -> service("short"));
    }
}

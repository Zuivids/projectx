package lv.lms.auth.mfa;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import lv.lms.config.AppProperties;

import org.springframework.stereotype.Component;

/**
 * Encrypts MFA secrets with AES-256-GCM. Output is {@code iv || ciphertext+tag}. The
 * user id is bound as associated data, so a ciphertext copied to another user's row
 * fails to decrypt.
 */
@Component
public class SecretCipher {

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";

	private static final int IV_LENGTH = 12;

	private static final int TAG_BITS = 128;

	private final SecretKey key;

	private final SecureRandom random = new SecureRandom();

	public SecretCipher(AppProperties properties) {
		byte[] raw;
		try {
			raw = Base64.getDecoder().decode(properties.mfa().encryptionKey());
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException("app.mfa.encryption-key must be base64", ex);
		}
		if (raw.length != 32) {
			throw new IllegalStateException("app.mfa.encryption-key must decode to 32 bytes (AES-256)");
		}
		this.key = new SecretKeySpec(raw, "AES");
	}

	public byte[] encrypt(byte[] plaintext, long userId) {
		try {
			byte[] iv = new byte[IV_LENGTH];
			this.random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, this.key, new GCMParameterSpec(TAG_BITS, iv));
			cipher.updateAAD(associatedData(userId));
			byte[] ciphertext = cipher.doFinal(plaintext);
			return ByteBuffer.allocate(IV_LENGTH + ciphertext.length).put(iv).put(ciphertext).array();
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Could not encrypt MFA secret", ex);
		}
	}

	public byte[] decrypt(byte[] data, long userId) {
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, this.key,
					new GCMParameterSpec(TAG_BITS, Arrays.copyOfRange(data, 0, IV_LENGTH)));
			cipher.updateAAD(associatedData(userId));
			return cipher.doFinal(data, IV_LENGTH, data.length - IV_LENGTH);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Could not decrypt MFA secret", ex);
		}
	}

	private static byte[] associatedData(long userId) {
		return ByteBuffer.allocate(Long.BYTES).putLong(userId).array();
	}

}

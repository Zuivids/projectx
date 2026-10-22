package lv.lms.auth.mfa;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

/** RFC 4648 Base32 without padding, as used by authenticator apps. */
public final class Base32 {

	private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

	private Base32() {
	}

	public static String encode(byte[] data) {
		StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
		int buffer = 0;
		int bits = 0;
		for (byte b : data) {
			buffer = (buffer << 8) | (b & 0xFF);
			bits += 8;
			while (bits >= 5) {
				out.append(ALPHABET.charAt((buffer >> (bits - 5)) & 0x1F));
				bits -= 5;
			}
		}
		if (bits > 0) {
			out.append(ALPHABET.charAt((buffer << (5 - bits)) & 0x1F));
		}
		return out.toString();
	}

	public static byte[] decode(String encoded) {
		String normalized = encoded.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
		ByteArrayOutputStream out = new ByteArrayOutputStream(normalized.length() * 5 / 8);
		int buffer = 0;
		int bits = 0;
		for (char c : normalized.toCharArray()) {
			int value = ALPHABET.indexOf(c);
			if (value < 0) {
				throw new IllegalArgumentException("Invalid Base32 character");
			}
			buffer = (buffer << 5) | value;
			bits += 5;
			if (bits >= 8) {
				out.write((buffer >> (bits - 8)) & 0xFF);
				bits -= 8;
			}
		}
		return out.toByteArray();
	}

}

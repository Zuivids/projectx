package lv.lms.auth.dto;

/**
 * @param secret Base32 secret for manual entry in an authenticator app
 * @param otpauthUri URI to render as a QR code
 */
public record TotpSetupResponse(String secret, String otpauthUri) {
}

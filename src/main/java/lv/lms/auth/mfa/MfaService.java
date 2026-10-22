package lv.lms.auth.mfa;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import lv.lms.audit.AuditEventType;
import lv.lms.audit.AuditService;
import lv.lms.common.ApiException;
import lv.lms.config.AppProperties;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MfaService {

	/** 160-bit secret, the length recommended by RFC 4226. */
	private static final int SECRET_BYTES = 20;

	private final TotpFactorRepository totpFactors;

	private final RecoveryCodeRepository recoveryCodes;

	private final SecretCipher cipher;

	private final AuditService audit;

	private final AppProperties properties;

	private final Clock clock;

	private final SecureRandom random = new SecureRandom();

	MfaService(TotpFactorRepository totpFactors, RecoveryCodeRepository recoveryCodes, SecretCipher cipher,
			AuditService audit, AppProperties properties, Clock clock) {
		this.totpFactors = totpFactors;
		this.recoveryCodes = recoveryCodes;
		this.cipher = cipher;
		this.audit = audit;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public boolean isEnabled(long userId) {
		return this.totpFactors.findById(userId).map(TotpFactor::isConfirmed).orElse(false);
	}

	@Transactional(readOnly = true)
	public long remainingRecoveryCodes(long userId) {
		return this.recoveryCodes.countUnused(userId);
	}

	/**
	 * Creates a new, unconfirmed authenticator secret. Calling it again before
	 * confirming replaces the previous secret.
	 */
	@Transactional
	public TotpSetup startTotpSetup(long userId, String email) {
		Instant now = this.clock.instant();
		Optional<TotpFactor> existing = this.totpFactors.findForUpdate(userId);
		if (existing.isPresent() && existing.get().isConfirmed()) {
			throw ApiException.conflict("MFA is already enabled");
		}
		byte[] secret = new byte[SECRET_BYTES];
		this.random.nextBytes(secret);
		byte[] ciphertext = this.cipher.encrypt(secret, userId);
		existing.ifPresentOrElse((factor) -> factor.replaceSecret(ciphertext, now),
				() -> this.totpFactors.save(new TotpFactor(userId, ciphertext, now)));
		String base32 = Base32.encode(secret);
		return new TotpSetup(base32, otpauthUri(email, base32));
	}

	/**
	 * Confirms the authenticator with a first code and issues new recovery codes.
	 * @return the plaintext recovery codes; they are not stored and cannot be shown again
	 */
	@Transactional
	public List<String> confirmTotpSetup(long userId, String code, String ipAddress) {
		TotpFactor factor = this.totpFactors.findForUpdate(userId)
			.filter((f) -> !f.isConfirmed())
			.orElseThrow(() -> ApiException.conflict("No MFA setup in progress"));
		Instant now = this.clock.instant();
		OptionalLong step = Totp.verify(secretOf(factor), code, now);
		if (step.isEmpty()) {
			throw ApiException.badRequest("Invalid verification code");
		}
		factor.confirm(step.getAsLong(), now);
		List<String> codes = issueRecoveryCodes(userId, now);
		this.audit.record(AuditEventType.MFA_ENABLED, userId, ipAddress, null);
		return codes;
	}

	/**
	 * Checks a login challenge answer: a 6-digit authenticator code or a recovery code.
	 * Each authenticator code and each recovery code works only once.
	 */
	@Transactional
	public boolean verifyChallenge(long userId, String code, String ipAddress) {
		Instant now = this.clock.instant();
		String trimmed = code.strip();
		if (trimmed.length() == Totp.DIGITS) {
			Optional<TotpFactor> factor = this.totpFactors.findForUpdate(userId).filter(TotpFactor::isConfirmed);
			if (factor.isEmpty()) {
				return false;
			}
			OptionalLong step = Totp.verify(secretOf(factor.get()), trimmed, now);
			return step.isPresent() && factor.get().useStep(step.getAsLong());
		}
		boolean used = this.recoveryCodes.markUsed(userId, RecoveryCodes.hash(trimmed), now) == 1;
		if (used) {
			this.audit.record(AuditEventType.RECOVERY_CODE_USED, userId, ipAddress,
					"remaining=" + this.recoveryCodes.countUnused(userId));
		}
		return used;
	}

	private List<String> issueRecoveryCodes(long userId, Instant now) {
		this.recoveryCodes.deleteAllForUser(userId);
		List<String> codes = new ArrayList<>(RecoveryCodes.COUNT);
		while (codes.size() < RecoveryCodes.COUNT) {
			String code = RecoveryCodes.generate();
			if (!codes.contains(code)) {
				codes.add(code);
			}
		}
		this.recoveryCodes.saveAll(codes.stream().map((c) -> new RecoveryCode(userId, RecoveryCodes.hash(c), now)).toList());
		return codes;
	}

	private byte[] secretOf(TotpFactor factor) {
		return this.cipher.decrypt(factor.getSecretCiphertext(), factor.getUserId());
	}

	private String otpauthUri(String email, String base32Secret) {
		String issuer = encode(this.properties.name());
		return "otpauth://totp/" + issuer + ":" + encode(email) + "?secret=" + base32Secret + "&issuer=" + issuer
				+ "&algorithm=SHA1&digits=" + Totp.DIGITS + "&period=" + Totp.PERIOD_SECONDS;
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	/**
	 * @param secret Base32 secret for manual entry in an authenticator app
	 * @param otpauthUri URI the frontend renders as a QR code
	 */
	public record TotpSetup(String secret, String otpauthUri) {
	}

}

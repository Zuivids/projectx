package lv.lms.auth.dto;

import java.util.List;

/** Shown once: the codes are stored only as hashes. */
public record RecoveryCodesResponse(List<String> recoveryCodes) {
}

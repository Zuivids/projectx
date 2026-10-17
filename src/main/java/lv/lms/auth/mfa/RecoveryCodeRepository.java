package lv.lms.auth.mfa;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, Long> {

	/**
	 * Atomically marks an unused code as used.
	 * @return 1 if the code was valid and unused, otherwise 0
	 */
	@Modifying
	@Query("update RecoveryCode c set c.usedAt = :now "
			+ "where c.userId = :userId and c.codeHash = :codeHash and c.usedAt is null")
	int markUsed(Long userId, String codeHash, Instant now);

	@Modifying
	@Query("delete from RecoveryCode c where c.userId = :userId")
	void deleteAllForUser(Long userId);

	@Query("select count(c) from RecoveryCode c where c.userId = :userId and c.usedAt is null")
	long countUnused(Long userId);

}

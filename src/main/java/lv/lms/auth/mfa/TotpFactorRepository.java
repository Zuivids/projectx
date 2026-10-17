package lv.lms.auth.mfa;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface TotpFactorRepository extends JpaRepository<TotpFactor, Long> {

	/** Row lock, so two concurrent requests cannot both use the same code. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select f from TotpFactor f where f.userId = :userId")
	Optional<TotpFactor> findForUpdate(Long userId);

}

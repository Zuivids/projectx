package lv.lms.auth.mfa;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface TotpFactorRepository extends JpaRepository<TotpFactor, Long> {

	/** Row lock, so two concurrent requests cannot both use the same code. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select f from TotpFactor f where f.userId = :userId")
	Optional<TotpFactor> findForUpdate(Long userId);

	@Query("select f.userId from TotpFactor f where f.userId in :userIds and f.confirmedAt is not null")
	Set<Long> findConfirmedUserIds(Collection<Long> userIds);

}

package lv.lms.ratelimit;

import java.time.Duration;
import java.time.Instant;

import lv.lms.MutableClock;
import lv.lms.config.AppProperties.RateLimit.Rule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTests {

	private final MutableClock clock = new MutableClock(Instant.parse("2026-10-22T10:00:00Z"));

	private final RateLimiter limiter = new RateLimiter(this.clock);

	private final Rule tenPerMinute = new Rule(10, Duration.ofMinutes(1));

	@Test
	void allowsABurstUpToTheLimitThenRefuses() {
		for (int i = 0; i < 10; i++) {
			assertThat(this.limiter.tryAcquire("a", this.tenPerMinute).allowed()).isTrue();
		}
		RateLimiter.Decision refused = this.limiter.tryAcquire("a", this.tenPerMinute);
		assertThat(refused.allowed()).isFalse();
		// One token comes back every 6 seconds
		assertThat(refused.retryAfter()).isEqualTo(Duration.ofSeconds(6));
	}

	@Test
	void refillsGradually() {
		for (int i = 0; i < 10; i++) {
			this.limiter.tryAcquire("a", this.tenPerMinute);
		}
		this.clock.advance(Duration.ofSeconds(6));
		assertThat(this.limiter.tryAcquire("a", this.tenPerMinute).allowed()).isTrue();
		assertThat(this.limiter.tryAcquire("a", this.tenPerMinute).allowed()).isFalse();

		this.clock.advance(Duration.ofMinutes(5));
		for (int i = 0; i < 10; i++) {
			assertThat(this.limiter.tryAcquire("a", this.tenPerMinute).allowed()).isTrue();
		}
		// Idle time does not let tokens pile up beyond the limit
		assertThat(this.limiter.tryAcquire("a", this.tenPerMinute).allowed()).isFalse();
	}

	@Test
	void keysAreIndependent() {
		for (int i = 0; i < 10; i++) {
			this.limiter.tryAcquire("a", this.tenPerMinute);
		}
		assertThat(this.limiter.tryAcquire("a", this.tenPerMinute).allowed()).isFalse();
		assertThat(this.limiter.tryAcquire("b", this.tenPerMinute).allowed()).isTrue();
	}

}

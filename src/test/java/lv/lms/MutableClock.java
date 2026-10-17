package lv.lms;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Test clock that only moves when told to. */
public class MutableClock extends Clock {

	private Instant now;

	public MutableClock(Instant start) {
		this.now = start;
	}

	public synchronized void advance(Duration duration) {
		this.now = this.now.plus(duration);
	}

	@Override
	public synchronized Instant instant() {
		return this.now;
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		throw new UnsupportedOperationException();
	}

}

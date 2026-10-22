package lv.lms.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/** Append-only security event. Never updated or deleted by the application. */
@Entity
@Immutable
@Table(name = "audit_log")
public class AuditLogEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 50)
	private AuditEventType eventType;

	@Column(name = "user_id")
	private Long userId;

	@Column(name = "ip_address", length = 45)
	private String ipAddress;

	@Column(length = 500)
	private String details;

	protected AuditLogEntry() {
	}

	AuditLogEntry(Instant occurredAt, AuditEventType eventType, Long userId, String ipAddress, String details) {
		this.occurredAt = occurredAt;
		this.eventType = eventType;
		this.userId = userId;
		this.ipAddress = ipAddress;
		this.details = details;
	}

	public Instant getOccurredAt() {
		return this.occurredAt;
	}

	public AuditEventType getEventType() {
		return this.eventType;
	}

	public Long getUserId() {
		return this.userId;
	}

	public String getIpAddress() {
		return this.ipAddress;
	}

	public String getDetails() {
		return this.details;
	}

}

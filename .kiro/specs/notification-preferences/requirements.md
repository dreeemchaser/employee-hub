# Notification Preferences

**Source:** `docs/next-gen-features.md`, Feature 17.  
**Scope:** per-event email preferences backed by the existing in-app notification system. SMS,
WebSockets, digests, and quiet hours are deferred.

## Requirements

- Every authenticated employee can view preferences for every `NotificationType`.
- New preferences default to email disabled.
- In-app notifications are always created and are not controlled by email preferences.
- An employee can enable or disable email delivery independently for each notification type.
- Preference updates affect only the authenticated employee; no employee id is accepted from the client.
- Email delivery remains safe when mail is disabled or SMTP is unavailable.
- The existing notification event sends an email only when the recipient's preference for that
  event type is enabled.
- The preference table uses one row per employee and notification type.

## Acceptance checks

- `mvnw verify` or `mvnw test` passes.
- The Flyway migration validates against the entity model.
- The employee portal exposes the preference controls beside the notification inbox.

# Design

`NotificationPreference` stores one row per employee and `NotificationType`, with a unique
constraint on `(employee_id, notification_type)`. Missing rows are represented as disabled
defaults by `NotificationService`; they are created only when the employee changes a setting.

`GET /notifications/preferences` returns the complete fixed set of notification types.
`PATCH /notifications/preferences/{type}` updates only the authenticated employee's row. The
controller resolves the employee from `UserDetails`; the service performs the repository write.

`NotificationService.send` always persists the in-app `Notification`. It then checks the
recipient's preference and delegates email delivery to the existing config-gated `EmailService`.
Email failures cannot fail the workflow because `EmailService` already treats delivery as
best-effort.

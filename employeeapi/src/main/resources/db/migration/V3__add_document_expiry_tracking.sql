-- Document expiration tracking. expiry_date is optional (not every document
-- type expires); the three reminder_*_sent_at columns record the first time
-- each threshold's reminder fired for a document, so the daily reminder job
-- never re-sends the same threshold twice. hr_expiry_notified_at is a
-- separate flag for the distinct "a VERIFIED document has now expired"
-- notification sent to HR/Admin, not the employee-facing reminders.
ALTER TABLE documents ADD COLUMN expiry_date date;
ALTER TABLE documents ADD COLUMN reminder_30_sent_at timestamp(6) without time zone;
ALTER TABLE documents ADD COLUMN reminder_14_sent_at timestamp(6) without time zone;
ALTER TABLE documents ADD COLUMN reminder_7_sent_at timestamp(6) without time zone;
ALTER TABLE documents ADD COLUMN hr_expiry_notified_at timestamp(6) without time zone;

-- The reminder job's queries filter on expiry_date; this table grows with
-- every upload, so the column used in the WHERE clause is indexed per
-- steering (index filter/join keys on growing tables).
CREATE INDEX idx_documents_expiry_date ON documents (expiry_date);

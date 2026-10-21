# Design

`DocumentService` is the single authorization boundary. Controllers resolve the authenticated
employee and pass that employee id into service read methods. The service returns
`ResourceNotFoundException` for records the caller may not know about.

`Document.accessLevel` is an enum stored as a string. Migration `V4__add_document_access_levels.sql`
adds the non-null column, backfills existing payslips, and constrains valid values.

The HR documents page exposes a visibility selector backed by
`PATCH /documents/{id}/access-level`. The endpoint is restricted at the route layer to HR and
super administrators, and the service repeats the role check before mutating the record.

Downloads are audited after authorization and before file loading. No filename or storage URL is
used as an authorization token.

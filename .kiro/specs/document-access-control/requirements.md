# Document Access Control & Audit Trail

**Source:** `docs/next-gen-features.md`, Feature 8.  
**Scope:** access levels and object-level authorization for document metadata/downloads, plus audit events. Digital signatures, OCR, sharing links, and archival retention are separate features.

## Requirements

- Every document has an access level: `STANDARD`, `CONFIDENTIAL`, or `PAYROLL_ONLY`.
- New payslips default to `PAYROLL_ONLY`; other uploads default to `STANDARD`.
- The document owner may access their own document.
- `HR_ADMIN` and `SUPER_ADMIN` may access all documents.
- `PAYROLL_ADMIN` may access `PAYROLL_ONLY` documents.
- Other employees and managers may not access another employee's documents.
- Unauthorized document reads return not-found semantics rather than revealing that a document exists.
- HR administrators may change a document's access level.
- Uploads, downloads, verification/rejection, and access-level changes are recorded in the existing audit log.
- The access level is exposed in `DocumentResponse`; internal storage paths remain private.
- Existing documents are migrated; existing payslips become `PAYROLL_ONLY`, and all other documents become `STANDARD`.

## Acceptance checks

- `mvnw test` passes.
- A database with Flyway enabled starts successfully and validates the new column.
- Both frontend builds pass when dependencies are installed.

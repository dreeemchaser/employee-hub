# Tax Table Administration

**Source:** `docs/next-gen-features.md`, Feature 12.  
**Scope:** administration of the existing South African PAYE bracket data. Multiple jurisdictions,
EMP201 reporting, external SARS integrations, and flexible employee deductions are deferred.

## Requirements

- Payroll and HR administrators may list brackets, optionally filtered by tax year.
- Payroll and HR administrators may create and update brackets.
- Employees and managers cannot access tax-table administration endpoints.
- Requests validate non-negative monetary values, rates from 0 to 100, and tax years from 2000 onward.
- A bracket's minimum income cannot exceed its maximum income.
- Brackets in the same tax year may not overlap; an open-ended bracket may exist only once.
- Changes are written through the service layer and audit logged with before/after values.
- Existing PAYE calculation behavior remains unchanged and historical tax years remain available.
- The HR dashboard provides a tax-year filter and an editor for bracket values.

## Acceptance checks

- `mvnw test` passes.
- The HR dashboard production build passes.
- Existing salary/PAYE tests remain green.

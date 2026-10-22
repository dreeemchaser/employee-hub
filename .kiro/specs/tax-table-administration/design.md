# Design

`TaxBracketService` owns validation, persistence, and audit logging. `TaxBracketController`
resolves the authenticated actor and returns DTOs only. List responses are paged and can be
filtered by `taxYear`, following the repository's controller and collection rules.

Overlap validation compares the proposed range with all persisted brackets in the same tax year.
An omitted maximum represents the open-ended top bracket. Updates exclude the record being edited
from its own overlap check.

The existing `TaxBracketRepository.findBracketForIncome` query and `SalaryService` PAYE formula
are unchanged. This feature only adds a safe administration surface over the current data model,
so no schema migration is required.

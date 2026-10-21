# Design

`LeaveController` resolves the authenticated employee and passes the caller id to
`LeaveService.getCalendar`. The service derives the visibility predicate from the caller's role;
the client never supplies employee or manager ids.

`LeaveRequestRepository.findApprovedInRange` applies the visibility predicate, month overlap,
and optional leave-type filter in one JPQL query. This prevents fetching all records and filtering
them in application memory.

The existing calendar grid remains the presentation layer. It sends the optional leave type id,
uses the scoped response unchanged, and marks days with more than one visible entry as busy.

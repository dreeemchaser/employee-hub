# Design

## API

Prefer a scoped read API rather than returning the entire employee graph:

- `GET /organization/tree` — returns the caller's permitted root nodes and summary children.
- `GET /organization/search?q=&departmentId=&teamId=` — paged employee summaries.
- `GET /organization/nodes/{employeeId}` — permitted node details and direct reports.

The response DTO should contain only employee id, display name, job title, department/team
summary, manager summary, and child-count metadata. It must not contain id numbers, salary,
documents, passwords, or unrestricted entity relationships.

## Service and query strategy

`OrganizationService` owns visibility rules and delegates filtered queries to repositories.
Avoid loading the whole organization into memory. Use projections and paged queries, with a
separate child lookup for expanded nodes. Managers are scoped through the existing manager
relationship; HR/Admin roles receive the organization-wide scope.

The controller resolves `UserDetails` to the current employee. Every endpoint taking an employee
identifier performs an object-level check and returns not-found semantics for invisible nodes.

## Frontend

Use a read-only tree with collapsed nodes, search, breadcrumbs, and responsive list fallback.
Do not add a second assignment/editing workflow. Reuse the existing design system and local
state patterns unless performance testing justifies a shared store.

## Testing

- Repository tests for department/team/manager filters and pagination.
- Service tests for employee, manager, HR, and super-admin scopes.
- WebMvc authorization matrix.
- Frontend tests for expansion, search, empty teams, and inaccessible nodes.

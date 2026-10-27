# Team Organization Chart

**Status:** Design only — no implementation in this slice.  
**Source:** `docs/next-gen-features.md`, Feature 15.  
**Purpose:** Expose the existing department, team, and manager hierarchy as a navigable organization view.

## Use cases

- HR views the complete organization by department and team.
- HR searches for an employee and sees their manager, team, and direct reports.
- A manager sees their own team hierarchy without gaining HR-only employee-management powers.
- An employee sees their own team and reporting line, subject to privacy rules.
- HR identifies employees missing a team or manager assignment.
- The chart remains usable for large organizations through search, filtering, and collapsed nodes.

## Requirements

- The chart must use existing department, team, employee, and manager relationships as the source of truth.
- HR/Admin may view the full organization.
- Managers may view their own team and reporting descendants only.
- Employees may view their own team and immediate reporting context only; sensitive employee fields must not be exposed.
- The API must return DTO projections, never JPA entities or sensitive PII.
- The chart must handle missing managers, empty teams, and terminated employees explicitly.
- Search and filters must be server-side for large datasets.
- Changes to assignments remain in the existing employee/team management workflow; the chart is not a second editing system.
- Access to organization data must follow object-level authorization, not only route-level roles.

## MVP scope

- Read-only organization tree endpoint.
- Department/team/manager filters.
- Employee search and node selection.
- HR dashboard visualization.
- Employee portal “My Organization” view with scoped results.

## Out of scope

- Drag-and-drop reassignment.
- Matrix reporting and dotted-line relationships.
- Org planning/version history.
- Headcount forecasting.
- Exposing salary, documents, performance scores, or other sensitive fields in nodes.

## Acceptance checks

- A manager cannot query another department or unrelated team by changing URL parameters.
- Large trees support pagination/search or lazy child loading.
- Missing hierarchy links are visible to HR as data-quality warnings.
- DTO and authorization tests cover each role.

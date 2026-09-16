-- =============================================================================
-- EmployeeHub seed data
-- Runs automatically when the postgres_data volume is first created.
-- This mirrors DataSeeder.java so the Spring seeder has nothing to insert.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Department
-- -----------------------------------------------------------------------------
INSERT INTO departments (name, description)
SELECT 'Human Resources', 'HR and administration'
WHERE NOT EXISTS (SELECT 1 FROM departments WHERE name = 'Human Resources');

-- -----------------------------------------------------------------------------
-- Team
-- -----------------------------------------------------------------------------
INSERT INTO teams (name, description, department_id)
SELECT 'Management', 'Leadership and management', d.id
FROM departments d
WHERE d.name = 'Human Resources'
  AND NOT EXISTS (SELECT 1 FROM teams WHERE name = 'Management');

-- -----------------------------------------------------------------------------
-- Employees
-- BCrypt (cost 10) hashes:
--   Admin@1234    -> $2b$10$X3ac0M3Iyin2GU9Kk9Xnpe55ghFt34zSbjOf6mTr2OZdhj3MfwCWq
--   Employee@1234 -> $2b$10$3OLdSI13USc0PFba5M8q5uOr8Uh2ewh.IysU2bXdPJQptemKHBY0O
-- -----------------------------------------------------------------------------
INSERT INTO employees (
    id, employee_number, first_name, last_name, email, password,
    job_title, employment_type, employment_status,
    start_date, role, department_id, team_id,
    created_at, updated_at
)
SELECT
    gen_random_uuid()::text,
    'EMP-001',
    'System', 'Admin',
    'admin@employeehub.com',
    '$2b$10$X3ac0M3Iyin2GU9Kk9Xnpe55ghFt34zSbjOf6mTr2OZdhj3MfwCWq',
    'System Administrator',
    'FULL_TIME', 'ACTIVE',
    CURRENT_DATE,
    'SUPER_ADMIN',
    d.id, t.id,
    NOW(), NOW()
FROM departments d
JOIN teams t ON t.department_id = d.id
WHERE d.name = 'Human Resources'
  AND NOT EXISTS (SELECT 1 FROM employees WHERE email = 'admin@employeehub.com');

INSERT INTO employees (
    id, employee_number, first_name, last_name, email, password,
    job_title, employment_type, employment_status,
    start_date, role, department_id, team_id,
    created_at, updated_at
)
SELECT
    gen_random_uuid()::text,
    'EMP-002',
    'Jane', 'Doe',
    'jane.doe@employeehub.com',
    '$2b$10$3OLdSI13USc0PFba5M8q5uOr8Uh2ewh.IysU2bXdPJQptemKHBY0O',
    'Software Engineer',
    'FULL_TIME', 'ACTIVE',
    CURRENT_DATE,
    'EMPLOYEE',
    d.id, t.id,
    NOW(), NOW()
FROM departments d
JOIN teams t ON t.department_id = d.id
WHERE d.name = 'Human Resources'
  AND NOT EXISTS (SELECT 1 FROM employees WHERE email = 'jane.doe@employeehub.com');

-- -----------------------------------------------------------------------------
-- Leave Types
-- -----------------------------------------------------------------------------
INSERT INTO leave_types (name, default_days, cycle_years, requires_documentation, is_paid)
SELECT name, default_days, cycle_years, requires_documentation, is_paid
FROM (VALUES
    ('Annual Leave',           15,  1, false, true),
    ('Sick Leave',             30,  3, true,  true),
    ('Family Responsibility',   3,  1, false, true),
    ('Maternity Leave',        120, 1, true,  true),
    ('Parental Leave',         10,  1, false, true),
    ('Study Leave',             5,  1, true,  false)
) AS v(name, default_days, cycle_years, requires_documentation, is_paid)
WHERE NOT EXISTS (SELECT 1 FROM leave_types lt WHERE lt.name = v.name);

-- -----------------------------------------------------------------------------
-- Leave Balances for Jane Doe (cycle: today → today + cycle_years)
-- -----------------------------------------------------------------------------
INSERT INTO leave_balances (
    employee_id, leave_type_id,
    total_days, used_days, remaining_days,
    cycle_start_date, cycle_end_date
)
SELECT
    e.id,
    lt.id,
    lt.default_days,
    0,
    lt.default_days,
    CURRENT_DATE,
    CURRENT_DATE + (lt.cycle_years || ' years')::interval
FROM employees e
JOIN leave_types lt ON true
WHERE e.email = 'jane.doe@employeehub.com'
  AND NOT EXISTS (
      SELECT 1 FROM leave_balances lb
      WHERE lb.employee_id = e.id AND lb.leave_type_id = lt.id
  );

-- -----------------------------------------------------------------------------
-- Tax Brackets  (SA 2025/2026 tax year)
-- -----------------------------------------------------------------------------
INSERT INTO tax_brackets (tax_year, min_income, max_income, base_tax, marginal_rate, rebate)
SELECT tax_year, min_income, max_income, base_tax, marginal_rate, rebate
FROM (VALUES
    (2025,       0, 237100,       0, 18.00, 17235),
    (2025,  237101, 370500,   42678, 26.00, 17235),
    (2025,  370501, 512800,   77362, 31.00, 17235),
    (2025,  512801, 673000,  121475, 36.00, 17235),
    (2025,  673001, 857900,  179147, 39.00, 17235),
    (2025,  857901,1817000,  251258, 41.00, 17235),
    (2025, 1817001,   NULL,  644489, 45.00, 17235)
) AS v(tax_year, min_income, max_income, base_tax, marginal_rate, rebate)
WHERE NOT EXISTS (SELECT 1 FROM tax_brackets WHERE tax_year = 2025);

-- -----------------------------------------------------------------------------
-- Benefit Types
-- -----------------------------------------------------------------------------
INSERT INTO benefit_types (name, description, employee_contribution, employer_contribution, is_optional)
SELECT name, description, employee_contribution, employer_contribution, is_optional
FROM (VALUES
    ('Medical Aid',  'Company medical aid scheme', 1500.00, 2000.00, false),
    ('Pension Fund', 'Retirement pension fund',    1000.00, 1500.00, false),
    ('Life Cover',   'Group life insurance',        200.00,  500.00, true)
) AS v(name, description, employee_contribution, employer_contribution, is_optional)
WHERE NOT EXISTS (SELECT 1 FROM benefit_types bt WHERE bt.name = v.name);

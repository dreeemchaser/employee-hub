-- Attendance clock-in/clock-out tracking. One row per work session: a session
-- is OPEN from clock-in until clock-out closes it (sets clock_out_at, flips to
-- CLOSED). AttendanceRecordRepository.findByEmployeeIdAndStatus enforces at
-- most one OPEN session per employee at a time (application-level rule).
CREATE TABLE attendance_records (
    id           character varying(255) NOT NULL,
    employee_id  character varying(255) NOT NULL,
    work_date    date NOT NULL,
    clock_in_at  timestamp(6) without time zone NOT NULL,
    clock_out_at timestamp(6) without time zone,
    status       character varying(255) NOT NULL,
    notes        character varying(500),
    created_at   timestamp(6) without time zone,
    CONSTRAINT attendance_records_pkey PRIMARY KEY (id),
    CONSTRAINT attendance_records_status_check
        CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT fk_attendance_records_employee
        FOREIGN KEY (employee_id) REFERENCES employees (id)
);

-- employee_id is filtered on every query (getMy, getAll, the open-session check);
-- work_date is filtered via the from/to range in getAll. Both will grow with
-- every clock-in, so both are indexed per steering (filter/join keys on
-- growing tables).
CREATE INDEX idx_attendance_records_employee_id ON attendance_records (employee_id);
CREATE INDEX idx_attendance_records_work_date ON attendance_records (work_date);

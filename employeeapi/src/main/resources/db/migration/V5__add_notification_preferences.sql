CREATE TABLE notification_preferences (
    id                character varying(255) NOT NULL,
    employee_id       character varying(255) NOT NULL,
    notification_type character varying(255) NOT NULL,
    email_enabled     boolean NOT NULL DEFAULT false,
    CONSTRAINT notification_preferences_pkey PRIMARY KEY (id),
    CONSTRAINT notification_preferences_type_check CHECK (((notification_type)::text = ANY (
        (ARRAY['LEAVE'::character varying, 'TIMESHEET'::character varying,
               'SALARY'::character varying, 'DOCUMENT'::character varying,
               'GENERAL'::character varying, 'ATTENDANCE'::character varying,
               'OFFBOARDING'::character varying])::text[]
    ))),
    CONSTRAINT uk_notification_preferences_employee_type UNIQUE (employee_id, notification_type),
    CONSTRAINT fk_notification_preferences_employee
        FOREIGN KEY (employee_id) REFERENCES employees(id)
);

CREATE INDEX idx_notification_preferences_employee
    ON notification_preferences (employee_id);

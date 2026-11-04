CREATE TABLE leave_policy_versions (
    id varchar(36) NOT NULL,
    leave_type_id bigint NOT NULL,
    version_number integer NOT NULL,
    name varchar(255) NOT NULL,
    paid boolean NOT NULL DEFAULT TRUE,
    default_entitlement integer NOT NULL,
    requires_documentation boolean NOT NULL DEFAULT FALSE,
    notice_period_days integer NOT NULL DEFAULT 0,
    effective_from date NOT NULL,
    effective_to date,
    status varchar(20) NOT NULL DEFAULT 'DRAFT',
    created_at timestamp,
    updated_at timestamp,
    CONSTRAINT leave_policy_versions_pkey PRIMARY KEY (id),
    CONSTRAINT fk_leave_policy_versions_type FOREIGN KEY (leave_type_id) REFERENCES leave_types(id),
    CONSTRAINT uk_leave_policy_versions_number UNIQUE (leave_type_id, version_number)
);

ALTER TABLE leave_balances ADD COLUMN policy_version_id varchar(36);
ALTER TABLE leave_requests ADD COLUMN policy_version_id varchar(36);

ALTER TABLE leave_balances
    ADD CONSTRAINT fk_leave_balances_policy_version
    FOREIGN KEY (policy_version_id) REFERENCES leave_policy_versions(id);
ALTER TABLE leave_requests
    ADD CONSTRAINT fk_leave_requests_policy_version
    FOREIGN KEY (policy_version_id) REFERENCES leave_policy_versions(id);

CREATE INDEX idx_leave_policy_versions_effective
    ON leave_policy_versions (leave_type_id, status, effective_from);

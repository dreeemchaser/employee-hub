ALTER TABLE documents
    ADD COLUMN access_level character varying(255) NOT NULL DEFAULT 'STANDARD';

UPDATE documents
SET access_level = 'PAYROLL_ONLY'
WHERE document_type = 'PAYSLIP';

ALTER TABLE documents
    ADD CONSTRAINT documents_access_level_check
    CHECK (((access_level)::text = ANY (
        (ARRAY['STANDARD'::character varying, 'CONFIDENTIAL'::character varying,
               'PAYROLL_ONLY'::character varying])::text[]
    )));

ALTER TABLE judge_schema.p_submission_event_outboxes
DROP CONSTRAINT p_submission_event_outboxes_status_check;

ALTER TABLE judge_schema.p_submission_event_outboxes
    ADD CONSTRAINT chk_submission_event_outboxes_status_values
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'));
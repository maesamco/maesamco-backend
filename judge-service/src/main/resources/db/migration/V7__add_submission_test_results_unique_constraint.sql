ALTER TABLE judge_schema.p_submission_test_results
    ADD CONSTRAINT uk_submission_test_results_submission_test_case
        UNIQUE (submission_id, test_case_id);
-- p_problem_execution_specs.time_limit_ms/memory_limit_mb에 대한 방어 CHECK 제약 추가
ALTER TABLE judge_schema.p_problem_execution_specs
    ADD CONSTRAINT chk_problem_execution_specs_time_limit_positive CHECK (time_limit_ms > 0);

ALTER TABLE judge_schema.p_problem_execution_specs
    ADD CONSTRAINT chk_problem_execution_specs_memory_limit_positive CHECK (memory_limit_mb > 0);
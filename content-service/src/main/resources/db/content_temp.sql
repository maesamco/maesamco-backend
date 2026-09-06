DROP TABLE IF EXISTS content_schema.p_problems CASCADE;

CREATE TABLE content_schema.p_problems (
                            id UUID PRIMARY KEY,

                            title VARCHAR(100) NOT NULL,

                            language VARCHAR(20) NOT NULL,

                            difficulty VARCHAR(20) NOT NULL,

                            type VARCHAR(20) NOT NULL,

                            description TEXT NOT NULL,

                            starter_code TEXT,

                            running_time_limit INTEGER NOT NULL,

                            running_memory_limit INTEGER NOT NULL,

                            timer_policy VARCHAR(20) NOT NULL,

                            source VARCHAR(100) NOT NULL,

                            problem_status VARCHAR(20) NOT NULL,

                            current_version_no INTEGER NOT NULL DEFAULT 1,

                            created_at TIMESTAMPTZ NOT NULL,
                            created_by UUID,

                            updated_at TIMESTAMPTZ,
                            updated_by UUID,

                            deleted_at TIMESTAMPTZ,
                            deleted_by UUID
);
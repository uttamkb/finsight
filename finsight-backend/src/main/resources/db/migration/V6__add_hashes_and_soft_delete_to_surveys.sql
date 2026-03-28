-- 1. Add columns to survey_responses
ALTER TABLE survey_responses ADD COLUMN hash VARCHAR(64);
ALTER TABLE survey_responses ADD COLUMN is_active BOOLEAN DEFAULT 1;

-- 2. Add columns to survey_action_items
ALTER TABLE survey_action_items ADD COLUMN hash VARCHAR(64);
ALTER TABLE survey_action_items ADD COLUMN is_active BOOLEAN DEFAULT 1;

-- 3. Create unique indices (SQLite ignores duplicate NULLs, but we'll populate them anyway)
CREATE UNIQUE INDEX ux_survey_response_hash ON survey_responses(hash) WHERE hash IS NOT NULL;
CREATE UNIQUE INDEX ux_action_item_hash ON survey_action_items(hash) WHERE hash IS NOT NULL;

-- Migration to remove redundant workflow_xml column and use steps_xml uniformely
-- First, ensure any data from workflow_xml is preserved if steps_xml is empty
UPDATE workflow_templates 
SET steps_xml = workflow_xml 
WHERE (steps_xml IS NULL OR steps_xml = '') 
  AND (workflow_xml IS NOT NULL AND workflow_xml <> '');

-- Drop the old column
ALTER TABLE workflow_templates DROP COLUMN workflow_xml;

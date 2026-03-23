-- Add internal_notes column missing due to reverted migrations
ALTER TABLE project_proposals ADD COLUMN IF NOT EXISTS internal_notes TEXT;

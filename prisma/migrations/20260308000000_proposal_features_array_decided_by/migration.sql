-- Update any UNDER_ANALYSIS proposals to PENDING before removing the enum value
UPDATE project_proposals SET status = 'PENDING' WHERE status = 'UNDER_ANALYSIS';

-- Recreate ProposalStatus enum without UNDER_ANALYSIS
ALTER TYPE "ProposalStatus" RENAME TO "ProposalStatus_old";
CREATE TYPE "ProposalStatus" AS ENUM ('PENDING', 'ACCEPTED', 'REJECTED');
ALTER TABLE project_proposals ALTER COLUMN status DROP DEFAULT;
ALTER TABLE project_proposals
  ALTER COLUMN status TYPE "ProposalStatus" USING status::text::"ProposalStatus";
DROP TYPE "ProposalStatus_old";

-- Change features from TEXT (nullable) to TEXT[] only if not already an array
DO $$
BEGIN
  IF (SELECT data_type FROM information_schema.columns
      WHERE table_schema=current_schema() AND table_name='project_proposals' AND column_name='features') = 'text' THEN
    ALTER TABLE project_proposals
      ALTER COLUMN features TYPE TEXT[] USING
        CASE WHEN features IS NULL THEN ARRAY[]::TEXT[] ELSE ARRAY[features] END;
  END IF;
END$$;

-- Add decided_by_id column
ALTER TABLE project_proposals
  ADD COLUMN decided_by_id INTEGER REFERENCES members(id) ON DELETE SET NULL;

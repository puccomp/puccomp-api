-- reference_links: TEXT -> TEXT[]
ALTER TABLE "project_proposals" DROP COLUMN "reference_links";
ALTER TABLE "project_proposals" ADD COLUMN "reference_links" TEXT[] NOT NULL DEFAULT '{}';
ALTER TABLE "project_proposals" ALTER COLUMN "reference_links" DROP DEFAULT;

-- budget_range: replace enum with new granular values
ALTER TABLE "project_proposals" DROP COLUMN "budget_range";
DROP TYPE "BudgetRange";
CREATE TYPE "BudgetRange" AS ENUM (
  'UNDER_3K',
  'FROM_3K_TO_10K',
  'FROM_10K_TO_25K',
  'FROM_25K_TO_50K',
  'ABOVE_50K'
);
ALTER TABLE "project_proposals" ADD COLUMN "budget_range" "BudgetRange";

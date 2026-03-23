CREATE TYPE "BudgetRange" AS ENUM ('ATE_3K', 'ENTRE_3K_10K', 'ACIMA_10K');
CREATE TYPE "ProposalStatus" AS ENUM ('PENDING', 'UNDER_ANALYSIS', 'ACCEPTED', 'REJECTED');

ALTER TABLE "project_proposals" RENAME COLUMN "date" TO "created_at";
ALTER TABLE "project_proposals" RENAME COLUMN "description" TO "problem_description";

ALTER TABLE "project_proposals"
  ADD COLUMN "solution_overview" TEXT,
  ADD COLUMN "reference_links" TEXT,
  ADD COLUMN "budget_range" "BudgetRange",
  ADD COLUMN "status" "ProposalStatus" NOT NULL DEFAULT 'PENDING',
  ADD COLUMN "internal_notes" TEXT,
  ADD COLUMN "updated_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP;

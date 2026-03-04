-- AlterTable: add new columns
ALTER TABLE "technologies" ADD COLUMN "slug" TEXT;
ALTER TABLE "technologies" ADD COLUMN "color" TEXT;
ALTER TABLE "technologies" ADD COLUMN "description" TEXT;
ALTER TABLE "technologies" ADD COLUMN "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE "technologies" ADD COLUMN "updated_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- BackFill: generate slug from existing name values
-- (lowercase, non-alphanumeric sequences become hyphens, strip leading/trailing hyphens)
UPDATE "technologies"
SET "slug" = REGEXP_REPLACE(
  REGEXP_REPLACE(LOWER("name"), '[^a-z0-9]+', '-', 'g'),
  '^-+|-+$', '', 'g'
);

-- Make slug NOT NULL after backfill
ALTER TABLE "technologies" ALTER COLUMN "slug" SET NOT NULL;

-- Add unique constraint
ALTER TABLE "technologies" ADD CONSTRAINT "technologies_slug_key" UNIQUE ("slug");

-- CreateEnum
CREATE TYPE "ProjectStatus" AS ENUM ('PLANNING', 'IN_PROGRESS', 'DONE', 'PAUSED');

-- CreateEnum
CREATE TYPE "AssetType" AS ENUM ('IMAGE', 'VIDEO', 'DOCUMENT');

-- AlterTable: add new columns
ALTER TABLE "projects" ADD COLUMN "slug" TEXT;
ALTER TABLE "projects" ADD COLUMN "status" "ProjectStatus" NOT NULL DEFAULT 'PLANNING';
ALTER TABLE "projects" ADD COLUMN "is_featured" BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE "projects" ADD COLUMN "priority" INTEGER NOT NULL DEFAULT 0;
ALTER TABLE "projects" ADD COLUMN "start_date" DATE;
ALTER TABLE "projects" ADD COLUMN "end_date" DATE;
ALTER TABLE "projects" ADD COLUMN "is_internal" BOOLEAN NOT NULL DEFAULT false;

-- Populate slug from existing names (lowercase, underscores to hyphens)
UPDATE "projects" SET "slug" = LOWER(REPLACE("name", '_', '-'));

-- Make slug NOT NULL and unique
ALTER TABLE "projects" ALTER COLUMN "slug" SET NOT NULL;
CREATE UNIQUE INDEX "projects_slug_key" ON "projects"("slug");

-- Drop old image_key column
ALTER TABLE "projects" DROP COLUMN IF EXISTS "image_key";

-- CreateTable
CREATE TABLE "project_assets" (
    "id" SERIAL NOT NULL,
    "project_id" INTEGER NOT NULL,
    "type" "AssetType" NOT NULL DEFAULT 'IMAGE',
    "key" TEXT NOT NULL,
    "caption" TEXT,
    "order" INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT "project_assets_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "project_assets_key_key" ON "project_assets"("key");

-- AddForeignKey
ALTER TABLE "project_assets" ADD CONSTRAINT "project_assets_project_id_fkey"
    FOREIGN KEY ("project_id") REFERENCES "projects"("id") ON DELETE CASCADE ON UPDATE CASCADE;

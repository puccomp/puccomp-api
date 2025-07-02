/*
  Warnings:

  - You are about to drop the `Contributor` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `CvApplication` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `ImageMemory` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `Member` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `Project` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `ProjectProposal` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `ProjectTechnology` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `Role` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `Technology` table. If the table is not empty, all the data it contains will be lost.

*/
-- DropForeignKey
ALTER TABLE "Contributor" DROP CONSTRAINT "Contributor_member_id_fkey";

-- DropForeignKey
ALTER TABLE "Contributor" DROP CONSTRAINT "Contributor_project_id_fkey";

-- DropForeignKey
ALTER TABLE "Member" DROP CONSTRAINT "Member_role_id_fkey";

-- DropForeignKey
ALTER TABLE "ProjectTechnology" DROP CONSTRAINT "ProjectTechnology_project_id_fkey";

-- DropForeignKey
ALTER TABLE "ProjectTechnology" DROP CONSTRAINT "ProjectTechnology_technology_id_fkey";

-- DropTable
DROP TABLE "Contributor";

-- DropTable
DROP TABLE "CvApplication";

-- DropTable
DROP TABLE "ImageMemory";

-- DropTable
DROP TABLE "Member";

-- DropTable
DROP TABLE "Project";

-- DropTable
DROP TABLE "ProjectProposal";

-- DropTable
DROP TABLE "ProjectTechnology";

-- DropTable
DROP TABLE "Role";

-- DropTable
DROP TABLE "Technology";

-- CreateTable
CREATE TABLE "roles" (
    "id" SERIAL NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT,
    "level" INTEGER NOT NULL,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "roles_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "members" (
    "id" SERIAL NOT NULL,
    "email" TEXT NOT NULL,
    "password" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "surname" TEXT NOT NULL,
    "bio" TEXT,
    "course" TEXT NOT NULL,
    "avatar_url" TEXT,
    "entry_date" DATE NOT NULL,
    "exit_date" DATE,
    "is_active" BOOLEAN NOT NULL,
    "github_url" TEXT,
    "instagram_url" TEXT,
    "linkedin_url" TEXT,
    "is_admin" BOOLEAN NOT NULL,
    "role_id" INTEGER,

    CONSTRAINT "members_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "projects" (
    "id" SERIAL NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "image_key" TEXT,
    "created_at" DATE NOT NULL,
    "updated_at" DATE,

    CONSTRAINT "projects_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "contributors" (
    "member_id" INTEGER NOT NULL,
    "project_id" INTEGER NOT NULL,

    CONSTRAINT "contributors_pkey" PRIMARY KEY ("member_id","project_id")
);

-- CreateTable
CREATE TABLE "technologies" (
    "id" SERIAL NOT NULL,
    "name" TEXT NOT NULL,
    "icon_url" TEXT,
    "type" "TechnologyType" NOT NULL DEFAULT 'OTHER',

    CONSTRAINT "technologies_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "project_technologies" (
    "project_id" INTEGER NOT NULL,
    "technology_id" INTEGER NOT NULL,
    "usage_level" "TechnologyUsageLevel" NOT NULL,

    CONSTRAINT "project_technologies_pkey" PRIMARY KEY ("project_id","technology_id")
);

-- CreateTable
CREATE TABLE "cv_applications" (
    "cv_key" TEXT NOT NULL,
    "fullname" TEXT NOT NULL,
    "phone" TEXT NOT NULL,
    "linkedin" TEXT,
    "github" TEXT,
    "course" TEXT NOT NULL,
    "period" TEXT NOT NULL,

    CONSTRAINT "cv_applications_pkey" PRIMARY KEY ("cv_key")
);

-- CreateTable
CREATE TABLE "project_proposals" (
    "id" SERIAL NOT NULL,
    "name" TEXT NOT NULL,
    "phone" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "features" TEXT,
    "visual_identity" TEXT,
    "date" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "project_proposals_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "image_memories" (
    "id" SERIAL NOT NULL,
    "key" TEXT NOT NULL,
    "title" TEXT,
    "description" TEXT,
    "date" TEXT,

    CONSTRAINT "image_memories_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "roles_name_key" ON "roles"("name");

-- CreateIndex
CREATE UNIQUE INDEX "members_email_key" ON "members"("email");

-- CreateIndex
CREATE UNIQUE INDEX "projects_name_key" ON "projects"("name");

-- CreateIndex
CREATE UNIQUE INDEX "technologies_name_key" ON "technologies"("name");

-- CreateIndex
CREATE UNIQUE INDEX "image_memories_key_key" ON "image_memories"("key");

-- AddForeignKey
ALTER TABLE "members" ADD CONSTRAINT "members_role_id_fkey" FOREIGN KEY ("role_id") REFERENCES "roles"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "contributors" ADD CONSTRAINT "contributors_member_id_fkey" FOREIGN KEY ("member_id") REFERENCES "members"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "contributors" ADD CONSTRAINT "contributors_project_id_fkey" FOREIGN KEY ("project_id") REFERENCES "projects"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "project_technologies" ADD CONSTRAINT "project_technologies_project_id_fkey" FOREIGN KEY ("project_id") REFERENCES "projects"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "project_technologies" ADD CONSTRAINT "project_technologies_technology_id_fkey" FOREIGN KEY ("technology_id") REFERENCES "technologies"("id") ON DELETE CASCADE ON UPDATE CASCADE;

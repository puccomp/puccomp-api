-- CreateEnum
CREATE TYPE "TechnologyType" AS ENUM ('LANGUAGE', 'FRAMEWORK', 'LIBRARY', 'TOOL', 'OTHER');

-- CreateEnum
CREATE TYPE "TechnologyUsageLevel" AS ENUM ('PRIMARY', 'SECONDARY', 'SUPPORTIVE', 'OBSOLETE');

-- CreateTable
CREATE TABLE "Role" (
    "id" SERIAL NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT,
    "level" INTEGER NOT NULL,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Role_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Member" (
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

    CONSTRAINT "Member_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Project" (
    "id" SERIAL NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "image_key" TEXT,
    "created_at" DATE NOT NULL,
    "updated_at" DATE,

    CONSTRAINT "Project_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Contributor" (
    "member_id" INTEGER NOT NULL,
    "project_id" INTEGER NOT NULL,

    CONSTRAINT "Contributor_pkey" PRIMARY KEY ("member_id","project_id")
);

-- CreateTable
CREATE TABLE "Technology" (
    "id" SERIAL NOT NULL,
    "name" TEXT NOT NULL,
    "icon_url" TEXT,
    "type" "TechnologyType" NOT NULL DEFAULT 'OTHER',

    CONSTRAINT "Technology_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ProjectTechnology" (
    "project_id" INTEGER NOT NULL,
    "technology_id" INTEGER NOT NULL,
    "usage_level" "TechnologyUsageLevel" NOT NULL,

    CONSTRAINT "ProjectTechnology_pkey" PRIMARY KEY ("project_id","technology_id")
);

-- CreateTable
CREATE TABLE "CvApplication" (
    "cv_key" TEXT NOT NULL,
    "fullname" TEXT NOT NULL,
    "phone" TEXT NOT NULL,
    "linkedin" TEXT,
    "github" TEXT,
    "course" TEXT NOT NULL,
    "period" TEXT NOT NULL,

    CONSTRAINT "CvApplication_pkey" PRIMARY KEY ("cv_key")
);

-- CreateTable
CREATE TABLE "ProjectProposal" (
    "id" SERIAL NOT NULL,
    "name" TEXT NOT NULL,
    "phone" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "features" TEXT,
    "visual_identity" TEXT,
    "date" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ProjectProposal_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ImageMemory" (
    "id" SERIAL NOT NULL,
    "key" TEXT NOT NULL,
    "title" TEXT,
    "description" TEXT,
    "date" TEXT,

    CONSTRAINT "ImageMemory_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "Role_name_key" ON "Role"("name");

-- CreateIndex
CREATE UNIQUE INDEX "Member_email_key" ON "Member"("email");

-- CreateIndex
CREATE UNIQUE INDEX "Project_name_key" ON "Project"("name");

-- CreateIndex
CREATE UNIQUE INDEX "Technology_name_key" ON "Technology"("name");

-- CreateIndex
CREATE UNIQUE INDEX "ImageMemory_key_key" ON "ImageMemory"("key");

-- AddForeignKey
ALTER TABLE "Member" ADD CONSTRAINT "Member_role_id_fkey" FOREIGN KEY ("role_id") REFERENCES "Role"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Contributor" ADD CONSTRAINT "Contributor_member_id_fkey" FOREIGN KEY ("member_id") REFERENCES "Member"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Contributor" ADD CONSTRAINT "Contributor_project_id_fkey" FOREIGN KEY ("project_id") REFERENCES "Project"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ProjectTechnology" ADD CONSTRAINT "ProjectTechnology_project_id_fkey" FOREIGN KEY ("project_id") REFERENCES "Project"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ProjectTechnology" ADD CONSTRAINT "ProjectTechnology_technology_id_fkey" FOREIGN KEY ("technology_id") REFERENCES "Technology"("id") ON DELETE CASCADE ON UPDATE CASCADE;

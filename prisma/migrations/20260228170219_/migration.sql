/*
  Warnings:

  - You are about to drop the column `is_active` on the `members` table. All the data in the column will be lost.
  - A unique constraint covering the columns `[invite_token]` on the table `members` will be added. If there are existing duplicate values, this will fail.

*/
-- CreateEnum
CREATE TYPE "MemberStatus" AS ENUM ('PENDING', 'ACTIVE', 'INACTIVE');

-- AlterTable
ALTER TABLE "cv_applications" ADD COLUMN     "submitted_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- AlterTable
ALTER TABLE "members" DROP COLUMN "is_active",
ADD COLUMN     "invite_token" TEXT,
ADD COLUMN     "invite_token_expires_at" TIMESTAMP(3),
ADD COLUMN     "status" "MemberStatus" NOT NULL DEFAULT 'PENDING',
ALTER COLUMN "password" DROP NOT NULL;

-- CreateIndex
CREATE UNIQUE INDEX "members_invite_token_key" ON "members"("invite_token");

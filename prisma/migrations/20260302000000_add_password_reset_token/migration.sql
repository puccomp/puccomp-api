-- AlterTable
ALTER TABLE "members" ADD COLUMN "password_reset_token" TEXT,
ADD COLUMN "password_reset_expires_at" TIMESTAMP(3);

-- CreateIndex
CREATE UNIQUE INDEX "members_password_reset_token_key" ON "members"("password_reset_token");

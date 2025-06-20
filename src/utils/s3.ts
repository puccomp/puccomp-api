import {
  S3Client,
  PutObjectCommand,
  DeleteObjectCommand,
  GetObjectCommand,
} from '@aws-sdk/client-s3'
import { getSignedUrl } from '@aws-sdk/s3-request-presigner'

const s3Client = new S3Client({
  region: process.env.AWS_REGION!,
  credentials: {
    accessKeyId: process.env.AWS_ACCESS_KEY_ID!,
    secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY!,
  },
})

export interface UploadedFile {
  buffer: Buffer
  mimetype: string
  originalname: string
}

export async function uploadObjectToS3(
  file: UploadedFile,
  fileKey: string
): Promise<{ Key: string }> {
  const params = {
    Bucket: process.env.AWS_S3_BUCKET_NAME,
    Key: fileKey,
    Body: file.buffer,
    ContentType: file.mimetype,
  }

  const command = new PutObjectCommand(params)
  await s3Client.send(command)
  return { Key: fileKey }
}

export async function deleteObjectFromS3(fileKey: string): Promise<void> {
  const params = {
    Bucket: process.env.AWS_S3_BUCKET_NAME,
    Key: fileKey,
  }
  const command = new DeleteObjectCommand(params)
  await s3Client.send(command)
}

export function getS3URL(fileKey: string): string {
  return `https://${process.env.AWS_S3_BUCKET_NAME}.s3.${process.env.AWS_REGION}.amazonaws.com/${fileKey}`
}

export async function getSignedS3URL(
  fileKey: string,
  expiresInSeconds: number = 3600
): Promise<string> {
  const params = {
    Bucket: process.env.AWS_S3_BUCKET_NAME,
    Key: fileKey,
  }
  const command = new GetObjectCommand(params)
  return await getSignedUrl(s3Client, command, { expiresIn: expiresInSeconds })
}

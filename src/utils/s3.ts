import {
  S3Client,
  PutObjectCommand,
  DeleteObjectCommand,
  GetObjectCommand,
} from '@aws-sdk/client-s3'
import { getSignedUrl } from '@aws-sdk/s3-request-presigner'

const IS_PROD = process.env.NODE_ENV === 'production'

// In dev/test, connect to LocalStack. Override with AWS_ENDPOINT_URL if needed.
const DEV_ENDPOINT = process.env.AWS_ENDPOINT_URL ?? 'http://localstack:4566'

// Public URL used in API responses — frontend accesses LocalStack via localhost
const DEV_PUBLIC_URL = process.env.AWS_PUBLIC_URL ?? 'http://localhost:4566'

let _s3Client: S3Client | null = null

function getS3Client(): S3Client {
  if (!_s3Client) {
    _s3Client = IS_PROD
      ? new S3Client({
          region: process.env.AWS_REGION!,
          credentials: {
            accessKeyId: process.env.AWS_ACCESS_KEY_ID!,
            secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY!,
          },
        })
      : new S3Client({
          region: process.env.AWS_REGION ?? 'us-east-1',
          endpoint: DEV_ENDPOINT,
          forcePathStyle: true, // required for LocalStack
          credentials: { accessKeyId: 'test', secretAccessKey: 'test' },
        })
  }
  return _s3Client
}

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
  await getS3Client().send(command)
  return { Key: fileKey }
}

export async function deleteObjectFromS3(fileKey: string): Promise<void> {
  const params = {
    Bucket: process.env.AWS_S3_BUCKET_NAME,
    Key: fileKey,
  }
  const command = new DeleteObjectCommand(params)
  await getS3Client().send(command)
}

export function getS3URL(fileKey: string): string {
  const bucket = process.env.AWS_S3_BUCKET_NAME
  if (!IS_PROD) {
    return `${DEV_PUBLIC_URL}/${bucket}/${fileKey}`
  }
  return `https://${bucket}.s3.${process.env.AWS_REGION}.amazonaws.com/${fileKey}`
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
  return await getSignedUrl(getS3Client(), command, {
    expiresIn: expiresInSeconds,
  })
}

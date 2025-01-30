import AWS from 'aws-sdk'

const s3 = new AWS.S3({
  accessKeyId: process.env.AWS_ACCESS_KEY_ID,
  secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY,
  region: process.env.AWS_REGION,
})

export async function uploadObjectToS3(file, fileKey) {
  const params = {
    Bucket: process.env.AWS_S3_BUCKET_NAME,
    Key: fileKey,
    Body: file.buffer,
    ContentType: file.mimetype,
  }
  return await s3.upload(params).promise()
}

export async function deleteObjectFromS3(fileKey) {
  const params = {
    Bucket: process.env.AWS_S3_BUCKET_NAME,
    Key: fileKey,
  }
  await s3.deleteObject(params).promise()
}

export function getS3URL(fileKey) {
  return `https://${process.env.AWS_S3_BUCKET_NAME}.s3.${process.env.AWS_REGION}.amazonaws.com/${fileKey}`
}

export function getSignedS3URL(fileKey, expiresInSeconds = 3600) {
  const params = {
    Bucket: process.env.AWS_S3_BUCKET_NAME,
    Key: fileKey,
    Expires: expiresInSeconds,
  }
  return s3.getSignedUrl('getObject', params)
}

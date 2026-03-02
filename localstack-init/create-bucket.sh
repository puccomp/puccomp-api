#!/bin/bash
set -e

BUCKET="${AWS_S3_BUCKET_NAME:-puccomp-dev}"

awslocal s3 mb "s3://${BUCKET}" --region us-east-1

echo "LocalStack: bucket '${BUCKET}' created."

import { S3Client, PutObjectCommand } from '@aws-sdk/client-s3';
import { v4 as uuidv4 } from 'uuid';

// Get region from environment variables or use a default
const REGION = 'eu-central-1';
const BUCKET_NAME = process.env.S3_BUCKET_NAME || 'uuid-storage';

// Initialize S3 client with region
const s3Client = new S3Client({ region: REGION });

export const handler = async (event) => {
    try {
        // Generate 10 random UUIDs
        const uuids = Array(10).fill().map(() => uuidv4());

        // Create JSON payload with the UUIDs
        const payload = {
            ids: uuids
        };

        // Generate filename based on current timestamp
        const timestamp = new Date().toISOString();
        const filename = timestamp;

        // Create the command for S3 upload
        const command = new PutObjectCommand({
            Bucket: BUCKET_NAME,
            Key: filename,
            Body: JSON.stringify(payload, null, 4),
            ContentType: 'application/json'
        });

        // Upload the file to S3
        await s3Client.send(command);

        console.log(`Successfully uploaded UUIDs to S3: s3://${BUCKET_NAME}/${filename}`);

        // No return statement needed for CloudWatch event triggers
    } catch (error) {
        console.error('Error:', error);
        throw error;
    }
};
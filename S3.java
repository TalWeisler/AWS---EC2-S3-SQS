import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;
import software.amazon.awssdk.core.sync.RequestBody;

public class S3 {

    private static S3Client s3;
    private static String bucket;
    //private static String key;
    private static final String multipartKey = "multiPartKey";

    public S3(){
        s3 = S3Client.builder().region(Region.US_EAST_1).build();
        bucket = "bucket" + System.currentTimeMillis();
    }

    public String getBucket(){ return this.bucket;}

    public String PutObject(String objectFile,String bucket) throws IOException { //returns the unique version ID for the object being stored
        try{
            String key = "key"+ System.currentTimeMillis();
            PutObjectResponse response= s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(),
                    Paths.get(objectFile));
            return key;
        }
        catch (S3Exception e){
            System.err.println(e.getMessage());
        }
        return "";
    }
    public synchronized String PutObject(String objectFile,String bucket, String key) throws IOException { //returns the unique version ID for the object being stored
        try{
            PutObjectResponse response= s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(),
                    Paths.get(objectFile));
            return key;
        }
        catch (S3Exception e){
            System.err.println(e.getMessage());
        }
        return "";
    }
    public static void MultipartUploadFiles() throws IOException {
        // Multipart Upload a file
        multipartUpload(bucket, multipartKey);
    }
    public static void ManualPagination(){
        ListObjectsV2Request listObjectsReqManual = ListObjectsV2Request.builder()
                .bucket(bucket)
                .maxKeys(1)
                .build();

        boolean done = false;
        while (!done) {
            ListObjectsV2Response listObjResponse = s3.listObjectsV2(listObjectsReqManual);
            for (S3Object content : listObjResponse.contents()) {
                System.out.println(content.key());
            }

            if (listObjResponse.nextContinuationToken() == null) {
                done = true;
            }

            listObjectsReqManual = listObjectsReqManual.toBuilder()
                    .continuationToken(listObjResponse.nextContinuationToken())
                    .build();
        }
    }
    public void DeleteObject(String key){
        // Delete Object
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder().bucket(bucket).key(key).build();
        s3.deleteObject(deleteObjectRequest);
    }
    public void DeleteObject(String key,String bucket){
        // Delete Object
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder().bucket(bucket).key(key).build();
        s3.deleteObject(deleteObjectRequest);
    }
    public static void DeleteMulti () {
        // Delete Object
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder().bucket(bucket).key(multipartKey).build();
        s3.deleteObject(deleteObjectRequest);
    }
    public static String GetObject(String key, String bucket) throws IOException {
        // Get Objects
        String ans = "";
        try {
            ResponseInputStream<GetObjectResponse> res = s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
            BufferedReader reader = new BufferedReader(new InputStreamReader(res));
            String line;
            while ((line = reader.readLine()) != null){
                ans+=line+"\n";
            }
        }
        catch (IOException e){
            System.out.println("Unable to get the object: bucket "+bucket+" key "+key);
        }
        return ans;
    }
    public synchronized ResponseBytes<GetObjectResponse> getObjectBytes(String keyName, String bucketName){
        GetObjectRequest request= GetObjectRequest.builder().key(keyName).bucket(bucketName).build();
        return s3.getObjectAsBytes(request);
    }
    public void createBucket() {
        s3.createBucket(CreateBucketRequest
                .builder()
                .bucket(bucket)
                .acl(BucketCannedACL.PUBLIC_READ)
                .createBucketConfiguration(CreateBucketConfiguration.builder().build())
                .build());
    }
    public void deleteBucket(String bucket) {
        DeleteBucketRequest deleteBucketRequest = DeleteBucketRequest.builder().bucket(bucket).build();
        s3.deleteBucket(deleteBucketRequest);
    }

    /**
     * Uploading an object to S3 in parts
     */
    private static void multipartUpload(String bucketName, String key) throws IOException {
        int mb = 1024 * 1024;
        // First create a multipart upload and get upload id 
        CreateMultipartUploadRequest createMultipartUploadRequest = CreateMultipartUploadRequest.builder()
                .bucket(bucketName).key(key)
                .build();
        CreateMultipartUploadResponse response = s3.createMultipartUpload(createMultipartUploadRequest);
        String uploadId = response.uploadId();
        System.out.println(uploadId);

        // Upload all the different parts of the object
        UploadPartRequest uploadPartRequest1 = UploadPartRequest.builder().bucket(bucketName).key(key)
                .uploadId(uploadId)
                .partNumber(1).build();
        String etag1 = s3.uploadPart(uploadPartRequest1, RequestBody.fromByteBuffer(getRandomByteBuffer(5 * mb))).eTag();
        CompletedPart part1 = CompletedPart.builder().partNumber(1).eTag(etag1).build();

        UploadPartRequest uploadPartRequest2 = UploadPartRequest.builder().bucket(bucketName).key(key)
                .uploadId(uploadId)
                .partNumber(2).build();
        String etag2 = s3.uploadPart(uploadPartRequest2, RequestBody.fromByteBuffer(getRandomByteBuffer(3 * mb))).eTag();
        CompletedPart part2 = CompletedPart.builder().partNumber(2).eTag(etag2).build();


        // Finally call completeMultipartUpload operation to tell S3 to merge all uploaded
        // parts and finish the multipart operation.
        CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder().parts(part1, part2).build();
        CompleteMultipartUploadRequest completeMultipartUploadRequest =
                CompleteMultipartUploadRequest.builder().bucket(bucketName).key(key).uploadId(uploadId)
                        .multipartUpload(completedMultipartUpload).build();
        s3.completeMultipartUpload(completeMultipartUploadRequest);
    }
    private static ByteBuffer getRandomByteBuffer(int size) throws IOException {
        byte[] b = new byte[size];
        new Random().nextBytes(b);
        return ByteBuffer.wrap(b);
    }
}
package Ex1;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static Ex1.awsVars.*;


public class AWS {
    private Ec2Client ec2;
    private S3Client s3;
    private SqsClient sqs;
    private Region region = REGION;

    public AWS(){
        initEC2();
        initS3();
    }

    public void initEC2(){
        ec2 = Ec2Client.builder()
                .region(region)
                .build();
    }

    public void initS3(){
        s3 = S3Client.builder()
                .region(region)
                .build();
    }

    public ArrayList<String> EC2initiateInstance(String instanceImageID, int min, int max, String instanceType, String userData, Tag tag) {
        String script = null;
        try {
            script = getScript(userData);
            System.out.println("Script: " + Arrays.toString(Base64.getDecoder().decode(script)));
        } catch (IOException e){
            e.printStackTrace();
            System.exit(1);
        }
        RunInstancesRequest runRequest = RunInstancesRequest.builder()
                .imageId(instanceImageID)
                .instanceType(instanceType)
                .iamInstanceProfile(IamInstanceProfileSpecification.builder()
                        .name(IAM_PROFILE_NAME)
                        .build())
                .maxCount(max)
                .minCount(min)
                .keyName(KEY_PAIR_NAME)
                .securityGroups(SECURITY_GROUP)
                .userData(script)
                .build();
        RunInstancesResponse response = ec2.runInstances(runRequest);
        List<Instance> instances = response.instances();
        ArrayList<String> idAllInstances = new ArrayList<>();
        for (Instance instance : instances){
            try {
                String instanceID = instance.instanceId();
                CreateTagsRequest tagRequest = CreateTagsRequest.builder()
                        .resources(instanceID)
                        .tags(tag)
                        .build();
                ec2.createTags(tagRequest);
                idAllInstances.add(instanceID);
            } catch (Ec2Exception e){
                System.err.println(e.getMessage());
                System.exit(1);
            }
        }
        return idAllInstances;
    }

    private String getScript(String fileName) throws IOException {
        StringBuilder ans = new StringBuilder();
        File file = new File(fileName);
        Scanner sc = new Scanner(file);
        while(sc.hasNextLine()) {
            ans.append(sc.nextLine());
            ans.append(System.lineSeparator());
        }
        sc.close();
        return Base64.getEncoder()
                .encodeToString(
                        ans.toString()
                                .getBytes());
    }

    public String EC2SearchByTag(Tag tag, String state) {
        DescribeInstancesResponse response = ec2.describeInstances();
        List<Reservation> reservations = response.reservations();

        Set<Instance> instances = new HashSet<>();
        for(Reservation reservation: reservations){
            instances.addAll(reservation.instances());
        }

        for(Instance instance: instances){
            InstanceState instanceState = instance.state();
            List<Tag> isTags = instance.tags();
            if(isTags.contains(tag)){
                if(instanceState.nameAsString().equals(state)){
                    return instance.instanceId();
                }
            }
        }
        return null;
    }

    public void S3UploadFile(String bucketName, String key, File file) {
        CreateBucketRequest createBucketRequest = CreateBucketRequest
                .builder()
                .bucket(bucketName)
                .createBucketConfiguration(CreateBucketConfiguration.builder()
                        .build())
                .build();
        s3.createBucket(createBucketRequest);
        s3.putObject(PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .acl(ObjectCannedACL.PUBLIC_READ)
                .build(),RequestBody.fromFile(file));
    }

    public void initSQS() {
        sqs = SqsClient.builder()
                .region(region)
                .build();
    }

    public String SQSinitializeQueue(String queueName, String visibilityTimeout) throws QueueDoesNotExistException {
        Map<QueueAttributeName, String> attributes = new HashMap<>();
        attributes.put(QueueAttributeName.VISIBILITY_TIMEOUT, visibilityTimeout);
        CreateQueueRequest request = CreateQueueRequest.builder()
                .queueName(queueName)
                .attributes(attributes)
                .build();
        sqs.createQueue(request);
        return getQueueURL(queueName);
    }
    
    public String getQueueURL(String queueName) {
    	GetQueueUrlRequest getQueueRequest = GetQueueUrlRequest.builder()
                .queueName(queueName)
                .build();
        return sqs.getQueueUrl(getQueueRequest).queueUrl();
    }


    public HashMap<String,String> SQSinitializeQueue(ArrayList<Map.Entry<String,String>> queues) throws QueueDoesNotExistException {
        String queueURL = null;
        HashMap<String, String> queueURLs = new HashMap<>();

        for(Map.Entry<String, String> pair: queues) {
            String queueName = pair.getKey();
            String visibility = pair.getValue();

            try{
                queueURL = sqs.getQueueUrl(GetQueueUrlRequest.builder()
                        .queueName(queueName)
                        .build()).queueUrl();
            } catch (SqsException e){
                if(e.statusCode() == 400) { //queue doesn't exist
                    queueURL = SQSinitializeQueue(queueName, visibility);
                    System.out.printf("Queue created: %s\n", queueURL);
                }
                else{
                    System.out.println(e.getLocalizedMessage());
                }
            }
            queueURLs.put(queueName,queueURL);
        }
        return queueURLs;
    }

    public void SQSSendMessage(String queueURL, String message) {
        SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                .queueUrl(queueURL)
                .messageBody(message)
                .delaySeconds(5)
                .build();
        sqs.sendMessage(sendMessageRequest);
    }

    public List<Message> SQSReceiveMessages(String queueURL) {
        ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest
                .builder()
                .queueUrl(queueURL)
                .maxNumberOfMessages(1)
                .build();
        return sqs.receiveMessage(receiveMessageRequest).messages();
    }

    public void SQSDeleteMessage(String queueURL, String receiptHandle) {
        DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                .queueUrl(queueURL)
                .receiptHandle(receiptHandle)
                .build();
        sqs.deleteMessage(deleteMessageRequest);
    }

    public void SQSDeleteQueue(String queueName){
        sqs.deleteQueue(DeleteQueueRequest.builder()
                .queueUrl(queueName)
                .build());
    }

    public void S3DownloadFiles(String bucketName, String key, File file) {
        s3.getObject(GetObjectRequest.builder().bucket(bucketName).key(key).build(),
                ResponseTransformer.toFile(file));
    }

    public void EC2TerminateInstance(String instanceID) {
        ec2.terminateInstances(TerminateInstancesRequest.builder()
                .instanceIds(instanceID)
                .build());
    }

    public void EC2TerminateInstance(Collection<String> instanceIDs) {
        ec2.terminateInstances(TerminateInstancesRequest.builder()
                .instanceIds(instanceIDs)
                .build());
    }

    public void deleteAllBuckets() {
        List<Bucket> buckets = s3.listBuckets().buckets();
        for(Bucket b: buckets){
            if(!b.name().equals(APPLICATION_CODE_BUCKET_NAME)) {
                List<S3Object> s3ObjectList = s3.listObjects(ListObjectsRequest.builder()
                        .bucket(b.name())
                        .build()).contents();
                for (S3Object s3Object : s3ObjectList) {
                    s3.deleteObject(DeleteObjectRequest.builder()
                            .bucket(b.name())
                            .key(s3Object.key())
                            .build());
                }
                s3.deleteBucket(DeleteBucketRequest.builder()
                        .bucket(b.name())
                        .build());
            }
        }
    }

    public void InitAllServices() {
        initEC2();
        initS3();
        initSQS();
    }

}
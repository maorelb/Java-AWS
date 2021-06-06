package Ex1;

import software.amazon.awssdk.services.ec2.model.InstanceType;
import software.amazon.awssdk.regions.Region;

import java.util.UUID;

public class awsVars {
    static final String LOCAL_APPLICATION_ID = UUID.randomUUID().toString();
    static final String SQS_MSG_DELIMETER = "##MSG##";
    static final String TERMINATED_STRING = "TERMINATED";
    static final String INSTANCE_ID = "ami-076515f20540e6e0b";
    static final String MANAGER_SCRIPT = "managerScript.txt";
    static final String WORKER_SCRIPT = "workerScript.txt";
    static final String EX1_JAR = "Ex1.jar";
    static final String APP_INPUT_QUEUE_NAME = "app-input-queue-boris-dsp202";
    static final String APP_OUTPUT_QUEUE_NAME = "app-output-queue-boris-dsp202";
    static final String MNG_INPUT_QUEUE_NAME = "mng-input-queue-boris-dsp202";
    static final String MNG_OUTPUT_QUEUE_NAME = "mng-output-queue-boris-dsp202";
    static final String INPUT_BUCKET_NAME = "input-bucket";
    static final String OUTPUT_BUCKET_NAME = "output-bucket";
    static final String APPLICATION_CODE_BUCKET_NAME = "apllication-code-bucket-dsp202";
    static final String INSTANCE_TYPE =  InstanceType.T2_NANO.toString();
    static final String IAM_PROFILE_NAME = "Application";
    static final String KEY_PAIR_NAME = "Boris";
    static final String SECURITY_GROUP = "Boris";
    static final String HTML_TEMPLATE = "template.html";
    static final String SUMMARY_FILE = "summary.html";
    static final Region REGION = Region.US_EAST_1;
    static final int MAX_INSTANCES = 9;
}
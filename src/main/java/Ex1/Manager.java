package Ex1;

import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.sqs.model.Message;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.apache.commons.io.FileUtils;

import static Ex1.awsVars.*;

public class Manager {

    private static boolean terminate;
    private static HashMap<String, WorkerDataStructure> localWorkers;
    private static HashMap<String, String> awsQueues;
    private static int numOfWorkers;
    private final static Tag WORKER_TAG = Tag.builder()
            .key("Name")
            .value("worker")
            .build();
    private static Collection<String> workerInstanceIDs = new ArrayList<>();

    public static void main(String[] args) {
        AWS aws = new AWS();
        aws.InitAllServices();
        terminate = false;
        localWorkers = new HashMap<>();
        try{
            initializeAllQueues(aws);
            //tasks to do
            while(true) {
                if(!terminate)
                    downloadInputFile(aws);
                //check if finished all work
//                System.out.println("checking for messages");
                checkFinishedTask(aws);
                if(localWorkers.isEmpty() && terminate) {
                    deleteWorkers(aws);
                    aws.SQSDeleteQueue(awsQueues.get(MNG_INPUT_QUEUE_NAME));
                    aws.SQSDeleteQueue(awsQueues.get(MNG_OUTPUT_QUEUE_NAME));
                    System.out.println("MANAGER is exiting");
                    break;
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e){
                    e.printStackTrace();
                }
            }
            aws.SQSSendMessage(awsQueues.get(APP_OUTPUT_QUEUE_NAME), TERMINATED_STRING);
            System.out.println("MANAGER FINISHED");
        } catch (Exception e){
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void deleteWorkers(AWS aws) {
        if(workerInstanceIDs.size() > 0){
            aws.EC2TerminateInstance(workerInstanceIDs);
            workerInstanceIDs.clear();
            numOfWorkers = 0;
        }
    }
    //check if finished all work
    private static void checkFinishedTask(AWS aws) {
        List<Message> messages = aws.SQSReceiveMessages(awsQueues.get(MNG_OUTPUT_QUEUE_NAME));
        boolean localWorkerIsFull = false;
        for(Message message: messages){
            AWSMessage awsMessage = new AWSMessage(message, SQS_MSG_DELIMETER);
            WorkerDataStructure local = localWorkers.get(awsMessage.getLocalApplicationID());
            if(local != null){
                localWorkerIsFull = local.addProcessedString(awsMessage.getMessage());
                localWorkers.put(awsMessage.getLocalApplicationID(), local);
                System.out.println("read message from worker and added it to the list");
            }
            else{ System.out.println("no such local worker"); }
            if(localWorkerIsFull) {
                System.out.println("local worker is full");
                File file = createSummaryFile(local);
                //upload summary file to S3
                aws.S3UploadFile(OUTPUT_BUCKET_NAME + awsMessage.getLocalApplicationID(), awsMessage.getLocalApplicationID(), file);
                System.out.println("uploaded output file");
                file.delete();
                aws.SQSSendMessage(awsQueues.get(APP_OUTPUT_QUEUE_NAME), TERMINATED_STRING+awsMessage.getLocalApplicationID());
                localWorkerIsFull = false;
                localWorkers.remove(awsMessage.getLocalApplicationID());
            }
            System.out.println("deleting handled message from SQS");
            String receiptHandle = message.receiptHandle();
            aws.SQSDeleteMessage(awsQueues.get(MNG_OUTPUT_QUEUE_NAME), receiptHandle);
        }
    }

    //tasks to do
    private static void downloadInputFile(AWS aws) {
        // check if there is a new file
        String queueURL = awsQueues.get(APP_INPUT_QUEUE_NAME);
        List<Message> messages = aws.SQSReceiveMessages(queueURL);
        if(messages.isEmpty()){ return; }
        System.out.println("got message, parsing...");
        Message message = messages.get(0);
        System.out.println(message.body());
        AWSMessage awsMessage = new AWSMessage(message, SQS_MSG_DELIMETER);
        File file = downloadFile(aws, awsMessage);
        List<String> inputs = parseInput(file);
        System.out.println("Downloaded Input File");
        initiateWorkers(aws, awsMessage.getSize(), inputs.size());
        System.out.println("started workers");
        localWorkers.put(
                awsMessage.getLocalApplicationID(),
                new WorkerDataStructure(
                        awsMessage.getLocalApplicationID(), inputs.size()));
        sendWork(aws, inputs, awsMessage.getLocalApplicationID());
        System.out.println("Sent Messages to Workers");
        if (awsMessage.getTerminate()) {
            terminate = true;
            System.out.println("Request to terminate");
        }
        String receiptHandle = message.receiptHandle();
        aws.SQSDeleteMessage(queueURL, receiptHandle);
        System.out.println("deleted Message");
    }

    private static void sendWork(AWS aws, List<String> inputs, String localApplicationID) {
        String queueURL = awsQueues.get(MNG_INPUT_QUEUE_NAME);
        AWSMessage message = new AWSMessage(localApplicationID, SQS_MSG_DELIMETER);
        for(String input: inputs) {
            aws.SQSSendMessage(queueURL, message.buildMessage(input));
        }
    }

    private static List<String> parseInput(File file) {
        List<String> opList = new ArrayList<>();
        try {
            FileReader fr = new FileReader(file);
            BufferedReader br = new BufferedReader(fr);
            String line;
            while((line = br.readLine()) != null) {
                opList.add(line);
            }
            fr.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return opList;
    }

    private static void initiateWorkers(AWS aws, int inputPerWorker, int size) {
        int numberOfWorkersNeeded = (size + inputPerWorker - 1) / inputPerWorker;
        int spare = MAX_INSTANCES - numOfWorkers;
        int newWorkers = Math.min(numberOfWorkersNeeded, spare); //max in EC2 is 10
        workerInstanceIDs.addAll(aws.EC2initiateInstance(INSTANCE_ID, newWorkers, newWorkers, INSTANCE_TYPE, WORKER_SCRIPT, WORKER_TAG));
        numOfWorkers += newWorkers;
    }

    private static File downloadFile(AWS aws, AWSMessage message) {
        String input = message.getInput();
        String localApplicationID = message.getLocalApplicationID();
        File file = new File("files");
        file.mkdir();
        file = new File(file.getAbsolutePath() + File.separatorChar + input);
        aws.S3DownloadFiles(INPUT_BUCKET_NAME + localApplicationID, input, file);
        return file;
    }

    private static void initializeAllQueues(AWS aws) {
        ArrayList<Map.Entry<String, String>> queues = new ArrayList<>();
        queues.add(new AbstractMap.SimpleEntry<>(APP_OUTPUT_QUEUE_NAME, "0"));
        queues.add(new AbstractMap.SimpleEntry<>(APP_INPUT_QUEUE_NAME, "0"));
        queues.add(new AbstractMap.SimpleEntry<>(MNG_OUTPUT_QUEUE_NAME, "0"));
        queues.add(new AbstractMap.SimpleEntry<>(MNG_INPUT_QUEUE_NAME, "30"));
        awsQueues = aws.SQSinitializeQueue(queues);
    }

    private static File createSummaryFile(WorkerDataStructure local) {
        File htmlTemplateFile = new File(HTML_TEMPLATE);
        File newHtmlFile = new File(SUMMARY_FILE);
        try {
            String htmlString = FileUtils.readFileToString(htmlTemplateFile, StandardCharsets.UTF_8);
            String title = "summary";
            StringBuilder body = new StringBuilder();
            for(String processedMsg: local.getProcessed())
                body.append(processedMsg).append("<br>");
            htmlString = htmlString.replace("$title", title);
            htmlString = htmlString.replace("$body", body.toString());
            FileUtils.writeStringToFile(newHtmlFile, htmlString, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Successfully wrote to summary file");
        return newHtmlFile;
    }
}

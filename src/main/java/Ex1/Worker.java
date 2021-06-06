package Ex1;

import static Ex1.awsVars.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import Ex1.Base.Operation;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;

public class Worker {

    private static String localApplicationId;
    private static String dir = "files" + File.separator;

    protected static class Operation {
        String fileName;
        private String operation;
        private String input;
        private String output;

        protected Operation(String line) {
            String[] sp = line.split("\t");
            this.operation = sp[0];
            this.input = sp[1];
            sp = input.split("/");
            this.fileName = sp[sp.length - 1];
            this.output = null;
        }

        protected String getOperation(){ return operation; }
        protected String getInput(){ return input; }
        protected String getOutput(){ return output; }
        protected String getFileName() { return fileName; }

        protected void setOutput(String output){
            if(this.output == null)
                this.output = output;
            else
                System.out.printf("cant redefine output of file: %s\n", this.input);
        }
        @Override
        public String toString(){
            return this.operation + " " + this.input + " " + this.output;
        }
    }
    
    private static void work(Operation op){
        try {
            System.out.printf("started download : file name: %s\n",op.getFileName());
            URL url = new URL(op.getInput());
            HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            InputStream is=connection.getInputStream();
            PDDocument document = PDDocument.load(is);
            System.out.println("finished download, starting to remove pages");
            removePages(document);
            System.out.println("finished removing pages, starting to convert");
            File newFile = makeOperation(op, document);
            System.out.println("DONE!!!");
            document.close();
            op.setOutput(newFile.getAbsolutePath());
        }
        catch (IOException e){
            System.out.println("Something went wrong, check output.html when finished for more info");
            op.setOutput(e.toString());
        }
    }
    
    private static void removePages(PDDocument document){
        int noOfPages= document.getNumberOfPages();
        for(int i = 1; i < noOfPages; i++)
            document.removePage(1);
    }
    
    private static File makeOperation(Operation op, PDDocument document) throws IOException{
        File file;
        String name;
        switch (op.getOperation()){
            case "ToImage":
                PDFRenderer renderer = new PDFRenderer(document);
                BufferedImage image = renderer.renderImage(0);
                name = op.getFileName().split("\\.")[0] + ".png";
                file = new File(dir + name);
                file.createNewFile();
                ImageIO.write(image, "png", file);
                break;
            case "ToHTML":
                PDFTextStripper pdfStripper = new PDFTextStripper();
                String text = pdfStripper.getText(document);
                name = op.getFileName().split("\\.")[0] + ".html";
                file = new File(dir + name);
                file.createNewFile();
                FileWriter myWriter = new FileWriter(file);
                myWriter.write(text);
                myWriter.close();
            case "ToText":
                pdfStripper = new PDFTextStripper();
                text = pdfStripper.getText(document);
                name = op.getFileName().split("\\.")[0] + ".txt";
                file = new File(dir + name);
                file.createNewFile();
                myWriter = new FileWriter(file);
                myWriter.write(text);
                myWriter.close();
                break;
            default:
                throw new IOException("Operation is Not one of the defaults");
        }
        return file;
    }
    
    private static void sendToSQS(AWS aws, String message) throws QueueDoesNotExistException {
        String outputQueueURL = aws.getQueueURL(MNG_OUTPUT_QUEUE_NAME);
        String toSend = localApplicationId + SQS_MSG_DELIMETER + message;
        aws.SQSSendMessage(outputQueueURL, toSend);
    }
    public static void main(String[] args) {
        AWS aws = new AWS();
        aws.InitAllServices();
    	String queueURL = aws.getQueueURL(MNG_INPUT_QUEUE_NAME);
        while(true) {
	        List<Message> messages = aws.SQSReceiveMessages(queueURL);
	        for(Message msg: messages) {
				System.out.printf("received message: %s\n",msg.body());
				AWSMessage awsMessage = new AWSMessage(msg, SQS_MSG_DELIMETER);
				localApplicationId = awsMessage.getLocalApplicationID();
				Operation op = new Operation(awsMessage.getMessage());
				File file = new File("files");
				file.mkdir();
				work(op); 
				sendToSQS(aws,op.toString());
				String receiptHandle = msg.receiptHandle();
				aws.SQSDeleteMessage(queueURL, receiptHandle);
				System.out.println("deleted Message");
	        }
	        try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
        }
    }
}

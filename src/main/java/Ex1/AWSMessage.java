package Ex1;

import software.amazon.awssdk.services.sqs.model.Message;

public class AWSMessage {

    private String localApplicationID;
    private boolean terminate;
    private int n;
    private String input;
    private String output;
    private String messageDelimiter;
    private String message;


    public AWSMessage(Message message, String delimiter) {
        String[] parse = message.body().split(delimiter);
        if(parse.length == 5) {
            this.localApplicationID = parse[0];
            this.terminate = Boolean.valueOf(parse[1]);
            this.n = Integer.parseInt(parse[2]);
            this.input = parse[3];
            this.output = parse[4];
        }
        else if(parse.length == 3){
            this.localApplicationID = parse[0];
            this.message = parse[1];
        }
        else if(parse.length == 2) {
        	this.localApplicationID = parse[0];
        	this.message = parse[1];
        }
        this.messageDelimiter = delimiter;
    }

    public AWSMessage(String localApplicationID, String messageDelimiter) {
        this.localApplicationID = localApplicationID;
        this.messageDelimiter = messageDelimiter;
    }


    public String getLocalApplicationID() {
        return localApplicationID;
    }

    public boolean getTerminate() {
        return terminate;
    }

    public int getSize() {
        return n;
    }

    public String getInput() {
        return input;
    }

    public String getOutput() {
        return output;
    }

    public String buildMessage(String message){
        return localApplicationID + messageDelimiter + message;
    }

    public String getMessage() {
        return message;
    }

}

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SQS
{
    private final String QUEUE_NAME;
    private SqsClient sqsClient;
    private String url;
    private Region region;
    private int numMsgsToRead= 1; //read 1 message in the q each time
    private int numSecDisappear= 20;

    public SQS(String name){
        QUEUE_NAME= name;
        region = Region.US_EAST_1;
        sqsClient= SqsClient.builder().region(region).build();
    }

    public void setNumMsgsToRead(int numMsgsToRead) {
        this.numMsgsToRead = numMsgsToRead;
    }

    public void setNumSecDisappear(int numSecDisappear) {
        this.numSecDisappear = numSecDisappear;
    }

    public String createQueue() {
        Map<QueueAttributeName,String> att = new HashMap<>();
        att.put(QueueAttributeName.VISIBILITY_TIMEOUT,"60");
       // att.put(QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS, "20");
        CreateQueueRequest qRequest= CreateQueueRequest.builder().queueName(QUEUE_NAME).attributes(att).build();
        sqsClient.createQueue(qRequest);
        url= getUrl();
        return url;
    }

    public String getUrl(){
        if(url==null){
            GetQueueUrlRequest req= GetQueueUrlRequest.builder().queueName(QUEUE_NAME).build();
            url = sqsClient.getQueueUrl(req).queueUrl();
        }
        return url;
       }

    public void waitForAMessage() throws InterruptedException {
        wait(30);
    }

    public void sendMessage(String message){
        try {
            SendMessageRequest send_msg_request = SendMessageRequest.builder()
                    .queueUrl(url)
                    .messageBody(message)
                    .delaySeconds(5)
                    .build();
            sqsClient.sendMessage(send_msg_request);

        } catch (QueueNameExistsException e) {
            System.err.println(Arrays.toString(e.getStackTrace()));
            throw e;
        }
    }

    public void sendMultipleMessages(){
        // TODO the messages should be in the arguments
        // Send multiple messages to the queue
        SendMessageBatchRequest send_batch_request = SendMessageBatchRequest.builder()
                .queueUrl(url)
                .entries(
                        SendMessageBatchRequestEntry.builder()
                                .messageBody("Hello from message 1")
                                .id("msg_1")
                                .build()
                        ,
                        SendMessageBatchRequestEntry.builder()
                                .messageBody("Hello from message 2")
                                .delaySeconds(10)
                                .id("msg_2")
                                .build())
                .build();
        sqsClient.sendMessageBatch(send_batch_request);
    }

    public List<Message> getMessages(){
        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest
                .builder()
                .queueUrl(url)
                .waitTimeSeconds(numSecDisappear)
                .maxNumberOfMessages(numMsgsToRead)
                .build();
        return sqsClient.receiveMessage(receiveRequest).messages();
    }
    public List<Message> getAllMessages(){
        PrintSQS();
        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest
                .builder()
                .queueUrl(url)
                .waitTimeSeconds(numSecDisappear)
                .build();
        return sqsClient.receiveMessage(receiveRequest).messages();
    }

    public void deleteMessages(List<Message> messages){
        for (Message m : messages) {
            DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                    .queueUrl(url)
                    .receiptHandle(m.receiptHandle())
                    .build();
            sqsClient.deleteMessage(deleteRequest);
            System.out.println("***** Delete Message: "+messages.get(0).body()+" Queue: "+QUEUE_NAME+" *****\n");
        }
    }

    public void deleteSQS(){
        if (url!=null) {
            System.out.println("*** Deleting a Q: "+ QUEUE_NAME + "*** \n");
            DeleteQueueRequest dqr = DeleteQueueRequest.builder().queueUrl(url).build();
            sqsClient.deleteQueue(dqr);
        }
    }

    public void PrintSQS(){
        System.out.println("******** Queue Name= "+QUEUE_NAME+" *****\n");
        System.out.println("******* url= "+url+" *********\n");
    }
}
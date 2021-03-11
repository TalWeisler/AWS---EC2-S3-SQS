import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import java.io.*;
import java.util.*;

public class LocalApplication {
    private static String InputFile;
    private static File OutputFile;
    private static int n;
    private static S3 s3;
    private static SQS MyQueue;
    private static SQS LocalManagerSQS;
    private static List<Message> myQueueMessages= new LinkedList<Message>();
    static String ManagerData= "#! /bin/bash\n"+"wget https://ass01.s3.amazonaws.com/Manager.jar\n"+"java -jar Manager.jar\n";
    private static boolean terminate= false;
    private static boolean CheckActiveManager(Ec2Client ec2) {
        try {
            DescribeInstancesRequest req = DescribeInstancesRequest.builder().build();
            DescribeInstancesResponse res = ec2.describeInstances(req);
            for (Reservation reservation : res.reservations()) {
                for (Instance instance : reservation.instances()) {
                    String name=instance.tags().get(0).value();
                    String state=instance.state().name().toString();
                    if(name.equals("Manager")&&(state.equals("running")||state.equals("pending")))
                        return true;
                }
            }
            return false;
        } catch (Ec2Exception e) {
            System.out.println("Problem in function: CheckActiveManager");
            System.out.println(e.awsErrorDetails().errorMessage());
            return false;
        }
    }
    private static void ActivateManager(){
        Ec2Client checker= Ec2Client.builder().build();
        LocalManagerSQS = new SQS("LocalManagerSQS");
        boolean found = CheckActiveManager(checker);
        if(found){
            System.out.println("Found the manager!!!!");
            LocalManagerSQS.getUrl();
        }
        else{
            new EC2("Manager",1,1,ManagerData);
            LocalManagerSQS.createQueue();
        }
        LocalManagerSQS.PrintSQS();
    }
    public static String UploadToS3() throws IOException {
        s3= new S3();
        s3.createBucket();
        return s3.PutObject(InputFile,s3.getBucket());
    }
    public static String isDoneMessage(){
        List<Message> messages= MyQueue.getMessages();
        if (!messages.isEmpty()){
            for (Message m : messages){
                myQueueMessages.add(m);
                if ((m.body().substring(0,4)).equals("done")){ //the done message will include the word "done" +" "+ bucketName
                    return m.body().substring(5);
                }
            }
        }
        return "";
    }
    public static File summaryFile(String summaryInfo){ //creates a summary file (not html) and returns the path of the summary file
        String [] info= summaryInfo.split(" ");
        if (info.length !=1){
            System.out.println("In the summary information should be bucketNameand it is missing.");
            System.exit(1);
        }
        String bucketName= info[0];
        String keyName= "SummaryFile.txt";
        try{
            ResponseBytes<GetObjectResponse> responseBytes= s3.getObjectBytes(keyName,bucketName);
            byte [] objectData= responseBytes.asByteArray();
            String path= System.getProperty("user.dir")+"/src/summaryFile.txt";
            File summaryF= new File(path);
            OutputStream outputStream= new FileOutputStream(summaryF);
            outputStream.write(objectData);
            outputStream.flush();
            outputStream.close();
            s3.DeleteObject(keyName,bucketName);
            s3.deleteBucket(bucketName);
            return summaryF;
        } catch (IOException e) {
            System.out.println("problem in function: summary file");
            e.printStackTrace();
        }
        return null;
    }
    public static void createHTMLFile(File summaryF) throws IOException {
        // read the information in the summary file and store it in summaryF
        BufferedReader b= new BufferedReader(new FileReader(summaryF));
        BufferedWriter bw= new BufferedWriter(new FileWriter(OutputFile));
        String line;
        String headline= "<h2>HTML Output File</h2>"+"\n";
        bw.write(headline);
        while ((line= b.readLine())!=null){
            System.out.println(line);
            if (line.length()>=26 && line.substring(0,26).equals("Unable to open the picture"))
            {
                String htmlString= "<p><br />"+line+"</p>";
                bw.write(htmlString);
            }
            else if(line.length()>=7 && line.substring(0,7).equals("http://"))
            {
                String htmlString= "<p><br /></p><img src="+line+">";
                bw.write(htmlString);
            }
            else
            {
                String htmlString= "<p> "+line+"</p>";
                bw.write(htmlString);
            }
        }
        bw.close();
        b.close();

        //when finished, delete the task from myQueue.
        MyQueue.deleteMessages(myQueueMessages);
        MyQueue.deleteSQS();
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        //OutputFile= new File("/home/caspl202/IdeaProjects/DSP1/src/Output.html");
       // File sum= new File("/home/caspl202/IdeaProjects/DSP1/src/summaryFile.txt");
       // createHTMLFile(sum);

        if (args.length >= 3) {
            // 1. arguments
            String path = System.getProperty("user.dir"); ///home/caspl202/IdeaProjects/DSP1
            String Input = args[0];
            InputFile = path + "/src/" + Input;
            String Output = args[1];
            OutputFile = new File(path + "/src/" + Output);
            n = Integer.parseInt(args[2]);
            if(args.length == 4){
                terminate= true;
            }

            // 2. Find the manager
            ActivateManager();

            // 3. Upload the input file to S3
            String FileKey = UploadToS3();

            // 4. Create the special queue between the Manager to this Local
            String QueueName = "ManagerLocalQueue" + new Date().getTime();
            MyQueue = new SQS(QueueName);
            MyQueue.createQueue();

            LocalManagerSQS.PrintSQS();

            // 5. Send message to the manager about the location of the Input file
            LocalManagerSQS.sendMessage("NewTask " + FileKey + " " + n + " " + s3.getBucket() + " " + QueueName);

            // 6. check if the process is DONE
            System.out.println("waiting for manager");
            String summaryCheck = "";
            while (summaryCheck.equals("")) {
                summaryCheck = isDoneMessage();
            }
            System.out.println("****finish waiting for manager****");
            // 7. download the summary file
            File SummaryF = summaryFile(summaryCheck);

            // 8. create html file
            createHTMLFile(SummaryF);


            // 9. if terminate= true than send a message to the manager
            if(terminate)
                LocalManagerSQS.sendMessage("Terminate");
        }
        else {
            System.out.println("There are not enough arguments.");
        }
    }
}

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.sqs.model.Message;

import java.io.*;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class LocalManagerCommunication implements Runnable {
    private SQS LocalManagerSQS;
    private SQS ManagerWorkersSQS;
    private S3 s3;
    private DataBase DB;
    private boolean Done;
    private int countT;
    private Thread Workerschecker;

    public LocalManagerCommunication(S3 s, SQS MW){
        this.ManagerWorkersSQS= MW;
        s3=s;
        DB= DataBase.getInstance();
        Done=false;
        countT= 0;
    }

    private int getWorkersNeeded(int ImgCount, int n){
        return ((int) (Math.ceil((double) ImgCount / n)-DB.getWorkersCounts()));
    }

    public int WorkMassages(File InputFile, String Task_key, String bucket, String LocalQueue) throws FileNotFoundException {
        int count=0;
        try{
            BufferedReader reader = new BufferedReader(new FileReader(InputFile));
            String line = reader.readLine();
            while (line != null){
                String [] l= line.split("\n"); //TODO:check!
                //example: Task_24.11.20..._Queue_1 http:... bucket24.11...
                String t = Task_key+"_"+count+" "+l[0]+" "+bucket+ " "+ LocalQueue;
                ManagerWorkersSQS.sendMessage(t);
                line = reader.readLine();
                count++;
            }
            reader.close();
        }
        catch (IOException e){
            System.out.println("Problem in function: WorkMassages");
            e.printStackTrace();
        }
        return count;
    }

    public Integer countURLs(File InputFile){
        int count= 0;
        try{
            BufferedReader reader = new BufferedReader(new FileReader(InputFile));
            String line = reader.readLine();
            while (line != null){
                line = reader.readLine();
                count++;
            }
            reader.close();
        }
        catch (IOException e){
            System.out.println("Problem in function: countURLs");
            e.printStackTrace();
        }
        return count;
    }

    public void NewTask(Message M) throws IOException {
        if(!Done) {
            System.out.println("***** IN NEW TASK *****\n");
            // 1. Get the info about the new Task "NewTask " + FileKey + " " + n + " " + s3.getBucket() + " " + QueueName
            String Info=M.body().substring(8); //FileKey + " " + n + " " + s3.getBucket() + " " + QueueName
            String[] info = Info.split(" ");
            if (info.length != 4) {
                System.out.println("The Data should contain file key, n, bucket name, queue");
                System.exit(1);
            }
            String TaskName = "Task" + new Date().getTime();
            String key = info[0];
            int n = Integer.parseInt(info[1]);
            String bucket= info[2];
            String LocalQueue= info[3];

            // 2. get the file with all the urls from s3
            ResponseBytes<GetObjectResponse> responseBytes= s3.getObjectBytes(key,bucket);
            byte [] objectData= responseBytes.asByteArray();
            String path= System.getProperty("user.dir")+"/"+TaskName+".txt";
            File InputFile= new File(path);
            OutputStream outputStream= new FileOutputStream(InputFile);
            outputStream.write(objectData);
            outputStream.flush();
            outputStream.close();

            // 3. get only the number of URLs
            Integer countURL= countURLs(InputFile);
            System.out.println("*** there are: "+countURL+" URLs ***\n");

            // 4. delete the file first input file created
            InputFile.delete();
            System.out.println("*** deleting the first input file ***\n");

            // 5. write in a file how many urls do we have and put it on the local's bucket in s3
            String OCRFilePath= System.getProperty("user.dir")+"/OCRFile.txt";
            File OCRFile= new File (OCRFilePath);
            OutputStream outputStreamOCR= new FileOutputStream(OCRFile);
            System.out.println("*** ImgCount: "+ countURL.toString()+" ***");
            byte [] dataImgC= countURL.toString().getBytes();
            outputStreamOCR.write(dataImgC);
            outputStreamOCR.flush();
            outputStreamOCR.close();
            System.out.println("*** put the OCRfile with the number of URLs in S3 ***\n");
            s3.PutObject(OCRFilePath, bucket, "OCRFile.txt");
            OCRFile.delete(); //delete the file after we finished with it (scalability)

            // 6. create a new and empty file for all the results from the workers
            String summaryFilePath= System.getProperty("user.dir")+"/SummaryFile.txt";
            File summaryFile= new File (summaryFilePath);
            OutputStream outputStreamSum= new FileOutputStream(summaryFile);
            byte [] data= "nothing yet".getBytes();
            outputStreamSum.write(data);
            outputStreamSum.flush();
            outputStreamSum.close();
            System.out.println("*** put the not really empty summary file in S3 ***\n");
            s3.PutObject(summaryFilePath, bucket, "SummaryFile.txt");
            System.out.println("**** Add Task: "+TaskName+" *****");
            summaryFile.delete(); //delete the file after we finished with it (scalability)

            // 7. activate the workers we need for this task
            System.out.println("*** now activate the workers if needed *** \n");
            int WorkersNeeded = getWorkersNeeded(countURL,n);
            DB.WorkersToJob(WorkersNeeded);

            if (countT==0){
                countT++;
                Workerschecker.start();
            }

            // 8. get the input file from s3, this time in order to send the urls to the workers
            System.out.println("*** download again the input file ***\n");
            ResponseBytes<GetObjectResponse> responseBytes2= s3.getObjectBytes(key,bucket);
            byte [] objectData2= responseBytes2.asByteArray();
            String path2= System.getProperty("user.dir")+"/"+TaskName+"2.txt";
            File InputFile2= new File(path2);
            OutputStream outputStream2= new FileOutputStream(InputFile2);
            outputStream2.write(objectData2);
            outputStream2.flush();
            outputStream2.close();

            // 9. Creates an SQS message for each URL in the input file
            // together with the operation that should be performed on it.
            System.out.println("*** send the tasks to the workers *** \n");
            Integer ImgCount = WorkMassages(InputFile2, TaskName, bucket, LocalQueue);


            //10. delete the infut file from s3 and from the local computer
            s3.DeleteObject(key,bucket);
            System.out.println("**** Deleting the task: "+ TaskName+ " Key: "+key+ " from bucket: "+ bucket +" ****");
            InputFile2.delete();

            // add the task to the Data Base
            DB.addTask();

            //11. delete the message from the q
            List<Message> toDelete= new LinkedList<Message>();
            toDelete.add(M);
            LocalManagerSQS.deleteMessages(toDelete);
        }
    }

    public void Terminate() throws InterruptedException {
        // 1. Does Not accept any more input files
        Done=true;
        // 2. wait for all the workers to finish
        System.out.println("*** Waiting for all tasks to finish ***");
        while(DB.getTasksCounts()>0){
            try {
                TimeUnit.SECONDS.sleep(45);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println("*** All tasks are finished ***");

        Workerschecker.interrupt();

        //3.
        EC2 worker = DB.getWorker();
        while(worker!= null){
            System.out.println("*** Terminates 1 worker ***");
            worker.Terminate();
            worker = DB.getWorker();
        }
        // 4. terminate the locals to manager sqs
        LocalManagerSQS.deleteSQS();
        ManagerWorkersSQS.deleteSQS();
        SQS WorkersManagerSQS = new SQS("WorkersManagerSQS");
        WorkersManagerSQS.getUrl();
        WorkersManagerSQS.deleteSQS();
        EC2 ec2= new EC2();
        try {
            DescribeInstancesRequest req = DescribeInstancesRequest.builder().build();
            DescribeInstancesResponse res = ec2.getEC2().describeInstances(req);
            for (Reservation reservation : res.reservations()) {
                for (Instance instance : reservation.instances()) {
                    String name=instance.tags().get(0).value();
                    String state=instance.state().name().toString();
                    if(name.equals("Manager")&&(state.equals("running")||state.equals("pending"))) {
                        ec2.Terminate(instance.instanceId());
                    }
                }
            }
        } catch (Ec2Exception e) {
            System.out.println("Problem in function: TerminateManager");
            System.out.println(e.awsErrorDetails().errorMessage());
        }
    }

    public void CheckLocalManagerSQS() throws InterruptedException, IOException {
        List<Message> messages= LocalManagerSQS.getMessages();
        if (!messages.isEmpty()){
            System.out.println("*** there is a new Message in LocalManagerSQS ***");
            for (Message m : messages){
                System.out.println("*** the message is: "+m.body()+" ***\n");
                if ((m.body().substring(0,9)).equals("Terminate")){ //the done message will include the word "Terminate" +" "+ bucketName+ keyName+ path
                    Terminate();
                    return;
                }
                else if((m.body().substring(0,7)).equals("NewTask")){
                    NewTask(m);
                }
            }
        }
    }

    public void run() {
        try {
            LocalManagerSQS = new SQS("LocalManagerSQS");
            LocalManagerSQS.getUrl();
            WorkerChecker WC= new WorkerChecker();
            Workerschecker = new Thread(WC);
            while(!Done){
                CheckLocalManagerSQS();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}

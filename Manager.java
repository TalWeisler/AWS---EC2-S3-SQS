import software.amazon.awssdk.services.ec2.model.*;

import java.util.LinkedList;

public class Manager {
    private static Thread ThreadForLocal;
    private static Thread ThreadForWorkers;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("***** Creat ManagerWorkersSQS *****");
        SQS ManagerWorkerSQS = new SQS("ManagerWorkersSQS" );
        ManagerWorkerSQS.createQueue();
        ManagerWorkerSQS.PrintSQS();
        System.out.println("***** Creat WorkersManagerSQS *****");
        SQS WorkersManagerSQS = new SQS("WorkersManagerSQS");
        WorkersManagerSQS.createQueue();
        ManagerWorkerSQS.PrintSQS();
        System.out.println("***** Creat s3 *****");
        S3 s3 = new S3();
        LocalManagerCommunication LMC =new LocalManagerCommunication(s3,ManagerWorkerSQS);
        WorkerManagerCommunication WMC= new WorkerManagerCommunication(WorkersManagerSQS);

        ThreadForLocal= new Thread(LMC);
        ThreadForLocal.start();
        ThreadForWorkers= new Thread(WMC);
        ThreadForWorkers.start();

        try{
            ThreadForLocal.join();
        }
        catch(InterruptedException e){
            e.printStackTrace();
        }
        try{
            ThreadForWorkers.join();
        }
        catch(InterruptedException e){
            e.printStackTrace();
        }
    }
}

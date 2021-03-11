import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.Reservation;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

public class WorkerChecker implements Runnable{
    public void run() {
        EC2 ec2= new EC2();
        DataBase DB= DataBase.getInstance();
        while(true) {
            try {
                TimeUnit.SECONDS.sleep(45);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            LinkedList<EC2> workers = DB.getWorkers();
            System.out.println("*** workers list ****");
            if (!workers.isEmpty()) {
                for (EC2 worker : workers) {
                    System.out.println("**********Start**************");
                    DescribeInstancesRequest req = DescribeInstancesRequest.builder().instanceIds(worker.getInstanceId()).build();
                    DescribeInstancesResponse res = ec2.getEC2().describeInstances(req);
                    System.out.println("*** finish res ***");
                    for (Reservation reservation : res.reservations()) {
                        for (Instance instance : reservation.instances()) {
                            if(instance.instanceId().equals(worker.getInstanceId())) {
                                System.out.println("*** after fors ***");
                                String state = instance.state().name().toString();
                                System.out.println("*** got the state ***");
                                if (state.equals("shutting-down") || state.equals("terminated") || state.equals("stopping") || state.equals("stopped")) {
                                    System.out.println("**********Found**************");
                                    DB.DeleteWorker(worker);
                                    DB.WorkersToJob(1);
                                    try {
                                        TimeUnit.SECONDS.sleep(45);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                    System.out.println("**********ADD**************");
                                }
                            }
                        }
                    }
                }
            }
        }

    }
}

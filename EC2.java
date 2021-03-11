import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import java.util.Base64;

public class EC2 {
    private Ec2Client EC2;
    private String name;
    private String AMI="ami-013de9c3851a399c6";
    private Tag tag;
    private String instanceId;
    public EC2(){
        EC2= Ec2Client.builder().build();
    }
    public EC2 (String Name, int Min, int Max, String Data){//.securityGroupIds("sg-03cc857fb8716d749")
        name=Name;
        EC2= Ec2Client.builder().build();

        IamInstanceProfileSpecification IAM_role = IamInstanceProfileSpecification.builder()
                .arn("arn:aws:iam::263387240235:instance-profile/IAM_Ass1_1").build();

        RunInstancesRequest runRequest = RunInstancesRequest.builder()
                .instanceType(InstanceType.T2_MICRO)
                .imageId(AMI)
                .iamInstanceProfile(IAM_role)
                .securityGroupIds("launch-wizard-3")
                .keyName("Ass1_key")
                .maxCount(Max)
                .minCount(Min)
                .userData(Base64.getEncoder().encodeToString(Data.getBytes()))
                .build();

        RunInstancesResponse response = EC2.runInstances(runRequest);
        instanceId = response.instances().get(0).instanceId();
        tag = Tag.builder()
                .key("Name")
                .value(name)
                .build();

        CreateTagsRequest tagRequest = CreateTagsRequest.builder()
                .resources(instanceId)
                .tags(tag)
                .build();
        try {
            EC2.createTags(tagRequest);
            System.out.printf("Successfully started EC2 instance %s based on AMI %s",instanceId, AMI);
        }
        catch (Ec2Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);}
        System.out.println("Done!");
    }
    public void Terminate(){
        try {
            System.out.println("*** Trying to terminare EC2 name: "+ name+ ", instanceId: "+ instanceId+" ***\n");
            TerminateInstancesRequest TerminateRequest = TerminateInstancesRequest.builder().instanceIds(instanceId).build();
            EC2.terminateInstances(TerminateRequest);
        }
        catch (Ec2Exception e){
            System.out.println("***Unable to terminate the instance:" +instanceId+ " , name:"+name+" ***\n");
            System.out.println(e.awsErrorDetails().errorMessage());
        }
    }
    public void Terminate(String instanceId){
        try {
            TerminateInstancesRequest TerminateRequest = TerminateInstancesRequest.builder().instanceIds(instanceId).build();
            EC2.terminateInstances(TerminateRequest);
        }
        catch (Ec2Exception e){
            System.out.println("Unable to terminate the instance:" +instanceId+ " , name:"+name+"\n");
            System.out.println(e.awsErrorDetails().errorMessage());
        }
    }
    public Ec2Client getEC2(){return EC2;}
    public String getInstanceId(){return instanceId;}


}
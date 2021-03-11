import software.amazon.awssdk.services.sqs.model.Message;

import java.io.IOException;
import java.util.List;

public class WorkerManagerCommunication implements Runnable {
    private DataBase DB;
    private boolean Done;
    private SQS WorkersManagerSQS;

    public WorkerManagerCommunication( SQS WM){
        DB = DataBase.getInstance();
        Done=false;
        WorkersManagerSQS=WM;
    }

    public void CheckWorkersManagerSQS() throws IOException {
        List<Message> messages= WorkersManagerSQS.getMessages();
        if (!messages.isEmpty()){
            for (Message m : messages){
                System.out.println("*** another message arrived in WorkersManagerSQS ***");
                if ((m.body().substring(0,6)).equals("Finish")){ //"Finish "+ taskName+ " " + bucket+" " + localQ+ " "+ urlImage+ " "+text
                    String [] s = m.body().substring(7).split(" "); //taskName+ " " + bucket+" " + localQ +" "+ urlImage+ " "+text
                    if (s.length < 3) {
                        System.out.println("The Data should contain Task key , url & text");
                        System.exit(1);
                    }
                    String Task_key= s[0];
                    System.out.println("The Task: "+Task_key+"\n");
                    String bucket = s[1];
                    String localQ= s[2];
                    String url=s[3];

                    int place=7+Task_key.length()+1 +url.length()+1+bucket.length()+1+localQ.length()+1;
                    String text= m.body().substring(place);
                    String res="";
                    if(text.substring(0,26).equals("Unable to open the picture")){
                        res=text+"\n";
                    }
                    else {
                        res=url+"\n"+text+"\n";
                    }
                    boolean check_finish= DB.SetTaskImg(res, bucket);
                    if (check_finish){
                        DB.removeTask("OCRFile.txt", bucket);
                        SQS ans= new SQS(localQ);
                        ans.getUrl();
                        ans.sendMessage("done "+bucket);
                    }
                }
            }
            WorkersManagerSQS.deleteMessages(messages);
        }
    }

    public void run() {
        try {
            WorkersManagerSQS = new SQS("WorkersManagerSQS");
            WorkersManagerSQS.getUrl();
            while(!Done) {
                CheckWorkersManagerSQS();
            }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

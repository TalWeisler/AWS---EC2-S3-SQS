import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import software.amazon.awssdk.services.sqs.model.Message;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.net.URL;
import java.util.List;

public class Worker {// when finished a task return "final"+t

    public static void main(String[] args) {
        SQS workersToManagerSQS = new SQS("WorkersManagerSQS");
        workersToManagerSQS.getUrl();
        SQS managerToWorkersSQS = new SQS("ManagerWorkersSQS");
        managerToWorkersSQS.getUrl();
        boolean terminate = false;
        while (!terminate) {
            System.out.println("***** Start Working!! ******\n");

            // 1. get an image message from an SQS q
            List<Message> messages = managerToWorkersSQS.getMessages();
            if (!messages.isEmpty()) {
                // example: Task_key+"_"+count+" "+l[0]+" "+bucket+ " "+ LocalQueue;
                String task = messages.get(0).body();

                //2. Download the image file indicated in the message
                String[] splitTask = task.split(" ");
                if (splitTask.length >= 3) {
                    String taskName = splitTask[0];
                    String imageUrl = splitTask[1];
                    String bucket= splitTask[2];
                    String queue= splitTask[3];

                    //3. Apply OCR on the image
                    String result;
                    Tesseract instance = new Tesseract();
                    instance.setDatapath("./tessdata");
                    try {
                        result = instance.doOCR(ImageIO.read(new URL(imageUrl).openStream()));
                    } catch (TesseractException | IOException e) {
                        result = "Unable to open the picture\n input file: "+ imageUrl+" \n" +
                                "A short description of the exception: "+ e.getMessage()+ "\n";
                        e.printStackTrace();
                    }

                    //4. Notify the manager of the text associated with that image
                    // "Finish "+ taskName+ " " + bucket+" " + localQ " "+ urlImage+ " "+text
                    String message = "Finish " + taskName + " "+ bucket+ " " +queue+ " " + imageUrl + " " + result;
                    workersToManagerSQS.sendMessage(message);

                    //5. remove the image message from the SQS q
                    managerToWorkersSQS.deleteMessages(messages);

                }
            } else {
                System.out.println("There is not eanogh information for the worker to do his job.");
            }
        }
    }
}


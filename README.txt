DSP - Assignment 1

Submit by: Tal Weisler 316297019 & Rotem Amit 314976903

Details about the work:
- AMI - ami-013de9c3851a399c6
- InstanceID - T2_micro
- IAM_Role - arn:aws:iam::263387240235:role/IAM_Ass1_1

- Instructions - Running the project - 
	1. update the credential - on the local dir /.aws/credentials
	2. upload the Manager.jar & Worker.jar to the bucket ass01 in S3
	3. make the files (Manager.jar & Worker.jar) public
	4. insert the input file to the src folder- *create src folder if needed* 
	5. put the Local.jar in the same path as the src folder
	6. run the localApplication- java -jar Local.jar <inputFileName> <outputFileName> <n> <terminate>

- Instructions - How the program works - 
	1.	Starting the local Application:
		The main of Local Application checks which arguments it has (especially if it needs to send a terminate message in the end).
	2. Activate manager:
		first, we create a new SQS object that will connect the new local application to the manager. 
		Then, the local application checks if the manager is already running or not .
		If not-  it creates a new EC2 (asks for 1 instance of EC2) with the proper tag. 
		If yes- it gets the URL of the already existing LocalManagerSQS. 
	3. UploadToS3: it creates a new S3 object,  a special bucket for this local application and uploads the file with the list of images to S3.
	4. Create a special Queue for messages from the Manager to the current Local Application (MyQueue).
	5. Local Application sends a message (in the LocalManagerSQS queue) stating that the manager has a new task, the location of the images list on S3, 
		the ratio of workers-images, and the queue name to which it needs to send the "finish" message.
	6. The Manager creates the following queues: ManagerWorkersSQS, WorkersManagerSQS and starts 2 threads. 
		One thread for LocalManagerCommunication and the other for WorkerManagerCommunication. 
	7. The thread of the LocalManagerCommunication works in an endless loop (until it gets a termination message from the LocalManagerSQS queue),  
		and checks if there are new messages in the LocalManagerSQS (the function CheckLocalManagerSQS).
		If the new message includes the word "Terminate" than it activates the function Terminate. 
		If not, and the message includes the word "NewTask" than, we create a new Task from this message (in the function NewTask). 
		First, it gets the information from the message, (key, bucket and n (the ratio of workers-images)). Then it gets the matching file from S3, and saves it's information (all the URLs) to a file. 
	8. countURLs: after the manager downloaded the file from S3 it counts how many URLs there are in the file, and returns the number.
	9. The manager deletes the file he just created and , it saves the number of URLs to a file, named OCRfile, and uploads it to the relevant local application's special bucket in S3, 
		and deletes it from the memory of the Manager.
	10. Now, it creates a new file, named SummaryFile and initialize it with "nothing yet", and uploads it to the relevant local application's special bucket in S3, and deletes it from the memory of the Manager.
	11. In this stage the Manager calculate how much more workers it will need for the current task (if any), and activates them, with the function WorkersToJob in the DataBase. 
	12. The thread of the LocalManagerCommunication downloads again the input file (the file with all the URLs) and saves it.  
	13. WorkMasseges: in this function, the Manager send each URL as a separate message to the workers via ManagerWorkersSQS with the information of: 
		Task_key, the message's URL, bucket (of the relevant local application) and the SQS to the relevant Local application. 
	14. If the Manager is doing it for the first time, then it will start a new thread Workerschecker, that will check that all the workers we need are working all the time, and start new ones if not.
	15. Now, it deletes the input file (the URLs) from S3, deletes the input file, updates the number of tasks the Manager has, and delete the message from the LocalManagerSQS. 
		(and goes back to see if there is a new message).
	16. Workers: after all the workers was activated in the DataBase (by the LocalManagerCommunication) it creates the SQS: WorkersManagerSQS and ManagerWorkersSQS.
		Then in an endless loop every worker does the following:
		Check if there is a new message in the managerWorkersSQS, and if there is it gets the information from the message. Then it tries to apply the OCR to the given URL. 
		If it was successful, then the string in the image returns, if not than we create a matching message stating the problem. 
		Afterwards it creates the "Finish" message (witch includes: "Finish", the task name, the bucket and SQS of the local who requested this image, 
		the image's URL, and the result of the OCR) and sends it to the Manager via WorkersManagerSQS and deletes the original message from ManagerWorkersSQS.
	17. In the Manager, the thread of WorkerManagerCommunication works in an endless loop (until the termination of the Manager), and checks if there are new messages in the WorkersManagerQueue. 
		If the message contains the word "Finish" than we do the following: 
			Get the needed information from the message.
			Checks whether the reading of the image was successful or not, and creates the relevant text to add to the SummaryFile for the case. 
	18. In order to update that a new result message arrived from the WorkersManagerSQS, and check if the relevant task is done we go to the function SetTaskImg.
	19. SetTaskImg: receives in the parameters, the text that is to be written to the SummaryFile, and the bucket of the relevant local application (that the SummaryFile belongs to).  
		First in the function SetImageResult, it removes the SummaryFile from S3, add the text of the OCR's result to the end of the file and uploads it back to the Local Application's bucket, 
		and deletes it from the Manager memory. Than it gets the OCRFile from S3 and deletes it from there (this is the file with the number of URLs that don't have answer yet). 
		Now, it checks how many URLs there are left, if 0 then it returns true (this task is finished), 
		otherwise it create a new OCRFile with the updated number of URLs left, and uploads it to the relevant local application's bucket, 
		and deletes it from the manager, and returns false in the end (the task is nit finished yet). 
	20. If all the images have an answer than the thread of WorkerManagerCommunication sends a "done" message to the relevant local application through it's SQS.
		In the end we delete from the WorkersManagerSQS the message it finished.
	21. Local Application reads SQS message (in the function isDoneMessage) and checks if it has the word "done" in it. If so, we return the message in it.
	22. Local Application downloads summary file from S3 with the information from the message received before, and returns it.
	23. Local Application creates html output file.
	24. If the local application got "Terminate" in the arguments, than it sends a "Terminate" message to the Manager via LocalManagerSQS. Otherwise, the local application finish it's job and terminates. 
	25. The thread of the LocalManagerCommunication takes the new message of "Terminate" (if exists) from LocalManagerSQS and apply the function Terminate. The function Terminate updates the value of Done to be true, and then waits for all the tasks that the manager already taking care of will be done. After all the tasks are done the localManagerCommunication interrupt the Workerschecker thread (so it won't make new workers), and terminates all the instances of workers, and deletes the LocalManagerSQS, the ManagerWorkersSQS and WorkersManagerSQS. Finally it terminates the instance of the Manager EC2.


- Running Times -
	 n=6  time= 3 min
	 n=14 time= 3 min

- Security - 
	We use the plugin: AWS Toolkit
	which allow us to save the credential on a local file and connect without revealing the details.

- Scalability - 
	The manager contain 3 threads in order to divide his work.
	The 2 first threads cause the manager to never wait because one thread checks the LocalsManagerSQS 
	and the other checks the WorkersManagerSQS.
	We created the DataBase class to handle the connection between the thread.
	Moreover, we don't save anything in the Manager memory, just the number of tasks the Manager has, the number of workers, and the EC2 object (of the workers). 
	The numbers don't take uo a lot of memory (even a very big number) and also the pointers to the EC2 object.
	

- Persistence - 
	worker will delete a task from the queue only after its done. If something happend and the worker couldn't finish the task, the task will go back to the queue and another worker will take it.
	In order to deal with the case thet a node stalls for a while, we updated to a longer time the visibility time out, so the worker has eonogh time to finish his job.

- Threads - 
	The manager contain 3 threads: 
	1. for the connaction between the local to the manager- includes handling New-Task messages or termination messages
	2. for the connaction between the worker to the manager- includes handling the worker's response 
	   and sending the final result to the relevant local.
	3. for checking if all of the workers are working - if a worker suddenly terminate, the worker replace with new EC2 (& update in the DataBase).

- More than one client at the same time -
	Checked! 

- Termination -
	After a local get respone, the local require to:
	- Delete his own queue from SQS
	- Delete the manager's response file from S3
	- Delete his own buccket from S3
	After the local send termination message, the manager require to:
	- Terminate the running workers
	- Delete the queues: LocalManagerSQS, ManagerWorkersSQS, WorkersManagerSQS
	- Terminate himself

- Division of tasks - 
	We desided to limit the queues to one message at a time, in that way the tasks are shared equally among the workers.

- Managing tasks - 	
	Each local has its own bucket.
	Inside the bucket there is the input file and later 2 more files:
	the summary file and a file for the amount of images left.
	When a worker returns an answer to the manager, its indicates to which bucket the result belongs 
	and thus there is no mixing of results between the different locals.
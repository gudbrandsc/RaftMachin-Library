import org.json.simple.JSONObject;

import java.util.concurrent.atomic.AtomicInteger;

public class AppendEntrySenderThread implements Runnable {
    private String host;
    private String path;
    private String requestBody;
    private int port;
    private ServiceHelper serviceHelper;
    private RaftMachine raftMachine;

    /** Constructor */
    public AppendEntrySenderThread(String host, int port, String path, String requestBody, RaftMachine raftMachine) {
        this.host = host;
        this.path = path;
        this.requestBody = requestBody;
        this.port = port;
        this.serviceHelper = new ServiceHelper();
        this.raftMachine = raftMachine;
    }

    /** Run method that sends a write request to a secondary */
    @Override
    public void run() {
        JSONObject respData = serviceHelper.sendPostRequestAndReturnRespData(host, port, path, requestBody);
        if(respData != null){
            int respTerm = Integer.valueOf(respData.get("term").toString());
            if(raftMachine.compareAndUpdateTerm(respTerm)){
                synchronized (raftMachine){
                    if(raftMachine.isTermLeader()) {
                        raftMachine.setAsFollower();
                        System.out.println("Received a response with a greater term. Reset back as a follower... ");
                    }
                    notifyAll();
                }
            }
        }else{
            System.out.println(host + ":" + port + " did not respond to heart beat..");
        }
    }
}

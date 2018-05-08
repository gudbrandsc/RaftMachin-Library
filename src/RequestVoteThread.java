import org.json.simple.JSONObject;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Gudbrand Schistad
 * Thread class used by master userservice to replicate a write request to a secondary
 */
class RequestVoteThread implements Runnable {
    private String host;
    private String path;
    private String requestBody;
    private int port;
    private ServiceHelper serviceHelper;
    private AtomicInteger votes;

    /** Constructor */
    public RequestVoteThread(String host, int port, String path, String requestBody, AtomicInteger votes) {
        this.host = host;
        this.path = path;
        this.requestBody = requestBody;
        this.port = port;
        this.serviceHelper = new ServiceHelper();
        this.votes = votes;
    }

    /** Run method that sends a write request to a secondary */
    @Override
    public void run() {
        JSONObject respData = serviceHelper.sendPostRequestAndReturnRespData(host, port, path, requestBody);
        if(respData != null) {
            if (respData.get("voteGranted").equals(true)) {
                votes.incrementAndGet();
            }
        }
    }
}

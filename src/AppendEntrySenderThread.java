import org.json.simple.JSONObject;


public class AppendEntrySenderThread implements Runnable {
    private NodeInfo nodeInfo;
    private String path;
    private ServiceHelper serviceHelper;
    private RaftMachine raftMachine;

    /** Constructor */
    public AppendEntrySenderThread(NodeInfo nodeInfo, String path,  RaftMachine raftMachine) {
        this.nodeInfo = nodeInfo;
        this.path = path;
        this.serviceHelper = new ServiceHelper();
        this.raftMachine = raftMachine;
    }

    /** Run method that sends a write request to a secondary */
    @Override
    public void run() {
        LogEntry entry = raftMachine.getLogEntry(nodeInfo.getAppendIndex().intValue());
        JSONObject obj = new JSONObject();
        obj.put("term", raftMachine.getCurrentTerm().intValue());
        obj.put("candidateId", raftMachine.getCandidateId());

        if(entry != null) {
            System.out.println("[L] Sending append entry with data for index: " + nodeInfo.getAppendIndex().intValue());
            obj.put("prevLogIndex", entry.getPrevLogIndex());
            obj.put("prevLogTerm", entry.getPrevLogTerm());
            obj.put("data", entry.getData());
            obj.put("dataterm", entry.getEntryTerm());
        }else{
            obj.put("prevLogIndex", raftMachine.getLastAppliedIndex());
            obj.put("prevLogTerm", raftMachine.getLastAppliedTerm());
        }

        obj.put("leaderCommit", raftMachine.getLastCommitIndex());
        JSONObject respData = serviceHelper.sendPostRequestAndReturnRespData(nodeInfo.getIp(), nodeInfo.getPort(), path, obj.toJSONString());

        if(respData != null){
            int respTerm = Integer.valueOf(respData.get("term").toString());
            if(raftMachine.compareAndUpdateTerm(respTerm)){
                synchronized (raftMachine){
                    if(raftMachine.isTermLeader()) {
                        raftMachine.setAsFollower();
                        System.out.println("[L] Received a response with a greater term. \n Resetting back as a follower state... ");
                    }
                    notifyAll();
                }

            }else if (respData.get("success").equals(false)){
                System.out.println("Append entry was not success, decrement appendIndex for node " + nodeInfo.getIp()+":"+nodeInfo.getPort());
                nodeInfo.getAppendIndex().decrementAndGet();
            }else if(respData.get("success").equals(true)){
                if(obj.containsKey("data")){
                    System.out.println("Replicated entry with index "+ nodeInfo.getAppendIndex() + " to node " + nodeInfo.getIp() + ":" + nodeInfo.getPort());
                    nodeInfo.getAppendIndex().incrementAndGet();
                    entry.getSuccessReplication().incrementAndGet();
                }else{ //TODO Remove
                 //   System.out.println("True resp for empty request");
                }
            }
        }else{
            System.out.println("[L]"+nodeInfo.getIp() + ":" + nodeInfo.getPort() + " did not respond to heart beat..");
        }
    }
}

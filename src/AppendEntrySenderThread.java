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
        System.out.println("I want to send this index to the node ++++ " + nodeInfo.getAppendIndex().intValue());
        LogEntry entry = raftMachine.getLogEntry(nodeInfo.getAppendIndex().intValue());
        JSONObject obj = new JSONObject();
        obj.put("term", raftMachine.getCurrentTerm().intValue());
        obj.put("candidateId", raftMachine.getCandidateId());

        if(entry != null) {
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
                        System.out.println("Received a response with a greater term. Reset back as a follower... ");
                    }
                    notifyAll();
                }

            }else if (respData.get("success").equals(false)){
                System.out.println("Success == false");
                nodeInfo.getAppendIndex().decrementAndGet();
            }else if(respData.get("success").equals(true)){
                if(obj.containsKey("data")){
                    System.out.println("Replicated data to node " + nodeInfo.getIp() + ":" + nodeInfo.getPort());
                    nodeInfo.getAppendIndex().incrementAndGet();
                    entry.getSuccessReplication().incrementAndGet();
                }else{
                    System.out.println("True resp for empty request");
                }
            }
        }else{
            System.out.println(nodeInfo.getIp() + ":" + nodeInfo.getPort() + " did not respond to heart beat..");
        }
    }
}

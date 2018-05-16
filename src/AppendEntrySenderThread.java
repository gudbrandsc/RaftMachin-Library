import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

/**@author gudbrandschistad
 * Class that impliments runnable, and will send an append entry RPC to a node.
 * @see AppendEntriesRPC for info about how data is read and used.
 */
public class AppendEntrySenderThread implements Runnable {
    private NodeInfo nodeInfo;
    private ServiceHelper serviceHelper;
    private RaftMachine raftMachine;

    /** Constructor
     * @param nodeInfo  info about the current to send RPC to.
     * @param raftMachine instance of the nodes raft machine.
     * */
    public AppendEntrySenderThread(NodeInfo nodeInfo, RaftMachine raftMachine) {
        this.nodeInfo = nodeInfo;
        this.serviceHelper = new ServiceHelper();
        this.raftMachine = raftMachine;
    }

    /** Run method that checks if there is any log entry with the current nodes append index.
     * If there is any data, then it will be added to the body of the append entry RPC request.
     * If a secondary was unable to add a entry to log(different last appended index or term), then it will decrement the index of the next log
     * entry to send.
     * If response success is true and a log entry was sent, then increment the nodes append index number.
     * */
    @Override
    public void run() {
        LogEntry entry = raftMachine.getLogEntry(nodeInfo.getAppendIndex().intValue());
        JSONObject obj = new JSONObject();
        obj.put("term", raftMachine.getCurrentTerm().intValue());
        obj.put("candidateId", raftMachine.getCandidateId());

        if(entry != null) { // If there is an log entry with index from nodes append index
            System.out.println("[L] Sending append entry with data for index: " + nodeInfo.getAppendIndex().intValue() + " to " + nodeInfo.getIp() + ":" + nodeInfo.getPort());
            obj.put("prevLogIndex", entry.getPrevLogIndex());
            obj.put("prevLogTerm", entry.getPrevLogTerm());
            obj.put("data", entry.getData());
            obj.put("dataterm", entry.getEntryTerm());
        }else{
            obj.put("prevLogIndex", raftMachine.getLastAppliedIndex());
            obj.put("prevLogTerm", raftMachine.getLastAppliedTerm());
        }

        obj.put("leaderCommit", raftMachine.getLastCommitIndex());
        JSONObject respData = serviceHelper.sendPostRequestAndReturnRespData(nodeInfo.getIp(), nodeInfo.getPort(), raftMachine.getAppendEntryPath(), obj.toJSONString());

        if(respData != null){ // If receiving node responds
            int respTerm = Integer.valueOf(respData.get("term").toString());
            if(raftMachine.updateTerm(respTerm)){ // Checks if current term is > term in response.
                if(raftMachine.isTermLeader()) {
                    raftMachine.setAsFollower();
                    System.out.println("[L] Received a response with a greater term. \n Resetting back as a follower state... ");
                }
            }else if (respData.get("success").equals(false)){
                System.out.println("[L] Follower were unable to append entry decrementing append entry index  for node: " + nodeInfo.getIp()+":"+nodeInfo.getPort());
                nodeInfo.getAppendIndex().decrementAndGet();
            }else if(respData.get("success").equals(true)){
                if(obj.containsKey("data")){
                    System.out.println("[L] Replicated entry with index "+ nodeInfo.getAppendIndex() + " to node " + nodeInfo.getIp() + ":" + nodeInfo.getPort());
                    // Increment the index number for the next log entry to send
                    nodeInfo.getAppendIndex().incrementAndGet();
                    // Increment number of successful replications for the log entry.
                    entry.getSuccessReplication().incrementAndGet();
                }
            }
        }else{
            System.out.println("[L] "+nodeInfo.getIp() + ":" + nodeInfo.getPort() + " did not respond to heart beat..");
        }
    }
}

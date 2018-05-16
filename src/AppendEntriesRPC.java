import org.json.simple.JSONObject;
/**
 * @author gudbrandschistad
 * Class used to handle append entry RPC.
 * @see AppendEntrySenderThread for info about incomming append RPC data
 */
public class AppendEntriesRPC {
    private RaftMachine raftMachine;

    /** Constructor
     * @param raftMachine instance of the nodes raft machine.
     */
    public AppendEntriesRPC(RaftMachine raftMachine) {
        this.raftMachine = raftMachine;
    }

    /**
     * Method used to build a response from the received Append entry RPC.
     * If the RPC contains any data, then the data will be added to log.
     * If the committed index received in RPC is greater than last committed index, then commit
     * all log entries with a lower index than received commit index.
     * @param requestJson JSON object received from the leader.
     * @return JSON format string. */
    public String buildAppendEntriesResp(JSONObject requestJson) {

        int term = Integer.valueOf(requestJson.get("term").toString());
        int candidateId = Integer.valueOf(requestJson.get("candidateId").toString());
        int prevLogIndex = Integer.valueOf(requestJson.get("prevLogIndex").toString());
        int prevLogTerm = Integer.valueOf(requestJson.get("prevLogTerm").toString());
        int leaderCommit = Integer.valueOf(requestJson.get("leaderCommit").toString());
        int dataTerm = 0;
        JSONObject data = null;
        Boolean gotData = requestJson.containsKey("data"); //TODO Change to CONTAINSKEY

        if (gotData) {
            ServiceHelper helper = new ServiceHelper();
            data = helper.stringToJsonObject(requestJson.get("data").toString());
            dataTerm = Integer.valueOf(requestJson.get("dataterm").toString());
        }

        JSONObject responseObj = new JSONObject();


        if (term < raftMachine.getCurrentTerm().intValue()) {
            System.out.println("[F] Term in request: " + term + " but I'm on term " + raftMachine.getCurrentTerm().intValue());
            responseObj.put("term", raftMachine.getCurrentTerm());
            responseObj.put("success", false);
        } else {
            if (term > raftMachine.getCurrentTerm().intValue()) {
                //If leader receives a append entry RPC with a higher term then the current term. Then go back to follower state
                if (raftMachine.isTermLeader()) {
                    System.out.println("[L] Received a append entry RPC with a higher term then current term ");
                    raftMachine.setAsFollower();
                }
                raftMachine.appendEntryTermUpdate(term, candidateId);
            }
            if((candidateId != raftMachine.getLeaderId()) && (term == raftMachine.getCurrentTerm().intValue())){
                raftMachine.updateLeaderInfo(candidateId);
            }

            if (prevLogTerm == raftMachine.getLastAppliedTerm() && (prevLogIndex == raftMachine.getLastAppliedIndex())) {
                responseObj.put("term", raftMachine.getCurrentTerm());
                responseObj.put("success", true);
                if (leaderCommit > raftMachine.getLastCommitIndex()) {
                    raftMachine.commitLogEntries(leaderCommit);
                }

                if (gotData) {
                    if (raftMachine.getLogEntry(prevLogIndex + 1) == null) {
                        raftMachine.appendEntryToLog(data, dataTerm, prevLogIndex, prevLogTerm);
                    } else if ((raftMachine.getLogEntry(prevLogIndex + 1) != null) && (raftMachine.getLogEntry(prevLogIndex + 1).getEntryTerm() < dataTerm)) {
                        raftMachine.appendEntryToLog(data, dataTerm, prevLogIndex, prevLogTerm);
                    }
                }
            } else {
                responseObj.put("term", raftMachine.getCurrentTerm());
                responseObj.put("success", false);
            }
        }
        return responseObj.toJSONString();
    }
}
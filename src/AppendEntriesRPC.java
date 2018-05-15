import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class AppendEntriesRPC {
    private RaftMachine raftMachine;

    public AppendEntriesRPC(RaftMachine raftMachine) {
        this.raftMachine = raftMachine;
    }
    //Make sure that request comes from leader


    public String buildResponse(JSONObject requestJson) {

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
            System.out.println("Term in request: " + term + " but I'm on term " + raftMachine.getCurrentTerm().intValue());
            responseObj.put("term", raftMachine.getCurrentTerm());
            responseObj.put("success", false);
        } else {
            if (term > raftMachine.getCurrentTerm().intValue()) { //If leader receives a append entry rpc
                if (raftMachine.isTermLeader()) {
                    System.out.println("[L] Received a append entry RPC with a higher term then current term ");
                    raftMachine.setAsFollower();
                }

                System.out.println("Updating to term: " + term);
                raftMachine.updateTerm(term);
            }

            if (candidateId != raftMachine.getLeaderId()) {
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
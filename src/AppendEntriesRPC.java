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
        } else if (term > raftMachine.getCurrentTerm().intValue()) { //If leader receives a append entry rpc
            if (raftMachine.isTermLeader()) {
                raftMachine.setAsFollower();
            }

            if (candidateId != raftMachine.getLeaderId()) { // If term is greater than currant term and candidateID is not the same as current
                raftMachine.updateLeaderInfo(candidateId);
            }

            System.out.println("Term from request was higher then the current term.");
            System.out.println("Updating to term: " + term);
            raftMachine.updateTerm(term);
            System.out.println("Inside top " + prevLogTerm + " ==  " + raftMachine.getLastAppliedTerm() + " && " + prevLogIndex + " == " + raftMachine.getLastAppliedIndex());
            if (prevLogTerm == raftMachine.getLastAppliedTerm() && (prevLogIndex == raftMachine.getLastAppliedIndex() )) {
                responseObj.put("term", raftMachine.getCurrentTerm());
                responseObj.put("success", true);
                //todo check more conds before adding
                if (gotData) {
                    if (raftMachine.getLogEntry(prevLogIndex + 1) == null) {
                        raftMachine.appendEntryToLog(data, dataTerm, prevLogIndex, prevLogTerm);
                    } else if (raftMachine.getLogEntry(prevLogIndex + 1) != null && raftMachine.getLogEntry(prevLogIndex + 1).getEntryTerm() < dataTerm) {
                        raftMachine.appendEntryToLog(data, dataTerm, prevLogIndex, prevLogTerm);
                    } else {
                        System.out.println("Got data for term " + data + " and index " + prevLogIndex + 1 + " But I got it allready");
                    }
                }
            } else {
                responseObj.put("term", raftMachine.getCurrentTerm());
                responseObj.put("success", false);
            }
            //TODO add to log







        } else {
            if (candidateId != raftMachine.getLeaderId()) {
                raftMachine.updateLeaderInfo(candidateId);
            }

            System.out.println(requestJson.toJSONString() + "Data received");
            System.out.println("If down -- "+prevLogTerm + " == " + raftMachine.getLastAppliedTerm() + " && " + prevLogIndex + " == " + raftMachine.getLastAppliedIndex());
            if (prevLogTerm == raftMachine.getLastAppliedTerm() && (prevLogIndex == raftMachine.getLastAppliedIndex())) {
                responseObj.put("term", raftMachine.getCurrentTerm());
                responseObj.put("success", true);
                if(gotData) {
                    if (raftMachine.getLogEntry(prevLogIndex + 1) == null) {
                        raftMachine.appendEntryToLog(data, dataTerm, prevLogIndex, prevLogTerm);


                    } else if ((raftMachine.getLogEntry(prevLogIndex + 1) != null) && (raftMachine.getLogEntry(prevLogIndex + 1).getEntryTerm() < dataTerm)) {
                        raftMachine.appendEntryToLog(data, dataTerm, prevLogIndex, prevLogTerm);
                    } else {
                        System.out.println("Got data for term " + data + " and index " + prevLogIndex + 1 + "But I got it already");
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
import org.json.simple.JSONObject;

public class AppendEntriesRPC {
    private RaftMachine raftMachine;

    public AppendEntriesRPC(RaftMachine raftMachine) {
        this.raftMachine = raftMachine;
    }

    public String buildResponse(JSONObject obj){

        int term = Integer.valueOf(obj.get("term").toString());
        int candidateId = Integer.valueOf(obj.get("candidateId").toString());
        int prevLogIndex = Integer.valueOf(obj.get("prevLogIndex").toString());
        int prevLogTerm = Integer.valueOf(obj.get("prevLogTerm").toString());
        int leaderCommit = Integer.valueOf(obj.get("leaderCommit").toString());
        JSONObject responseObj = new JSONObject();

        if(term < raftMachine.getCurrentTerm().intValue()){
            System.out.println("Term in request: "+ term + " but I'm on term " + raftMachine.getCurrentTerm().intValue());
            responseObj.put("term", raftMachine.getCurrentTerm());
            responseObj.put("success", false);
        }else if(term > raftMachine.getCurrentTerm().intValue()){
            if(raftMachine.isTermLeader()){
                raftMachine.setAsFollower();
            }

            if(candidateId != raftMachine.getLeaderId()){
                for(NodeInfo info : raftMachine.getRaftMembersCopy()){
                    if(info.getCandidateId() == candidateId){
                        raftMachine.updateLeaderInfo(info.getIp(), info.getPort(), info.getCandidateId());
                        System.out.println("Updated leader host to candidate " + candidateId + " running on " + info.getIp() +":"+info.getPort());
                    }
                }
            }

            System.out.println("Term from request was higher then the current term.");
            System.out.println("Updating to term: " + term);
            raftMachine.updateTerm(term);
            responseObj.put("term", raftMachine.getCurrentTerm());
            responseObj.put("success", true);

        }else{
            if(candidateId != raftMachine.getLeaderId()){
                for(NodeInfo info : raftMachine.getRaftMembersCopy()){
                    if(info.getCandidateId() == candidateId){
                        raftMachine.updateLeaderInfo(info.getIp(), info.getPort(), info.getCandidateId());
                        System.out.println("Updated leader host to candidate " + candidateId);
                        System.out.println(info.getIp() +":"+info.getPort());
                    }
                }
            }
            responseObj.put("term", raftMachine.getCurrentTerm());
            responseObj.put("success", true);
        }
        return responseObj.toJSONString();
    }

}

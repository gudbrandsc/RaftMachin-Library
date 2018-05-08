import org.json.simple.JSONObject;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RequestVoteRPC {
    private AtomicInteger numberOfVotes;
    private RaftMachine raftMachine;

    public RequestVoteRPC(RaftMachine raftMachine) {
        this.raftMachine = raftMachine;
    }

    public void sendRequestVoteRPC(){
        numberOfVotes = new AtomicInteger( 1);
        List<Thread> replicationThreads = new ArrayList<>();
        int newTerm = raftMachine.getCurrentTerm().incrementAndGet();
        System.out.println("Sending election request for term: " + newTerm);
        for(NodeInfo info : raftMachine.getRaftMembersCopy()) {
            if (info.getCandidateId() != raftMachine.getCandidateId()) {
                JSONObject obj = new JSONObject();
                obj.put("term", newTerm);
                obj.put("candidateId", raftMachine.getCandidateId());
                obj.put("lastLogIndex", raftMachine.getLastAppliedIndex());
                obj.put("lastLogTerm", raftMachine.getLastAppliedTerm());
                String path = "/requestvote";

                Thread t = new Thread(new RequestVoteThread(info.getIp(), info.getPort(), path, obj.toJSONString(), this.numberOfVotes));
                t.start();
                replicationThreads.add(t);
            }

            for (Thread t : replicationThreads) {
                try {
                    t.join();
                } catch (InterruptedException e) {
                    //log.info("[P] Unable to replicate to a secondary still returning 200");
                }
            }
        }

        System.out.println("Number of votes received: " + numberOfVotes.intValue());
        float res = (float)numberOfVotes.intValue()/(float)raftMachine.getMemberListSize();
        System.out.println("I got " + res*100 + "% of the votes");
        if(res > 0.5){
            raftMachine.setAsTermLeader();
            System.out.println("I'm now the leader for term: " + newTerm);
        } else{
            raftMachine.setAsFollower();
            //TODO I can prob remove this
            System.out.println("I did not get enough votes to become leader, reset state to follower...");
        }
    }

    public String validateVoteRequest(JSONObject requestData){
//        System.out.println(requestData.get("term").toString() +" > "+ raftMachine.getCurrentTerm().intValue());
        JSONObject respObj = new JSONObject();
        raftMachine.resetTimer();
        if(Integer.valueOf(requestData.get("term").toString()) > raftMachine.getCurrentTerm().intValue()) {
            if(Integer.valueOf(requestData.get("lastLogTerm").toString()) >= raftMachine.getLastAppliedTerm()){
                if(Integer.valueOf(requestData.get("lastLogTerm").toString()) == raftMachine.getLastAppliedTerm()){
                    if(Integer.valueOf(requestData.get("lastLogIndex").toString()) >= raftMachine.getCommitIndex()){
                        System.out.println("Log is up to date as mine");
                        raftMachine.updateTerm(Integer.valueOf(requestData.get("term").toString()));
                        respObj.put("voteGranted", true);
                    }else{
                        System.out.println("Same term, lower index");
                        respObj.put("voteGranted", false);
                    }
                }else{
                    System.out.println("Log term for last applied was higher but not equal");

                    raftMachine.updateTerm(Integer.valueOf(requestData.get("term").toString()));
                    respObj.put("voteGranted", true);
                }
            }else{
                System.out.println("log term last applied to low");
                respObj.put("voteGranted", false);
            }
        }else {
            System.out.println("Term lower than currant term");
            respObj.put("voteGranted", false);
        }
        respObj.put("term", raftMachine.getCurrentTerm());
        System.out.println("Resp for vote: " + respObj.toJSONString());
        return respObj.toJSONString();
    }

    public JSONObject getJsonFromRequest(HttpServletRequest request){
        ServiceHelper helper = new ServiceHelper();
        try {
            return helper.stringToJsonObject(helper.requestToString(request));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}

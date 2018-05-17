import org.json.simple.JSONObject;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * @author gudbrandschistad
 * Class used to send and recieve election requests
 */
public class RequestVoteRPC {
    private AtomicInteger numberOfVotes;
    private RaftMachine raftMachine;

    /**
     *  Constructor
     *  @param raftMachine current raft machine
     */
    public RequestVoteRPC(RaftMachine raftMachine) {
        this.raftMachine = raftMachine;
    }

    /**
     * Method called by a candidate to send a election request to all members.
     * Increments current term, and builds request body for receiver to check if vote should be given.
     */
    public void sendRequestVoteRPC(){
        numberOfVotes = new AtomicInteger( 1);
        List<Thread> replicationThreads = new ArrayList<>();
        int newTerm = raftMachine.getCurrentTerm().incrementAndGet();
        System.out.println("[C] Sending election request for term: " + newTerm);
        for(NodeInfo info : raftMachine.getRaftMembersCopy()) {
            if (info.getCandidateId() != raftMachine.getCandidateId()) {
                JSONObject obj = new JSONObject();
                obj.put("term", newTerm);
                obj.put("candidateId", raftMachine.getCandidateId());
                obj.put("lastLogIndex", raftMachine.getLastAppliedIndex());
                obj.put("lastLogTerm", raftMachine.getLastAppliedTerm());

                Thread t = new Thread(new RequestVoteThread(info.getIp(), info.getPort(), raftMachine.getRequestVotePath(), obj.toJSONString(), this.numberOfVotes));
                t.start();
                replicationThreads.add(t);
            }

            for (Thread t : replicationThreads) {
                try {
                    t.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        System.out.println("[C] Number of votes received: " + numberOfVotes.intValue());
        float res = (float)numberOfVotes.intValue() / (float)raftMachine.getMemberListSize();
        System.out.println("[C] Received " + res * 100 + "% of the votes");
        // Check that candidate received a majority of the votes
        if(res > 0.5){
            System.out.println("[C] I'm now the leader for term: " + newTerm);
            raftMachine.setAsTermLeader();
        } else{
            System.out.println("[C] Did not get enough votes to become leader, reset back to follower state...");
            raftMachine.setAsFollower();
        }
    }

    /**
     * Method used to determine if a vote should be given or not.
     * @param requestData Data received from leader
     * @return JSON formatted string with response
     */
    public synchronized String validateVoteRequest(JSONObject requestData){
        JSONObject respObj = new JSONObject();
        if(raftMachine.isTermLeader()){
            raftMachine.setAsFollower();
        }
        raftMachine.resetTimer(); // Reset timer to make sure that the current node can't start an election before the new leader can send append entry RPC
        System.out.println("[F] Got election request for term: " + requestData.get("term"));
        if(Integer.valueOf(requestData.get("term").toString()) > raftMachine.getCurrentTerm().intValue()) {
            if(Integer.valueOf(requestData.get("lastLogTerm").toString()) >= raftMachine.getLastAppliedTerm()){
                if(Integer.valueOf(requestData.get("lastLogTerm").toString()) == raftMachine.getLastAppliedTerm()){
                    if(Integer.valueOf(requestData.get("lastLogIndex").toString()) >= raftMachine.getLastCommitIndex()){
                        System.out.println("[F] Last log index from candidate is equal or greater than mine..");
                        raftMachine.updateTerm(Integer.valueOf(requestData.get("term").toString()));
                        respObj.put("voteGranted", true);
                    }else{
                        System.out.println("[F] Same term for last applied log, but lower last applied index");
                        respObj.put("voteGranted", false);
                    }
                }else{
                    System.out.println("[F] Candidates last applied log term was greater than mine");
                    raftMachine.updateTerm(Integer.valueOf(requestData.get("term").toString()));
                    respObj.put("voteGranted", true);
                }
            }else{
                System.out.println("[F] Candidates last applied log term was lower than mine");
                respObj.put("voteGranted", false);
            }
        }else {
            System.out.println("[F] Candidates term was lower or equal to my current term");
            respObj.put("voteGranted", false);
        }
        System.out.println("[F] Vote granted: " + respObj.get("voteGranted"));

        respObj.put("term", raftMachine.getCurrentTerm());
        return respObj.toJSONString();
    }
    /**
     * Method that takes a HTTPServletRequest body and makes it into a json
     * @param request vote request
     * @return JSON formatted string response
     */
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

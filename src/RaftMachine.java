import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

enum MachineState {
    FOLLOWER, LEADER, CANDIDATE;
}

public class RaftMachine {
    private AtomicInteger currentTerm;
    private int lastCommitIndex;
    private int lastAppliedIndex;
    private int lastAppliedTerm;
    private List<NodeInfo> raftMembers;
    private int candidateId;
    private volatile boolean timeoutSwitch;
    private String leaderHost;
    private int leaderPort;
    private  int leaderId;
    private MachineState machineState;

    public RaftMachine(int candidateId){
        this.currentTerm = new AtomicInteger(0);
        this.lastAppliedIndex = 0;
        this.lastCommitIndex = 0;
        this.lastAppliedTerm = 0;
        this.candidateId = candidateId;
        this.raftMembers = new ArrayList<>();
    }

    public AtomicInteger getCurrentTerm() {
        return this.currentTerm;
    }



    public boolean getTimeoutSwitch() {
        return this.timeoutSwitch;
    }

    public int getLeaderId() {
        return this.leaderId;
    }

    public void updateLeaderInfo(String leaderHost, int leaderPort, int leaderId){
        this.leaderHost = leaderHost;
        this.leaderPort = leaderPort;
        this.leaderId = leaderId;
    }//TODO FIX THIS

    public void resetTimer(){
        this.timeoutSwitch = true;
    }

    public void newHeartBeat(){
        this.timeoutSwitch = false;
    }
    public void setAsTermLeader(){
        System.out.println("Setting myself to leader state");
        this.machineState = MachineState.LEADER;
        Thread t = new Thread(new AppendEntriesBuilderThread(this));
        t.start();
    }

    public void setAsFollower(){
        System.out.println("Setting myself to follower state");
        this.machineState = MachineState.FOLLOWER;
        Thread t = new Thread(new TimeoutThread(this));
        t.start();
    }

    public void setAsCandidate(){
        System.out.println("Setting myself to candidate state");
        this.machineState = MachineState.CANDIDATE;
        RequestVoteRPC requestVoteRPC = new RequestVoteRPC(this);
        requestVoteRPC.sendRequestVoteRPC();
    }

    public boolean isTermLeader() {
        if(this.machineState.equals(MachineState.LEADER)){
            return true;
        }
        return false;
    }

    public MachineState getMachineState(){
        return this.machineState;
    }

    public int getCommitIndex() {
        return this.lastCommitIndex;
    }

    public int getLastAppliedIndex() {
        return this.lastAppliedIndex;
    }

    public int getLastCommitIndex() {
        return lastCommitIndex;
    }

    public void updateTerm(int term){
        this.currentTerm.set(term);
    }

    public boolean compareAndUpdateTerm(int respTerm){
        if(respTerm > currentTerm.intValue()){
            this.currentTerm.set(respTerm);
            return true;
        }
        return false;
    }

    public int getLastAppliedTerm() {
        return lastAppliedTerm;
    }

    //public RaftLog getRaftLog() {
       // return this.raftLog;
    //}

    public synchronized List<NodeInfo> getRaftMembersCopy() {
        return new ArrayList<>(this.raftMembers);
    }

    public synchronized void addRaftMember(String host, int port, int candidateId){
        this.raftMembers.add(new NodeInfo(host, port, candidateId));
    }

    public int getMemberListSize(){
        return this.raftMembers.size();
    }


    public int getCandidateId() {
        return this.candidateId;
    }

    public int registerNode(String memberHost, int memberPort, String host, int port, String path){
        JSONObject obj = new JSONObject();
        obj.put("host", host);
        obj.put("port", port);
        ServiceHelper serviceHelper = new ServiceHelper();
       return serviceHelper.sendPostRequest(memberHost, memberPort, path, obj.toJSONString());
    }

    public String buildEntryRPCResponse(JSONObject obj) {
        AppendEntriesRPC appendEntriesRPC = new AppendEntriesRPC(this);
        return appendEntriesRPC.buildResponse(obj);
    }
}

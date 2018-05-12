import org.json.simple.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
enum MachineState {
    FOLLOWER, LEADER, CANDIDATE;
}

public class RaftMachine {
    private AtomicInteger currentTerm;
    private int lastCommitIndex;
    private int lastAppliedIndex;
    private int lastAppliedTerm;
    private AtomicInteger nextEntryIndex;
    private List<NodeInfo> raftMembers;
    private int candidateId;
    private volatile boolean timeoutSwitch;
    private String leaderHost;
    private int leaderPort;
    private  int leaderId;
    private List<LogEntry> machineLog;
    private MachineState machineState;

    public RaftMachine(int candidateId){
        this.currentTerm = new AtomicInteger(0);
        this.lastAppliedIndex = -1;
        this.lastCommitIndex = -1;
        this.lastAppliedTerm = 0;
        this.nextEntryIndex = new AtomicInteger(0);
        this.candidateId = candidateId;
        this.raftMembers = new ArrayList<>();
        this.machineLog = new ArrayList<>();
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

    public void updateLeaderInfo(int newLeaderId){
        System.out.println("Updating leader..");
        for(NodeInfo info : this.getRaftMembersCopy()){
            if(info.getCandidateId() == newLeaderId){
                this.leaderHost = info.getIp();
                this.leaderPort = info.getPort();
                this.leaderId = info.getCandidateId();
                System.out.println("Leader updated to " + this.leaderHost+":"+ this.leaderPort);
            }
        }

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
    public void incrementLastCommitted(){
        this.lastCommitIndex++;
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

    public synchronized boolean appendEntryToLog(JSONObject obj, int dataTerm, int prevLogIndex, int lastAppliedTerm){
        LogEntry newEntry =  new LogEntry(prevLogIndex + 1, dataTerm, obj, lastAppliedTerm, prevLogIndex);
        this.machineLog.add(newEntry);
        this.lastAppliedTerm = dataTerm;
        this.lastAppliedIndex = prevLogIndex + 1;
        System.out.println("Added entry with index: " + this.lastAppliedIndex + " and term " + dataTerm);
        return true;
    }

    private synchronized LogEntry createNewLogEntry(JSONObject obj){
        LogEntry newEntry =  new LogEntry(this.nextEntryIndex.intValue(), getCurrentTerm().intValue(), obj, lastAppliedTerm, lastAppliedIndex);
        this.machineLog.add(newEntry);
        System.out.println("Added entry with index: " + this.nextEntryIndex + " and term " + getCurrentTerm().intValue() + " prev index "+ lastAppliedIndex);
        //Updated value for next entry
        newEntry.getSuccessReplication().incrementAndGet();
        this.lastAppliedIndex = this.nextEntryIndex.intValue();
        this.lastAppliedTerm = getCurrentTerm().intValue();
        this.nextEntryIndex.incrementAndGet();
        return newEntry;
    }

    public boolean addDataToLog(JSONObject obj) throws InterruptedException {
        //TODO check if leader and redirect ok
        LogEntry newEntry = createNewLogEntry(obj);
        System.out.println("Start waiting");
        newEntry.getNotifier().await();
        System.out.println("Finished waiting..");


        return true;
    }

    public LogEntry getLogEntry(int index){
        for(LogEntry entry : this.machineLog){
            if((entry.getEntryIndex() == index)){
                return entry;
            }
        }
        return null;
    }
}




























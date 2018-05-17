import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**@see java.lang.Enum
 * To set the state for the raft machine
 */
enum MachineState {
    FOLLOWER, LEADER, CANDIDATE
}

/**
 * @author gudbrandschistad
 * Main class for raft. This class controls most opperations for raft see further doc.
 */
public class RaftMachine {
    private AtomicInteger currentTerm;
    private volatile int lastCommitIndex;
    private volatile int lastAppliedIndex;
    private volatile int lastAppliedTerm;
    private AtomicInteger nextEntryIndex;
    private List<NodeInfo> raftMembers;
    private int candidateId;
    private volatile boolean timeoutSwitch;
    private String leaderHost;
    private int leaderPort;
    private  int leaderId;
    private List<LogEntry> machineLog;
    private MachineState machineState;
    private JSONArray committedEntries;
    private String storageFile;
    private String requestVotePath;
    private String appendEntryPath;

    /**
     * @param appendEntryPath Path that append entries must to
     * @param candidateId Id of the current node
     * @param latch Countdown latch, too make server wait for raft machine to read persistent storage before starting
     * @param requestVotePath path that request votes must be sent too.
     * @param storageFile Name of persistent storage file.
     */
    public RaftMachine(int candidateId, String storageFile, CountDownLatch latch, String requestVotePath, String appendEntryPath){
        this.currentTerm = new AtomicInteger(0);
        this.lastAppliedIndex = -1;
        this.lastCommitIndex = -1;
        this.lastAppliedTerm = 0;
        this.nextEntryIndex = new AtomicInteger(0);
        this.candidateId = candidateId;
        this.raftMembers = new ArrayList<>();
        this.machineLog = new ArrayList<>();
        this.committedEntries = new JSONArray();
        this.storageFile = storageFile;
        this.requestVotePath = requestVotePath;
        this.appendEntryPath = appendEntryPath;
        checkIfFileExist(latch);
    }

    /**
     * @return Atomic integer with current term number
     */
    public AtomicInteger getCurrentTerm() {
        return this.currentTerm;
    }

    /**
     * @return Boolean value used by followers to start an election
     */
    public boolean getTimeoutSwitch() {
        return this.timeoutSwitch;
    }

    /**
     * @return Id of current term leader
     */
    public int getLeaderId() {
        return this.leaderId;
    }

    /**
     * @param newLeaderId ID for the term leader node to update to
     * Updates the the information about the term leader.
     */
    public void updateLeaderInfo(int newLeaderId){
        System.out.println("[F] Updating leader..");
        for (NodeInfo info : this.getRaftMembersCopy()) {
            if (info.getCandidateId() == newLeaderId) {
                this.leaderHost = info.getIp();
                this.leaderPort = info.getPort();
                this.leaderId = info.getCandidateId();
                System.out.println("[F] Leader updated to " + this.leaderHost + ":" + this.leaderPort);
            }
        }
    }

    /**
     * @return path of where append entries must be sent
     */
    public String getAppendEntryPath() {
        return this.appendEntryPath;
    }

    /**
     * @return path of where request vote requests must be sent
     */
    public String getRequestVotePath(){
        return this.requestVotePath;
    }

    /**
     * Method that must be called each time a append entry RPC is received,
     * to reset the timeout timer.
     */
    public void resetTimer(){
        this.timeoutSwitch = true;
    }

    /**
     * Method that resets the timeout switch
     */
    public void newHeartBeat(){
        this.timeoutSwitch = false;
    }

    /**
     * Method used to add members to the raft membership list
     * @param candidateId ID of the node.
     * @param port Port for the node
     * @param host IP of the node
     *
     */
    public synchronized void addRaftMember(String host, int port, int candidateId){
        this.raftMembers.add(new NodeInfo(host, port, candidateId));
    }

    /**
     * Method used to change the raft state to a LEADER
     * Start sending append entry RPC using
     * @see AppendEntriesBuilderThread
     */
    public void setAsTermLeader(){
        if (this.getMachineState() != MachineState.LEADER) {
            System.out.println("[F]Setting myself to leader state...");
            this.machineState = MachineState.LEADER;
            Thread t = new Thread(new AppendEntriesBuilderThread(this));
            t.start();
        }
    }

    /**
     * Method used to change the raft state to a FOLLOWER
     * Starts timeout thread the make sure leader is alive.
     * @see TimeoutThread
     */
    public synchronized void setAsFollower() {
        if (this.getMachineState() != MachineState.FOLLOWER){
            System.out.println("[C/L]Setting myself to follower state...");
            this.machineState = MachineState.FOLLOWER;
            Thread t = new Thread(new TimeoutThread(this));
            t.start();
        }
    }

    /**
     * Method used to change the raft state to a CANDIDATE.
     * Sends a request vote RPC to all raft members.
     * @see TimeoutThread
     */
    public synchronized void setAsCandidate() {
        if (this.getMachineState() != MachineState.CANDIDATE){
            System.out.println("[F] Setting myself to candidate state...");
            this.machineState = MachineState.CANDIDATE;

            RequestVoteRPC requestVoteRPC = new RequestVoteRPC(this);
            requestVoteRPC.sendRequestVoteRPC();
        }
    }

    /**
     * @return True if node state is term leader, else false.*/
    public boolean isTermLeader() {
        if(this.machineState.equals(MachineState.LEADER)){
            return true;
        }
        return false;
    }

    /**
     * @return Current machine state
     */
    public MachineState getMachineState(){
        return this.machineState;
    }

    /**
     * @return Last applied log index
     */
    public int getLastAppliedIndex() {
        return this.lastAppliedIndex;
    }

    /**
     * @return Last committed log index
     */
    public int getLastCommitIndex() {
        return this.lastCommitIndex;
    }

    /**
     * Increment last committed value
     */
    public void incrementLastCommitted(){
        this.lastCommitIndex++;
    }

    /**
     * Synchronized method to make sure that only one thread can update the term at the time.
     * Also checks that the new term is greater than the current term, to make sure that the highest known term always is applied.
     * @param term Term to update to.
     * @return True if term was updated.
     */
    public synchronized boolean updateTerm(int term){
        if(term > currentTerm.intValue()){
            this.currentTerm.set(term);
            System.out.println("[F] Updating to term: " + term);
            return true;
        }
        return false;
    }

    public synchronized void appendEntryTermUpdate(int term, int candidateId){
        if(updateTerm(term)) {
            updateLeaderInfo(candidateId);
        }
    }

    public int getLastAppliedTerm() {
        return this.lastAppliedTerm;
    }

    public synchronized List<NodeInfo> getRaftMembersCopy() {
        return new ArrayList<>(this.raftMembers);
    }

    public int getMemberListSize(){
        return this.raftMembers.size();
    }


    public int getCandidateId() {
        return this.candidateId;
    }

    /**
     * Method used handle append entry RPC from leader.
     * @return JSON format string with RPC response
     * @see AppendEntriesRPC
     */
    public String buildEntryRPCResponse(JSONObject obj) {
        AppendEntriesRPC appendEntriesRPC = new AppendEntriesRPC(this);
        return appendEntriesRPC.buildAppendEntriesResp(obj);
    }

    /**
     * Method used by secondaries to add data received in append entry RPC
     * Method is synchronized to make sure all values are updated in order.
     */
    public synchronized boolean appendEntryToLog(JSONObject obj, int dataTerm, int prevLogIndex, int lastAppliedTerm){
        //TODO CHECK IF LEADER and redirect
        LogEntry newEntry =  new LogEntry(prevLogIndex + 1, dataTerm, obj, lastAppliedTerm, prevLogIndex);
        this.machineLog.add(newEntry);
        this.lastAppliedTerm = dataTerm;
        this.lastAppliedIndex = prevLogIndex + 1;
        this.nextEntryIndex.incrementAndGet();

        System.out.println("[F] Appended log entry with index: " + this.lastAppliedIndex + " and term " + dataTerm);
        return true;
    }

    /**
     * Method used to create a new log entry
     * @param obj data for the log entry
     * @return LogEntry object
     */
    private synchronized LogEntry createNewLogEntry(JSONObject obj){
        LogEntry newEntry =  new LogEntry(this.nextEntryIndex.intValue(), getCurrentTerm().intValue(), obj, lastAppliedTerm, lastAppliedIndex);
        this.machineLog.add(newEntry);
        System.out.println("[L] Added entry with index: " + this.nextEntryIndex + " and term " + getCurrentTerm().intValue() + " prev index "+ lastAppliedIndex);
        //Updated value for next entry
        newEntry.getSuccessReplication().incrementAndGet();
        this.lastAppliedIndex = this.nextEntryIndex.intValue();
        this.lastAppliedTerm = getCurrentTerm().intValue();
        this.nextEntryIndex.incrementAndGet();
        return newEntry;
    }

    /**
     * Method used by leader to create and add a new log entry.
     * @param obj data to be stored with entry
     */
    public JSONObject addDataToLog(JSONObject obj)  {
        //TODO check if leader and redirect ok
        JSONObject resp = new JSONObject();
        if(this.isTermLeader()) {
            LogEntry newEntry = createNewLogEntry(obj);
            System.out.println("[L] Adding entry to log, waiting for it to be committed...");
            try {
                newEntry.getNotifier().await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            System.out.println("[L] Entry committed, respond back to client..");
            resp.put("success", true);
        }
        else{
            System.out.println("[F] Unable to add log entry, node is not term leader");
            resp.put("success", true);
            resp.put("host", leaderHost);
            resp.put("port",leaderPort);
        }
        return resp;
    }

    /**
     * Method to check and get a log entry
     * @param index of the log entry
     * @return If there are no log entry with the index, then return null*/
    public LogEntry getLogEntry(int index){
        for(LogEntry entry : this.machineLog){
            if((entry.getEntryIndex() == index)){
                return entry;
            }
        }
        return null;
    }

    /**
     * Method used to commit all log entries received in leaders append entry RPC commit index
     * Method is called by secondaries.
     * @param leaderCommitIndex The last committed index number received from the leader */
    public void commitLogEntries(int leaderCommitIndex){
        System.out.println("[F] Committing all entries to index: " + leaderCommitIndex);
        for(LogEntry logEntry: machineLog){
            if((logEntry.getEntryIndex() <= leaderCommitIndex) && (!logEntry.isCommited())){
                writeCommittedEntryToFile(logEntry);
                logEntry.setCommited();
                System.out.println("[F] Committed index: " + logEntry.getEntryIndex());
                if (logEntry.getEntryIndex() > this.lastCommitIndex) {
                    this.lastCommitIndex = logEntry.getEntryIndex();
                }
            }
        }
    }

    /**
     * Method used to set the next append index for all nodes when state is changed to leader.
     */
    public void updateAllNodesAppendEntryIndex(){
        for(NodeInfo info : this.raftMembers){
            if(info.getCandidateId() != this.candidateId) {
                if (lastAppliedIndex == -1) {
                    info.setAppendIndex(0);
                } else {
                    info.setAppendIndex(lastAppliedIndex + 1);
                }
            }
        }
    }

    /**Method that builds a json object to store in the persistent storage array */
    private JSONObject buildStorageObject(LogEntry entry){
        JSONObject obj = new JSONObject();
        obj.put("data", entry.getData());
        obj.put("index", entry.getEntryIndex());
        obj.put("term", entry.getEntryTerm());
        obj.put("prevlogindex",entry.getPrevLogIndex());
        obj.put("prevlogterm", entry.getPrevLogTerm());
        return obj;
    }

    /**
     * Write all committed log entries to persistent storage.
     * Method takes a new log entry, and adds it to the committed entries json array,
     * then writes it to persistent storage
     */
    public synchronized void writeCommittedEntryToFile(LogEntry entry){
        this.committedEntries.add(buildStorageObject(entry));
        JSONObject writeData = new JSONObject();
        System.out.println("[L/F] Writing committed entries to file : " + this.storageFile);
        writeData.put("storagedata",this.committedEntries);
        File test = new File(this.storageFile);
        FileWriter f2 = null;
        try {
            f2 = new FileWriter(test, false);
            f2.write(writeData.toJSONString());
            f2.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    /**
     * Method called by the constructor to check if there exist a persistent storage file, to read data from.
     * @param latch to make sure server do not start before all data is read.
     */
    private boolean checkIfFileExist(CountDownLatch latch){
        if(Files.exists(Paths.get(storageFile))) {
            System.out.println("[X]Found persistent storage file, adding committed data...");
            readAndAddData();
            latch.countDown();
            return true;
        }
        System.out.println("[X]No persistent storage file...");
        latch.countDown();
        return false;
    }

    /**
     * Method that reads the persistent storage file, and adds data to inn memory data storage.
     * Also updates all related variables to make sure it do not overwrite committed entries.
     */
    private void readAndAddData(){
        JSONParser parser = new JSONParser();

        try {
            Object obj = parser.parse(new FileReader(storageFile));
            JSONObject jsonObject =  (JSONObject) obj;
            JSONArray storagedata = (JSONArray) jsonObject.get("storagedata");
            Iterator<JSONObject> iterator = storagedata.iterator();
            while (iterator.hasNext()) {
                JSONObject entry = iterator.next();
                int index = Integer.valueOf(entry.get("index").toString());
                int term = Integer.valueOf(entry.get("term").toString());
                JSONObject data =  (JSONObject) entry.get("data");
                int prevlogterm = Integer.valueOf(entry.get("prevlogterm").toString());
                int prevlogindex = Integer.valueOf(entry.get("prevlogindex").toString());
                System.out.println("[X]Adding index: "+ index + " term " + term);
                LogEntry readEntry = new LogEntry(index, term, data, prevlogterm, prevlogindex);
                readEntry.setCommited();
                addReadData(readEntry);
            }

            this.nextEntryIndex.set(this.lastAppliedIndex + 1);
            System.out.println("-------------------------------");
            System.out.println("Last committed:  " + this.lastCommitIndex);
            System.out.println("Last applied term: " + this.lastAppliedTerm);
            System.out.println("Last applied index: " + this.lastAppliedIndex);
            System.out.println("Next entry index: " + this.nextEntryIndex.intValue());
            System.out.println("-------------------------------");

        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    /**
     * Method used to add a new entry to inn memory data storage.
     */
    private void addReadData(LogEntry entry){
        if(entry.getEntryIndex() > this.lastAppliedIndex){
            this.lastAppliedIndex = entry.getEntryIndex();
            System.out.println("[X] Last applied index updated to: " + this.lastAppliedIndex);
        }

        if(entry.getEntryTerm() > this.lastAppliedTerm){
            this.lastAppliedTerm = entry.getEntryTerm();
            System.out.println("[X] Last applied term updated to: " + this.lastAppliedTerm);

        }

        if(this.lastCommitIndex < entry.getEntryIndex()){
            this.lastCommitIndex =  entry.getEntryIndex();
            System.out.println("[X] Last commited updated to: " + this.lastCommitIndex);
        }
        if(currentTerm.intValue() < entry.getEntryTerm()){
            currentTerm.set(entry.getEntryTerm());
        }

        this.committedEntries.add(buildStorageObject(entry));
        this.machineLog.add(entry);
    }
}
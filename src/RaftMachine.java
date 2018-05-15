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

enum MachineState {
    FOLLOWER, LEADER, CANDIDATE
}

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

    public RaftMachine(int candidateId, String storageFile, CountDownLatch latch){
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
        checkIfFileExist(latch);
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
        System.out.println("Setting myself to leader state...");
        this.machineState = MachineState.LEADER;

        Thread t = new Thread(new AppendEntriesBuilderThread(this));
        t.start();
    }

    public void setAsFollower(){
        System.out.println("Setting myself to follower state...");
        this.machineState = MachineState.FOLLOWER;
        Thread t = new Thread(new TimeoutThread(this));
        t.start();
    }

    public void setAsCandidate(){
        System.out.println("Setting myself to candidate state...");
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
        //TODO CHECK IF LEADER and redirect
        LogEntry newEntry =  new LogEntry(prevLogIndex + 1, dataTerm, obj, lastAppliedTerm, prevLogIndex);
        this.machineLog.add(newEntry);
        this.lastAppliedTerm = dataTerm;
        this.lastAppliedIndex = prevLogIndex + 1;
        this.nextEntryIndex.incrementAndGet();

        System.out.println("[F] Added entry with index: " + this.lastAppliedIndex + " and term " + dataTerm);
        return true;
    }

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

    public boolean addDataToLog(JSONObject obj) throws InterruptedException {
        //TODO check if leader and redirect ok
        LogEntry newEntry = createNewLogEntry(obj);
        System.out.println("[L] Adding entry to log, waiting for it to be committed...");
        newEntry.getNotifier().await();
        System.out.println("[L] Entry committed, respond back to client..");

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

    public void commitLogEntries(int leaderCommitIndex){
        System.out.println("Committing all entries to index: " + leaderCommitIndex);
        for(LogEntry logEntry: machineLog){
            if(logEntry.getEntryIndex() <= leaderCommitIndex){
                if(!logEntry.isCommited()) {
                    writeCommittedEntriesToFile(logEntry);
                    logEntry.setCommited();
                    if (logEntry.getEntryIndex() > this.lastCommitIndex) {
                        this.lastCommitIndex = logEntry.getEntryIndex();
                        System.out.println("Committed index: " + logEntry.getEntryIndex());
                    }
                }
            }
        }
    }

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
    private JSONObject buildStorageObject(LogEntry entry){
        JSONObject obj = new JSONObject();
        obj.put("data", entry.getData());
        obj.put("index", entry.getEntryIndex());
        obj.put("term", entry.getEntryTerm());
        obj.put("prevlogindex",entry.getPrevLogIndex());
        obj.put("prevlogterm", entry.getPrevLogTerm());
        return obj;
    }

    public synchronized void writeCommittedEntriesToFile(LogEntry entry){
        this.committedEntries.add(buildStorageObject(entry));
        JSONObject writeData = new JSONObject();
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

    private boolean checkIfFileExist(CountDownLatch latch){
        if(Files.exists(Paths.get(storageFile))) {
            System.out.println("Data existing, adding committed data..");
            readAndAddData();
            latch.countDown();
            return true;
        }
        System.out.println("No saved data to add...");
        latch.countDown();
        return false;
    }
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
                System.out.println("Adding index: "+ index + " term " + term);
                LogEntry readEntry = new LogEntry(index, term, data, prevlogterm, prevlogindex);
                readEntry.setCommited();
                addReadData(readEntry);
            }
            this.nextEntryIndex.set(this.lastAppliedIndex + 1);
            System.out.println("-------------------------------");
            System.out.println("Last commited:  " + this.lastCommitIndex);
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

    private void addReadData(LogEntry entry){
        if(entry.getEntryIndex() > this.lastAppliedIndex){
            this.lastAppliedIndex = entry.getEntryIndex();
            System.out.println("Last applied index updated to: " + this.lastAppliedIndex);

        }

        if(entry.getEntryTerm() > this.lastAppliedTerm){
            this.lastAppliedTerm = entry.getEntryTerm();
            System.out.println("last applied term updated to: " + this.lastAppliedTerm);

        }

        if(this.lastCommitIndex < entry.getEntryIndex()){
            this.lastCommitIndex =  entry.getEntryIndex();
            System.out.println("last commited updated to: " + this.lastCommitIndex);
        }
        this.committedEntries.add(buildStorageObject(entry));
        this.machineLog.add(entry);
    }
}




























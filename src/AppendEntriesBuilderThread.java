import java.util.List;
import java.util.concurrent.TimeUnit;
/**
 * @author gudbrandschistad
 * Class that impliments runnable, and is used by a term leader to replicate data, and
 * commit data.
 */
public class AppendEntriesBuilderThread implements Runnable {
    private RaftMachine raftMachine;


    /** Constructor
     * @param raftMachine instance of the nodes raft machine.
     * Calls update all nodes entry index to set it equal to nodes last applied index.
     */
    public AppendEntriesBuilderThread(RaftMachine raftMachine) {
        this.raftMachine = raftMachine;
        this.raftMachine.updateAllNodesAppendEntryIndex();

    }

    /** Running thread for a leader node.
     * This thread will automatically start when a not state is set to LEADER
     * The thread has two responsibilities:
     * 1. Check if the next entry index should be committed.
     * 2. For each raft member node, send a append entry RPC
     */
    @Override
    public void run() {
        System.out.println("[L] Started sending append entry RPC's..");
        List<NodeInfo> nodeInfoListCopy = raftMachine.getRaftMembersCopy();
        while (raftMachine.isTermLeader()){
            //Check if there is a log entry with index greater than last committed entry.
            LogEntry nextCommitEntry = raftMachine.getLogEntry(raftMachine.getLastCommitIndex() + 1);
            if(nextCommitEntry != null){
                //Check that the log entry have been replicated on a majority of the nodes.
                if((float)nextCommitEntry.getSuccessReplication().intValue()/(float)nodeInfoListCopy.size() > 0.5) {
                    raftMachine.writeCommittedEntryToFile(nextCommitEntry);
                    //Add entry to json array, and write to file
                    nextCommitEntry.setCommited();
                    raftMachine.incrementLastCommitted();
                    System.out.println("[L] Committed entry with index: " + raftMachine.getLastCommitIndex());
                }
            }

            try {
                TimeUnit.MILLISECONDS.sleep(800);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if(nodeInfoListCopy.size() != raftMachine.getMemberListSize()){
                nodeInfoListCopy = raftMachine.getRaftMembersCopy();
            }

            for(NodeInfo info : nodeInfoListCopy) {
                if(info.getCandidateId() != raftMachine.getCandidateId()) {
                    Thread t = new Thread(new AppendEntrySenderThread(info, raftMachine));
                    t.start();
                }
            }
        }
        System.out.println("[L] Stop sending RPC. Im no longer the leader");
    }
}

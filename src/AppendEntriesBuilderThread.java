import org.json.simple.JSONObject;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class AppendEntriesBuilderThread implements Runnable {
    private RaftMachine raftMachine;

    /** Constructor */
    public AppendEntriesBuilderThread(RaftMachine raftMachine) {
        this.raftMachine = raftMachine;
        this.raftMachine.updateAllNodesAppendEntryIndex();

    }

    /** Run method that sends a write request to a secondary */
    @Override
    public void run() {
        System.out.println("Started sending append entry RPC's as leader ");
        List<NodeInfo> nodeInfoListCopy = raftMachine.getRaftMembersCopy();
        String path = "/appendentry";
        while (raftMachine.isTermLeader()){

            System.out.println("Last commit index: " + raftMachine.getLastCommitIndex());
            LogEntry nextCommitEntry = raftMachine.getLogEntry(raftMachine.getLastCommitIndex() + 1);
            if(nextCommitEntry != null){
                System.out.println("successes: " + nextCommitEntry.getSuccessReplication().intValue());
                System.out.println("res: " + (float)nextCommitEntry.getSuccessReplication().intValue()/(float)nodeInfoListCopy.size());

                if((float)nextCommitEntry.getSuccessReplication().intValue()/(float)nodeInfoListCopy.size() > 0.5) {
                    nextCommitEntry.setCommited();
                    raftMachine.incrementLastCommitted();
                    System.out.println("Committed new value");
                }
            }

            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }//TODO REMOVE

            if(nodeInfoListCopy.size() != raftMachine.getMemberListSize()){
                nodeInfoListCopy = raftMachine.getRaftMembersCopy();
            }

            for(NodeInfo info : nodeInfoListCopy) {
                if(info.getCandidateId() != raftMachine.getCandidateId()) {
                    //TODO make sure data is applied at follower
                    Thread t = new Thread(new AppendEntrySenderThread(info, path, raftMachine));
                    t.start();
                    //TODO Check resp is ok and term is not greater
                }
            }
            //Join all threads.
            //Check
        }
        System.out.println("Stop sending RPC. Im no longer the leader");
    }

}

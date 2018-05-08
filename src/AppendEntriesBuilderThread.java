import org.json.simple.JSONObject;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class AppendEntriesBuilderThread implements Runnable {
    private RaftMachine raftMachine;

    /** Constructor */
    public AppendEntriesBuilderThread(RaftMachine raftMachine) {
        this.raftMachine = raftMachine;

    }

    /** Run method that sends a write request to a secondary */
    @Override
    public void run() {
        System.out.println("Started sending append entry RPC's as leader ");
        List<NodeInfo> nodeInfoListCopy = raftMachine.getRaftMembersCopy();
        String path = "/appendentry";
        while (raftMachine.isTermLeader()){
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
                    JSONObject obj = new JSONObject();
                    obj.put("term", raftMachine.getCurrentTerm().intValue());
                    obj.put("candidateId", raftMachine.getCandidateId());
                    obj.put("prevLogIndex", 0);
                    obj.put("prevLogTerm", 0);
                    obj.put("leaderCommit", raftMachine.getCommitIndex());
                    Thread t = new Thread(new AppendEntrySenderThread(info.getIp(), info.getPort(), path, obj.toJSONString(), raftMachine));
                    t.start();
                    //TODO Check resp is ok and term is not greater
                }
            }
        }
        System.out.println("Stop sending RPC. Im no longer the leader");
    }
}

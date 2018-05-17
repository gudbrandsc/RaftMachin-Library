import java.util.concurrent.TimeUnit;

/**
 * @author gudbrandschistad
 * Runnable class that checks if the term leader is alive.
 */
public class TimeoutThread implements Runnable {
    private RaftMachine raftMachine;

    /**
     * Constructor
     * @param raftMachine current raft machine
     */
    public TimeoutThread(RaftMachine raftMachine) {
        this.raftMachine = raftMachine;

    }

    /**
     * Run method that checks if a append RPC have been received, using the timeout switch.
     * If a append entry RPC is not received by the time interval, then set to CANDIDATE state
     * else flip heartbeat switch off
     */
    @Override
    public void run() {
        System.out.println("[F] Starting timeout thread...");
        this.raftMachine.resetTimer();
        try {
            TimeUnit.SECONDS.sleep(2); //Wait for server to start up
            while(this.raftMachine.getMachineState().equals(MachineState.FOLLOWER)){
              int random = 1 + (int)(Math.random() * ((1 - 2) + 1));
                TimeUnit.SECONDS.sleep(random);

                if(!this.raftMachine.getTimeoutSwitch()){
                    System.out.println("[F] Leader timed out, starting election for new term..." );
                    this.raftMachine.setAsCandidate();
                }else{
                    this.raftMachine.newHeartBeat();
                }
            }
        } catch (InterruptedException e) {
            System.out.println("Follower fail");
            e.printStackTrace();
        }
    }
}

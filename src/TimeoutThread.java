import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TimeoutThread implements Runnable {
    private RaftMachine raftMachine;

    /**
     * Constructor
     */
    public TimeoutThread(RaftMachine raftMachine) {
        this.raftMachine = raftMachine;

    }

    /**
     * Run method that sends a write request to a secondary
     */
    @Override
    public void run() {
        System.out.println("inside timeout thread");
        this.raftMachine.resetTimer();
        try {
            TimeUnit.SECONDS.sleep(2); //Wait for server to start up
            while(this.raftMachine.getMachineState().equals(MachineState.FOLLOWER)){
              int random = 5 + (int)(Math.random() * ((5 - 10) + 1));
                TimeUnit.SECONDS.sleep(random);

                if(!this.raftMachine.getTimeoutSwitch()){
                    this.raftMachine.setAsCandidate();
                    System.out.println("Leader timed out, starting election for new term..." );
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

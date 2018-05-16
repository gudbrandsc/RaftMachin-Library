import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

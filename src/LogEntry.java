import org.json.simple.JSONObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class LogEntry {
    private int entryIndex;
    private int entryTerm;
    private JSONObject data;
    private boolean isCommitted;
    private int prevLogTerm;
    private int prevLogIndex;
    private AtomicInteger successReplication;
    private CountDownLatch notifier;

    public LogEntry(int entryIndex, int entryTerm, JSONObject data, int prevLogTerm, int prevLogIndex) {
        this.entryIndex = entryIndex;
        this.entryTerm = entryTerm;
        this.data = data;
        this.isCommitted = false;
        this.prevLogTerm = prevLogTerm;
        this.prevLogIndex = prevLogIndex;
        this.successReplication = new AtomicInteger(0);
        this.notifier = new CountDownLatch(1);
    }

    public int getEntryIndex() {
        return this.entryIndex;
    }


    public CountDownLatch getNotifier() {
        return this.notifier;
    }

    public int getEntryTerm() {
        return this.entryTerm;
    }

    public JSONObject getData() {
        return this.data;
    }

    public boolean isCommited(){
        return this.isCommitted;
    }

    public void setCommited(){
        this.isCommitted = true;
        this.notifier.countDown();
    }

    public boolean isCommitted() {
        return isCommitted;
    }

    public int getPrevLogTerm() {
        return prevLogTerm;
    }

    public int getPrevLogIndex() {
        return prevLogIndex;
    }

    public AtomicInteger getSuccessReplication() {
        return successReplication;
    }
}

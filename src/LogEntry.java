import org.json.simple.JSONObject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author gudbrandschistad
 * Class used to create a log entry.
 */
public class LogEntry {
    private int entryIndex;
    private int entryTerm;
    private JSONObject data;
    private boolean isCommitted;
    private int prevLogTerm;
    private int prevLogIndex;
    private AtomicInteger successReplication;
    private CountDownLatch notifier;

    /**Constructor
     * @param data entry data
     * @param entryTerm term entry is added
     * @param entryIndex log index number for entry
     * @param prevLogIndex index for the log entry added before current entry
     * @param prevLogTerm term for the log entry added before current entry
     */
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
    /**
     * @return Entry index for log entry
     */
    public int getEntryIndex() {
        return this.entryIndex;
    }

    /**
     * Countdown latch used to hold a response back to client until
     * the entry is committed.
     * @return Countdown latch for log entry. */
    public CountDownLatch getNotifier() {
        return this.notifier;
    }

    /**
     * @return The term the entry was added
     */
    public int getEntryTerm() {
        return this.entryTerm;
    }

    /**
     * @return Json object with the stored data for the entry
     */
    public JSONObject getData() {
        return this.data;
    }

    /**
     * @return True if the entry is committed
     */
    public boolean isCommited(){
        return this.isCommitted;
    }

    /**
     * Set a log entry as committed and countdown the latch to
     * notify all waiting for the current entry to be committed
     */
    public void setCommited(){
        this.isCommitted = true;
        this.notifier.countDown();
    }

    /**
     * @return The term of the log entry added before this entry.
     */
    public int getPrevLogTerm() {
        return prevLogTerm;
    }

    /**
     * @return The index of the log entry added before this entry.
     */
    public int getPrevLogIndex() {
        return prevLogIndex;
    }

    /**
     * @return Atomic integer with number of successful replications for this entry.
     */
    public AtomicInteger getSuccessReplication() {
        return successReplication;
    }
}

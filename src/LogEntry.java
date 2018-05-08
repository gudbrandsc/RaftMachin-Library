public class LogEntry {
    private int entryIndex;
    private int entryTerm;
    private Object data;
    private boolean isCommitted;

    public LogEntry(int entryIndex, int entryTerm, Object data) {
        this.entryIndex = entryIndex;
        this.entryTerm = entryTerm;
        this.data = data;
        isCommitted = false;
    }

    public int getEntryIndex() {
        return entryIndex;
    }

    public int getEntryTerm() {
        return entryTerm;
    }

    public Object getData() {
        return data;
    }

    public boolean isCommited(){
        return this.isCommitted;
    }

    public void setCommited(){
        this.isCommitted = true;
    }
}

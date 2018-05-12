import java.util.concurrent.atomic.AtomicInteger;

public class NodeInfo {
    private String ip;
    private int port;
    private int candidateId;
    private AtomicInteger appendIndex;

    public NodeInfo(String ip, int port, int candidateId){
        this.ip = ip;
        this.port = port;
        this.candidateId = candidateId;
        this.appendIndex = new AtomicInteger(0);
    }

    public String getIp() {
        return this.ip;
    }

    public int getPort() {
        return this.port;
    }

    public int getCandidateId() {
        return this.candidateId;
    }

    public AtomicInteger getAppendIndex() {
        return appendIndex;
    }
    public void setAppendIndex(int i){
        this.appendIndex.set(i);
    }
}

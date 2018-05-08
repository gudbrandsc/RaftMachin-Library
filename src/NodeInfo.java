public class NodeInfo {
    private String ip;
    private int port;
    private int candidateId;

    public NodeInfo(String ip, int port, int candidateId){
        this.ip = ip;
        this.port = port;
        this.candidateId = candidateId;
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
}

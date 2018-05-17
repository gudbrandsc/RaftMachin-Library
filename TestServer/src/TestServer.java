import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CountDownLatch;


public class TestServer {
    public static void main(String[] args) {
        int candidateId = Integer.valueOf(args[2].toString());
        String requestVotePath = "/requestvote";
        String appendEntryPath = "/appendentry";
        CountDownLatch latch = new CountDownLatch(1);
        RaftMachine raftMachine = new RaftMachine(Integer.valueOf(args[2]), args[3], latch, requestVotePath, appendEntryPath);

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        raftMachine.addRaftMember("localhost",4400,1);
        raftMachine.addRaftMember("localhost",4444,2);
        raftMachine.addRaftMember("localhost",4445,3);
        raftMachine.addRaftMember("localhost",4446,4);
        raftMachine.addRaftMember("localhost",4447,5);

        int port = Integer.valueOf(args[0]);
        Server server = new Server(port);
        ServletHandler handler = new ServletHandler();
        server.setHandler(handler);
        handler.addServletWithMapping(new ServletHolder(new HelloServlet(raftMachine)), "/*");
        handler.addServletWithMapping(new ServletHolder(new VoteReceiver(raftMachine)),  requestVotePath);
        handler.addServletWithMapping(new ServletHolder(new AppendRecieve(raftMachine)), appendEntryPath);

        if(args[1].equals("1")){
            System.out.println("Im leader");
            raftMachine.setAsTermLeader();
        }else {
            String host = null;
            try {
                host = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                System.out.println("hoho");
            }
           // raftMachine.registerNode("localhost", 4400, "localhost",port , "/register");
            raftMachine.setAsFollower();

        }

        try {
            server.start();
            server.join();
        }
        catch (Exception ex) {
            System.exit(-1);
        }

    }
}

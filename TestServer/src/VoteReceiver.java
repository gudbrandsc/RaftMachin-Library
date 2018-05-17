import org.eclipse.jetty.http.HttpStatus;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class VoteReceiver extends HttpServlet {


    private RaftMachine raftMachine;

    public VoteReceiver(RaftMachine raftMachine){
        this.raftMachine = raftMachine;

    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        RequestVoteRPC requestVoteRPC = new RequestVoteRPC(raftMachine);
        String  obj = requestVoteRPC.validateVoteRequest(requestVoteRPC.getJsonFromRequest(req));

        try(PrintWriter pw = resp.getWriter()) {
           pw.write(obj);
       }
        resp.setStatus(HttpStatus.OK_200);
    }
}

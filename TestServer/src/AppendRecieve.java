
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class AppendRecieve  extends HttpServlet{


    private RaftMachine raftMachine;
    private FrontServiceHelper frontServiceHelper;

    public AppendRecieve(RaftMachine raftMachine){
        this.raftMachine = raftMachine;
        this.frontServiceHelper = new FrontServiceHelper();

    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        raftMachine.resetTimer();
        ServiceHelper helper = new ServiceHelper();

        JSONObject obj = helper.stringToJsonObject(helper.requestToString(req));
        PrintWriter pw = resp.getWriter();
        String hola = raftMachine.buildEntryRPCResponse(obj);
        pw.write(hola);
        pw.flush();
        pw.close();
    }
}

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicInteger;

public class HelloServlet extends HttpServlet {
    private RaftMachine raftMachine;

    public HelloServlet(RaftMachine raftMachine){
        this.raftMachine = raftMachine;

    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        ServiceHelper helper = new ServiceHelper();
        JSONObject obj = helper.stringToJsonObject(helper.requestToString(req));
        JSONObject raftResp = raftMachine.addDataToLog(obj);
            if(raftResp.get("success").equals(true)){
                resp.setStatus(200);
            }else{
                System.out.println("Node is not leader request rejected. Leader is : " + raftResp.get("host") + ":" + raftResp.get("port"));
                resp.setStatus(401);

            }


    }
}



import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;



public class SenderThread implements Runnable {
    private String host;
    private int port;

    public SenderThread(String host, String port) {
        this.host = host;
        this.port = Integer.valueOf(port);
    }

    /** Run method that sends a write request to a secondary */
    @Override
    public void run() {
        String body = "{\"name\":\"Gudbrand\",\"age\":26}";
        sendPostRequestAndReturnRespData(host, port, body );
    }


    /**
     * Method that sends a post request and returns the http response
     * @return http response status
     */
    public void sendPostRequestAndReturnRespData(String host, int port, String body) {
        try {
            String url = "http://" + host + ":" + port;
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setDoOutput(true);
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-type", "application/json");
            OutputStreamWriter wr =  new OutputStreamWriter(con.getOutputStream());
            wr.write(body);
            wr.flush();
            wr.close();
            if(con.getResponseCode() != 200) { //TODO remove
                System.out.println("fail");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

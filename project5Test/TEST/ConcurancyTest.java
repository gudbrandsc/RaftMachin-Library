import java.util.ArrayList;
import java.util.List;

public class ConcurancyTest {
    public static void main(String[] args) {
		for(int i = 0; i < 50; i++){
			Thread t = new Thread(new SenderThread(args[0],args[1]));
			t.start();
		}
	}
}
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Subordinate {

    private Socket coordinatorSocket;
    private BufferedReader reader;
    private OutputStreamWriter writer;

    private Subordinate(Socket socket) throws IOException {
        this.coordinatorSocket = socket;
        this.reader = new BufferedReader(new InputStreamReader(coordinatorSocket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new OutputStreamWriter(coordinatorSocket.getOutputStream(), StandardCharsets.UTF_8);
    }

    private Socket getCoordinatorSocket() {
        return coordinatorSocket;
    }

    private String receive() throws IOException {

        return this.reader.readLine();

    }

    private void send(String msg) throws IOException {

        this.writer.write(msg + "\n");
        this.writer.flush();

    }

    private void initiate() throws IOException {

        this.send("Hello, this is a subordinate!");
        System.out.println(receive());
        System.out.println("My coordinator is at: " + this.getCoordinatorSocket().getPort());
        this.phaseOne();

    }

    private void phaseOne() throws IOException {

        System.out.println(this.receive());
        this.send("YES");
        this.getCoordinatorSocket().close();

    }

    public static void main(String[] args) throws IOException {

        // Trying to connect with coordinator. TODO: Repeat this several times, until a connection has been established.
        Socket coordinatorSocket = new Socket("localhost", 8080);

        Subordinate subordinate = new Subordinate(coordinatorSocket);
        subordinate.initiate();

        coordinatorSocket.close();

    }
}

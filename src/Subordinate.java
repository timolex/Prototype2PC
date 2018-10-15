import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Subordinate {

    //public static Socket coordinatorSocket;
    private Socket coordinatorSocket;

    public Subordinate(Socket socket) {
        this.coordinatorSocket = socket;
    }

    public Socket getCoordinatorSocket() {
        return coordinatorSocket;
    }

    public void setCoordinatorSocket(Socket coordinatorSocket) {
        this.coordinatorSocket = coordinatorSocket;
    }



    public static void main(String[] args) throws IOException, InterruptedException {

        // Trying to connect with coordinator. TODO: Repeat this several times, until a connection has been established.
        Socket coordinatorSocket = new Socket("localhost", 8080);

        //OutputStreamWriter writer = new OutputStreamWriter(coordinatorSocket.getOutputStream(), StandardCharsets.UTF_8);

        //writer.write("Hello, this is a subordinate!\n");
        //writer.flush();

        //send(coordinatorSocket, "Hello, this is a subordinate!\n");

        //System.out.println(receive(coordinatorSocket));
        //System.out.println("My coordinator is at: " + coordinatorSocket.getPort());


        Subordinate subordinate = new Subordinate(coordinatorSocket);
        subordinate.initiate();



        coordinatorSocket.close();
    }

    public static String receive(Socket coordinatorSocket) throws IOException, InterruptedException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(coordinatorSocket.getInputStream(), StandardCharsets.UTF_8));
        String line = reader.readLine();
        return line;
    }

    public static void send(Socket coordinatorSocket, String msg) throws IOException {
        OutputStreamWriter writer = new OutputStreamWriter(coordinatorSocket.getOutputStream(), StandardCharsets.UTF_8);
        writer.write(msg + "\n");
        writer.flush();
    }

    private void initiate() throws IOException, InterruptedException {
        send(this.getCoordinatorSocket(), "Hello, this is a subordinate!\n");
        System.out.println(receive(this.getCoordinatorSocket()));
        System.out.println("My coordinator is at: " + this.getCoordinatorSocket().getPort());
        //this.phaseOne();

    }

    private void phaseOne() throws IOException, InterruptedException {

        System.out.println(receive(this.getCoordinatorSocket()));
        this.getCoordinatorSocket().close();

    }
}

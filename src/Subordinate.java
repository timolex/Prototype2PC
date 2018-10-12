import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Subordinate {

    public static void main(String[] args) throws IOException {
        // Trying to connect with coordinator. TODO: Repeat this several times, until a connection has been established.
        Socket coordinatorSocket = new Socket("localhost", 8080);

        OutputStreamWriter writer = new OutputStreamWriter(coordinatorSocket.getOutputStream(), StandardCharsets.UTF_8);
        BufferedReader reader = new BufferedReader(new InputStreamReader(coordinatorSocket.getInputStream(), StandardCharsets.UTF_8));

        writer.write("Hello, this is a subordinate!\n");
        writer.flush();

        System.out.println(reader.readLine());
        System.out.println("My coordinator is at: " + coordinatorSocket.getPort());

        coordinatorSocket.close();
    }
}

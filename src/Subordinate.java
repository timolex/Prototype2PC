import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Subordinate {

    public static void main(String[] args) throws IOException {
        // Trying to connect with coordinator. TODO: Repeat this several times, until a connection has been established.
        Socket socket = new Socket("localhost", 8080);

        OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

        writer.write("Hello, this is a subordinate! \n");
        writer.flush();

        // TODO: Find out how we can pause this (repeat it in a while loop perhaps?) until an actual msg arrives.
        // problem: If not all subordinates are running yet, this causes trouble, as no message arrives.
        System.out.println(reader.readLine());

        socket.close();
    }
}

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class Coordinator {

    // TODO: Eventually we could accept program arguments here to set the max. no of subordinates.
    private static final int MAX_SUBORDINATES = 2;
    private static int subordinateCounter = 0;

    private static ArrayList<Socket> sockets = new ArrayList<>(MAX_SUBORDINATES);

    public static void main (String[] args) throws IOException {

        ServerSocket serverSocket = new ServerSocket(8080);
        Coordinator coordinator = new Coordinator();

        try {
            while (subordinateCounter < MAX_SUBORDINATES) {
            Socket socket = serverSocket.accept();
            addSubordinateSocket(socket);
            }
            coordinator.doStuff();
        } finally {
            serverSocket.close();
        }

    }

    private static void addSubordinateSocket(Socket socket) {
        sockets.add(socket);
        subordinateCounter++;
        System.out.println("added Socket of subordinate w/ port " + socket.getPort() + ".");

    }

    private void doStuff() throws IOException {
        for (Socket socket : sockets){

            OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            System.out.println(reader.readLine());

            writer.write("Hello, this is your coordinator speaking! You are subordinate at port number " + socket.getPort() + ".");
            writer.flush();

            socket.close();

        }

    }
}

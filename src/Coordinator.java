import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class Coordinator {

    private static final int MAX_SUBORDINATES = 2;
    private static int subordinateCounter = 0;

    private static ArrayList<Socket> sockets = new ArrayList<>(MAX_SUBORDINATES);

    public static void main (String[] args) throws IOException {

        ServerSocket serverSocket = new ServerSocket(8080);
        Coordinator coordinator = new Coordinator();

        try {
            // TODO: Change this to while (subordinateCounter < MAX_SUBORDINATES-1)
            while (subordinateCounter < MAX_SUBORDINATES-1) {
            Socket socket = serverSocket.accept();
            addSubordinateSocket(socket);
            serverSocket.close();
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
        OutputStreamWriter writer = new OutputStreamWriter(sockets.get(0).getOutputStream(), StandardCharsets.UTF_8);
        BufferedReader reader = new BufferedReader(new InputStreamReader(sockets.get(0).getInputStream(), StandardCharsets.UTF_8));

        System.out.println(reader.readLine());

        writer.write("Hello, this is your coordinator speaking!");
        writer.flush();

        // Do this after every possible end of the 2PC protocol
        for (Socket socket : sockets){
            socket.close();
        }
    }
}

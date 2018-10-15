import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class Coordinator {

    // TODO: Eventually we could accept program arguments in main to set the max. no of subordinates here.
    private static final int MAX_SUBORDINATES = 2;
    private static int subordinateCounter = 0;

    private ArrayList<Socket> sockets;
    private ArrayList<OutputStreamWriter> writers = new ArrayList<>();
    private ArrayList<BufferedReader> readers = new ArrayList<>();

    private Coordinator(ArrayList<Socket> sockets) throws IOException {

        this.sockets = sockets;

        for(Socket socket : this.sockets){
            this.writers.add(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            this.readers.add(new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)));
        }

    }

    private void broadcast(String msg) throws IOException {
        for (OutputStreamWriter writer : this.writers){
            writer.write(msg + "\n");
            writer.flush();
        }
    }

    private ArrayList<String> receive() throws IOException {
        ArrayList<String> msgs = new ArrayList<>();
        for(BufferedReader reader : readers) {
            msgs.add(reader.readLine());
        }
        return msgs;
    }

    private void initiate() throws IOException {

        this.broadcast("Hello, this is your coordinator speaking!");

        for (String msg : this.receive()){
            System.out.println(msg);
        }

        this.phaseOne();

    }

    private void phaseOne() throws IOException {

        broadcast("PREPARE");
        for(String msg : this.receive()){
            System.out.println(msg);
        }

        for (Socket socket : this.sockets) {
            socket.close();
        }

    }

    public static void main (String[] args) throws IOException {

        ServerSocket serverSocket = new ServerSocket(8080);
        ArrayList<Socket> sockets = new ArrayList<>(MAX_SUBORDINATES);

        try {
            while (subordinateCounter < MAX_SUBORDINATES) {
                Socket socket = serverSocket.accept();
                sockets.add(socket);
                subordinateCounter++;
                System.out.println("Added Socket of subordinate w/ port " + socket.getPort() + ".");
            }
            Coordinator coordinator = new Coordinator(sockets);
            coordinator.initiate();
        } finally {
            serverSocket.close();
        }

    }
}

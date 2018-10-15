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


    private ArrayList<Socket> sockets = new ArrayList<>(MAX_SUBORDINATES);

    public Coordinator(ArrayList<Socket> sockets) {
        this.sockets = sockets;
    }

    public ArrayList<Socket> getSockets() {
        return sockets;
    }


    public static void main (String[] args) throws IOException {

        ServerSocket serverSocket = new ServerSocket(8080);


        ArrayList<Socket> sockets = new ArrayList<>(MAX_SUBORDINATES);

        try {
            while (subordinateCounter < MAX_SUBORDINATES) {
            Socket socket = serverSocket.accept();
            sockets.add(socket);
            subordinateCounter++;
            System.out.println("added Socket of subordinate w/ port " + socket.getPort() + ".");

            }
            Coordinator coordinator = new Coordinator(sockets);
            coordinator.initiate();
        } finally {
            serverSocket.close();
        }

    }


    public static void broadcast(ArrayList<Socket> sockets, String msg) throws IOException {
        for (Socket socket : sockets){

            OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            System.out.println(reader.readLine());

            writer.write(msg + "\n");
            writer.flush();

        }
    }

    private void initiate() throws IOException {

        broadcast(this.getSockets(), "Hello, this is your coordinator speaking!");

        //this.phaseOne();

    }

    //TODO: Find out, why this isn't working properly...
    private void phaseOne() throws IOException {

        broadcast(this.getSockets(), "PREPARE");

        for (Socket socket : this.getSockets()) {
            socket.close();
        }

    }
}

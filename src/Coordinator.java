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
        System.out.println("Message broadcast to subordinates: " + "\"" + msg + "\"\n");
    }

    private ArrayList<String> receive() throws IOException {
        int i=1;
        ArrayList<String> msgs = new ArrayList<>();

        for(BufferedReader reader : readers) {

            String msg = reader.readLine();
            msgs.add(msg);
            System.out.println("S" + i + ": " + "\"" + msg + "\"");
            i++;

        }

        System.out.println();

        return msgs;
    }

    private void initiate() throws IOException {

        // this.broadcast("Hello, this is your coordinator speaking!");

        /*for (String msg : this.receive()){
            System.out.println(msg);
        }*/

        this.phaseOne();

    }


    private void phaseOne() throws IOException {

        System.out.println("=============== START OF PHASE 1 ===============");

        boolean decision = true;
        boolean phaseOneFailure = false;

        broadcast("PREPARE");

        ArrayList<String> votes;
        votes = this.receive();

        for(String msg : votes){
            if(msg.equals("NO")) decision = false;
            if(msg.equals("")) {
                decision = false;
                phaseOneFailure = true;
            }
        }

        if(decision) System.out.println("Phase 1 decision: COMMIT (all subordinates answered w/ YES VOTES)");
        if(phaseOneFailure) System.out.println("Phase 1 decision: ABORT (one or more subordinates did not vote)");
        if(!decision && !phaseOneFailure) System.out.println("Phase 1 decision: ABORT (one or more subordinates answered w/ NO VOTES)");

        System.out.println("=============== END OF PHASE 1 =================\n");

        //TODO: Always move this to the last step of the protocol.
        for (Socket socket : this.sockets) {
            socket.close();
        }

    }

    public static void main (String[] args) throws IOException {

        ServerSocket serverSocket = new ServerSocket(8080);
        ArrayList<Socket> sockets = new ArrayList<>(MAX_SUBORDINATES);

        try {
            System.out.println("\nCoordinator-Socket established, waiting for " + MAX_SUBORDINATES + " subordinates to connect...\n");
            while (subordinateCounter < MAX_SUBORDINATES) {
                Socket socket = serverSocket.accept();
                sockets.add(socket);
                subordinateCounter++;
                System.out.println("Added Socket for subordinate " + "S" + subordinateCounter + " @ port " + socket.getPort() + ".");
            }
            System.out.println("\n");
            Coordinator coordinator = new Coordinator(sockets);
            coordinator.initiate();
        } finally {
            serverSocket.close();
        }

    }
}

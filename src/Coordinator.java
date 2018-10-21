import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Coordinator {

    private List<Socket> sockets;
    private List<OutputStreamWriter> writers = new ArrayList<>();
    private List<BufferedReader> readers = new ArrayList<>();

    private Coordinator(List<Socket> sockets) throws IOException {

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

    private List<String> receive() throws IOException {
        int i=1;
        List<String> msgs = new ArrayList<>();

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
        boolean illegalAnswer = false;

        this.broadcast("PREPARE");

        List<String> votes;
        votes = this.receive();

        for(String msg : votes){
            if(msg.equals("NO")) decision = false;
            if(msg.equals("")) {
                decision = false;
                phaseOneFailure = true;
            } else if (!msg.equals("YES") && !msg.equals("NO") && !msg.equals("")){
                illegalAnswer = true;
            }
        }

        if(!illegalAnswer) {
            if(phaseOneFailure) System.out.println("Phase 1 decision: ABORT (one or more subordinates did not vote)");
            if(decision) System.out.println("Phase 1 decision: COMMIT (all subordinates answered w/ 'YES' VOTES)");
            if(!decision && !phaseOneFailure) System.out.println("Phase 1 decision: ABORT (one or more subordinates answered w/ 'NO' VOTES)");

            System.out.println("=============== END OF PHASE 1 =================\n");

            this.phaseTwo(decision);
        } else {
            for (Socket socket : this.sockets) {
                socket.close();
            }
            throw new IOException("Illegal vote received from a subordinate");
        }

    }

    private void phaseTwo(boolean decision) throws IOException {

        System.out.println("\n=============== START OF PHASE 2 ===============");

        if(decision){
           this.broadcast("COMMIT");
        } else {
            this.broadcast("ABORT");
        }

        //TODO: Check acknowledgements
        this.receive();
        //List<String> acknowledgements = this.receive();

        System.out.println("=============== END OF PHASE 2 =================\n");

        //TODO: Always move this to the last step of the protocol.
        for (Socket socket : this.sockets) {
            socket.close();
        }
    }

    private static void printHelp(){
        System.out.println("USAGE\n=====\n arguments:\n  - Coordinator -S [NO_OF_SUBORDINATES]");
    }

    public static void main (String[] args) throws IOException {

        if(args.length == 0) {
            printHelp();
        } else if(args[0].equals("-S")){
            if(args[1] != null && Integer.parseInt(args[1]) > 0) {

                int subordinateCounter = 0;
                int maxSubordinates = Integer.parseInt(args[1]);

                ServerSocket serverSocket = new ServerSocket(8080);
                List<Socket> sockets = new ArrayList<>(maxSubordinates);

                try {
                    System.out.println("\nCoordinator-Socket established, waiting for " + maxSubordinates + " subordinates to connect...\n");
                    while (subordinateCounter < maxSubordinates) {
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
        } else printHelp();

    }
}

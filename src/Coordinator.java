import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Coordinator {

    private List<Socket> sockets;
    private List<OutputStreamWriter> writers = new ArrayList<>();
    private List<BufferedReader> readers = new ArrayList<>();
    private Scanner scanner;
    private Logger logger;


    private Coordinator(List<Socket> sockets) throws IOException {

        this.sockets = sockets;

        for(Socket socket : this.sockets){
            this.writers.add(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            this.readers.add(new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)));
        }

    }

    private void broadcast(String msg, boolean verbosely) throws IOException {
        for (OutputStreamWriter writer : this.writers){
            writer.write(msg + "\n");
            writer.flush();
        }

        if(verbosely){
            System.out.println("Message broadcast to subordinates: " + "\"" + msg + "\"\n");
        }
    }

    private void send(int index, String msg, boolean verbosely) throws IOException {
        OutputStreamWriter writer = this.writers.get(index);
        writer.write(msg + "\n");
        writer.flush();

        if(verbosely) {
            System.out.println("Message sent to S" + (index+1) + ": " + "\"" + msg + "\"");
        }
    }

    private List<String> receive() throws IOException {
        int i=1;
        List<String> msgs = new ArrayList<>();

        for(BufferedReader reader : readers) {

            String msg = reader.readLine();
            msgs.add(msg);
            switch (msg) {
                case ("Y"): {
                    System.out.println("S" + i + ": " + "\"YES\"");
                    break;
                }
                case ("N"): {
                    System.out.println("S" + i + ": " + "\"NO\"");
                    break;
                }
                case ("") : {
                    System.out.println("S" + i + ": " + "[No message received]");
                    break;
                }
                default: {
                    System.out.println("S" + i + ": " + "\"" + msg + "\"");
                    break;
                }
            }
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
        boolean phaseOneSubordinateFailure = false;
        boolean phaseOneCoordinatorFailure = false;
        boolean illegalAnswer = false;
        this.scanner = new Scanner(System.in);

        System.out.println("If you wish to let this subordinate fail at this stage, please enter 'f': ");

        if(scanner.nextLine().toUpperCase().equals("F")) phaseOneCoordinatorFailure = true;

        if(phaseOneCoordinatorFailure) {

            this.broadcast("PREPARE", true);
            this.broadcast("COORDINATOR_FAILURE", false);
            System.out.println("=============== COORDINATOR CRASHES =================\n");

        } else {

            this.broadcast("PREPARE", true);
            this.broadcast("", false);

            List<String> votes;
            votes = this.receive();
            int i = 0;

            for(String msg : votes){
                if(msg.equals("N")) decision = false;
                if(msg.equals("")) {
                    // Here, any not responding Subordinates are removed. Please note, that the Subordinate's indices
                    // might change accordingly (e.g., if S2 fails, S3 'inherits' index 2 in the receive method.
                    this.readers.remove(i);
                    this.writers.remove(i);
                    this.sockets.get(i).close();
                    this.sockets.remove(i);
                    decision = false;
                    phaseOneSubordinateFailure = true;
                } else if (!msg.equals("Y") && !msg.equals("N") && !msg.equals("")){
                    illegalAnswer = true;
                }
                i ++;
            }

            if(!illegalAnswer) {
                if(phaseOneSubordinateFailure) System.out.println("Phase 1 decision: ABORT (one or more subordinates did not vote)");
                if(decision) System.out.println("Phase 1 decision: COMMIT (all subordinates answered w/ 'YES' VOTES)");
                if(!decision && !phaseOneSubordinateFailure) System.out.println("Phase 1 decision: ABORT (one or more subordinates answered w/ 'NO' VOTES)");

                this.logger = new Logger("/tmp/CoordinatorLog.txt", "Coordinator");
                if(decision) {
                    logger.log("COMMIT", true);
                } else {
                    logger.log("ABORT", true);
                }

                System.out.println("=============== END OF PHASE 1 =================\n");

                this.phaseTwo(decision);
            } else {
                for (Socket socket : this.sockets) {
                    socket.close();
                }
                throw new IOException("Illegal vote received from a subordinate");
            }
        }
    }

    private void phaseTwo(boolean decision) throws IOException {

        System.out.println("\n=============== START OF PHASE 2 ===============");

        if(decision){
           this.broadcast("COMMIT", true);
        } else {
            this.broadcast("ABORT", true);
        }

        //TODO: Check acknowledgments several times, if still some subordinates do not answer with ACKs
        this.checkAcknowledgements();
    }

    private void checkAcknowledgements() throws IOException {
        List<String> acknowledgements = this.receive();
        boolean subordinateFailure = false;
        boolean invalidAcknowledgement = false;
        int count = 0;
        List<Integer> crashedSubordinateIndices = new ArrayList<>();

        for(String ack : acknowledgements) {
            if (ack.equals("")) {
                subordinateFailure = true;
                crashedSubordinateIndices.add(count);
            } else if (!ack.equals("ACK")) {
                invalidAcknowledgement = true;
            }
            count++;
        }

        if(subordinateFailure && !invalidAcknowledgement) {
            System.out.println("Subordinate crash(es) detected!\nHanding transaction over to recovery process...\n");
            this.recoveryProcess(crashedSubordinateIndices);
        } else if (invalidAcknowledgement) {
            throw new IOException("Illegal acknowledgement received from a subordinate");
        } else {
            logger.log("END", false);
            System.out.println("=============== END OF PHASE 2 =================\n");
        }
    }

    private void recoveryProcess(List<Integer> crashedSubordinateIndices) throws IOException {
        System.out.println("\n=============== START OF RECOVERY PROCESS ===============");

        String decision = logger.readLog().split(" ")[0];

        for(Integer index : crashedSubordinateIndices) {
            switch (decision) {
                case "COMMIT":
                case "ABORT":
                    this.send(index, decision, true);
                    break;
                case "":
                    this.send(index, "ABORT", true);
                    break;
                default:
                    throw new IOException("Illegal log entry: " + decision);
            }
        }

        logger.log("END", false);
        System.out.println("=============== END RECOVERY PROCESS =================\n");
        System.out.println("=============== END OF PHASE 2 =================\n");
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
                    for (Socket socket : sockets) {
                        socket.close();
                    }
                }

            }
        } else printHelp();

    }
}

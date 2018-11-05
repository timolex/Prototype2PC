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
    private String loggedDecision;
    private boolean recoveryProcessStarted;


    private Coordinator(List<Socket> sockets) throws IOException {

        this.sockets = sockets;

        for(Socket socket : this.sockets){

            this.writers.add(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            this.readers.add(new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)));

        }

        this.scanner = new Scanner(System.in);
        this.logger = new Logger("/tmp/CoordinatorLog.txt", "Coordinator");
        this.loggedDecision = "";
        this.recoveryProcessStarted = false;

    }

    private void broadcast(String msg, boolean verbosely) throws IOException {
        for (OutputStreamWriter writer : this.writers){
            writer.write(msg + "\n");
            writer.flush();
        }

        switch (msg) {

            case "" :

                if(verbosely){

                    System.out.println("[No message broadcast to subordinates]\n");

                }
                break;

            default:

                if(verbosely){

                    System.out.println("Message broadcast to subordinates: " + "\"" + msg + "\"\n");

                }
                break;

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

        for(BufferedReader reader : this.readers) {

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

    private String receiveSingle(int index) throws IOException {

        String msg = this.readers.get(index).readLine();

        switch (msg) {

            case "":
                System.out.println("S" + (index+1) + ": " + "[No message received]");
                break;

            default:
                System.out.println("S" + (index+1) + ": " + "\"" + msg + "\"");
                break;

        }

        return msg;
    }

    private void initiate() throws IOException {

        // this.broadcast("Hello, this is your coordinator speaking!");

        /*for (String msg : this.receive()){
            System.out.println(msg);
        }*/

        this.phaseOne();

    }


    private void phaseOne() throws IOException {

        Printer.print("=============== START OF PHASE 1 ===============", "blue");

        boolean decision = true;
        boolean phaseOneSubordinateFailure = false;
        boolean illegalAnswer = false;

        System.out.print("If you wish to let the coordinator fail after broadcasting \"PREPARE\", please enter 'f': ");


        if(scanner.nextLine().toUpperCase().equals("F")) {

            Printer.print("", "");
            this.broadcast("PREPARE", true);
            this.broadcast("COORDINATOR_FAILURE", false);
            Printer.print("=============== COORDINATOR CRASHES =================\n", "red");

        } else {

            Printer.print("", "white");
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

                if(phaseOneSubordinateFailure) System.out.println("Phase 1 decision: ABORT (one or more subordinates did" +
                        " not vote)");

                if(decision) System.out.println("Phase 1 decision: COMMIT (all subordinates answered w/ 'YES' VOTES)");

                if(!decision && !phaseOneSubordinateFailure) System.out.println("Phase 1 decision: ABORT (one or more " +
                        "subordinates answered w/ 'NO' VOTES)");

                if(decision) {

                    logger.log("COMMIT", true);

                } else {

                    logger.log("ABORT", true);

                }

                Printer.print("=============== END OF PHASE 1 =================\n", "blue");

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

        Printer.print("\n=============== START OF PHASE 2 ===============", "green");

        System.out.print("If you wish to let the coordinator fail at this stage, please enter 'f': ");

        if (this.scanner.nextLine().toUpperCase().equals("F")) {

            this.broadcast("", false);
            this.broadcast("COORDINATOR_FAILURE", false);
            Printer.print("=============== COORDINATOR CRASHES =================\n", "red");


        } else {

            Printer.print("", "white");

            if(decision){

                this.broadcast("COMMIT", true);
                this.broadcast("", false);

            } else {

                this.broadcast("ABORT", true);
                this.broadcast("", false);

            }

            List<Integer> allSubordinates = new ArrayList<>();

            for(int i = 0; i < this.sockets.size(); i++) allSubordinates.add(i);

            this.checkAcknowledgements(allSubordinates);
        }

    }

    private void checkAcknowledgements(List<Integer> subordinatesToBeChecked) throws IOException {

        List<String> acknowledgements = new ArrayList<>();

        for(int subordinateIndex : subordinatesToBeChecked) {

            acknowledgements.add(this.receiveSingle(subordinateIndex));

        }

        boolean subordinateFailure = false;
        boolean invalidAcknowledgement = false;
        int count = 0;
        List<Integer> crashedSubordinateIndices = new ArrayList<>();

        for(String ack : acknowledgements) {

            if (ack.equals("")) {

                subordinateFailure = true;
                crashedSubordinateIndices.add(subordinatesToBeChecked.get(count));

            } else if (!ack.equals("ACK") && !ack.equals("")) {

                invalidAcknowledgement = true;

            }

            count++;

        }

        if(subordinateFailure && !invalidAcknowledgement) {

            System.out.println("\nSubordinate crash(es) detected!\n");

            if(!this.recoveryProcessStarted) System.out.println("Handing transaction over to recovery process...");

            this.recoveryProcess(crashedSubordinateIndices);

        } else if (invalidAcknowledgement) {

            throw new IOException("Illegal acknowledgement received from a subordinate");

        } else {

            logger.log("END", false);
            if(this.recoveryProcessStarted) Printer.print("=============== END OF RECOVERY PROCESS =================\n", "orange");

            Printer.print("=============== END OF PHASE 2 =================\n", "green");

        }

    }

    private void recoveryProcess(List<Integer> crashedSubordinateIndices) throws IOException {

        if (!this.recoveryProcessStarted) Printer.print("\n=============== START OF RECOVERY PROCESS ===============", "orange");

        this.recoveryProcessStarted = true;

        if (loggedDecision.isEmpty()) this.loggedDecision = logger.readLog().split(" ")[0];

        for(Integer index : crashedSubordinateIndices) {

            switch (loggedDecision) {

                case "COMMIT":
                case "ABORT":

                    this.send(index, loggedDecision, true);
                    this.send(index, "", false);
                    break;

                case "":

                    this.send(index, "ABORT", true);
                    this.send(index, "", false);
                    break;

                default:

                    throw new IOException("Illegal log entry: " + loggedDecision);

            }
        }

        this.checkAcknowledgements(crashedSubordinateIndices);

    }

    private static void printHelp(){

        System.out.println("USAGE\n=====\n arguments:\n  - Coordinator -S [NO_OF_SUBORDINATES]");

    }

    public static void main (String[] args) throws IOException {

        if((args.length == 2) && (args[0].equals("-S")) && (Integer.parseInt(args[1]) > 0)) {

            int subordinateCounter = 0;
            int maxSubordinates = Integer.parseInt(args[1]);

            ServerSocket serverSocket = new ServerSocket(8080);
            List<Socket> sockets = new ArrayList<>(maxSubordinates);

            try {

                System.out.println("\nCoordinator-socket established, waiting for " + maxSubordinates + " subordinates to connect...\n");

                while (subordinateCounter < maxSubordinates) {

                    Socket socket = serverSocket.accept();
                    sockets.add(socket);
                    subordinateCounter++;
                    System.out.println("Added socket for subordinate " + "S" + subordinateCounter + " @ port " + socket.getPort() + ".");

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

        } else {

            printHelp();

        }

    }
}

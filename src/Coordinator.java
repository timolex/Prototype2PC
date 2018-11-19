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

    //TODO: Think about this value; How long should we wait for Subordinate's answers?
    public static final int TIMEOUT_MILLISECS = 10000;

    private List<Socket> sockets;
    private ServerSocket serverSocket;
    private List<OutputStreamWriter> writers = new ArrayList<>();
    private List<BufferedReader> readers = new ArrayList<>();
    private Scanner scanner;
    private Logger logger;
    private Logger socketLogger;
    private String loggedDecision;
    private boolean recoveryProcessStarted;


    private Coordinator(List<Socket> sockets, ServerSocket serverSocket) throws IOException {

        this.sockets = sockets;
        this.serverSocket = serverSocket;

        this.scanner = new Scanner(System.in);
        this.logger = new Logger("/tmp/CoordinatorLog.txt", "Coordinator", true);
        this.socketLogger = new Logger("/tmp/SubordinatePorts.txt", "Coordinator",true);
        this.loggedDecision = "";
        this.recoveryProcessStarted = false;


        for (Socket socket : this.sockets) {

            this.writers.add(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            this.readers.add(new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)));
            this.socketLogger.log(Integer.toString(socket.getPort()), false, false, false);

        }

    }

    private void broadcast(String msg) throws IOException {

        for (OutputStreamWriter writer : this.writers) {
            writer.write(msg + "\n");
            writer.flush();
        }

        switch (msg) {

            case "":

                System.out.println("[No message broadcast to subordinates]\n");
                break;

            default:

                System.out.println("Message broadcast to subordinates: " + "\"" + msg + "\"\n");
                break;

        }

    }

    private void send(int index, String msg) throws IOException {

        OutputStreamWriter writer = this.writers.get(index);

        writer.write(msg + "\n");
        writer.flush();

    }

    private List<String> receive(List<BufferedReader> subordinateReaders) {

        List<String> msgs = new ArrayList<>();
        List<CoordinatorReceiver> receiverThreads = new ArrayList<>();
        int subordinateIndex = 1;
        int msgCounter = 0;

        for (BufferedReader subordinateReader : subordinateReaders) {

            CoordinatorReceiver coordinatorReceiver = new CoordinatorReceiver(subordinateReader, subordinateIndex);
            coordinatorReceiver.start();
            receiverThreads.add(coordinatorReceiver);

            ++subordinateIndex;

        }

        while (msgCounter < receiverThreads.size()) {

            msgCounter = 0;

            for (CoordinatorReceiver thread : receiverThreads) {

                if (thread.isMsgYetReceived()) {
                    ++msgCounter;
                }
            }

        }

        for (CoordinatorReceiver receiverThread : receiverThreads) {

            msgs.add(receiverThread.getReceivedMessage());

        }

        return msgs;

    }

    private void phaseOne() throws IOException {

        Printer.print("=============== START OF PHASE 1 ===============", "blue");

        boolean decision = true;
        boolean phaseOneSubordinateFailure = false;
        boolean illegalAnswer = false;

        System.out.print("Please press enter to broadcast \"PREPARE\" to the subordinates: ");

        if (scanner.nextLine().toUpperCase().equals(""))  {

            Printer.print("", "white");
            this.broadcast("PREPARE");

            List<String> votes;
            votes = this.receive(this.readers);
            List<Integer> socketsToRemove = new ArrayList<>();
            int i = 0;

            for (String msg : votes) {

                if (msg.equals("N")) decision = false;

                if (msg.equals("")) {

                    socketsToRemove.add(i);

                    decision = false;
                    phaseOneSubordinateFailure = true;

                } else if (!msg.equals("Y") && !msg.equals("N") && !msg.equals("")) {

                    illegalAnswer = true;

                }

                i++;

            }

            this.removeSockets(socketsToRemove);

            if (!illegalAnswer) {

                if (phaseOneSubordinateFailure)
                    System.out.println("Phase 1 decision: ABORT (one or more subordinates did" +
                            " not vote)");

                if (decision) System.out.println("Phase 1 decision: COMMIT (all subordinates answered w/ 'YES' VOTES)");

                if (!decision && !phaseOneSubordinateFailure)
                    System.out.println("Phase 1 decision: ABORT (one or more " +
                            "subordinates answered w/ 'NO' VOTES)");

                if (decision) {

                    logger.log("COMMIT", true, true, true);

                } else {

                    logger.log("ABORT", true, true, true);

                }

                Printer.print("=============== END OF PHASE 1 =================\n", "blue");

                if (this.sockets.size() > 0) this.phaseTwo(decision);

            } else {

                for (Socket socket : this.sockets) {

                    socket.close();

                }

                throw new IOException("Illegal vote received from a subordinate");

            }

        } else {

            Printer.print("", "");
            Printer.print("=============== COORDINATOR CRASHES =================\n", "red");

        }
    }

    private void phaseTwo(boolean decision) throws IOException {

        Printer.print("\n=============== START OF PHASE 2 ===============", "green");

        long startTime = System.currentTimeMillis();
        long timeDiff = 0;
        boolean userInputPresent = false;
        String decisionMessage = decision ? "COMMIT" : "ABORT";

        System.out.print("Please press enter within " + Coordinator.TIMEOUT_MILLISECS/1000 +
                " seconds to broadcast \"" + decisionMessage + "\" to the subordinates: ");

        InputHandler inputHandler = new InputHandler(new Scanner(System.in));
        inputHandler.start();

        while(!userInputPresent && (timeDiff < Coordinator.TIMEOUT_MILLISECS)) {

            userInputPresent = inputHandler.isInputYetReceived();
            timeDiff = System.currentTimeMillis() - startTime;

            System.out.print("");

        }

        if (userInputPresent &&
                inputHandler.getUserInput().toUpperCase().equals("") &&
                (timeDiff < Coordinator.TIMEOUT_MILLISECS)) {

            Printer.print("", "white");

            this.broadcast(decisionMessage);

            List<Integer> allSubordinates = new ArrayList<>();

            for (int i = 0; i < this.sockets.size(); i++) allSubordinates.add(i);

            this.checkAcknowledgements(allSubordinates);

        } else {

            Printer.print("\n=============== COORDINATOR CRASHES =================\n", "red");

            // Terminate the program, even if System.in still blocks in InputHandler
            System.exit(0);

        }

    }

    private void checkAcknowledgements(List<Integer> subordinatesToBeChecked) throws IOException {

        List<String> acknowledgements;
        List<BufferedReader> subordinateReaders = new ArrayList<>();

        for (int subordinateIndex : subordinatesToBeChecked) {

            subordinateReaders.add(this.readers.get(subordinateIndex));

        }

        acknowledgements = this.receive(subordinateReaders);

        boolean invalidAcknowledgement = false;
        int count = 0;
        List<Integer> crashedSubordinateIndices = new ArrayList<>();

        for (String ack : acknowledgements) {

            if (ack.equals("")) {

                crashedSubordinateIndices.add(subordinatesToBeChecked.get(count));

            } else if (!ack.equals("ACK") && !ack.equals("")) {

                invalidAcknowledgement = true;

            }

            count++;

        }

        if (crashedSubordinateIndices.size() > 0 && !invalidAcknowledgement) {

            Printer.print("\nSubordinate crash(es) detected!\n", "red");

            if (!this.recoveryProcessStarted) System.out.println("Handing transaction over to recovery process...");

            this.recoveryProcess(crashedSubordinateIndices);

        } else if (invalidAcknowledgement) {

            throw new IOException("Illegal acknowledgement received from a subordinate");

        } else {

            this.logger.log("END", false, true, true);
            this.socketLogger.emptyLog();

            if (this.recoveryProcessStarted)
                Printer.print("=============== END OF RECOVERY PROCESS =================\n", "orange");

            Printer.print("=============== END OF PHASE 2 =================\n", "green");

        }

    }

    private void recoveryProcess(List<Integer> crashedSubordinateIndices) throws IOException {

        if (!this.recoveryProcessStarted)
            Printer.print("\n=============== START OF RECOVERY PROCESS ===============", "orange");

        this.recoveryProcessStarted = true;

        //Is this necessary?
        /*TODO: Time out at reAccept-method, if not all crashed subordinates reconnected. -> Return these, which did not
                reconnect and add them straight to the unreachableSubordinateIndices list and also remove them from
                the crashedSubordinateIndices list!!!
         */
        this.reAcceptCrashedSubordinates(crashedSubordinateIndices);

        if (this.loggedDecision.isEmpty()) this.loggedDecision = logger.readLog().split(" ")[0];

        boolean decisionMsgPrinted = false;
        List<Integer> unreachableSubordinatesIndices = new ArrayList<>();
        List<Integer> reachableSubordinatesIndices = new ArrayList<>();


        for (Integer index : crashedSubordinateIndices) {

            switch (loggedDecision) {

                case "COMMIT":
                case "ABORT":

                    if (!decisionMsgPrinted) Printer.print("Message sent to crashed subordinate(s): " + loggedDecision, "white");
                    decisionMsgPrinted = true;

                    break;

                case "":

                    if (!decisionMsgPrinted) Printer.print("Message sent to crashed subordinate(s): ABORT", "white");
                    decisionMsgPrinted = true;

                    break;

                default:

                    throw new IOException("Illegal log entry: " + loggedDecision);

            }

            try {

                this.send(index, loggedDecision);
                reachableSubordinatesIndices.add(index);

            } catch (IOException e) {

                unreachableSubordinatesIndices.add(index);
                Printer.print("Unable to reach S" + (index+1) + " waiting for it to reconnect...", "orange");

            }
        }


        if(unreachableSubordinatesIndices.size() > 0) this.recoveryProcess(unreachableSubordinatesIndices);

        Printer.print("", "white");

        if(reachableSubordinatesIndices.size() > 0) this.checkAcknowledgements(reachableSubordinatesIndices);

    }

    private void reAcceptCrashedSubordinates(List<Integer> crashedSubordinateIndices) throws IOException {

        //TODO: Think about this assumption: The subordinates have to reconnect in their order of index,
        // such that the coordinator knows, which subordinate is acknowledging.
        // Solution to that: Send the subordinate index to coordinator during resurrection (make this
        // method threaded. -> Is this overkill?

        int numberOfReconnectedSubordinates = 0;

        while (numberOfReconnectedSubordinates < crashedSubordinateIndices.size()) {

            int nextSocketToReplaceIndex = crashedSubordinateIndices.get(numberOfReconnectedSubordinates);

            Socket socket = this.serverSocket.accept();

            this.sockets.set(nextSocketToReplaceIndex, socket);
            this.readers.set(nextSocketToReplaceIndex, new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)));
            this.writers.set(nextSocketToReplaceIndex, new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            ++numberOfReconnectedSubordinates;

        }

        for (int i = 0; i < crashedSubordinateIndices.size(); i++) {

            this.sockets.get(crashedSubordinateIndices.get(i)).setSoTimeout(TIMEOUT_MILLISECS);

        }

    }

    private void removeSockets(List<Integer> socketsToRemove) throws IOException {

        // Here, any not responding Subordinates are removed. Please note, that the Subordinate's indices
        // might change accordingly (e.g., if S2 fails, S3 'inherits' index 2 in the receive method.
        List<Socket> newSockets = new ArrayList<>();
        List<OutputStreamWriter> newWriters = new ArrayList<>();
        List<BufferedReader> newReaders = new ArrayList<>();

        for (int count = 0; count < this.sockets.size(); count++) {

            boolean found = false;

            for (int i : socketsToRemove) {

                if (count == i) found = true;

            }

            if (found) {

                this.sockets.get(count).close();

            } else {

                newSockets.add(this.sockets.get(count));
                newWriters.add(this.writers.get(count));
                newReaders.add(this.readers.get(count));

            }

        }

        this.sockets = newSockets;
        this.writers = newWriters;
        this.readers = newReaders;

    }

    private static void printHelp() {

        System.out.println("USAGE\n=====\n arguments:\n  - Coordinator -S [NO_OF_SUBORDINATES]");

    }

    public static void main(String[] args) throws IOException {

        if ((args.length == 2) && (args[0].equals("-S")) && (Integer.parseInt(args[1]) > 0)) {

            int subordinateCounter = 0;
            int maxSubordinates = Integer.parseInt(args[1]);

            ServerSocket serverSocket = new ServerSocket(8080);
            List<Socket> sockets = new ArrayList<>(maxSubordinates);

            try {

                System.out.println("\nCoordinator-socket established, waiting for " + maxSubordinates + " subordinates to connect...\n");

                while (subordinateCounter < maxSubordinates) {

                    Socket socket = serverSocket.accept();
                    socket.setSoTimeout(TIMEOUT_MILLISECS);
                    sockets.add(socket);
                    subordinateCounter++;
                    System.out.println("Added socket for subordinate " + "S" + subordinateCounter + ".");

                }

                System.out.println("\n");

                Coordinator coordinator = new Coordinator(sockets, serverSocket);
                coordinator.phaseOne();

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

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
    public static final int TIMEOUT_MILLIS = 10000;
    public static final int SERVER_SOCKET_PORT = 8080;
    public static final String SERVER_SOCKET_HOST = "localhost";

    private List<Socket> sockets = new ArrayList<>();;
    private ServerSocket serverSocket = new ServerSocket(SERVER_SOCKET_PORT);;
    private List<OutputStreamWriter> writers = new ArrayList<>();
    private List<BufferedReader> readers = new ArrayList<>();
    private Scanner scanner = new Scanner(System.in);;
    private Logger coordinatorLog = new Logger("/tmp/CoordinatorLog.txt", "Coordinator", true);
    private Logger failedSubordinatesLog = new Logger("/tmp/FailedSubordinates.txt", "", true);
    private int maxSubordinates;
    private String loggedDecision = "";
    private boolean isRecoveryProcessStarted = false;
    private boolean isCoordinatorResurrecting = false;


    private Coordinator(int maxSubordinates) throws IOException {
        this.maxSubordinates = maxSubordinates;
    }

    private void broadcast(String msg) throws IOException {
        msg = msg + "\n";
        for (OutputStreamWriter writer : this.writers) {
            writer.write(msg);
            writer.flush();
        }

        if (msg.equals("")) {
            System.out.println("[No message broadcast to subordinates]");
        } else {
            System.out.println("Message broadcast to subordinates: " + "\"" + msg + "\"");
        }
    }

    private void send(int index, String msg) throws IOException {
        msg = msg + "\n";
        OutputStreamWriter writer = this.writers.get(index);
        writer.write(msg);
        writer.flush();
    }

    private List<String> receive(List<BufferedReader> subordinateReaders) {
        List<String> msgs = new ArrayList<>();
        List<CoordinatorReceiver> receiverThreads = new ArrayList<>();
        int subordinateIndex = 1;
        int msgCounter = 0;

        for (BufferedReader subordinateReader : subordinateReaders) {
            CoordinatorReceiver coordinatorReceiver = new CoordinatorReceiver(subordinateReader, subordinateIndex++);
            coordinatorReceiver.start();
            receiverThreads.add(coordinatorReceiver);
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

    private void acceptSubordinates() throws IOException {
        int subordinateCounter = 0;
        while (subordinateCounter < this.maxSubordinates) {
            Socket socket = this.serverSocket.accept();
            this.sockets.add(socket);
            socket.setSoTimeout(TIMEOUT_MILLIS);
            this.writers.add(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            this.readers.add(new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)));
            System.out.println("Added socket for subordinate " + "S" + subordinateCounter++ + ".\n");
        }
        this.serverSocket.close();
    }

    private void initiate() throws IOException {
        try {
            if (this.coordinatorLog.isLatestMsg("COMMIT") ||
                    this.coordinatorLog.isLatestMsg("ABORT")) {

                Printer.print("\n=============== COORDINATOR RESURRECTS =================", "red");
                this.isCoordinatorResurrecting = true;
                int numberOfPreviouslyCrashedSubordinates;

                if (!this.failedSubordinatesLog.readLogBottom().isEmpty()) {
                    numberOfPreviouslyCrashedSubordinates = Integer.parseInt(this.failedSubordinatesLog.readLogBottom());
                } else {
                    numberOfPreviouslyCrashedSubordinates = 0;
                }

                if (numberOfPreviouslyCrashedSubordinates > maxSubordinates || numberOfPreviouslyCrashedSubordinates < 0) {
                    throw new IOException("Illegal number of phase one subordinate crashes read: " + numberOfPreviouslyCrashedSubordinates);
                }

                this.maxSubordinates = this.maxSubordinates - numberOfPreviouslyCrashedSubordinates;


                Printer.print("\nWaiting for " + maxSubordinates + " subordinate(s) to reconnect...\n", "white");
            } else {
                Printer.print("\nWaiting for " + maxSubordinates + " subordinate(s) to connect...\n", "white");
            }

            this.acceptSubordinates();
            this.phaseOne();

        } finally {
            this.serverSocket.close();
            for (Socket socket : sockets) {
                socket.close();
            }
        }

    }

    private void phaseOne() throws IOException {
        if(this.isCoordinatorResurrecting) {
            Printer.print("\nRe-entering phase 1...\n", "blue");
        }
        Printer.print("=============== START OF PHASE 1 ===============", "blue");
        this.broadcast("PREPARE");
        boolean decision = this.checkVotes();
        Printer.print("=============== END OF PHASE 1 =================\n", "blue");
        if (this.sockets.size() > 0) this.phaseTwo(decision);

    }

    private boolean checkVotes() throws IOException {

        boolean decision = true;
        boolean phaseOneSubordinateFailure = false;
        boolean illegalAnswer = false;

        List<String> votes;
        votes = this.receive(this.readers);
        List<Integer> socketsToRemove = new ArrayList<>();
        int i = 0;

        for (String msg : votes) {

            if (msg.equals("N")) decision = false;

            if (msg.equals("")) {
                socketsToRemove.add(i++);
                decision = false;
                phaseOneSubordinateFailure = true;

            } else if (!msg.equals("Y") && !msg.equals("N") && !msg.equals("")) {
                illegalAnswer = true;
            }
        }

        if(!socketsToRemove.isEmpty()) this.removeSockets(socketsToRemove);

        if (!illegalAnswer) {
            if (decision) {
                System.out.println("Phase 1 decision: COMMIT (all subordinates answered w/ 'YES' VOTES)");
                this.coordinatorLog.log("COMMIT", true, true, true);

            } else if(phaseOneSubordinateFailure) {
                System.out.println("\nPhase 1 decision: ABORT (one or more subordinates did not vote)");
                this.coordinatorLog.log("ABORT", true, true, true);

            } else {
                System.out.println("Phase 1 decision: ABORT (one or more subordinates answered w/ 'NO' VOTES)");
            }

            if(this.sockets.size() == 0) {
                this.coordinatorLog.log("END", false, true, true);
                this.failedSubordinatesLog.emptyLog();
            }

        } else {
            throw new IOException("Illegal vote received from a subordinate");
        }
        return decision;

    }

    private void phaseTwo(boolean decision) throws IOException {

        Printer.print("\n=============== START OF PHASE 2 ===============", "green");

        long startTime = System.currentTimeMillis();
        long timeDiff = 0;
        boolean userInputPresent = false;
        String decisionMessage = decision ? "COMMIT" : "ABORT";

        System.out.print("Please press enter within " + Coordinator.TIMEOUT_MILLIS /2000 +
                " seconds to broadcast \"" + decisionMessage + "\" to the subordinates: ");

        InputHandler inputHandler = new InputHandler(new Scanner(System.in));
        inputHandler.start();

        while(!userInputPresent && (timeDiff < (Coordinator.TIMEOUT_MILLIS/2))) {
            userInputPresent = inputHandler.isInputYetReceived();
            timeDiff = System.currentTimeMillis() - startTime;
            System.out.print("");
        }

        if (userInputPresent &&
                inputHandler.getUserInput().toUpperCase().equals("") &&
                (timeDiff < Coordinator.TIMEOUT_MILLIS)) {

            Printer.print("", "white"); // is this necessary?
            this.broadcast(decisionMessage);
            List<Integer> allSubordinates = new ArrayList<>();
            for (int i = 0; i < this.sockets.size(); i++) allSubordinates.add(i);
            this.checkAcknowledgements(allSubordinates);

        } else {
            for (Socket socket : this.sockets) {
                socket.close();
            }

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
                crashedSubordinateIndices.add(subordinatesToBeChecked.get(count++));
            } else if (!ack.equals("ACK")) {
                invalidAcknowledgement = true;
            }
        }

        if (!crashedSubordinateIndices.isEmpty() && !invalidAcknowledgement) {
            Printer.print("\nSubordinate crash(es) detected!\n", "red");
            if (!this.isRecoveryProcessStarted) System.out.println("Handing transaction over to recovery process...");
            this.recoveryProcess(crashedSubordinateIndices);

        } else if (invalidAcknowledgement) {
            throw new IOException("Illegal acknowledgement received from a subordinate");
        } else {
            this.coordinatorLog.log("END", false, true, true);
            if (this.isRecoveryProcessStarted)
                Printer.print("=============== END OF RECOVERY PROCESS =================\n", "orange");
            this.failedSubordinatesLog.emptyLog();
            Printer.print("=============== END OF PHASE 2 =================\n", "green");
        }
    }

    private void recoveryProcess(List<Integer> crashedSubordinateIndices) throws IOException {

        if (!this.isRecoveryProcessStarted)
            Printer.print("\n=============== START OF RECOVERY PROCESS ===============", "orange");

        this.isRecoveryProcessStarted = true;

        //Is this necessary?
        /*TODO: Time out at reAccept-method, if not all crashed subordinates reconnected. -> Return these, which did not
                reconnect and add them straight to the unreachableSubordinateIndices list and also remove them from
                the crashedSubordinateIndices list!!!
         */
        this.reAcceptCrashedSubordinates(crashedSubordinateIndices);

        if (this.loggedDecision.isEmpty()) this.loggedDecision = coordinatorLog.readLogBottom().split(" ")[0];

        boolean decisionMsgPrinted = false;
        List<Integer> unreachableSubordinatesIndices = new ArrayList<>();
        List<Integer> reachableSubordinatesIndices = new ArrayList<>();


        for (Integer index : crashedSubordinateIndices) {

            switch (loggedDecision) {
                case "COMMIT":
                case "ABORT":
                    if (!decisionMsgPrinted) Printer.print("\nMessage sent to previously crashed subordinate(s): " + loggedDecision, "white");
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

        if(!unreachableSubordinatesIndices.isEmpty()) {
            this.recoveryProcess(unreachableSubordinatesIndices);
        }
        Printer.print("", "white");
        if(!reachableSubordinatesIndices.isEmpty()) {
            this.checkAcknowledgements(reachableSubordinatesIndices);
        }

    }

    private void reAcceptCrashedSubordinates(List<Integer> crashedSubordinateIndices) throws IOException {

        //TODO: Think about this assumption: The subordinates have to reconnect in their order of index,
        // such that the coordinator knows, which subordinate is acknowledging.
        // Solution to that: Send the subordinate index to coordinator during resurrection (make this
        // method threaded. -> Is this overkill?

        this.serverSocket.close();
        this.serverSocket = new ServerSocket(SERVER_SOCKET_PORT);

        Printer.print("\nWaiting for crashed subordinates to reconnect...\n", "white");
        int numberOfReconnectedSubordinates = 0;

        while (numberOfReconnectedSubordinates < crashedSubordinateIndices.size()) {
            int nextSocketToReplaceIndex = crashedSubordinateIndices.get(numberOfReconnectedSubordinates);
            Socket socket = this.serverSocket.accept();
            System.out.println("Added socket for subordinate " + "S" + ++numberOfReconnectedSubordinates + ".");
            socket.setSoTimeout(TIMEOUT_MILLIS);
            this.sockets.set(nextSocketToReplaceIndex, socket);
            this.readers.set(nextSocketToReplaceIndex, new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)));
            this.writers.set(nextSocketToReplaceIndex, new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
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
        this.failedSubordinatesLog.log(String.valueOf(socketsToRemove.size()), false, false, false);

    }

    private static void printHelp() {
        System.out.println("USAGE\n=====\n arguments:\n  - Coordinator -S [NO_OF_SUBORDINATES]");
    }

    public static void main(String[] args) throws IOException {
        if ((args.length == 2) && (args[0].equals("-S")) && (Integer.parseInt(args[1]) > 0)) {
            int maxSubordinates = Integer.parseInt(args[1]);
            Coordinator coordinator = new Coordinator(maxSubordinates);
            coordinator.initiate();
        } else {
            printHelp();
        }
    }
}

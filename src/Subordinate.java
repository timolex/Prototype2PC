import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class Subordinate {

    private Socket coordinatorSocket;
    private BufferedReader reader;
    private OutputStreamWriter writer;
    private Logger SubordinateLog;
    private boolean recoveryProcessStarted = false;
    private String loggedVote;

    private Subordinate(Socket socket, String index) throws IOException {

        this.coordinatorSocket = socket;
        this.reader = new BufferedReader(new InputStreamReader(coordinatorSocket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new OutputStreamWriter(coordinatorSocket.getOutputStream(), StandardCharsets.UTF_8);
        String filename = ("/tmp/Subordinate").concat(String.valueOf(index).concat("Log.txt"));
        this.SubordinateLog = new Logger(filename, "Subordinate", true);
    }

    private String receive(boolean verbosely) throws IOException {

        String msg = this.reader.readLine();

        if (verbosely && !msg.equals("")){
            System.out.println("C: \"" + msg + "\"\n");
        }

        return msg;
    }

    private void send(String msg) throws IOException {

        Printer.print("", "");
        this.writer.write(msg + "\n");
        switch (msg) {
            case ("Y"): {
                System.out.println("Message sent to coordinator: \"YES\"");
                break;
            }
            case ("N"): {
                System.out.println("Message sent to coordinator: \"NO\"");
                break;
            }
            case ("") : {
                System.out.println("[No message sent to coordinator]");
                break;
            }
            default: {
                System.out.println("Message sent to coordinator: \"" + msg + "\"");
                break;
            }
        }
        this.writer.flush();

    }

    private void initiate() throws IOException {

        System.out.println("\nMy coordinator (C) is @ port " + this.coordinatorSocket.getPort() + "\n\n");

        String loggedDecision = this.SubordinateLog.getLatestMsg();

        if(loggedDecision.equals("ABORT") || loggedDecision.equals("PREPARED") || loggedDecision.equals("COMMIT")) {

            this.resurrect(loggedDecision);

        } else {

            this.phaseOne();

        }

    }

    private void phaseOne() throws IOException {

        if(this.recoveryProcessStarted) Printer.print("\nRe-entering phase 1...\n", "blue");
        Printer.print("=============== START OF PHASE 1 ===============", "blue");


        String prepareMsg = "";
        boolean messageArrived = false;

        try {

            prepareMsg = this.receive(true);
            messageArrived = true;

        } catch (NullPointerException ste) {

            Printer.print("C: [No \"PREPARE\"-message received from coordinator]");
            Printer.print("\n=============== SUBORDINATE CRASHES =================\n", "red");

        }

        // Here, for all further incoming messages, a timeout (defined in Coordinator.java) is set.
        this.coordinatorSocket.setSoTimeout(Coordinator.TIMEOUT_MILLIS);

        if (messageArrived && prepareMsg.equals("PREPARE")){

            if(this.recoveryProcessStarted) {

                Printer.print("Subordinate-log reads: \"" + this.loggedVote + "\"");

                this.sendVote();

                Printer.print("=============== END OF PHASE 1 =================\n", "blue");

                this.phaseTwo();

            } else {

                System.out.print("Please enter the vote ('y' for 'YES'/ 'n' for 'NO') to be sent back to the coordinator within "
                        + Coordinator.TIMEOUT_MILLIS / Coordinator.MILLIS_PER_SECOND + " seconds. ");
                System.out.print("If you wish to let this subordinate fail at this stage, please enter 'f': ");
                long timeDiff = 0;
                boolean userInputPresent = false;

                InputHandler inputHandler = new InputHandler(new Scanner(System.in));
                inputHandler.start();
                long startTime = System.currentTimeMillis();

                while(!userInputPresent && (timeDiff < (Coordinator.TIMEOUT_MILLIS/2))) {

                    userInputPresent = inputHandler.isInputYetReceived();
                    timeDiff = System.currentTimeMillis() - startTime;

                    System.out.print("");

                }

                if (userInputPresent &&
                        inputHandler.getUserInput().toUpperCase().equals("Y") &&
                        ((System.currentTimeMillis() - startTime) < Coordinator.TIMEOUT_MILLIS))  {

                    this.SubordinateLog.log("PREPARED", true);
                    this.send("Y");
                    Printer.print("=============== END OF PHASE 1 =================\n", "blue");
                    this.phaseTwo();

                } else if (userInputPresent &&
                        inputHandler.getUserInput().toUpperCase().equals("N") &&
                        ((System.currentTimeMillis() - startTime) < Coordinator.TIMEOUT_MILLIS)) {

                    this.SubordinateLog.log("ABORT", true);
                    this.send("N");
                    Printer.print("=============== END OF PHASE 1 =================\n", "blue");
                    this.phaseTwo();


                } else if (userInputPresent &&
                        inputHandler.getUserInput().toUpperCase().equals("F") &&
                        ((System.currentTimeMillis() - startTime) < Coordinator.TIMEOUT_MILLIS)){

                    Printer.print("=============== SUBORDINATE CRASHES =================\n", "red");

                    // Terminate the program, even if System.in still blocks in InputHandler
                    System.exit(0);

                } else {

                    Printer.print("\nNo valid input detected within " + Coordinator.TIMEOUT_MILLIS / Coordinator.MILLIS_PER_SECOND + " seconds!", "red");
                    Printer.print("=============== SUBORDINATE CRASHES =================\n", "red");

                    // Terminate the program, even if System.in still blocks in InputHandler
                    System.exit(0);

                }

            }



        }

    }

    private void phaseTwo() throws IOException {

        Printer.print("\n=============== START OF PHASE 2 ===============", "green");
        Printer.print("Waiting for the coordinator's decision message...\n");

        String decisionMsg = "";
        this.loggedVote = this.SubordinateLog.getLatestMsg();
        boolean reconnectSuccess = true;
        boolean reEnterPhaseOne = false;

        if(!(loggedVote.equals("PREPARED") || loggedVote.equals("ABORT") || loggedVote.equals("COMMIT"))) {

            throw new IOException("Illegal logged vote read: "+ loggedVote);

        }

        long startTime = System.currentTimeMillis();

        try {

            decisionMsg = this.receive(true);


        } catch (NullPointerException npe) {

            while((System.currentTimeMillis() - startTime) < Coordinator.TIMEOUT_MILLIS) {

                // wait

            }

            Printer.print("\nNo message received from coordinator!", "orange");

            this.recoveryProcessStarted = true;
            reEnterPhaseOne = true;
            reconnectSuccess = this.recoveryProcess();


        } catch (SocketTimeoutException ste) {

            Printer.print("\nNo message received from coordinator!", "orange");

            this.recoveryProcessStarted = true;
            reEnterPhaseOne = true;
            reconnectSuccess = this.recoveryProcess();

        }

        if(!reconnectSuccess) {

            Printer.print("\nCoordinator is considered crashed permanently!", "red");

            if (!this.SubordinateLog.isLatestMsg("ABORT")) {

                this.SubordinateLog.log("ABORT", true);

            }

            this.SubordinateLog.log("END");

            Printer.print("=============== END OF RECOVERY PROCESS =================", "orange");
            Printer.print("=============== UNILATERAL ABORT =================\n", "red");

        } else {

            if(reEnterPhaseOne) {

                this.phaseOne();

            } else {

                switch (decisionMsg) {

                    case "COMMIT":
                    case "ABORT":

                        if (!this.SubordinateLog.isLatestMsg("ABORT") &&
                                !this.SubordinateLog.isLatestMsg("COMMIT")) {

                            this.SubordinateLog.log(decisionMsg, true);

                        }

                        this.sendAck();

                        break;

                    default:

                        throw new IOException("Illegal decision message received from coordinator: " + decisionMsg);

                }

            }

        }

    }

    private void sendVote() throws IOException {

        if(this.loggedVote.equals("")) {

            throw new IOException("loggedVote is empty");

        }

        if(this.loggedVote.equals("PREPARED")) {

            this.send("Y");


        } else {

            this.send("N");

        }

    }

    private boolean recoveryProcess() throws IOException {

        Printer.print("\n=============== START OF RECOVERY PROCESS ===============\n", "orange");

        int reattempts = 0;
        int maxReattempts = 3;

        while (reattempts != maxReattempts) {

            try {

                Printer.print("Reconnecting to coordinator...\n");

                this.coordinatorSocket = new Socket(Coordinator.SERVER_SOCKET_HOST, Coordinator.SERVER_SOCKET_PORT);
                this.coordinatorSocket.setSoTimeout(0);
                this.reader = new BufferedReader(new InputStreamReader(this.coordinatorSocket.getInputStream(), StandardCharsets.UTF_8));
                this.writer = new OutputStreamWriter(this.coordinatorSocket.getOutputStream(), StandardCharsets.UTF_8);

                Printer.print("Successfully reconnected to coordinator!\n");
                return true;

            } catch (ConnectException ce) {

                Printer.print("Unable to reconnect to coordinator!\n", "orange");

                long startTime = System.currentTimeMillis();

                while((System.currentTimeMillis() - startTime) < Coordinator.TIMEOUT_MILLIS) {

                    // wait

                }

                ++reattempts;

            }

        }

        return false;

    }

    private void sendAck() throws IOException {

        System.out.print("Please press enter within " + Coordinator.TIMEOUT_MILLIS / Coordinator.MILLIS_PER_SECOND + " seconds, for" +
                " letting this subordinate acknowledge the coordinator's decision: ");

        InputHandler inputHandler = new InputHandler(new Scanner(System.in));
        inputHandler.start();
        boolean inputPresent = false;

        long startTime = System.currentTimeMillis();
        long timeDiff = 0;

        while (!inputPresent && (timeDiff < Coordinator.TIMEOUT_MILLIS)) {

            inputPresent = inputHandler.isInputYetReceived();
            timeDiff = System.currentTimeMillis() - startTime;

            System.out.print("");

        }

        if (inputPresent &&
                inputHandler.getUserInput().toUpperCase().equals("") &&
                (timeDiff < Coordinator.TIMEOUT_MILLIS)) {

            this.send("ACK");
            this.SubordinateLog.log("END");
            Printer.print("=============== END OF PHASE 2 =================\n", "green");


        } else {

            Printer.print("\nNot acknowledged within " + Coordinator.TIMEOUT_MILLIS / Coordinator.MILLIS_PER_SECOND + " seconds!", "red");
            Printer.print("=============== SUBORDINATE CRASHES =================\n", "red");

            // Terminate the program, even if System.in still blocks in InputHandler
            System.exit(0);
        }

    }


    private void resurrect(String loggedDecision) throws IOException {

        Printer.print("=============== SUBORDINATE RESURRECTS =================\n", "red");
        Printer.print("Subordinate-log reads: \"" + loggedDecision + "\"");
        Printer.print("Re-entering phase 2...", "green");

        this.phaseTwo();
    }

    private static void printHelp(){

        System.out.println("USAGE\n=====\n arguments:\n  - Subordinate -F x       // x (integer) defines this " +
                "Subordinate's index required for its log's filename");

    }

    public static void main(String[] args) throws IOException {

        if ((args.length == 2) && (args[0].equals("-F")) && (Integer.parseInt(args[1]) > 0)) {

            Socket coordinatorSocket = new Socket(Coordinator.SERVER_SOCKET_HOST, Coordinator.SERVER_SOCKET_PORT);

            try {

                Subordinate subordinate = new Subordinate(coordinatorSocket, args[1]);
                subordinate.initiate();

            } finally {

                coordinatorSocket.close();

            }

        } else {

            printHelp();

        }
    }
}

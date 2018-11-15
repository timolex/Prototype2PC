import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class Subordinate {

    private Socket coordinatorSocket;
    private BufferedReader reader;
    private OutputStreamWriter writer;
    private Logger subordinateLogger;
    private Logger coordinatorLogger;
    private Scanner scanner;
    private boolean phaseTwoStarted;


    private Subordinate(Socket socket, String index) throws IOException {

        this.coordinatorSocket = socket;
        this.reader = new BufferedReader(new InputStreamReader(coordinatorSocket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new OutputStreamWriter(coordinatorSocket.getOutputStream(), StandardCharsets.UTF_8);
        String filename = ("/tmp/Subordinate").concat(String.valueOf(index).concat("Log.txt"));
        this.subordinateLogger = new Logger(filename, "Subordinate");
        this.phaseTwoStarted = false;
    }

    private Socket getCoordinatorSocket() {
        return coordinatorSocket;
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

        System.out.println("\nMy coordinator (C) is @ port " + this.getCoordinatorSocket().getPort() + "\n\n");

        this.phaseOne();

    }

    private void phaseOne() throws IOException {

        Printer.print("=============== START OF PHASE 1 ===============", "blue");

        String prepareMsg = this.receive(true);
        String phaseOneCoordinatorFailure = this.receive(false);

        if (prepareMsg.equals("PREPARE") && phaseOneCoordinatorFailure.equals("")){

            this.scanner = new Scanner(System.in);

            System.out.print("Please enter the vote ('y' for 'YES'/ 'n' for 'NO') to be sent back to the coordinator within "
                    + Coordinator.TIMEOUT_MILLISECS/1000 + " seconds. ");
            System.out.print("If you wish to let this subordinate fail at this stage, please enter 'f': ");
            long startTime = System.currentTimeMillis();

            String userInput = this.scanner.nextLine();

            if (userInput.toUpperCase().equals("Y") && ((System.currentTimeMillis() - startTime) < Coordinator.TIMEOUT_MILLISECS))  {

                this.subordinateLogger.log("PREPARED", true);
                this.send("Y");
                Printer.print("=============== END OF PHASE 1 =================\n", "blue");
                this.phaseTwo();

            } else if (userInput.toUpperCase().equals("N") && ((System.currentTimeMillis() - startTime) < Coordinator.TIMEOUT_MILLISECS)) {

                this.subordinateLogger.log("ABORT", true);
                this.send("N");
                Printer.print("=============== END OF PHASE 1 =================\n", "blue");
                this.phaseTwo();


            } else if (userInput.toUpperCase().equals("F") && ((System.currentTimeMillis() - startTime) < Coordinator.TIMEOUT_MILLISECS)){

                Printer.print("=============== SUBORDINATE CRASHES =================\n", "red");

                do {
                    // Here the process waits, such that no NullPointerException results in Coordinator.java:receive()
                } while (System.currentTimeMillis() - startTime < Coordinator.TIMEOUT_MILLISECS);


            } else {

                Printer.print("\nNo valid input detected within " + Coordinator.TIMEOUT_MILLISECS / 1000 + " seconds!", "red");
                Printer.print("=============== SUBORDINATE CRASHES =================\n", "red");

                do {
                    // Here the process waits, such that no NullPointerException results in Coordinator.java:receive()
                } while (System.currentTimeMillis() - startTime < Coordinator.TIMEOUT_MILLISECS);

            }


        } else if (prepareMsg.equals("PREPARE") && phaseOneCoordinatorFailure.equals("COORDINATOR_FAILURE")) {

            Printer.print("Coordinator crash detected!", "red");
            this.subordinateLogger.log("ABORT", true);
            Printer.print("=============== UNILATERAL ABORT =================\n", "red");

        } else {

            throw new IOException("Illegal prepare message received from coordinator: " + prepareMsg);

        }
    }

    private void phaseTwo() throws IOException {

        if(!this.phaseTwoStarted) Printer.print("\n=============== START OF PHASE 2 ===============", "green");

        this.phaseTwoStarted = true;

        String decisionMsg = this.receive(true);
        String coordinatorFailureMessage = this.receive(false);

        if(coordinatorFailureMessage.equals("COORDINATOR_FAILURE")) {

            Printer.print("Coordinator crash detected!\n", "red");

            System.out.println("Handing transaction over to recovery process...");

            this.coordinatorLogger = new Logger("/tmp/CoordinatorLog.txt");
            this.recoveryProcess();
            Printer.print("=============== END OF PHASE 2 =================\n", "green");

        } else {

            switch (decisionMsg) {

                case "COMMIT":
                case "ABORT":

                    System.out.print("Please decide ('y'/'f'), whether this subordinate should acknowledge the coordinator's decision, or fail now: ");
                    String input = this.scanner.next();

                    if (input.toUpperCase().equals("Y")) {

                        if(!this.subordinateLogger.readLog().split(" ")[0].equals("ABORT")) {

                            this.subordinateLogger.log(decisionMsg, true);

                        }

                        this.send("ACK");
                        Printer.print("=============== END OF PHASE 2 =================\n", "green");

                    } else {

                        Printer.print("=============== SUBORDINATE CRASHES =================\n", "red");
                        this.resurrect();

                    }

                    break;

                default:

                    throw new IOException("Illegal decision message received from coordinator: " + decisionMsg);

            }
        }

    }

    private void resurrect() throws IOException {

        Printer.print("=============== SUBORDINATE RESURRECTS =================\n", "red");

        this.phaseTwo();
    }

    private void recoveryProcess() throws IOException {

        Printer.print("\n=============== START OF RECOVERY PROCESS ===============", "orange");

        String loggedDecision = this.coordinatorLogger.readLog().split(" ")[0];

        switch (loggedDecision) {

            case "ABORT":
            case "COMMIT":

                System.out.println("Coordinator-log reads: \"" + loggedDecision + "\"");
                if(!this.subordinateLogger.readLog().split(" ")[0].equals("ABORT")) {

                    this.subordinateLogger.log(loggedDecision, true);

                }

                break;

            case "":

                System.out.println("Coordinator-log is empty. Transaction is aborted (no-information-case).");
                if(!this.subordinateLogger.readLog().split(" ")[0].equals("ABORT")) {

                    this.subordinateLogger.log("ABORT", true);

                }

                break;

            default:

                throw new IOException("Illegal coordinator-log entry: " + loggedDecision);

        }

        Printer.print("=============== END OF RECOVERY PROCESS =================\n", "orange");

    }

    private static void printHelp(){

        System.out.println("USAGE\n=====\n arguments:\n  - Subordinate -F x       // x (integer) defines this " +
                "Subordinate's index required for its log's filename");

    }

    public static void main(String[] args) throws IOException {

        if ((args.length == 2) && (args[0].equals("-F")) && (Integer.parseInt(args[1]) > 0)) {

            // Trying to connect with coordinator. TODO: Repeat this several times, until a connection has been established.
            Socket coordinatorSocket = new Socket("localhost", 8080);

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

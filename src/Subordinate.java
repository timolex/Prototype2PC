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
    private Logger coordinatorLogger;
    private Scanner scanner;
    private boolean phaseTwoStarted;

    private Subordinate(Socket socket) throws IOException {
        this.coordinatorSocket = socket;
        this.reader = new BufferedReader(new InputStreamReader(coordinatorSocket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new OutputStreamWriter(coordinatorSocket.getOutputStream(), StandardCharsets.UTF_8);
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
            System.out.print("Please enter the vote ('y' for 'YES'/ 'n' for 'NO') to be sent back to the coordinator. ");
            System.out.print("If you wish to let this subordinate fail at this stage, please enter 'f': ");
            String input = scanner.next().toUpperCase();
            if(input.equals("Y") || input.equals("N")){
                this.send(input);
                Printer.print("=============== END OF PHASE 1 =================\n", "blue");
                this.phaseTwo();
            } else if (input.equals("F")){
                this.send("");
                Printer.print("=============== SUBORDINATE CRASHES =================\n", "red");
            }

        } else if (prepareMsg.equals("PREPARE") && phaseOneCoordinatorFailure.equals("COORDINATOR_FAILURE")) {

            System.out.println("Coordinator crash detected!");
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

            System.out.println("Coordinator crash detected!\n");
            System.out.println("Handing transaction over to recovery process...");

            this.coordinatorLogger = new Logger("/tmp/CoordinatorLog.txt");
            this.recoveryProcess();
            Printer.print("=============== END OF PHASE 2 =================\n", "green");

        } else {

            switch (decisionMsg) {

                case "COMMIT":
                case "ABORT":

                    System.out.print("Please decide ('y'/'f'), whether this subordinate should acknowledge the coordinator's decision, or fail now:");
                    String input = this.scanner.next();

                    if (input.toUpperCase().equals("Y")) {

                        this.send("ACK");
                        Printer.print("=============== END OF PHASE 2 =================\n", "green");

                    } else {

                        this.send("");
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

                break;

            case "":

                System.out.println("Coordinator-log is empty. Transaction is ABORTed (no-information-case).");

                break;

            default:

                throw new IOException("Illegal coordinator-log entry: " + loggedDecision);

        }

        Printer.print("=============== END OF RECOVERY PROCESS =================\n", "orange");

    }

    public static void main(String[] args) throws IOException {

        // Trying to connect with coordinator. TODO: Repeat this several times, until a connection has been established.
        Socket coordinatorSocket = new Socket("localhost", 8080);

        try {
            Subordinate subordinate = new Subordinate(coordinatorSocket);
            subordinate.initiate();

        } finally {
            coordinatorSocket.close();
        }
    }
}

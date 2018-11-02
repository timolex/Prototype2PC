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
    private Scanner scanner;

    private Subordinate(Socket socket) throws IOException {
        this.coordinatorSocket = socket;
        this.reader = new BufferedReader(new InputStreamReader(coordinatorSocket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new OutputStreamWriter(coordinatorSocket.getOutputStream(), StandardCharsets.UTF_8);
    }

    private Socket getCoordinatorSocket() {
        return coordinatorSocket;
    }

    private String receive(boolean verbosely) throws IOException {

        String msg = this.reader.readLine();

        if (verbosely){
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
                System.out.println("No message sent to coordinator");
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

        System.out.println("=============== START OF PHASE 1 ===============");

        String prepareMsg = this.receive(true);
        String phaseOneCoordinatorFailure = this.receive(false);

        if (prepareMsg.equals("PREPARE") && phaseOneCoordinatorFailure.equals("")){
            this.scanner = new Scanner(System.in);
            System.out.println("Please enter the vote ('y' for 'YES'/ 'n' for 'NO') to be sent back to the coordinator.");
            System.out.println("If you wish to let this subordinate fail at this stage, please enter 'f':");
            String input = scanner.next().toUpperCase();
            if(input.equals("Y") || input.equals("N")){
                this.send(input);
                System.out.println("=============== END OF PHASE 1 =================\n");
                this.phaseTwo();
            } else if (input.equals("F")){
                this.send("");
                System.out.println("=============== SUBORDINATE FAILURE =================\n");
            }

        } else if (prepareMsg.equals("PREPARE") && phaseOneCoordinatorFailure.equals("COORDINATOR_FAILURE")) {

            System.out.println("Coordinator crash detected!\n");
            System.out.println("=============== UNILATERAL ABORT =================\n");

        } else {

            throw new IOException("Illegal prepare message received from coordinator: " + prepareMsg);

        }
    }

    private void phaseTwo() throws IOException {

        System.out.println("\n=============== START OF PHASE 2 ===============");
        this.handleAcknowledgement();
    }

    private void handleAcknowledgement() throws IOException {

        String decisionMsg = this.receive(true);

        switch (decisionMsg) {
            case "COMMIT":
            case "ABORT":
                System.out.println("Please decide ('y'/'n'), whether this subordinate should acknowledge the coordinator's decision, or not:");
                String input = this.scanner.next();
                if (input.toUpperCase().equals("Y")) {
                    this.send("ACK");
                    System.out.println("=============== END OF PHASE 2 =================\n");
                } else {
                    this.send("");
                    System.out.println("=============== SUBORDINATE CRASHES =================\n");
                    this.resurrect();
                }
                break;
            case "":

                break;
            default:
                throw new IOException("Illegal decision message received from coordinator: " + decisionMsg);
        }

    }

    private void resurrect() throws IOException {
        System.out.println("=============== SUBORDINATE RESURRECTS =================\n");

        this.handleAcknowledgement();
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

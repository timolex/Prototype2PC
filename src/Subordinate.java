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

    private String receive() throws IOException {

        String msg = this.reader.readLine();
        System.out.println("C: \"" + msg + "\"\n");
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
                System.out.println("Message sent to coordinator: \"\"");
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

        String prepareMsg = this.receive();

        switch (prepareMsg) {
            case "PREPARE":
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
                    System.out.println("=============== END OF PHASE 1 =================\n");
                    this.phaseTwo();
                }

                break;
            case "":
                //TODO: Handle phase 1 failure (Coordinator down -> abort unilaterally)
                this.coordinatorSocket.close();

                break;
            default:
                this.coordinatorSocket.close();
                throw new IOException("Illegal prepare message received from coordinator: " + prepareMsg);
        }

    }

    private void phaseTwo() throws IOException {

        System.out.println("\n=============== START OF PHASE 2 ===============");

        String decisionMsg = this.receive();

        switch (decisionMsg) {
            case "COMMIT":
                System.out.println("Please decide ('y'/'n'), if this subordinate should acknowledge the coordinator's decision, or not:");
                String input = this.scanner.next();
                if(input.equals("y")) {
                    this.send("ACK");
                } else {
                    this.send("");
                }
                System.out.println("=============== END OF PHASE 2 =================\n");

                break;
            case "ABORT":

                System.out.println("Please decide ('y'/'n'), if this subordinate should acknowledge the coordinator's decision, or not:");
                String input2 = this.scanner.next();
                if(input2.equals("y")) {
                    this.send("ACK");
                } else {
                    this.send("");
                }
                System.out.println("=============== END OF PHASE 2 =================\n");

                break;
            default:

                throw new IOException("Illegal decision message received from coordinator: " + decisionMsg);

        }

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

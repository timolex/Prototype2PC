import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Subordinate {

    private Socket coordinatorSocket;
    private BufferedReader reader;
    private OutputStreamWriter writer;
    private String vote;

    private Subordinate(Socket socket, String vote) throws IOException {
        this.coordinatorSocket = socket;
        this.vote = vote;
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
        System.out.println("Message sent to coordinator: " + "\"" + msg + "\"");
        this.writer.flush();

    }

    private void initiate() throws IOException {

        System.out.println("\nMy coordinator (C) is @ port " + this.getCoordinatorSocket().getPort() + "\n" +
                "Vote to be sent: " + this.vote +"\n");
        this.phaseOne();

    }


    private void phaseOne() throws IOException {

        System.out.println("=============== START OF PHASE 1 ===============");

        String prepareMsg = this.receive();

        switch (prepareMsg) {
            case "PREPARE":
                this.send(this.vote);
                System.out.println("=============== END OF PHASE 1 =================\n");

                this.phaseTwo();

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
                this.send("ACK");
                System.out.println("=============== END OF PHASE 2 =================\n");

                break;
            case "ABORT":

                this.send("ACK");
                System.out.println("=============== END OF PHASE 2 =================\n");

                break;
            default:

                throw new IOException("Illegal decision message received from coordinator: " + decisionMsg);

        }

        //TODO: Always move this to the last step of the protocol.
        this.coordinatorSocket.close();
    }

    private static void printHelp(){
        System.out.println("USAGE\n=====\n arguments:\n  - Subordinate -y (vote YES in phase one)\n  - Subordinate -n (vote NO in phase one)" +
                "\n  - Subordinate -f (do NOT vote in phase one)");
    }

    public static void main(String[] args) throws IOException {

        // Trying to connect with coordinator. TODO: Repeat this several times, until a connection has been established.
        Socket coordinatorSocket;

        if(args.length == 0){
            printHelp();
        } else if(args[0].equals("-y")) {
            coordinatorSocket = new Socket("localhost", 8080);
            Subordinate subordinate = new Subordinate(coordinatorSocket, "YES");
            subordinate.initiate();
            coordinatorSocket.close();
        } else if(args[0].equals("-n")){
            coordinatorSocket = new Socket("localhost", 8080);
            Subordinate subordinate = new Subordinate(coordinatorSocket, "NO");
            subordinate.initiate();
            coordinatorSocket.close();
        } else if(args[0].equals("-f")){
            coordinatorSocket = new Socket("localhost", 8080);
            Subordinate subordinate = new Subordinate(coordinatorSocket, "");
            subordinate.initiate();
            coordinatorSocket.close();
        } else printHelp();

    }
}

import java.io.BufferedReader;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class CoordinatorReceiver implements Runnable {

    private Thread t;
    private BufferedReader reader;
    private String receivedMessage;
    private int subordinateIndex;
    private boolean msgYetReceived = false;

    public CoordinatorReceiver(BufferedReader subordinateReader, int index) {

        this.reader = subordinateReader;
        this.subordinateIndex = index;

    }

    public String getReceivedMessage() {

        return receivedMessage;

    }

    public boolean isMsgYetReceived() {

        return msgYetReceived;

    }

    public void print() {

        switch (this.receivedMessage) {

            case ("Y"): {
                Printer.print("S" + this.subordinateIndex + ": " + "\"YES\"");
                break;
            }

            case ("N"): {
                Printer.print("S" + this.subordinateIndex + ": " + "\"NO\"");
                break;
            }

            case(""): {
                Printer.print("S" + this.subordinateIndex + ": " + "[No message received]");
                break;
            }

            default: {
                Printer.print("S" + this.subordinateIndex + ": " + "\"" + this.receivedMessage + "\"");
                break;
            }

        }

    }

    @Override
    public void run() {

        try {

            this.receivedMessage = this.reader.readLine();
            this.print();
            this.msgYetReceived = true;

        } catch (SocketTimeoutException | NullPointerException | SocketException e) {

            this.receivedMessage = "";
            this.print();
            this.msgYetReceived = true;

        } catch (IOException e) {

            e.printStackTrace();

        }

    }

    public void start() {

        if (t == null) {

            t = new Thread (this);
            t.start();

        }

    }

}

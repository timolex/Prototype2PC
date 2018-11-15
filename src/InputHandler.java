import java.util.Scanner;

public class InputHandler implements Runnable {

    private Thread t;
    private String userInput;
    private Scanner scanner;
    private boolean inputYetReceived = false;

    public InputHandler(Scanner scanner) {

        this.scanner = scanner;

    }

    public String getUserInput() {

        return userInput;

    }

    public boolean isInputYetReceived() {

        return inputYetReceived;

    }

    @Override
    public void run() {

        this.userInput = scanner.nextLine();
        this.inputYetReceived = true;

    }

    public void start() {

        if (t == null) {

            t = new Thread (this);
            t.start();

        }

    }

}

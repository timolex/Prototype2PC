public class Printer {

    private static final String RED = "\033[0;31m";
    private static final String GREEN = "\033[0;32m";
    private static final String ORANGE = "\033[0;33m";
    private static final String BLUE = "\033[0;34m";
    private static final String NO_COLOR = "\033[0m";

    public static void print(String msg, String colour){
        switch (colour) {
            case "red":
                System.out.println(RED + msg + NO_COLOR);
                break;
            case "green":
                System.out.println(GREEN + msg + NO_COLOR);
                break;
            case "orange":
                System.out.println(ORANGE + msg + NO_COLOR);
                break;
            case "blue":
                System.out.println(BLUE + msg + NO_COLOR);
                break;
            default:
                System.out.println(msg);
        }
    }
}

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Stack;

public class Logger {

    private String filename;
    private String nodeType;
    private File logFile;
    private BufferedReader br;
    private BufferedWriter bw;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
    private Stack<String> reverseLog = new Stack<>();


    public Logger(String filename, String nodeType, boolean append) throws IOException {

        this.filename = filename;
        this.nodeType = nodeType;
        this.logFile = new File(this.filename);

        if(!logFile.exists()) {
            logFile.createNewFile();
        }

        FileReader fr = new FileReader(logFile);
        FileWriter fw = new FileWriter(logFile, append);

        this.br = new BufferedReader(fr);
        this.bw = new BufferedWriter(fw);

        String line;

        while((line = this.br.readLine()) != null) {

            reverseLog.push(line);

        }

    }

    public void log(String msg, boolean forceWrite, boolean appendDate, boolean verbose) throws IOException {
        String newLogEntry;

        if(appendDate) {
            String timeStamp  = dateFormat.format(new Date());
            newLogEntry = msg + " " + timeStamp + "\n";
        } else {
            newLogEntry = msg + "\n";
        }


        this.bw.write(newLogEntry);
        this.bw.flush();

        this.reverseLog.push(newLogEntry);

        if (verbose) {
            if (forceWrite) {
                System.out.println("\n" + this.nodeType + " force-writes: \"" + msg + "\"");
            } else {
                System.out.println("\n" + this.nodeType + " writes: \"" + msg + "\"");
            }
        }

        if(msg.equals("END")) finalizeLog();

    }

    public String readLog() {

        if (this.reverseLog.isEmpty()) return "";
        return this.reverseLog.peek();

    }

    private void finalizeLog() throws IOException {

        this.bw.close();

    }

    public void emptyLog() throws IOException {

        FileWriter fw = new FileWriter(this.logFile, false);
        BufferedWriter bw = new BufferedWriter(fw);

        bw.write("");
        bw.flush();
        bw.close();

    }
}

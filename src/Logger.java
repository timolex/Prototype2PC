import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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

    public void log(String msg) throws IOException {
        log(msg, false, true, true);
    }

    public void log(String msg, boolean forceWrite) throws IOException {
        log(msg, forceWrite, true, true);
    }

    public void log(String msg, boolean forceWrite, boolean appendDate) throws IOException {
        log(msg, forceWrite, appendDate, true);
    }

    public void log(String msg, boolean forceWrite, boolean appendDate, boolean verbose) throws IOException {
        String newLogEntry = msg;

        if(appendDate) {
            String timeStamp = dateFormat.format(new Date());
            newLogEntry += " " + timeStamp;
        }

        newLogEntry += "\n";

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

    public String readBottom() {

        if (this.reverseLog.isEmpty()) return "";
        return this.reverseLog.peek();

    }

    public String getLatestMsg() {

        return this.readBottom().split(" ")[0];

    }

    public boolean isLatestMsg(String msg) {

        return this.getLatestMsg().equals(msg);

    }

    public boolean isEmpty() {

        return this.readBottom().isEmpty();

    }

    public List<String> getLog() throws IOException {

        return (new ArrayList<>(reverseLog));

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

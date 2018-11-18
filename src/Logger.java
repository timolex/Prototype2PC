import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Stack;

public class Logger {

    private BufferedReader br;
    private BufferedWriter bw;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
    private String nodeType;
    private Stack<String> reverseLog = new Stack<>();


    public Logger(String filename, String nodeType, boolean append) throws IOException {

        File logFile = new File(filename);

        if(!logFile.exists()) {
            logFile.createNewFile();
        }

        FileReader fr = new FileReader(logFile);
        FileWriter fw = new FileWriter(logFile, append);

        this.br = new BufferedReader(fr);
        this.bw = new BufferedWriter(fw);
        this.nodeType = nodeType;

        String line;

        while((line = this.br.readLine()) != null) {

            reverseLog.push(line);

        }

    }

    public void log(String msg, boolean forceWrite) throws IOException {
        String timeStamp  = dateFormat.format(new Date());
        String newLogEntry = msg + " " + timeStamp + "\n";

        this.bw.write(newLogEntry);
        this.bw.flush();

        this.reverseLog.push(newLogEntry);

        if(forceWrite){
            System.out.println("\n" + this.nodeType +" force-writes: \"" + msg + "\"");
        } else {
            System.out.println("\n" + this.nodeType +" writes: \"" + msg + "\"");
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
}

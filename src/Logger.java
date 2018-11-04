import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {

    private BufferedReader br;
    private BufferedWriter bw;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
    private String nodeType;


    public Logger(String filename, String nodeType) throws IOException {

        File logFile = new File(filename);

        if(!logFile.exists()) {
            logFile.createNewFile();
        }

        FileReader fr = new FileReader(logFile);
        FileWriter fw = new FileWriter(logFile);

        this.br = new BufferedReader(fr);
        this.bw = new BufferedWriter(fw);
        this.nodeType = nodeType;

    }

    public void log(String msg, boolean forceWrite) throws IOException {
        String timeStamp  = dateFormat.format(new Date());
        String newLogEntry = msg + " " + timeStamp + "\n";

        bw.write(newLogEntry);
        bw.flush();

        if(forceWrite){
            System.out.println("\n" + this.nodeType +" force-writes: \"" + msg + "\"");
        } else {
            System.out.println("\n" + this.nodeType +" writes: \"" + msg + "\"");
        }

    }

    public String readLog() throws IOException {
        return br.readLine();
    }

    protected void finalize( ) throws Throwable {
        this.bw.close();
    }
}

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {

    private BufferedReader br;
    private BufferedWriter bw;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");


    public Logger(String filename) throws IOException {

        File logFile = new File(filename);

        if(!logFile.exists()) {
            logFile.createNewFile();
        }

        FileReader fr = new FileReader(logFile);
        FileWriter fw = new FileWriter(logFile);

        this.br = new BufferedReader(fr);
        this.bw = new BufferedWriter(fw);

    }

    public void log(String msg) throws IOException{
        String timeStamp  = dateFormat.format(new Date());

        String newLogEntry = msg + " " + timeStamp;

        bw.write(newLogEntry);
        bw.flush();

        System.out.println("Coordinator force-writes: \"" + msg + "\"");

    }

    protected void finalize( ) throws Throwable {
        this.bw.close();
    }
}

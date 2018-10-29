import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {

    private FileReader fr;
    private FileWriter fw;
    private BufferedReader br;
    private BufferedWriter bw;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");


    public Logger(String filename) throws IOException {
        this.fr = new FileReader(filename);
        this.fw = new FileWriter(filename);

        this.br = new BufferedReader(this.fr);
        this.bw = new BufferedWriter(this.fw);

    }

    public void log(String msg) throws IOException{
        String timeStamp  = dateFormat.format(new Date());

        String newLogEntry = timeStamp + " " + msg;

        bw.write(newLogEntry);
        bw.flush();

        System.out.println("Coordinator force-writes: \"" + msg + "\"");

        //TODO: Find out what bw.close does
        //bw.close();
    }

}

package Server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.SocketException;

/*
BACKDOOR ACTIVITY
NOT USING AT MOMENT.
//TODO: INTEGRATE WITH NIO with more feature later
 */
public class Stop implements Runnable {
    private static PrintWriter clientPrint = null;
    private static BufferedReader clientReceive = null;
    private static Thread handler = null;

    public Stop(PrintWriter clientPrint, BufferedReader clientReceive, Thread handler) {
        this.clientPrint = clientPrint;
        this.clientReceive = clientReceive;
        this.handler = handler;
    }

    @Override
    public void run() {
        try {
            try {
                while (!(backdoorReceive().compareTo("quit") == 0)) {
                }
                System.err.println("!!Triggered client termination!!");
            } catch (NullPointerException e) {
                System.err.println();
                System.err.println("======================================================================");
                System.err.println("Server.Server has terminate the connection due to client not reachable issue!");
                System.err.println("======================================================================");
                System.err.println();

            } catch (SocketException e) {
                System.err.println();
                System.err.println("======================================================================");
                System.err.println("Server.Server has terminate the connection due to client not reachable issue!");
                System.err.println("======================================================================");
                System.err.println();
            }
            clientPrint.println("quit");
            clientPrint.flush();
            handler.interrupt();
            //handler.join();// join or intterupt?
            System.out.println();
            System.out.println("Waiting for next connection...");
            return;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String backdoorReceive() throws IOException {
        String in = clientReceive.readLine();
        return in;

    }
}

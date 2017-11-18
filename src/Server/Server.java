package Server;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Queue;

public class Server {
    //data structures
    private static final int LINGER_TIME = 5000;
    public static Server server = null;
    public static Queue pending = new ArrayDeque();
    private static int portNo = 9000;
    private static ServerSocketChannel serverChannel = null;
    private static ArrayList<String> libraryLst = null;
    private static int counter = 0;
    private Selector selector = null;
    private int phase = 0;
    private boolean sendflag = false;

    public static boolean loadDictionary(String path) throws IOException {
        libraryLst = new ArrayList();
        BufferedReader fromFile = new BufferedReader(new FileReader(path));
        while (!fromFile.ready()) {
        }
        while (fromFile.readLine() != null) {
            libraryLst.add(fromFile.readLine().toString().toLowerCase());
            libraryLst.add(fromFile.readLine().toString().toLowerCase());
        }
        System.out.println("Dictionary loaded!");
        fromFile.close();
        //System.out.print(content);//debug make it http base later

        return false;
    }

    //server start
    public static void main(String[] args) {
        server = new Server();
        server.start();
    }

    /*
    Reference from:http://rox-xmlrpc.sourceforge.net/niotut/
    to solve deadlock
     */
    private void cleanupQ() {
        synchronized (pending) {
            Iterator request = this.pending.iterator();
            while (request.hasNext()) {
                PendingRequest current = (PendingRequest) request.next();
                SelectionKey key = current.socket.keyFor(this.selector);
                key.interestOps(current.ops);
            }
        }
        pending.clear();
    }

    /*
    end
     */
    private void start() {
        try {
            initSelect();
            initChannel(portNo);//accept
            System.out.println("Waiting for connection...");
            if (loadDictionary("Resources/words.txt")) {
                System.err.println("Dictionary load error!!");
            }
            String in = "";
            while (true) {
                if (sendflag) {
                    cleanupQ();
                    sendflag = false;
                }
                selector.select();
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();
                    if (!key.isValid()) {
                        continue;
                    }

                    //new client join
                    if (key.isAcceptable()) {//accept
                        //refence to the example code and modified to suit my own program
                        ServerSocketChannel listendingSocket = (ServerSocketChannel) key.channel();
                        SocketChannel listendingChannel = listendingSocket.accept();
                        listendingChannel.configureBlocking(false);
                        Hanger handler = new Hanger(listendingChannel, libraryLst, server);
                        Client create = new Client(handler);
                        listendingChannel.register(selector, SelectionKey.OP_READ, create);
                        System.out.println("Currently user: " + create.hashCode());
                        listendingChannel.setOption(StandardSocketOptions.SO_LINGER, LINGER_TIME);
                    } else if (key.isReadable()) {//read
                        receive(key);//start quit
                    } else if (key.isWritable()) {//write
                        send(key);//session

                    }

                }
                //removed the backdoor monitor due to not enough time. leave backdoor with NIO for further development
                //TODO:Implement backdoor feature in the furture
               /* //backdoor monitor
                Thread stop= new Thread(new Stop(clientPrint,clientReceive,handler));
                stop.start();*/

            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (CancelledKeyException exception) {

        }
    }

    /* Method */
    public void selectorWake() {
        selector.wakeup();

    }

    public void cGameReset(SelectionKey key) {
        Client client = (Client) key.attachment();//session define
        client.gameReset();
    }

    public void cPhaseIncrement(SelectionKey key) {
        Client client = (Client) key.attachment();//session define
        client.phaseIncrement();
    }

    public void sendQ(SelectionKey key, String msg, SocketChannel listendingChannel) {
        Client client = (Client) key.attachment();//session define
        Queue<String> queue = client.getQ();
        synchronized (queue) {
            queue.add(msg);
        }
        sendflag = true;
        pending.add(new PendingRequest(listendingChannel, SelectionKey.OP_WRITE));
        server.selectorWake();

    }

    private void receive(SelectionKey key) throws IOException {
        Client client = (Client) key.attachment();//session define
        client.receive(key);
        key.interestOps(SelectionKey.OP_WRITE);

    }

    private void send(SelectionKey key) throws IOException {
        Client client = (Client) key.attachment();//session define
        try {
            client.sendAll();
            key.interestOps(SelectionKey.OP_READ);
        } catch (Exception e) {
            selector.wakeup();
            return;
        }
    }


//init method

    private void initSelect() throws IOException {
        selector = Selector.open();
    }

    private void initChannel(int port) throws IOException {
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);//accept new connection
    }

    /*
    Reference from:http://rox-xmlrpc.sourceforge.net/niotut/
    solve the deadlock
     */
    private class PendingRequest {
        //init
        public SocketChannel socket;
        public int ops;

        public PendingRequest(SocketChannel socket, int ops) {
            this.socket = socket;
            this.ops = ops;
        }
    }

    /*
    end
     */
    //client per game
    private class Client {
        //init
        private Hanger handler;
        private Queue<String> queue = new ArrayDeque<>();
        private int phase = 0;

        //constuctor
        private Client(Hanger handler) {
            this.handler = handler;

        }

        /* Method */
        private void sendAll() throws IOException {
            String msg = null;
            synchronized (queue) {
                while ((msg = queue.peek()) != null) {
                    handler.send(msg);
                    queue.remove();
                }
            }
        }

        private void receive(SelectionKey key) throws IOException {
            handler.receive(phase, key);

        }

        private Queue getQ() {
            return queue;
        }

        private int getPhase() {
            return phase;
        }

        private void gameReset() {
            phase = 0;//2= game start
        }

        private void phaseIncrement() {
            phase++;
        }


    }
}

package Server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;


public class Hanger implements Runnable {
    private static ArrayList<String> libraryLst = null;
    public String currentQuestion = null;
    //init
    private int score;
    private SocketChannel serverChannel = null;
    private ByteBuffer buff = ByteBuffer.allocateDirect(8192);
    private Server server = null;
    private SelectionKey key = null;
    private String in = "";
    private Random b = new Random();
    private int phase = 0;
    private int strLength = 0;
    private int currentChance = 0;
    private boolean run = true;

    //constructor
    Hanger(SocketChannel serverChannel, ArrayList<String> libraryLst, Server server) throws IOException {
        this.serverChannel = serverChannel;
        this.libraryLst = libraryLst;
        score = 0;
        this.server = server;
    }

    /*
    Reference from:
    https://www.tutorialspoint.com/javaexamples/string_removing_char.htm
     */
    public static String removeCharAt(String currentQuestion, String currentAnswer) {
        String input = currentQuestion;
        while (input.contains(currentAnswer)) {
            input = input.substring(0, input.indexOf(currentAnswer)) + input.substring(input.indexOf(currentAnswer) + 1);
        }
        return input;
    }

    //START METHOD
    public Thread start(String in, int phase, SelectionKey key, Server server) {
        this.in = in;
        this.phase = phase;
        this.key = key;
        this.server = server;
       /* Thread run =new Thread(this);
        run.start();*/
        ForkJoinPool.commonPool().execute(this);
        return null;
        //ForkJoinPool.commonPool().execute(this);

        // Thread run= new Thread(this);
    }

    public void send(String content) {
        ByteBuffer sendBuff = ByteBuffer.wrap(content.getBytes());
        try {
            int wrote = serverChannel.write(sendBuff);
            System.out.println("wrote " + wrote + " byte.");

            sendBuff.clear();
        } catch (IOException e) {
            clientKill();

        }

    }

    public void receive(int phase, SelectionKey key) {
        try {
            int length = 0;
            synchronized (buff) {
                synchronized (serverChannel) {
                    buff.clear();
                    length = serverChannel.read(buff);
                    buff.flip();
                }
            }

            byte[] bytes = new byte[buff.remaining()];
            buff.get(bytes);
            if (length == -1) {
                clientKill();
                throw new IOException("Client has closed connection.");
            }
            System.out.println("Received " + bytes.length + " byte.");

            String str = new String(bytes);
            //syscommand
            if (str.compareTo("thread") == 0) {
                int nbRunning = 0;
                for (Thread t : Thread.getAllStackTraces().keySet()) {
                    if (t.getState() == Thread.State.RUNNABLE) nbRunning++;
                }
                System.err.println("Currently thread in VM: " + Thread.getAllStackTraces().keySet().size());
                System.err.println("Currently executing thread: " + nbRunning);
            } else if (str.compareTo("client") == 0) {
                System.err.println("Currently running user handler: " + hashCode());
                System.err.println("Currently user question: " + currentQuestion);
            }
            start(str, phase, key, server);

        } catch (IOException e) {
            System.err.println(e);
        }
    }

    public void updateQuestion() {
        currentQuestion = libraryLst.get(b.nextInt(libraryLst.size() - 1));//length
        System.out.println("Currnt word: " + currentQuestion);
        strLength = currentQuestion.length();
        currentChance = strLength;
    }

    @Override
    public void run() {

        //TODO: IMPROVE AND REFACTOR LATER!!
        try {
            System.out.println("System message: " + "Current in phase " + phase + ".");
            if (phase == 0) {
                //start
                if (!(in.compareTo("start") == 0) && !(in.compareTo("quit") == 0)) {
                    server.sendQ(key, "wrongcommand", serverChannel);//session
                    System.err.println("Wrong input command!");
                } else {
                    if (in.compareTo("start") == 0) {
                        updateQuestion();
                        server.sendQ(key, new Integer(strLength).toString(), serverChannel);//send length
                        server.cPhaseIncrement(key);

                    } else if (in.compareTo("quit") == 0) {
                        //TODO:QUIT
                        server.cGameReset(key);
                        currentChance = 0;
                        strLength = 0;
                        phase = 0;
                        send("quittrigger");
                        clientKill();

                    }
                }
            } else if (phase == 1) {
                //GAME START
                //start testing answeres
                if (currentChance > 0 && strLength > 0) {
                    String currentAnswer = "";
                    synchronized (in) {
                        synchronized (currentAnswer) {
                            currentAnswer = in;//receive answer
                        }
                    }
                    if (currentAnswer != null) {//do not handle null input from client
                        System.out.println("User Input: " + currentAnswer);
                    }
                    //currect answer
                    try {
                        //result
                        synchronized (currentAnswer) {
                            if (currentAnswer.length() > 1 && currentQuestion.compareTo(currentAnswer) == 0) {//guessed out the whole word
                                currentQuestion = "";
                                strLength = currentQuestion.length();
                                System.out.println("User guessed out the whole word!");
                                server.sendQ(key, "KO", serverChannel);//guess feedback
                                server.cPhaseIncrement(key);
                                //break;
                            } else if (currentQuestion.contains(currentAnswer)) {//guessed correct one letter
                                currentQuestion = removeCharAt(currentQuestion, currentAnswer);
                                System.out.println("The answer is correct!");//guess feedback
                                strLength = currentQuestion.length();
                                if (strLength == 0) {
                                    server.sendQ(key, "end", serverChannel);//END OF THE GAME
                                    server.cPhaseIncrement(key);
                                } else {
                                    server.sendQ(key, "correct", serverChannel);
                                }
                            } else {//wrong guess
                                System.out.println("The answer is wrong!");
                                server.sendQ(key, "wrong", serverChannel);//guess feedback
                            }
                        }
                    } catch (NullPointerException e) {

                    }
                    System.out.println("Remained Questions: " + currentQuestion);
                    strLength = currentQuestion.length();
                    currentChance--;
                } else {
                    server.sendQ(key, "end", serverChannel);//END OF THE GAME
                    server.cPhaseIncrement(key);

                }
            } else if (phase == 2) {
                //GAME RESULT
                //flag=OP.Write
                if (strLength != 0) {
                    server.sendQ(key, "lose", serverChannel);//LOSE
                    System.out.println("User lose!");

                } else {
                    score++;
                    server.sendQ(key, "win", serverChannel);//WIN
                    System.out.println("User won!");
                }
                server.cPhaseIncrement(key);

            } else if (phase == 3) {
                System.out.println(in);
                server.sendQ(key, new Integer(score).toString(), serverChannel);
                server.cGameReset(key);
                currentChance = 0;
                strLength = 0;
                in = "";
                phase = 0;
                System.out.println("Session reseted!");

            }

        } catch (NullPointerException e) {
            e.printStackTrace();
/*            System.err.println();
            System.err.println("======================================================================");
            System.err.println("Server.Server has terminate the connection due to client not reachable issue!");
            System.err.println("======================================================================");*/
        } catch (Exception a) {

            clientKill();
        }
        return;
    }

    //kill the client
    //TODO: LOUSY IMPLEMENTATION. IMPROVE IT!!!
    private void clientKill() {
        System.err.println(key.attachment().hashCode() + " user disconnected");
        try {
            serverChannel.close();
            key.channel().close();
        } catch (IOException E) {

        }
        key.cancel();
        server.selectorWake();

    }


    //debugprivate
   /* public static void main(String[] args) {
        try{
            loadDictionary("Resources/words.txt");
        }catch (IOException e){
            System.err.println(e);
        }
    }*/
}

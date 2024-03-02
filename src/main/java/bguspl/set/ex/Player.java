package bguspl.set.ex;

import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    //new
    private ArrayBlockingQueue<Integer> actionsQueue;

    private volatile int setStatus;
    private Object playerLock;
    private Dealer dealer;
    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {                                                       //fix
        this.env = env;
        this.dealer = dealer;
        this.table = table;
        this.id = id;
        this.human = human;
        actionsQueue = new ArrayBlockingQueue<>(3);
        this.setStatus = 0;
        this.playerLock = new Object();
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            // TODO implement main player loop
            try {
                Integer slotAction = null;
                slotAction = actionsQueue.take();
                table.playerLock();
                boolean set = table.keyPressed(slotAction, id);
                table.playerUnlock();
                if(set){
                    dealer.declareSet(id);
                    if(setStatus == 1)
                        point();
                    else if(setStatus == -1)
                        penalty();
                }
            } catch (InterruptedException ignored) {}
        }    
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    public Object getPlayerLock(){
        return playerLock;
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                Random randomNumber = new Random();
                try {
                    actionsQueue.put(randomNumber.nextInt(env.config.tableSize));
                } catch (InterruptedException ignored) {} //generate a random key press
                
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        terminate = true;
        if(!human)
            aiThread.interrupt();
        playerThread.interrupt();
        try{
            playerThread.join();
        } catch (InterruptedException ignored) {}
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement
        if(human){
            try {
                actionsQueue.put(slot);
            } catch (InterruptedException ignored) {}
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {                                                       //fix
        // TODO implement
        try{    
            this.score++;
            env.ui.setScore(id, score);
            Thread.sleep(env.config.pointFreezeMillis);
            setStatus = 0;
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        //int ignored = table.countCards(); // this part is just for demonstration in the unit tests
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public synchronized void penalty() {                                                       //fix
        // TODO implement
        try{
            long endFreezeTime = System.currentTimeMillis()+env.config.penaltyFreezeMillis;
            env.ui.setFreeze(id, env.config.penaltyFreezeMillis);
            while(System.currentTimeMillis()<endFreezeTime){  
                Thread.sleep(1000);
                env.ui.setFreeze(id, endFreezeTime-System.currentTimeMillis());
            }
            setStatus = 0; 
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    public int score() {
        return score;
    }

    public Thread getPlayerThread(){                                                       //fix
        return playerThread;
    }

    public void setStatus(int status){
        setStatus = status;
    }
}

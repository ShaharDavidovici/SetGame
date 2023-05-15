package bguspl.set.ex;

import bguspl.set.Env;

import java.util.LinkedList;
import java.util.Queue;


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
    private volatile Thread playerThread;

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
    protected volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;


    Queue<Integer> actions;

    public volatile boolean isPunished;

    public Queue<Integer> tokens;

    private Dealer dealer2;

    public volatile boolean gotPoint;

    private Thread dealerThread;

    public Object lockPlayers;

    public volatile boolean isAwake;

    private static final int ACTION_QUEUQ_SIZE = 3;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;

        actions = new LinkedList<Integer>();
        isPunished = false;
        tokens = new LinkedList<Integer>();
        dealer2 = dealer;
        gotPoint = false;
        isAwake = true;
        lockPlayers = new Object();
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            // TODO implement main player loop
            
            if(isPunished || gotPoint){ 
                synchronized(this){ // we need this to happen line by line
                    if(isPunished){
                        try {
                            for(int i = (int)(env.config.penaltyFreezeMillis/1000); i>0; i--){
                                 
                                env.ui.setFreeze(id, i * 1000);
                                playerThread.sleep(1000);

                            }
                            env.ui.setFreeze(id, -1); // making it black again

                        } catch (InterruptedException ignored) {}
                        isPunished = false;
                    }
                    if(gotPoint){
                    
                        try {
                            for(int i = (int)(env.config.pointFreezeMillis/1000); i>0; i--){
                                
                                env.ui.setFreeze(id, i * 1000);
                                playerThread.sleep(1000);

                            }
                            env.ui.setFreeze(id, -1); // making it black again

                    } catch (InterruptedException ignored) {}
                    gotPoint = false;
                    }
                }
            }

            while(actions.isEmpty() && !terminate){ // if we didnt press we go to sleep
                synchronized(lockPlayers){ // waits until keyPressed
                    try{
                        while(actions.isEmpty() && !terminate){
                            isAwake = false;
                            lockPlayers.wait();
                            isAwake = true;
                            
                        }
                    }
                    catch(InterruptedException ignored){}
                    
                }
            }

            if((!actions.isEmpty()) && dealer2.getCanPutToken()){

                int slot = -1;
                synchronized(table){ // make sure we polls non null pointer
                    if(!actions.isEmpty())
                        slot = actions.poll();
                }
                
                if(slot != -1){ // if we polled a legal action
                    if(tokens.contains(slot)){
                        tokens.remove(slot);
                        table.removeToken(id, slot);
                    }
                    else if(tokens.size() < env.config.featureSize){
                        tokens.add(slot);
                        table.placeToken(id, slot);
                    }
                    if(tokens.size() == env.config.featureSize){ 

                        synchronized(this){
                            dealer2.requests.add(this);
                            dealerThread.interrupt(); // allerting the dealer its waky waky time
                        
                            try{
                                isAwake = false;
                                this.wait(); // until the dealer checks our set
                                isAwake = true;
                            }
                            catch(InterruptedException ignored){}
                        }
                    }
                }
            }

        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                // TODO implement player key press simulator
                keyPressed((int)(Math.floor(Math.random()*(env.config.tableSize))));
            }
            env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        terminate = true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement
         synchronized(table){ // can do it only if the table is available
            
            if(table.slotToCard[slot] != null && !isPunished && !gotPoint && dealer2.getCanPutToken()){

                if(actions.size() < ACTION_QUEUQ_SIZE && !(tokens.size() == env.config.featureSize && !tokens.contains(slot))){ // if we want to remove
                    actions.add(slot);
                }
    
            }
        }
        synchronized(lockPlayers){ // waking the player

            lockPlayers.notify();

        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement

        actions.clear();
        tokens.clear();
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
        gotPoint = true;
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement
        isPunished = true;
    }

    public int score() {
        return score;
    }

    public void setDealerThread(){
        this.dealerThread = Thread.currentThread();
    }
}

package bguspl.set.ex;

import bguspl.set.Env;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;



/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    protected volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    private volatile Boolean canPutToken;


    /* 
     * array of Player Threads
    */
    public Thread[] playerThreads;

    public Queue<Player> requests;


    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());

        reshuffleTime = env.config.turnTimeoutMillis;
        playerThreads = new Thread[players.length];
        requests = new LinkedList<Player>();
        canPutToken = true;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {

        // so the players can wake the dealer
        for(Player p : players){
            p.setDealerThread();
        }

        for(int i=0; i<playerThreads.length; i++){
            playerThreads[i] = new Thread(players[i], "Player ID: "+i);
            playerThreads[i].start();           
        }


        env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
        while (!shouldFinish()) {
            
            placeCardsOnTable();
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis; // Starting the timer
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        terminate();
        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        for(int i = playerThreads.length -1; i>=0; i--){ //running backwards
            try {
                players[i].terminate(); 

                if(!players[i].isAwake) {// if its in wait
                    playerThreads[i].interrupt();
                }
                
                playerThreads[i].join(); 
           } catch (InterruptedException ignored) {} 
        }
        terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        // TODO implement
        if(!terminate && !requests.isEmpty()){

            Player curPlayer = requests.poll();
            if(curPlayer != null){
                synchronized(curPlayer){ // we freeze the player while checking its set
                    if(curPlayer.tokens.size() == env.config.featureSize){ // checks if non of the tokens removed
                    
                        int[] checkSet = new int[env.config.featureSize]; // well copy the set of the player
                        Iterator<Integer> iter = curPlayer.tokens.iterator();
                        int j=0;
                        
                        boolean relevent =true;
                        while(iter.hasNext() & relevent){
                            checkSet[j] = iter.next();
                            if(table.slotToCard[checkSet[j]] == null) // checks if another player already sent a set with this card
                                relevent = false;
                            j++;
                        }
    
                        if(relevent && isASet(checkSet)){
    
                            for(int i=0; i<env.config.featureSize; i++){ // removing the set from the table
                                
                                env.ui.removeTokens(checkSet[i]);
                                table.removeCard(checkSet[i]);
                            }
        
        
                            for(Player some: players){ // removes from other players the tokens on the cards of this set
                                for(int i = 0; i <env.config.featureSize; i++){
                                    if(some.tokens.contains(checkSet[i])){
                                        some.tokens.remove(checkSet[i]);
                                    }
                                }
                            }
                            curPlayer.point();
        
                            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis; // restart the timer
                            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);
                        }
                        else if(relevent){
                            curPlayer.penalty();
                        }
                    }
                    curPlayer.notifyAll(); // wakes the player
                }
            }
            
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    protected void placeCardsOnTable() {
        // TODO implement
        synchronized(table){ // so the players wont touch the table

            for(int i = 0; i < env.config.tableSize; i++){
                int cardNum = (int)Math.floor(Math.random()*(env.config.deckSize));
                if(table.slotToCard[i] == null){
                    while(!deck.contains(cardNum) && !deck.isEmpty())
                        cardNum = (int)Math.floor(Math.random()*(env.config.deckSize));
                    if(!deck.isEmpty()){
                        table.placeCard(cardNum, i);
                        deck.remove(deck.indexOf(cardNum));
                    }
                }
            }
            if(!canPutToken){ //removes the actions during the card placing 
                for(Player p : players){
                    p.actions.clear();
                }
            }
            canPutToken = true; // letting the players know they can continue playing
        }

    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
        if(((reshuffleTime - System.currentTimeMillis()) >= 10000)){
            try {
                Thread.currentThread().sleep(50); 
           } catch (InterruptedException ignored) {} 
        }

        else if(((reshuffleTime - System.currentTimeMillis()) <= 10000) && 20<=(reshuffleTime - System.currentTimeMillis())){
            try {
                Thread.currentThread().sleep(10); 
           } catch (InterruptedException ignored) {} 
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        if((reshuffleTime - System.currentTimeMillis()) <= 0){
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);
        }
        else if((reshuffleTime - System.currentTimeMillis()) <= 10000)
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), true);
        else
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);   
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    protected void removeAllCardsFromTable() {
        // TODO implement
        synchronized(table){ // so the players wont touch the table
            canPutToken = false;
            env.ui.removeTokens();
        
            for(Player p: players){ //removes the actions until the card placing 
                p.actions.clear();
                p.tokens.clear();
            }
            for(int i=0; i < env.config.tableSize; i++){
                if(table.slotToCard[i] != null){
                    deck.add(table.slotToCard[i]);
                    table.removeCard(i);
                }
            }
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        int maxScore = 0;
        for(Player p: players){
            if(p.score() > maxScore)
                maxScore = p.score();
        }
        int winSize = 0;
        for(Player p: players){
            if(p.score() == maxScore)
                winSize++;
        }
        int[] winners = new int[winSize];
        int i=0;
        for(Player p: players){
            if(p.score() == maxScore){
                winners[i] = p.id;
                i++;
            }
        }
        env.ui.announceWinner(winners);
        
    }

    public boolean isASet(int[] checkSet){

        int[] cards = new int[env.config.featureSize];
        for(int i=0; i<3; i++){
            cards[i] = table.slotToCard[checkSet[i]];
        }
        
        return env.util.testSet(cards);
    }

    public Boolean getCanPutToken(){
        return canPutToken;
    }
}

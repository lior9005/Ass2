package bguspl.set.ex;

import bguspl.set.Env;

import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Vector;
import java.util.concurrent.ArrayBlockingQueue;


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
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    /** new
     * The thread representing the current player.
     */
    private Thread dealerThread;

    private Vector<Integer> waitingSets;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.waitingSets = new Vector<Integer>();
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {                                                                                   //fix
        dealerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        table.dealerLock();
        for(int i=0; i<players.length; i++){       //new for loop
            Thread playerT = new Thread(players[i], "player " + (i+1));
            playerT.start();
        }
        updateTimerDisplay(true);
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(true);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {                                                                          //fix
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            table.dealerUnlock();
            sleepUntilWokenOrTimeout();
            table.dealerLock();
            //updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }
//
    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        terminate = true;
        for(int i = players.length-1; i>=0; i--){
            players[i].terminate();
        }
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
    private void removeCardsFromTable() {                                                       //fix
        // TODO implement
        if(!waitingSets.isEmpty()){
            checkSetVector();
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private synchronized void placeCardsOnTable() {
        // TODO implement            
        int numOfCards = table.countCards();
        int slot = 0;
        Integer[] board = table.getSlotToCard();
        boolean cardsAdded = !deck.isEmpty() && numOfCards < board.length;
        while(!deck.isEmpty() && numOfCards < board.length){
            if(board[slot] == null){
                Random random = new Random();
                int randomIndex = random.nextInt(deck.size());
                int randomCard = deck.remove(randomIndex);
                table.placeCard(randomCard, slot);
                numOfCards++;
            }
            slot++;
        }
        updateTimerDisplay(cardsAdded);
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
        try{
            if(reshuffleTime-System.currentTimeMillis()<env.config.turnTimeoutWarningMillis){
                Thread.sleep(10);
                updateTimerDisplay(false);
            }
            else{
                Thread.sleep(1000);
                updateTimerDisplay(false);
            }
        }   catch (InterruptedException ignored) {

        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        if(reset)
            reshuffleTime = System.currentTimeMillis()+ env.config.turnTimeoutMillis;
        env.ui.setCountdown(reshuffleTime-System.currentTimeMillis(), reshuffleTime-System.currentTimeMillis()<=env.config.turnTimeoutWarningMillis);                
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
        for(int slot=0; slot<env.config.tableSize; slot++){
            if(table.cardAtSlot(slot) != null){
                deck.add(table.cardAtSlot(slot));
                table.removeCard(slot);
            }  
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        int maxScore = 0;
        int amount = 0;
        terminate();
        for(Player player : players){
            if(player.score()> maxScore){
                maxScore = player.score();
                amount = 1;
            }
            else if(player.score() == maxScore)    
                amount++;
        }
        int[] winners = new int[amount];
        int index = 0;
        for(Player player : players){
            if(player.score()== maxScore){
                winners[index]= player.id;
                index++;
            }
        }
        env.ui.announceWinner(winners);
    }

    public void declareSet(int playerID){                           //maybe after adding to the vector, the dealer will check the set before the playerthread wil wait...
        waitingSets.add(playerID);
        try{
            Object lock = players[playerID].getPlayerLock();
            synchronized(lock){
                dealerThread.interrupt();
                lock.wait();
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    public void checkSetVector() {  //new function
        while(!waitingSets.isEmpty()){
            int playerID = waitingSets.remove(0);
            if(table.numOfTokens(playerID) == 3){
                int[] set = new int[3];
                int index=0;
                for(int i = 0 ; i< env.config.tableSize; i++){
                    if(table.playerTokens[i][playerID]){
                        set[index] = table.slotToCard[i];
                        index++;
                    }
                }
                if(env.util.testSet(set))
                    correctSet(set, playerID);
                else
                    incorrectSet(playerID);
            }
            else{
                Object lock = players[playerID].getPlayerLock();
                synchronized(lock){
                    lock.notifyAll();
                }
            }
        }
    }

    private void correctSet(int[] set, int playerID){   //new function
        for(int card : set){
            table.removeCard(table.cardToSlot[card]);
        }
        players[playerID].setStatus(1);
        Object lock = players[playerID].getPlayerLock();
        synchronized(lock){
            lock.notifyAll();
        }
    }

    private void incorrectSet(int playerID){    //new function
        players[playerID].setStatus(-1);
        Object lock = players[playerID].getPlayerLock();
        synchronized(lock){
            lock.notifyAll();
        }
    }
}

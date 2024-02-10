package bguspl.set.ex;

import bguspl.set.Env;

import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        dealerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        for(int i=0; i<players.length; i++){       //new for loop
            Thread playerT = new Thread(players[i], "player " + (i+1));
            playerT.start();
        }
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
    private void timerLoop() {
        reshuffleTime = System.currentTimeMillis()+ env.config.turnTimeoutMillis;
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(!(System.currentTimeMillis() < reshuffleTime));
            //removeCardsFromTable();
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
        for(Player player : players){
            player.terminate();
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
                                            /*private void removeCardsFromTable() {
                                                // TODO implement
                                                for (int slot = 0; slot < env.config.tableSize; slot++) {
                                                    if(table.cardAtSlot(slot) != null)
                                                        deck.add(table.cardAtSlot(slot));
                                                    table.removeCard(slot);
                                                }
                                            }*/

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
        int numOfCards = table.countCards();
        int slot = 0;
        Integer[] board = table.getSlotToCard();
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
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
        try{
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {}
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        if(reset){
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
        }
        else{
            long currentTimer = reshuffleTime-System.currentTimeMillis();
            env.ui.setCountdown(currentTimer, currentTimer<=env.config.turnTimeoutWarningMillis);
        }
            
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
                for(int i=0; i<table.playerTokens[slot].length; i++){
                    if(table.removeToken(i, slot)){
                        players[i].decreaseCounter();
                    }
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
        int amount = 0;
        for(Player player : players){
            player.terminate();
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
        terminate();
    }

    public synchronized void checkSet(int playerID) {  //new function
        synchronized(table){
            if(players[playerID].getCounter() == 3){
                int[] set = new int[3];
                int j=0;
                for(int i = 0 ; i< env.config.tableSize; i++){
                    if(table.playerTokens[i][playerID])
                        set[j] = table.slotToCard[i];
                        j++;
                }
                if(env.util.testSet(set))
                    correctSet(set, playerID);
                else
                    incorrectSet(playerID);
            }
            dealerThread.interrupt();
        }
    }

    private void correctSet(int[] set, int playerID){   //new function
        for(int card : set){
            int slot = table.cardToSlot[card];
            table.removeCard(slot);
            for(int i=0; i<table.playerTokens[slot].length; i++){
                if(table.removeToken(i, slot)){
                    players[i].decreaseCounter();
                }
            }
        }
        players[playerID].point();
        reshuffleTime = System.currentTimeMillis()-1;
    }

    private void incorrectSet(int playerID){    //new function
        players[playerID].penalty();
    }
}

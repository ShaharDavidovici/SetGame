package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DealerTest {

    Table table;

    private Integer[] slotToCard;
    private Integer[] cardToSlot;

    Dealer dealer;
    private Player player;
    private Player[] players;



    @BeforeEach
    void setUp() {

        Properties properties = new Properties();

        properties.put("ComputerPlayers", "1");
        properties.put("HumenPlayers", "0");

        properties.put("Rows", "2");
        properties.put("Columns", "2");
        properties.put("FeatureSize", "3");
        properties.put("FeatureCount", "4");
        properties.put("TableDelaySeconds", "0");
        properties.put("PlayerKeys1", "81,87,69,82");
        properties.put("PlayerKeys2", "85,73,79,80");
        MockLogger logger = new MockLogger();
        Config config = new Config(logger, properties);
        slotToCard = new Integer[config.tableSize];
        cardToSlot = new Integer[config.deckSize];

        Env env = new Env(logger, config, new MockUserInterface(), new MockUtil());
        player = new Player(env, dealer, table, 0, false);
        players = new Player[1];
        players[0] = player;
        table = new Table(env, slotToCard, cardToSlot);
        dealer = new Dealer(env, table, players);
    }

    static class MockUserInterface implements UserInterface {
        @Override
        public void dispose() {}
        @Override
        public void placeCard(int card, int slot) {}
        @Override
        public void removeCard(int slot) {}
        @Override
        public void setCountdown(long millies, boolean warn) {}
        @Override
        public void setElapsed(long millies) {}
        @Override
        public void setScore(int player, int score) {}
        @Override
        public void setFreeze(int player, long millies) {}
        @Override
        public void placeToken(int player, int slot) {}
        @Override
        public void removeTokens() {}
        @Override
        public void removeTokens(int slot) {}
        @Override
        public void removeToken(int player, int slot) {}
        @Override
        public void announceWinner(int[] players) {}
    };

    static class MockUtil implements Util {
        @Override
        public int[] cardToFeatures(int card) {
            return new int[0];
        }

        @Override
        public int[][] cardsToFeatures(int[] cards) {
            return new int[0][];
        }

        @Override
        public boolean testSet(int[] cards) {
            return false;
        }

        @Override
        public List<int[]> findSets(List<Integer> deck, int count) {
            return null;
        }

        @Override
        public void spin() {}
    }

    static class MockLogger extends Logger {
        protected MockLogger() {
            super("", null);
        }
    }

    @Test
    void isPlaced(){
        dealer.placeCardsOnTable();
        assertEquals(4, table.countCards());
    }               

    @Test
    void isRemoved(){
        dealer.placeCardsOnTable();
        dealer.removeAllCardsFromTable();
        assertEquals(0, table.countCards());
    }

}

package jskills.elo;

import jskills.*;
import org.junit.Test;

import java.util.Collection;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class DuellingEloTest {
    private final static double ErrorTolerance = 0.1;

    @Test
    public void twoOnTwoDuellingTest() {
        DuellingEloCalculator calculator = new DuellingEloCalculator(new GaussianEloCalculator());

        GameInfo gameInfo = GameInfo.getDefaultGameInfo();

        Player<Integer> player1 = new PlayerInfo<Integer>(1);
        Player<Integer> player2 = new PlayerInfo<Integer>(2);


        Team team1 = new TeamInfo()
            .addPlayer(player1, gameInfo.getDefaultRating())
            .addPlayer(player2, gameInfo.getDefaultRating());

        Player<Integer> player3 = new PlayerInfo<Integer>(3);
        Player<Integer> player4 = new PlayerInfo<Integer>(4);

        Team team2 = new TeamInfo()
                    .addPlayer(player3, gameInfo.getDefaultRating())
                    .addPlayer(player4, gameInfo.getDefaultRating());

        Collection<Team> teams = TeamInfo.concat(team2, team1);
        Map<Player, Rating> newRatingsWinLose = calculator.calculateNewRatings(gameInfo, teams, 2, 1);

        // TODO: Verify?
        AssertRating(37, newRatingsWinLose.get(player1));
        AssertRating(37, newRatingsWinLose.get(player2));
        AssertRating(13, newRatingsWinLose.get(player3));
        AssertRating(13, newRatingsWinLose.get(player4));

        double quality = calculator.calculateMatchQuality(gameInfo, teams);
        assertEquals(1.0, quality, 0.001);
    }

    private static void AssertRating(double expected, Rating actual) {
        assertEquals(expected, actual.getMean(), ErrorTolerance);
    }
}
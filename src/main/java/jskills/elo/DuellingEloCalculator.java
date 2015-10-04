package jskills.elo;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jskills.GameInfo;
import jskills.PairwiseComparison;
import jskills.Player;
import jskills.PlayerInfo;
import jskills.RankSorter;
import jskills.Rating;
import jskills.SkillCalculator;
import jskills.Team;
import jskills.TeamInfo;
import jskills.numerics.MathUtils;
import jskills.numerics.Range;

public class DuellingEloCalculator extends SkillCalculator {

    private final TwoPlayerEloCalculator twoPlayerEloCalc;

    public DuellingEloCalculator(TwoPlayerEloCalculator twoPlayerEloCalculator) {
        super(EnumSet.noneOf(SupportedOptions.class), Range.<Team>atLeast(2), Range.<Player>atLeast(1));
        twoPlayerEloCalc = twoPlayerEloCalculator;
    }

    @Override
    public Map<Player, Rating> calculateNewRatings(GameInfo gameInfo,
            Collection<Team> teams, int... teamRanks) {
        // On page 6 of the TrueSkill paper, the authors write:
        /* "When we had to process a team game or a game with more than two 
         * teams we used the so-called *duelling* heuristic: For each player, 
         * compute the Δ's in comparison to all other players based on the team 
         * outcome of the player and every other player and perform an update 
         * with the average of the Δ's." 
         */
        // This implements that algorithm.

        validateTeamCountAndPlayersCountPerTeam(teams);
        List<Team> teamsl = RankSorter.sort(teams, teamRanks);
        Team[] teamsList = teamsl.toArray(new Team[0]);

        Map<Player, Map<Player, Double>> deltas = new HashMap<Player, Map<Player, Double>>();

        for (int ixCurrentTeam = 0; ixCurrentTeam < teamsList.length; ixCurrentTeam++) {
            for (int ixOtherTeam = 0; ixOtherTeam < teamsList.length; ixOtherTeam++) {
                if (ixOtherTeam == ixCurrentTeam) {
                    // Shouldn't duel against ourself ;)
                    continue;
                }

                Team currentTeam = teamsList[ixCurrentTeam];
                Team otherTeam = teamsList[ixOtherTeam];

                // Remember that bigger numbers mean worse rank (e.g. other-current is what we want)
                PairwiseComparison comparison = PairwiseComparison.fromMultiplier((int) Math.signum(teamRanks[ixOtherTeam] - teamRanks[ixCurrentTeam]));

                for (Player currentTeamPlayer : currentTeam.getPlayers()) {
                    for (Player otherTeamPlayer: otherTeam.getPlayers()) {
                        updateDuels(gameInfo, deltas,
                                currentTeamPlayer,
                                currentTeam.getRating(currentTeamPlayer),
                                otherTeamPlayer,
                                otherTeam.getRating(otherTeamPlayer),
                                comparison);
                    }
                }
            }
        }
        
        Map<Player, Rating> result = new HashMap<Player, Rating>();

        for (Team currentTeam : teamsList) {
            for (Player currentTeamPlayer : currentTeam.getPlayers()) {
                double currentPlayerAverageDuellingDelta = MathUtils.mean(deltas.get(currentTeamPlayer).values());
                result.put(currentTeamPlayer, new EloRating(currentTeam.getRating(currentTeamPlayer).getMean() + currentPlayerAverageDuellingDelta));
            }
        }

        return result;
    }
    
    private void updateDuels(GameInfo gameInfo,
            Map<Player, Map<Player, Double>> duels, Player player1,
            Rating player1Rating, Player player2, Rating player2Rating,
            PairwiseComparison weakToStrongComparison) {
        
        Map<Player, Rating> duelOutcomes = twoPlayerEloCalc.calculateNewRatings(gameInfo,
            TeamInfo.concat(new TeamInfo().addPlayer(player1, player1Rating), new TeamInfo().addPlayer(player2, player2Rating)),
            (weakToStrongComparison == PairwiseComparison.WIN) ? new int[] { 1, 2 } :
                (weakToStrongComparison == PairwiseComparison.LOSE) ? new int[] { 2, 1 } :
                    new int[] { 1, 1});

        updateDuelInfo(duels, player1, player1Rating, duelOutcomes.get(player1), player2);
        updateDuelInfo(duels, player2, player2Rating, duelOutcomes.get(player2), player1);
    }

    private static void updateDuelInfo(
            Map<Player, Map<Player, Double>> duels, Player self,
            Rating selfBeforeRating, Rating selfAfterRating, Player opponent) {
        Map<Player, Double> selfToOpponentDuelDeltas = duels.get(self);

        if (selfToOpponentDuelDeltas == null) {
            selfToOpponentDuelDeltas = new HashMap<Player, Double>();
            duels.put(self, selfToOpponentDuelDeltas);
        }

        selfToOpponentDuelDeltas.put(opponent, selfAfterRating.getMean() - selfBeforeRating.getMean());
    }

    @Override
    public double calculateMatchQuality(GameInfo gameInfo,
            Collection<Team> teams) {
        // HACK! Need a better algorithm, this is just to have something there and it isn't good
        double minQuality = 1.0;

        Team[] teamList = teams.toArray(new Team[0]);

        for (int ixCurrentTeam = 0; ixCurrentTeam < teamList.length; ixCurrentTeam++) {
            EloRating currentTeamAverageRating = new EloRating(Rating.calcMeanMean(teamList[ixCurrentTeam].getRatings()));;
            Team currentTeam = new TeamInfo().addPlayer(new PlayerInfo(ixCurrentTeam), currentTeamAverageRating);

            for (int ixOtherTeam = ixCurrentTeam + 1; ixOtherTeam < teamList.length; ixOtherTeam++) {
                EloRating otherTeamAverageRating = new EloRating(Rating.calcMeanMean(teamList[ixOtherTeam].getRatings()));
                Team otherTeam = new TeamInfo().addPlayer(new PlayerInfo(ixOtherTeam), otherTeamAverageRating);

                minQuality = Math.min(minQuality,
                                      twoPlayerEloCalc.calculateMatchQuality(gameInfo,
                                                                              TeamInfo.concat(currentTeam, otherTeam)));
            }
        }

        return minQuality;
    }
}
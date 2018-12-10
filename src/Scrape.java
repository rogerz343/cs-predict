import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

// TODO: fix problem where Windows doesn't allow filenames to be case-insensitive-equal

public class Scrape {
	
	public static final String RESULTS_PAGE = "https://www.hltv.org/results?offset=";
	public static final String MATCHES_DIR = "./match_pages";
	
	public static void main(String[] args) {
		try {
			// fetchUrls();
			// fetchMatches();
			// extractMatchData();
			savePlayerData();
			// generateFeaturesLabels();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Saves a list of match URLs to the file "./match_urls.txt". One URL for each line in the file.
	 * @throws IOException
	 */
	public static void fetchUrls() throws Exception {
		BufferedWriter bw = new BufferedWriter(new FileWriter("./match_urls.txt"));
		for (int i = 100; i <= 20000; i += 100) {
			System.out.println(i);
			Document doc = Jsoup.connect(RESULTS_PAGE + Integer.toString(i))
					.userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
					.referrer("http://www.google.com")
					.get();
			
			Elements matchRows = doc.getElementsByClass("result-con");
			for (Element e : matchRows) {
				bw.write(e.getElementsByTag("a").get(0).attr("abs:href"));
				bw.write("\n");
			}
			Thread.sleep(1000);		// try to not get blocked by website
		}
		bw.close();
	}
	
	/**
	 * Saves each match page as an html file in "./match_pages"
	 * @throws Exception
	 */
	public static void fetchMatches() throws Exception {
		BufferedReader br = new BufferedReader(new FileReader("./match_urls.txt"));
		String url = br.readLine();
		int i = 0;
		while (url != null) {
			Document doc = Jsoup.connect(url)
					.userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
					.referrer("http://www.google.com")
					.get();
			
			BufferedWriter bw = new BufferedWriter(new FileWriter("./match_pages/" + i + ".html"));
			bw.write(doc.toString());
			bw.close();
			
			++i;
			url = br.readLine();
			Thread.sleep(100);
		}
		br.close();
	}
	
	/**
	 * Saves some of the relevant data from the html file into a text document
	 */
	public static void extractMatchData() throws Exception {
		List<String> badFiles = new ArrayList<>();
		int debugCount = 0;
		int i = 0;
		for (File file : new File("./match_pages").listFiles()) {
			try {
				Document doc = Jsoup.parse(file, null);
				
				Element teamDiv = doc.getElementsByClass("standard-box teamsBox").get(0);
				String team1 = teamDiv.getElementsByClass("team1-gradient").get(0)
						.getElementsByClass("teamName").get(0).text();
				String team2 = teamDiv.getElementsByClass("team2-gradient").get(0)
						.getElementsByClass("teamName").get(0).text();
				
				String unixDate = "0";
				for (Element e : doc.getElementsByClass("date")) {
					if (e.tagName().equals("div")) {
						unixDate = e.attr("data-unix");		// this is not misspelled
					}
				}
				
				Map<String, String> mapScores = new HashMap<>();	// mapname -> score
				for (Element div : doc.getElementsByClass("mapholder")) {
					String mapName = div.getElementsByClass("mapname").get(0).text();
					for (Element innerDiv : div.getElementsByClass("results")) {
						String score1 = innerDiv.getElementsByTag("span").get(0).text();
						String score2 = innerDiv.getElementsByTag("span").get(2).text();
						mapScores.put(mapName, score1 + "-" + score2);
//						String winnerScore = innerDiv.getElementsByClass("won").get(0).text();
//						String loserScore = innerDiv.getElementsByClass("lost").get(0).text();
//						mapScores.put(mapName, winnerScore + "-" + loserScore);
					}
				}
				Map<String, String> idToMap = new HashMap<>();		// id -> mapname
				for (Element div : doc.getElementsByClass("matchstats").get(0)
						.getElementsByClass("dynamic-map-name-full")) {
					if (div.text().equals("All maps")) { continue; }
					idToMap.put(div.attr("id"), div.text());
				}
	
				for (Map.Entry<String, String> e : idToMap.entrySet()) {
					String id = e.getKey();
					String mapname = e.getValue();
					String score = mapScores.get(mapname);
					if (score == null) { break; }
					String team1Score = score.split("-")[0];
					String team2Score = score.split("-")[1];
					Element div = doc.getElementById(id + "-content");
					BufferedWriter bw = new BufferedWriter(new FileWriter("./match_stats/" + i + ".txt"));
					bw.write(team1 + ","
							+ team1Score + ","
							+ team2 + ","
							+ team2Score + ","
							+ mapname + ","
							+ unixDate + "\n");
					Elements playerRows = div.getElementsByTag("tr");
					String currTeam = "ERROR";
					for (Element playerRow : playerRows) {
						if (playerRow.hasClass("header-row")) {
							currTeam = playerRow.getElementsByClass("teamName").get(0).text();
							continue;
						}
						String name = playerRow.getElementsByClass("statsPlayerName").get(0).text();
						String kd = playerRow.getElementsByClass("kd").get(0).text();
						String adr = playerRow.getElementsByClass("adr").get(0).text();
						String kast = playerRow.getElementsByClass("kast").get(0).text();
						bw.write(name + ","
								+ currTeam + ","
								+ kd + ","
								+ adr + ","
								+ kast + "\n");
					}
					bw.close();
					++i;
				}
			} catch (IndexOutOfBoundsException e) {
				badFiles.add(file.getName());
			}
			++debugCount;
			if (debugCount % 200 == 0) { System.out.println(debugCount + "..."); }
		}
		System.out.println(badFiles);	// for debugging/informational purposes
	}
		
	/**
	 * This method reads the output files of extractMatchData() and saves data
	 * for each player into the "./player_stats/" directory
	 * @throws Exception 
	 */
	public static void savePlayerData() throws Exception {
		Map<String, List<Stats>> playerStats = new HashMap<>();
//		Map<String, Map<String, List<StatsByMap>>> playerStatsByMap = new HashMap<>();
		int debugcount = 0;
		for (File file : new File("./match_stats").listFiles()) {
			try {
				BufferedReader br = new BufferedReader(new FileReader(file));
				String[] gameSummary = br.readLine().split(",");	// first line is summary data
				long date = Long.parseLong(gameSummary[5]);
				String winner = gameSummary[0];
				int winnerScore = Integer.parseInt(gameSummary[1]);
				int loserScore = Integer.parseInt(gameSummary[3]);
				if (winnerScore < loserScore) {
					winner = gameSummary[2];
					int temp = winnerScore;
					winnerScore = loserScore;
					loserScore = temp;
				}
				String mapName = gameSummary[4];
				for (int i = 0; i < 10; ++i) {
					String[] playerRow = null;
					try {
						playerRow = br.readLine().split(",");
					} catch (NullPointerException npe) {
						break;
					}
					String name = playerRow[0];
					String team = playerRow[1];
					double roundsWon = ((double) (team.equals(winner) ? winnerScore : loserScore)) / 16.0;
					double k = Double.parseDouble(playerRow[2].split("-")[0]);
					double d = Double.parseDouble(playerRow[2].split("-")[1]);
					double pm = (30 + Math.max(-30, Math.min(30, k - d))) / 60.0;
					double adr = Math.min(200.0, Double.parseDouble(playerRow[3])) / 200.0;
					double kast = Double.parseDouble(playerRow[4].split("%")[0]) / 100.0;
					
					if (!playerStats.containsKey(name)) { playerStats.put(name, new ArrayList<>()); }
					playerStats.get(name).add(new Stats(date, team.equals(winner) ? 1 : 0, roundsWon, pm, adr, kast, mapName));
					
//					if (!playerStatsByMap.containsKey(name)) { playerStatsByMap.put(name, new HashMap<>()); }
//					if (!playerStatsByMap.get(name).containsKey(mapName)) { playerStatsByMap.get(name).put(mapName, new ArrayList<>()); }
//					playerStatsByMap.get(name).get(mapName).add(new StatsByMap(date, team.equals(winner) ? 1 : 0, roundsWon));
				}
				br.close();
				++debugcount;
				if (debugcount % 100 == 0) { System.out.println(debugcount + "..."); }
			} catch (NumberFormatException nfe) {
				System.out.println("bad format: " + file.getName());
			} catch (NullPointerException npe) {
				System.out.println("bad format: " + file.getName());
			}
		}
		
		for (String player : playerStats.keySet()) {
			List<Stats> statsList = playerStats.get(player);
//			Map<String, List<StatsByMap>> playerSBM = playerStatsByMap.get(player);
			BufferedWriter bw;
			try {
				bw = new BufferedWriter(new FileWriter(new File("./player_stats/" + player)));
				
				bw.write(statsList.size() + "\n");
				for (Stats stats : statsList) {
					bw.write(stats.date + ","
							+ stats.win + ","
							+ stats.roundsWon + ","
							+ stats.pm + ","
							+ stats.adr + ","
							+ stats.kast + ","
							+ stats.mapName + "\n");
				}
//				bw.write(playerSBM.size() + "\n");
//				for (Map.Entry<String, List<StatsByMap>> innerEntry : playerSBM.entrySet()) {
//					bw.write(innerEntry.getKey() + "\n");			// map name
//					bw.write(innerEntry.getValue().size() + "\n");	// num matches on this map
//					for (StatsByMap sbm : innerEntry.getValue()) {
//						bw.write(sbm.date + ","
//								+ sbm.win + ","
//								+ sbm.roundsWon + "\n");
//					}
//				}
				bw.close();
			} catch (Exception e) {
				System.out.println("player name has invalid characters: " + player);
			}
		}
	}
	
	public static final long ONE_HUNDRED_DAYS = 8_640_000_000L;
	
	/**
	 * Uses the outputs of savePlayerData() to create [feature -> label] entries, where
	 * the features for a particular instance (each game is one instance) consist of
	 * THE DIFFERENCE between the two teams's:
	 * - average of the 5 players's winrates in the past 100 days
	 * - average of the 5 players's number of rounds won per game in the past 100 days
	 * - average of the 5 players's winrates on the given map in the past 100 days
	 * - average of the 5 players's number of rounds won per game on the given map in the past 100 days
	 * - average of the 5 players's k-d difference in the past 100 days
	 * - average of the 5 players's adr in the past 100 days
	 * - average of the 5 players's kast in the past 100 days
	 * - (todo) average of the 5 players's rws
	 * - (todo) average of the 5 players's pistol round winrate
	 * - (todo) average of the 5 players's team entry frags per game
	 * 
	 * The output files are located in "./Xy.txt"
	 */
	public static void generateFeaturesLabels() throws Exception {
		// TODO: maybe optimize this function
		
		// first, just load all player data
		Map<String, List<Stats>> playerStats = new HashMap<>();
//		Map<String, Map<String, List<StatsByMap>>> playerStatsByMap = new HashMap<>();
		int debugcount = 0;
		for (File file : new File("./player_stats").listFiles()) {
			String name = file.getName();
			playerStats.put(name, new ArrayList<>());
//			playerStatsByMap.put(name, new HashMap<>());
			
			BufferedReader br = new BufferedReader(new FileReader(file));
			int numGames = Integer.parseInt(br.readLine());
			for (int i = 0; i < numGames; ++i) {
				String[] tokens = br.readLine().split(",");
				playerStats.get(name).add(new Stats(
						Long.parseLong(tokens[0]),
						Integer.parseInt(tokens[1]),
						Double.parseDouble(tokens[2]),
						Double.parseDouble(tokens[3]),
						Double.parseDouble(tokens[4]),
						Double.parseDouble(tokens[5]),
						tokens[6]
						));
			}
			Collections.sort(playerStats.get(name));

			br.close();
			++debugcount;
			if (debugcount % 200 == 0) { System.out.println(debugcount + "..."); }
		}
		
		System.out.println("done reading player data...");
		
		BufferedWriter bw = new BufferedWriter(new FileWriter(new File("./Xy.txt")));
		int total = 0;
		int good = 0;
		for (File file : new File("./match_stats").listFiles()) {
			++total;
			try {
				BufferedReader br = new BufferedReader(new FileReader(file));
				String[] gameSummary = br.readLine().split(",");	// first line is summary data
				long date = Long.parseLong(gameSummary[5]);
				String team1 = gameSummary[0];
				String team2 = gameSummary[2];
				String winner = gameSummary[0];
				int winnerScore = Integer.parseInt(gameSummary[1]);
				int loserScore = Integer.parseInt(gameSummary[3]);
				if (winnerScore < loserScore) {
					winner = gameSummary[2];
					int temp = winnerScore;
					winnerScore = loserScore;
					loserScore = temp;
				}
				List<String> team1Players = new ArrayList<>();
				List<String> team2Players = new ArrayList<>();
				String mapName = gameSummary[4];
				for (int i = 0; i < 10; ++i) {
					String[] playerRow = null;
					try {
						playerRow = br.readLine().split(",");
					} catch (NullPointerException npe) {
						System.out.println("bad format, skipping this match...");
						break;
					}
					String name = playerRow[0];
					String team = playerRow[1];
					if (team.equals(team1)) {
						team1Players.add(name);
					} else if (team.equals(team2)){
						team2Players.add(name);
					} else {
						throw new Exception();
					}
				}
				br.close();
				
				if (team1Players.size() != 5 || team2Players.size() != 5) {
					System.out.println("bad format, skipping this match...");
					continue;
				}
				
				// TEAM 1
				List<Double> team1winrate = new ArrayList<>();
				List<Double> team1roundsWon = new ArrayList<>();
				List<Double> team1map_winrate = new ArrayList<>();
				List<Double> team1map_roundsWon = new ArrayList<>();
				List<Double> team1pm = new ArrayList<>();
				List<Double> team1adr = new ArrayList<>();
				List<Double> team1kast = new ArrayList<>();
				for (String player : team1Players) {
					List<Stats> statsList = playerStats.get(player);
					if (statsList == null) {
						System.out.println("player not found: skipping player");
						System.out.println(player);
						continue;
					}
					
					int numGamesInRange = 0;
					int numGamesWithMapInRange = 0;
					double sum_winrate = 0;
					double sum_roundsWon = 0;
					double sum_map_winrate = 0;
					double sum_map_roundsWon = 0;
					double sum_pm = 0;
					double sum_adr = 0;
					double sum_kast = 0;
					
					int index = Collections.binarySearch(statsList, new Stats(date, 0, 0, 0, 0, 0, null));
					if (index < 0) { index = -index - 1; }
					if (index >= statsList.size()) { --index; }
					for (; index >= 0; --index) {
						Stats gameStats = statsList.get(index);
						if (gameStats.date >= date) { continue; }
						if (gameStats.date < date - ONE_HUNDRED_DAYS) { break; }
						++numGamesInRange;
						sum_winrate += gameStats.win;
						sum_roundsWon += gameStats.roundsWon;
						sum_pm += gameStats.pm;
						sum_adr += gameStats.adr;
						sum_kast += gameStats.kast;
						if (gameStats.mapName.equals(mapName)) {
							++numGamesWithMapInRange;
							sum_map_winrate += gameStats.win;
							sum_map_roundsWon += gameStats.roundsWon;
						}
					}
					
					if (numGamesInRange == 0) {
						System.out.println("no previous matches found for current player, skipping player");
						continue;
					}
					
					team1winrate.add(sum_winrate / numGamesInRange);
					team1roundsWon.add(sum_roundsWon / numGamesInRange);
					team1pm.add(sum_pm / numGamesInRange);
					team1adr.add(sum_adr / numGamesInRange);
					team1kast.add(sum_kast / numGamesInRange);
					
					// no data -> approximate with overall winrate
					team1map_winrate.add(numGamesWithMapInRange != 0 ? sum_map_winrate / numGamesWithMapInRange : sum_winrate / numGamesInRange);
					team1map_roundsWon.add(numGamesWithMapInRange != 0 ? sum_map_roundsWon / numGamesWithMapInRange : sum_roundsWon / numGamesInRange);
				}
				
				// TEAM 2
				List<Double> team2winrate = new ArrayList<>();
				List<Double> team2roundsWon = new ArrayList<>();
				List<Double> team2map_winrate = new ArrayList<>();
				List<Double> team2map_roundsWon = new ArrayList<>();
				List<Double> team2pm = new ArrayList<>();
				List<Double> team2adr = new ArrayList<>();
				List<Double> team2kast = new ArrayList<>();
				for (String player : team2Players) {
					List<Stats> statsList = playerStats.get(player);
					if (statsList == null) {
						System.out.println("player not found: skipping player");
						continue;
					}
					
					int numGamesInRange = 0;
					int numGamesWithMapInRange = 0;
					double sum_winrate = 0;
					double sum_roundsWon = 0;
					double sum_map_winrate = 0;
					double sum_map_roundsWon = 0;
					double sum_pm = 0;
					double sum_adr = 0;
					double sum_kast = 0;
					
					int index = Collections.binarySearch(statsList, new Stats(date, 0, 0, 0, 0, 0, null));
					if (index < 0) { index = -index - 1; }
					if (index >= statsList.size()) { --index; }
					for (; index >= 0; --index) {
						Stats gameStats = statsList.get(index);
						if (gameStats.date >= date) { continue; }
						++numGamesInRange;
						sum_winrate += gameStats.win;
						sum_roundsWon += gameStats.roundsWon;
						sum_pm += gameStats.pm;
						sum_adr += gameStats.adr;
						sum_kast += gameStats.kast;
						if (gameStats.mapName.equals(mapName)) {
							++numGamesWithMapInRange;
							sum_map_winrate += gameStats.win;
							sum_map_roundsWon += gameStats.roundsWon;
						}
					}
					
					if (numGamesInRange == 0) {
						System.out.println("no previous matches found for current player, skipping player");
						continue;
					}
					
					team2winrate.add(sum_winrate / numGamesInRange);
					team2roundsWon.add(sum_roundsWon / numGamesInRange);
					team2pm.add(sum_pm / numGamesInRange);
					team2adr.add(sum_adr / numGamesInRange);
					team2kast.add(sum_kast / numGamesInRange);
					
					// no data -> approximate with overall winrate
					team2map_winrate.add(numGamesWithMapInRange != 0 ? sum_map_winrate / numGamesWithMapInRange : sum_winrate / numGamesInRange);
					team2map_roundsWon.add(numGamesWithMapInRange != 0 ? sum_map_roundsWon / numGamesWithMapInRange : sum_roundsWon / numGamesInRange);
				}
				
				if (team1winrate.size() < 4 || team2winrate.size() < 4) {
					System.out.println("not enough players: skipping current match");
					continue;
				}
				
				Instance instance = new Instance(
						avg(team1winrate) - avg(team2winrate),
						avg(team1roundsWon) - avg(team2roundsWon),
						avg(team1map_winrate) - avg(team2map_winrate),
						avg(team1map_roundsWon) - avg(team2map_roundsWon),
						avg(team1pm) - avg(team2pm),
						avg(team1adr) - avg(team2adr),
						avg(team1kast) - avg(team2kast)
						);
				
				// each game gives 2 symmetric instances
				bw.write(instance.avg_winrate + ","
						+ instance.avg_roundsWon + ","
						+ instance.avg_map_winrate + ","
						+ instance.avg_map_roundsWon + ","
						+ instance.avg_pm + ","
						+ instance.avg_adr + ","
						+ instance.avg_kast + ","
						+ (team1.equals(winner) ? 1 : 0) + "\n"
						);
				bw.write(-instance.avg_winrate + ","
						+ -instance.avg_roundsWon + ","
						+ -instance.avg_map_winrate + ","
						+ -instance.avg_map_roundsWon + ","
						+ -instance.avg_pm + ","
						+ -instance.avg_adr + ","
						+ -instance.avg_kast + ","
						+ (team2.equals(winner) ? 1 : 0) + "\n"
						);
			} catch (Exception e) {
				System.out.println("exception, skipping current match...");
				e.printStackTrace();
			}
			++good;
		}
		bw.close();
		System.out.println(good + " / " + total);
	}
	
	public static double avg(List<Double> nums) {
		double x = 0;
		for (double d : nums) {
			x += d;
		}
		return x / nums.size();
	}
	
	/**
	 * Provides stats about a particular player for a particular game.
	 */
	static class Stats implements Comparable<Stats> {
		long date;				// measured in milliseconds since 1/1/1970 00:00:00
		int win;				// value in {0, 1} (0 = loss, 1 = win)
		double roundsWon;		// value in [0, 1]; := min(actual_rounds_won, 16) / 16
		double pm;				// value in [0, 1]; := ((k - d) + 30) / 60
		double adr;				// value in [0, 1]; := min(actual_adr, 200) / 200
		double kast;			// value in [0, 1]
		String mapName;
		public Stats(long date, int win, double roundsWon, double pm, double adr, double kast, String mapName) {
			this.date = date;
			this.win = win;
			this.roundsWon = roundsWon;
			this.pm = pm;
			this.adr = adr;
			this.kast = kast;
			this.mapName = mapName;
		}
		
		@Override
		public int compareTo(Stats arg0) {
			return Long.signum(this.date - arg0.date);
		}
		
		
	}
	
	/**
	 * each instance averages values from the last 100 days
	 */
	static class Instance {
		double avg_winrate;
		double avg_roundsWon;
		double avg_map_winrate;
		double avg_map_roundsWon;
		double avg_pm;
		double avg_adr;
		double avg_kast;
		public Instance(double avg_winrate,
				double avg_roundsWon,
				double avg_map_winrate,
				double avg_map_roundsWon,
				double avg_pm,
				double avg_adr,
				double avg_kast) {
			this.avg_winrate = avg_winrate;
			this.avg_roundsWon = avg_roundsWon;
			this.avg_map_winrate = avg_map_winrate;
			this.avg_map_roundsWon = avg_map_roundsWon;
			this.avg_pm = avg_pm;
			this.avg_adr = avg_adr;
			this.avg_kast = avg_kast;
		}
	}
	
}

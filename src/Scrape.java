import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
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

public class Scrape {
	
	public static final String RESULTS_PAGE = "https://www.hltv.org/results?offset=";
	public static final String MATCHES_DIR = "./match_pages";
	
	public static void main(String[] args) {
		try {
			// fetchUrls();
			// fetchMatches();
			// extractMatchData();
			// savePlayerData();
			generateFeaturesLabels();
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
						String winnerScore = innerDiv.getElementsByClass("won").get(0).text();
						String loserScore = innerDiv.getElementsByClass("lost").get(0).text();
						mapScores.put(mapName, winnerScore + "-" + loserScore);
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
		Map<String, Map<String, List<StatsByMap>>> playerStatsByMap = new HashMap<>();
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
					playerStats.get(name).add(new Stats(date, team.equals(winner) ? 1 : 0, roundsWon, pm, adr, kast));
					
					if (!playerStatsByMap.containsKey(name)) { playerStatsByMap.put(name, new HashMap<>()); }
					if (!playerStatsByMap.get(name).containsKey(mapName)) { playerStatsByMap.get(name).put(mapName, new ArrayList<>()); }
					playerStatsByMap.get(name).get(mapName).add(new StatsByMap(date, team.equals(winner) ? 1 : 0, roundsWon));
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
			Map<String, List<StatsByMap>> playerSBM = playerStatsByMap.get(player);
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
							+ stats.kast + "\n");
				}
				bw.write(playerSBM.size() + "\n");
				for (Map.Entry<String, List<StatsByMap>> innerEntry : playerSBM.entrySet()) {
					bw.write(innerEntry.getKey() + "\n");			// map name
					bw.write(innerEntry.getValue().size() + "\n");	// num matches on this map
					for (StatsByMap sbm : innerEntry.getValue()) {
						bw.write(sbm.date + ","
								+ sbm.win + ","
								+ sbm.roundsWon + "\n");
					}
				}
				bw.close();
			} catch (Exception e) {
				System.out.println("player name has invalid characters: " + player);
			}
		}
	}
	
	public final long ONE_HUNDRED_DAYS = 8_640_000_000L;
	
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
		Map<String, Map<String, List<StatsByMap>>> playerStatsByMap = new HashMap<>();
		int debugcount = 0;
		for (File file : new File("./player_stats").listFiles()) {
			String name = file.getName();
			playerStats.put(name, new ArrayList<>());
			playerStatsByMap.put(name, new HashMap<>());
			
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
						Double.parseDouble(tokens[5])
						));
			}
			Collections.sort(playerStats.get(name));
			
			int numMaps = Integer.parseInt(br.readLine());
			for (int i = 0; i < numMaps; ++i) {
				List<StatsByMap> gamesOnMap = new ArrayList<>();
				String mapName = br.readLine();
				int numGamesOnMap = Integer.parseInt(br.readLine());
				for (int j = 0; j < numGamesOnMap; ++j) {
					String[] tokens = br.readLine().split(",");
					gamesOnMap.add(new StatsByMap(
							Long.parseLong(tokens[0]),
							Integer.parseInt(tokens[1]),
							Double.parseDouble(tokens[2])
							));
				}
				Collections.sort(gamesOnMap);
				playerStatsByMap.get(name).put(mapName, gamesOnMap);
			}
			br.close();
			++debugcount;
			if (debugcount % 200 == 0) { System.out.println(debugcount + "..."); }
		}
		
		System.out.println("done reading player data...");
		
		for (File file : new File("./match_stats").listFiles()) {
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
				
				
				
//				public Instance(double avg_winrate,
//						double avg_roundsWon,
//						double avg_map_winrate,
//						double avg_map_roundsWon,
//						double avg_pm,
//						double avg_adr,
//						double avg_kast)
			} catch (Exception e) {
				System.out.println("exception, skipping current match...");
			}
		}
	}
	
	/**
	 * Provides stats about a particular player for a particular game.
	 */
	static class Stats implements Comparable<Stats> {
		long date;				// measured in milliseconds since 1/1/1970 00:00:00
		int win;				// value in {0, 1} (0 = loss, 1 = win)
		double roundsWon;		// value in [0, 1]; := min(actual_rounds_won, 16) / 16
		double pm;				// value in [0, 1]; := ((k - d) + 30) / 60
		double adr;			// value in [0, 1]; := min(actual_adr, 200) / 200
		double kast;			// value in [0, 1]
		public Stats(long date, int win, double roundsWon, double pm, double adr, double kast) {
			this.date = date;
			this.win = win;
			this.roundsWon = roundsWon;
			this.pm = pm;
			this.adr = adr;
			this.kast = kast;
		}
		
		@Override
		public int compareTo(Stats arg0) {
			return (int) (this.date - arg0.date);
		}
		
		
	}
	
	
	/**
	 * A somewhat simpler version of Stats, intended to be used in a Map<String, StatsByMap>
	 */
	static class StatsByMap implements Comparable<StatsByMap> {
		long date;
		int win;
		double roundsWon;
		public StatsByMap(long date, int win, double roundsWon) {
			this.date = date;
			this.win = win;
			this.roundsWon = roundsWon;
		}
		
		@Override
		public int compareTo(StatsByMap arg0) {
			return (int) (this.date - arg0.date);
		}
	}
	
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

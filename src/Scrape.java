import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
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
		for (File file : new File("./match_pages").listFiles()) {
			try {
				String matchId = file.getName().split("\\.")[0];
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
	
				int i = 0;
				for (Map.Entry<String, String> e : idToMap.entrySet()) {
					String id = e.getKey();
					String mapname = e.getValue();
					String score = mapScores.get(mapname);
					if (score == null) { break; }
					String team1Score = score.split("-")[0];
					String team2Score = score.split("-")[1];
					Element div = doc.getElementById(id + "-content");
					BufferedWriter bw = new BufferedWriter(new FileWriter("./match_stats/" + matchId + "-" + i + ".txt"));
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
		Map<String, Stats> playerStats = new HashMap<>();
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
					double adr = Math.max(200.0, Double.parseDouble(playerRow[3])) / 200.0;
					double kast = Double.parseDouble(playerRow[4].split("%")[0]) / 100.0;
					
					if (!playerStats.containsKey(name)) { playerStats.put(name, new Stats()); }
					Stats stats = playerStats.get(name);
					stats.dates.add(date);
					stats.wins.add(team.equals(winner) ? 1 : 0);
					stats.roundsWon.add(roundsWon);
					stats.pms.add(pm);
					stats.adrs.add(adr);
					stats.kasts.add(kast);
					if (!stats.winsByMap.containsKey(mapName)) { stats.winsByMap.put(mapName, new StatsByMap()); }
					stats.winsByMap.get(mapName).dates.add(date);
					stats.winsByMap.get(mapName).wins.add(team.equals(winner) ? 1 : 0);
					stats.winsByMap.get(mapName).roundsWon.add(roundsWon);
				}
				br.close();
				++debugcount;
				if (debugcount % 100 == 0) { System.out.println(debugcount); }
				// if (debugcount > 10000) { break; }
			} catch (NumberFormatException nfe) {
				System.out.println("bad format: " + file.getName());
			} catch (NullPointerException npe) {
				System.out.println("bad format: " + file.getName());
			}
		}
		
		for (Map.Entry<String, Stats> entry : playerStats.entrySet()) {
			// System.out.println(entry.getKey());
			BufferedWriter bw;
			try {
				bw = new BufferedWriter(new FileWriter(new File("./player_stats/" + entry.getKey())));
				
				Stats stats = entry.getValue();
				bw.write(stats.dates.toString() + '\n');
				bw.write(stats.wins.toString() + '\n');
				bw.write(stats.roundsWon.toString() + '\n');
				bw.write(stats.pms.toString() + '\n');
				bw.write(stats.adrs.toString() + '\n');
				bw.write(stats.kasts.toString() + '\n');
				bw.write(stats.winsByMap.size() + '\n');
				for (Map.Entry<String, StatsByMap> innerEntry : stats.winsByMap.entrySet()) {
					bw.write(innerEntry.getKey());
					bw.write(innerEntry.getValue().dates.toString());
					bw.write(innerEntry.getValue().wins.toString());
					bw.write(innerEntry.getValue().roundsWon.toString());
				}
				bw.close();
			} catch (Exception e) {
				System.out.println("player name has invalid characters: " + entry.getKey());
			}
		}
	}
	
	/**
	 * Uses the outputs of savePlayerData() to create [feature -> label] entries, where
	 * the features consist of
	 * - average of the 5 players's winrates in the past 200 days
	 * - average number of rounds won per game in the past 200 days
	 */
	public static void generateFeaturesLabels() {
		
	}
	
	static class Stats {
		// all of these lists should be the same length
		List<Long> dates = new ArrayList<>();					// measured in time since 1/1/1970 00:00:00
		List<Integer> wins = new ArrayList<>();					// values are in {0, 1} (0 = loss, 1 = win)
		List<Double> roundsWon = new ArrayList<>();				// values are in [0, 1]; := min(actual_rounds_won, 16) / 16
		List<Double> pms = new ArrayList<>();					// values are in [0, 1]; := ((k - d) + 30) / 60
		List<Double> adrs = new ArrayList<>();					// values are in [0, 1]
		List<Double> kasts = new ArrayList<>();					// values are in [0, 1]
		
		// map name -> [dates, wins]
		// example entry: "cache"=[[100, 101, 102, 103], [1, 0, 1, 1]]
		// first list is dates (in unix time)
		// second list is 1 for win, 0 for loss
		// third list is number of rounds won in that game (usually max 16)
		Map<String, StatsByMap> winsByMap = new HashMap<>();		// map name -> [dates, wins]
	}
	
	
	/**
	 * A somewhat simpler version of Stats, intended to be used in a Map<String, StatsByMap>
	 */
	static class StatsByMap {
		List<Long> dates = new ArrayList<>();
		List<Integer> wins = new ArrayList<>();
		List<Double> roundsWon = new ArrayList<>();
	}
	
}

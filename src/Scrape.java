import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
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
			extractMatchData();
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
		List<String> bongo = new ArrayList<>();
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
				bongo.add(file.getName());
			}
		}
		System.out.println(bongo);
	}
	
}

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Scrape {
	
	public static final String RESULTS_PAGE = "https://www.hltv.org/results?offset=";
	public static final String MATCHES_DIR = "./match_pages";
	
	public static void main(String[] args) {
		try {
			fetchMatches();
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
	
}

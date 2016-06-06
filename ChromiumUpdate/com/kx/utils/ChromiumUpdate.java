package com.kx.utils;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class ChromiumUpdate {

	private static final int REPORT_PERCENT_N_TIMES = 10;
	private static String UPDATE_URL_LATEST_WIN = "http://commondatastorage.googleapis.com/chromium-browser-continuous/Win/LAST_CHANGE";
	private static String DOWNLOAD_PATH = "http://commondatastorage.googleapis.com/chromium-browser-continuous/Win/XXXXXX/mini_installer.exe";
	private static String filePathToLog = "log.txt";
	private static String lastVersionInfo = "last.txt";

	private static final Logger logger = Logger.getLogger(ChromiumUpdate.class.getName());

	public static void main(String[] args) throws IOException {

		setupConfig(args);
		logger.info("\n\n--------------------Starting upgrade------------------------ ");
		logger.info("Configuration: \n\t URL: " + UPDATE_URL_LATEST_WIN + "\n\t Log file: " + filePathToLog);

		int currentVersionLocal = getCurrentChromiumVersionLocal();
		int currentRemoteVersion = getCurrentChromiumVersionOnWeb();
		boolean upgrade = false;

		StringBuffer logBuff = new StringBuffer("Local Version: " + currentVersionLocal + ", Remote version: " + currentRemoteVersion);

		if (currentVersionLocal < currentRemoteVersion) {
			logBuff.append(". Upgrading..");
			upgrade = true;
		} else {
			logBuff.append(". Skipping..");
		}

		logger.info(logBuff.toString());

		if (upgrade) {
			getAndStoreLatestInstaller (currentRemoteVersion);
		}

		String os = System.getProperty("os.name");
		if (os != null && os.contains("Win") && upgrade) {
			// Check if chromium is already running
			String chromeProcessNameIfRunning = checkIfChromeIsRunning();
			
			// IF running kill it
			if (chromeProcessNameIfRunning != null) { 
				logger.warning(os + " detected. Chromium is running. Killing it. Sorry, no questions asked.");
				Process install = Runtime.getRuntime().exec("taskkill /im " + chromeProcessNameIfRunning);
				BufferedReader in = new BufferedReader(new InputStreamReader(install.getInputStream()));
				String line;
				while ((line = in.readLine()) != null) {
					logger.warning(line);
				}
			}
			// Run installer
			try {
				Process install = Runtime.getRuntime().exec("installer.exe");
				logger.info("Installing now ...");
				BufferedReader in = new BufferedReader(new InputStreamReader(install.getInputStream()));
				String line;
				while ((line = in.readLine()) != null) {
					System.out.println(line);
				}
			} catch (Exception e) {
				e.printStackTrace();
				logger.warning(e.getMessage());
			}
			
			// Create marker filel
			BufferedWriter bw = null;
			try {
				logger.info("Updating current version information in :" + lastVersionInfo);
				bw = new BufferedWriter(new FileWriter(lastVersionInfo));
				bw.write(String.valueOf(currentRemoteVersion));
				bw.flush();
			} catch (IOException e) {
				logger.warning("Coudn't write current version information");
				e.printStackTrace();
			} finally {
				bw.close();
			}
		}
	}

	private static String checkIfChromeIsRunning() {
		String chromeProcess = null;
		try {
			String line;
			Process p = Runtime.getRuntime().exec("tasklist.exe /fo csv");
			BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
			while ((line = input.readLine()) != null) {
				if (!line.trim().equals("")) {
					// keep only the process name
					String thisProcess = line.split(",")[0];
					if (thisProcess.contains("chrome")) {
						chromeProcess = thisProcess;
						break;
					}
				}

			}
			input.close();
		} catch (Exception err) {
			err.printStackTrace();
		}
		return chromeProcess; 
	}

	private static void getAndStoreLatestInstaller(int versionNumber) throws IOException {
		URL url = null;
		InputStream is = null;
		BufferedOutputStream os = null;
		byte[] buf = new byte[204800];
		double totalBytesRead = 0; 
		DecimalFormat df = new DecimalFormat("#.##");
		List<Double> tenRandomReportBytePoints = getRandomReportingPercents(REPORT_PERCENT_N_TIMES);
		Collections.sort(tenRandomReportBytePoints);
		try {
			url = new URL(DOWNLOAD_PATH.replace("XXXXXX", String.valueOf(versionNumber)));
			URLConnection urlConn = url.openConnection();
			is = urlConn.getInputStream();
			double fileSize = urlConn.getContentLength();
			logger.info("Will need to download : " + fileSize/1024 + "k");
			os = new BufferedOutputStream(new FileOutputStream("installer.exe", false));
			int bytesRead = 0;
			long timeStart = System.nanoTime();
			long time = 0;
			int reportedCount = 0;
			double targetByteToReport = (tenRandomReportBytePoints.get(reportedCount) * fileSize) / 100;
			while ((bytesRead = is.read(buf)) != -1) {
				os.write(buf, 0, bytesRead);
				totalBytesRead += bytesRead;
				time = (System.nanoTime() - timeStart) / 1000000000;
				double percentDone = (totalBytesRead/fileSize) * 100;
				if (time > 0 && totalBytesRead > targetByteToReport && reportedCount < REPORT_PERCENT_N_TIMES-1 ) {
					long averageSpeed = (int)totalBytesRead / time;
					logger.info(df.format(percentDone) + "% done, Average Speed: " + averageSpeed + " ETA: " + ((fileSize - totalBytesRead)/averageSpeed) + " s");
					targetByteToReport = (tenRandomReportBytePoints.get(++reportedCount) * fileSize) / 100;
				}
			}
			logger.info("Downloaded : " + df.format(totalBytesRead / 1024) + " k in " + time + "s at average speed of " + totalBytesRead / time);
		} catch (MalformedURLException e) {
			logger.warning("Invalid download path. Google changed location ? Exiting.");
			e.printStackTrace();
		} catch (IOException e) {
			logger.warning("Invalid download path. Google changed location ? Exiting.");
			e.printStackTrace();
		} finally {
			os.close();
			is.close();
		}
	}

	private static List<Double> getRandomReportingPercents(int i) {
		List<Double> arrayOfSizeI = new ArrayList<Double>(i);
		for (int j = 0; j <i; j++) {
			arrayOfSizeI.add((Math.random() * 100));
		}
		return arrayOfSizeI;
	}

	private static int getCurrentChromiumVersionOnWeb() throws IOException {
		String lastVersionReleased = null;
		InputStreamReader is = null;
		BufferedReader br = null;
		int returnVal = -1;
		try {
			URL url = new URL(UPDATE_URL_LATEST_WIN);
			URLConnection urlConn = url.openConnection();
			is = new InputStreamReader(urlConn.getInputStream());
			br = new BufferedReader(is, 15);
			lastVersionReleased = br.readLine();
		} catch (MalformedURLException e) {
			logger.warning("Invalid URL configured. Exiting.");
			e.printStackTrace();
			return returnVal;
		} catch (IOException e) {
			logger.warning("Cannot connect to the configrued URL.");
			e.printStackTrace();
			return returnVal;
		} finally {
			if (is != null) {
				is.close();
			}
			if (br != null) {
				br.close();
			}
		}
		try {
			returnVal = Integer.parseInt(lastVersionReleased);
		} catch (NumberFormatException e) {
		}
		return returnVal;
	}

	private static int getCurrentChromiumVersionLocal() throws IOException {
		BufferedReader br = null;
		String lastVersionInstalled = null;
		int returnVal = -1;
		try {
			br = new BufferedReader(new FileReader(lastVersionInfo));
			lastVersionInstalled = br.readLine();
			logger.info("Current chromium build : " + lastVersionInstalled);
		} catch (FileNotFoundException e) {
			logger.warning("First run, No previous versions found.");
			return returnVal;
		} catch (IOException e) {
			logger.warning("Error in getting current version. Assuming not present");
			e.printStackTrace();
		} finally {
			if (br != null) {
				br.close();
			}
		}
		try {
			returnVal = Integer.parseInt(lastVersionInstalled);
		} catch (NumberFormatException e) {
		}
		return returnVal;
	}

	private static void setupConfig(String[] args) {
		if (args.length == 0) {
			logger.log(
					Level.INFO,
					"Arguments supported (In any order): \n\t -v \t\t (Optional)Be more verbose \n\t -f file.log \t (Optional) provide log file path. Default log.txt \n\t -u URL \t (Optional) Override LATEST change file URL (Default - "
							+ UPDATE_URL_LATEST_WIN + "\n\t -u1 URL \t\t(Optional, URL pattern of file to download. Use XXXXXX in place of version number. Default - "+ DOWNLOAD_PATH +")\n");
		}
		logger.setLevel(Level.INFO);

		boolean expectingFileName = false;
		boolean expectingURL = false;
		boolean expectingDownPath = false;
		for (String arg : args) {
			if ("-v".equalsIgnoreCase(arg)) {
				logger.setLevel(Level.FINE);
				continue;
			}
			if ("-f".equalsIgnoreCase(arg)) {
				expectingFileName = true;
				continue;
			}
			if (expectingFileName && arg.length() > 0) {
				filePathToLog = arg;
				expectingFileName = false;
				continue;
			}
			if ("-u".equalsIgnoreCase(arg)) {
				expectingURL = true;
				continue;
			}
			if (expectingURL && arg.length() > 0) {
				UPDATE_URL_LATEST_WIN = arg;
				expectingURL = false;
				continue;
			}
			if ("-u1".equalsIgnoreCase(arg)) {
				expectingDownPath = true;
				continue;
			}
			if (expectingDownPath && arg.length() > 0) {
				DOWNLOAD_PATH = arg;
				expectingDownPath = false;
				continue;
			}
		}

		FileHandler fHandler = null;
		try {
			fHandler = new FileHandler(filePathToLog, true);
			fHandler.setFormatter(new SimpleFormatter());
		} catch (SecurityException e) {
			logger.warning("Skipping file logging as the file " + filePathToLog + " could not be created with error: " + e.getLocalizedMessage());
		} catch (IOException e) {
			logger.warning("Skipping file logging as the file " + filePathToLog + " could not be created with error: " + e.getLocalizedMessage());
		}

		if (fHandler != null) {
			fHandler.setLevel(logger.getLevel());
			logger.addHandler(fHandler);
		}

	}

}

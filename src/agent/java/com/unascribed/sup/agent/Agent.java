/*
 * This file is part of unsup.
 * Copyright © 2020-2025 Una Kearney
 * https://git.sleeping.town/unascribed/unsup
 *
 * unsup is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * unsup is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with unsup; if not, see <https://www.gnu.org/licenses/>.
 */

package com.unascribed.sup.agent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonWriter;
import com.unascribed.sup.Unsup;
import com.unascribed.sup.Util;
import com.unascribed.sup.agent.PuppetHandler.AlertOption;
import com.unascribed.sup.agent.PuppetHandler.AlertOptionType;
import com.unascribed.sup.agent.pieces.MemoryCookieJar;
import com.unascribed.sup.agent.pieces.QDIni;
import com.unascribed.sup.agent.pieces.QDIni.QDIniException;
import com.unascribed.sup.agent.pieces.pseudolocale.AccentedEnglish;
import com.unascribed.sup.agent.pieces.pseudolocale.PigLatin;
import com.unascribed.sup.agent.signing.SigProvider;
import com.unascribed.sup.agent.util.RequestHelper;
import com.unascribed.sup.data.AlertMessageType;
import com.unascribed.sup.data.SysProps;
import com.unascribed.sup.pieces.ExceptableRunnable;
import com.unascribed.sup.util.Resources;

import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.brotli.BrotliInterceptor;
import okhttp3.tls.HandshakeCertificates;

public class Agent {

	public static final int EXIT_SUCCESS = 0;
	public static final int EXIT_CONFIG_ERROR = 1;
	public static final int EXIT_CONSISTENCY_ERROR = 2;
	public static final int EXIT_BUG = 3;
	public static final int EXIT_USER_REQUEST = 4;
	
	public static final long launchTime = System.nanoTime();

	static volatile boolean awaitingExit = false;
	
	public static boolean launched;
	static boolean standalone;
	
	private static List<ExceptableRunnable> cleanup = new ArrayList<>();
	private static Config config;
	
	public static final SigProvider unsupSig = SigProvider.of("signify RWTSwM40VCzVER3YWt55m4Fvsg0sjZLEICikuU3cD91gR/2lii/jk67B");
	
	// read by the Unsup class when it loads
	// be careful not to load that class until this is all initialized
	public static String sourceVersion;
	public static boolean updated;
	
	static boolean updatedComponents;

	private static JsonObject state;
	private static File stateFile;
	
	/** this mutex must be held while doing sensitive operations that shouldn't be interrupted */
	static final Object dangerMutex = new Object();
	
	private static OkHttpClient okhttp;

	public static void main(String[] args) {
		standalone = true;
		premain(args.length >= 1 ? args[0] : null);
	}
	
	public static void premain(String arg) {
		launched = true;
		long start = System.nanoTime();
		try {
			Log.init();
			Log.info((standalone ? "Starting in standalone mode" : "Launch hijack successful")+". unsup v"+Util.VERSION);
			if (!preinit(arg)) return;
			
			if (config().serverAuthority()) {
				Log.info("Performing pre-update to check for a new config");
				if (UpdateHandler.checkForUpdate(state, config().format(), config().source(),
						true,
						false,
						true,
						res -> {
					res.componentVersions.clear();
					if (res.plan != null) {
						res.plan.skipStateApplication = true;
						if (!res.plan.files.containsKey("unsup.ini")) {
							res.plan = null;
							return;
						}
						deleteNonMatchingKeys(res.plan.expectedState, "unsup.ini");
						deleteNonMatchingKeys(res.plan.files, "unsup.ini");
					}
				})) {
					Log.info("Reinitializing with newly updated config");
					destroyOkHttp();
					if (!preinit(arg)) return;
				} else {
					Log.info("No config update. Proceeding as normal.");
				}
			}
			
			if (!config().noGui()) {
				PuppetHandler.create();
				addCleanupAction(PuppetHandler::destroy);
			}
			
			if (config().updateMMCPack()) {
				MMCUpdater.scan();
			}
			
			PuppetHandler.sendConfig();
			
			PuppetHandler.tellPuppet(":build");
			PuppetHandler.tellPuppet(":subtitle="+config().initialSubtitle());
			String delay;
			if (config().offerChangeFlavors()) {
				delay = "";
			} else {
				// we don't want to flash a window on the screen if things aren't going slow, so we tell
				// the puppet to wait before actually making the window visible, and assign an id to our
				// order so we can belay it later if we finished before the timer expired
				delay = "1250";
			}
			PuppetHandler.tellPuppet("[openTimeout]"+delay+":visible=true");
			
			UpdateHandler.checkForUpdate(state, config().format(), config().source(),
					!config().behavior().promptUpdates(),
					SysProps.DRY_RUN,
					false,
					res -> {}
				);

			if (awaitingExit) Agent.blockForever();

			PuppetHandler.tellPuppet(":belay=openTimeout");
			if (updatedComponents) {
				PuppetHandler.openAlert("dialog.component_update.title",
						"dialog.component_update",
						AlertMessageType.INFO, AlertOptionType.OK, AlertOption.OK);
			} else if (PuppetHandler.puppetOut != null) {
				Log.info("Waiting for puppet to complete done animation...");
				PuppetHandler.tellPuppet(":title=title.done");
				PuppetHandler.tellPuppet(":mode=done");
				if (!PuppetHandler.puppet.waitFor(3, TimeUnit.SECONDS)) {
					Log.warn("Tired of waiting, killing the puppet.");
					PuppetHandler.puppet.destroyForcibly();
				}
			}
			
			if (SysProps.DRY_RUN) {
				Log.warn("Performed a dry run, per your request. No files in the working directory were changed!");
			}
			if (updatedComponents) {
				Log.info("A component update has been applied - exiting for game restart.");
				exit(EXIT_SUCCESS);
			} else if (standalone) {
				Log.info("Ran in standalone mode, no program will be started.");
			} else {
				Log.info("All done, handing over control.");
				// poke the Unsup class so it loads and finalizes all of its values
				if (Unsup.SOURCE_VERSION != null) Unsup.poke();
				String cmd = System.getProperty("sun.java.command");
				if ("org.multimc.EntryPoint".equals(cmd)) {
					// we actually run before MultiMC's Java-side launcher code, so print a
					// blank line to put "Using onesix launcher." in an island like it's
					// supposed to be
					System.out.println();
				}
			}
		} catch (QDIniException e) {
			Log.error("Config file error: "+e.getMessage()+"! Exiting.");
			exit(EXIT_CONFIG_ERROR);
			return;
		} catch (InterruptedException e) {
			throw new AssertionError(e);
		} finally {
			Log.info("Finished after "+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)+"ms.");
			cleanup();
		}
	}

	private static boolean preinit(String arg) {
		String lang = Locale.getDefault().toLanguageTag();
		if (SysProps.LANGUAGE != null) lang = SysProps.LANGUAGE;
		Log.debug("Language: "+lang);
		var ini = loadConfig(lang);
		if (ini == null) {
			Log.warn("Cannot find a config file, giving up.");
			// by returning but not exiting, we yield control to the program whose launch we hijacked, if any
			return false;
		}
		config = Config.parse(ini, arg, lang);
		
		setupOkHttp();
		
		stateFile = new File(".unsup-state.json");
		if (stateFile.exists()) {
			try (InputStream in = new FileInputStream(stateFile)) {
				state = JsonParser.object().from(in);
			} catch (Exception e) {
				Log.error("Couldn't load state file! Exiting.", e);
				exit(EXIT_CONSISTENCY_ERROR);
				return false;
			}
		} else {
			state = new JsonObject();
		}
		return true;
	}
	
	private static QDIni loadConfig(String lang) {
		File configFile = new File("unsup.ini");
		if (configFile.exists()) {
			QDIni ini;
			try {
				ini = QDIni.load(configFile);
				checkForbiddenKey(ini, "strings.dialog.progress.title");
				checkForbiddenKey(ini, "strings.dialog.progress.title.branded");
				Log.debug("Found and loaded unsup.ini. What secrets does it hold?");
			} catch (Exception e) {
				Log.error("Found unsup.ini, but couldn't parse it! Exiting.", e);
				throw exit(EXIT_CONFIG_ERROR);
			}
			checkRequiredKeys(ini, "version", "source_format", "source");
			int version = ini.getInt("version", -1);
			if (version != 1) {
				Log.error("Config file error: Unknown version "+version+" at "+ini.getBlame("version")+"! Exiting.");
				throw exit(EXIT_CONFIG_ERROR);
			}
			if (!"en-US".equals(lang)) {
				ini = mergePreset(ini, "lang/"+lang, false);
			}
			ini = mergePreset(ini, "lang/en-US", true);
			Function<String, String> pseudolocale = null;
			if ("en-PIG".equals(lang)) {
				pseudolocale = PigLatin::toPigLatin;
			} else if ("en-XA".equals(lang)) {
				pseudolocale = AccentedEnglish::toEnXA;
			}
			if (pseudolocale != null) {
				StringBuilder sb = new StringBuilder();
				for (String k : ini.keySet()) {
					if (k.startsWith("strings.")) {
						sb.append(k).append("=").append(pseudolocale.apply(ini.get(k))).append("\n");
					}
				}
				ini = ini.merge(QDIni.load("<pseudolocale>", sb.toString()));
			}
			ini = mergePreset(ini, "__global__", true);
			if (ini.containsKey("preset")) {
				ini = mergePreset(ini, ini.get("preset"), true);
			}
			return ini;
		} else {
			if (SysProps.BOOTSTRAP_URL != null) {
				Log.info("No config found, bootstrapping from "+SysProps.BOOTSTRAP_URL);
				SigProvider key = null;
				if (SysProps.BOOTSTRAP_KEY != null) {
					try {
						key = SigProvider.parse(SysProps.BOOTSTRAP_KEY);
					} catch (Exception e) {
						Log.error("Failed to parse bootstrap key", e);
						throw exit(EXIT_CONFIG_ERROR);
					}
				}
				setupOkHttp();
				int M = 1024*1024;
				try {
					var data = RequestHelper.loadAndVerify(new URI(SysProps.BOOTSTRAP_URL), 16*M, new URI(SysProps.BOOTSTRAP_URL+".sig"), key);
					Files.write(configFile.toPath(), data);
					Log.info("Successfully downloaded bootstrap config");
					destroyOkHttp();
					return loadConfig(lang);
				} catch (Exception e) {
					Log.error("Failed to download bootstrap config", e);
					throw exit(EXIT_CONFIG_ERROR);
				}
			}
			Log.warn("No config file found? Doing nothing.");
			return null;
		}
	}
	
	private static void checkForbiddenKey(QDIni ini, String key) {
		if (ini.containsKey(key)) {
			Log.error("Attempt to override a forbidden key: "+key);
			exit(EXIT_CONFIG_ERROR);
		}
	}

	private static void checkRequiredKeys(QDIni ini, String... requiredKeys) {
		for (String req : requiredKeys) {
			if (!ini.containsKey(req)) {
				Log.error("Config file error: "+req+" is required, but was not defined! Exiting.");
				exit(EXIT_CONFIG_ERROR);
				return;
			}
		}
	}
	
	private static void setupOkHttp() throws AssertionError {
		HandshakeCertificates.Builder certsBldr = new HandshakeCertificates.Builder()
				.addPlatformTrustedCertificates();
		for (X509Certificate cert : CACerts.certs) {
			certsBldr.addTrustedCertificate(cert);
		}
		HandshakeCertificates certs = certsBldr.build();
		OkHttpClient bootstrapOkHttp = new OkHttpClient.Builder()
			.connectTimeout(30, TimeUnit.SECONDS)
			.readTimeout(15, TimeUnit.SECONDS)
			.writeTimeout(15, TimeUnit.SECONDS)
			.sslSocketFactory(certs.sslSocketFactory(), certs.trustManager())
			.addInterceptor(chain -> {
				String url = chain.request().url().toString();
				var req = chain.request();
				for (var en : config().authorizers()) {
					if (url.startsWith(en.urlPrefix())) {
						var bldr = req.newBuilder();
						en.auth().authorize(req, bldr);
						req = bldr.build();
					}
				}
				return chain.proceed(req);
			})
			.addInterceptor(BrotliInterceptor.INSTANCE)
			.build();
		okhttp = bootstrapOkHttp.newBuilder()
				.cookieJar(new MemoryCookieJar())
				.dns(config() == null ? Dns.SYSTEM : config().dns(bootstrapOkHttp))
				.build();
	}
	
	private static void destroyOkHttp() {
		if (okhttp() != null) {
			okhttp().dispatcher().executorService().shutdown();
			okhttp().connectionPool().evictAll();
			okhttp = null;
		}
	}
	
	private static QDIni mergePreset(QDIni config, String presetName, boolean mustExist) {
		URL u = Resources.get("presets/"+presetName+".ini");
		if (u == null) {
			if (!mustExist) {
				Log.debug("Optional preset "+presetName+" not found");
				return config;
			}
			Log.error("Config file error: Preset "+presetName+" not found at "+config.getBlame("preset")+"! Exiting.");
			exit(EXIT_CONFIG_ERROR);
			return null;
		}
		try (InputStream in = u.openStream()) {
			QDIni preset = QDIni.load("<preset "+presetName+">", in);
			config = preset.merge(config);
		} catch (IOException e) {
			Log.error("Failed to load preset "+presetName+"! Exiting.", e);
			exit(EXIT_CONFIG_ERROR);
			return null;
		}
		return config;
	}
	
	private static void cleanup() {
		config = null;
		for (ExceptableRunnable er : cleanup) {
			try {
				er.run();
			} catch (Throwable t) {}
		}
		destroyOkHttp();
		cleanup = null;
	}
	
	static void saveState() throws IOException {
		saveJson(stateFile, state);
	}
	
	static void saveJson(File file, JsonObject obj) throws IOException {
		try (FileOutputStream fos = new FileOutputStream(file)) {
			JsonWriter.on(fos).object(obj).done();
		}
	}
	
	public static AssertionError exit(int code) {
		cleanup();
		System.exit(code);
		throw new AssertionError("unreachable");
	}

	/* (non-Javadoc)
	 * used in the agent to suspend the update flow at a safe point if we're waiting for a
	 * System.exit due to the user closing the puppet dialog (the puppet handling is multithreaded,
	 * and a mutex is used to ensure we don't kill the updater during a sensitive period that could
	 * corrupt the directory state)
	 */
	public static void blockForever() {
		while (true) {
			try {
				Thread.sleep(Integer.MAX_VALUE);
			} catch (InterruptedException e) {}
		}
	}

	private static <K, V> void deleteNonMatchingKeys(Map<K, V> map, K key) {
		var iter = map.entrySet().iterator();
		while (iter.hasNext()) {
			if (!Objects.equals(iter.next().getKey(), key)) {
				iter.remove();
			}
		}
	}
	
	public static void addCleanupAction(ExceptableRunnable r) {
		cleanup.add(r);
	}

	public static Config config() {
		return config;
	}

	public static OkHttpClient okhttp() {
		return okhttp;
	}
	
}

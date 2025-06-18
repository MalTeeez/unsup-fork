/*
 * This file is part of unsup.
 * Copyright © 2025 Una Kearney
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

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import com.github.bsideup.jabel.Desugar;
import com.unascribed.sup.agent.auth.AWS4Authorizer;
import com.unascribed.sup.agent.auth.Authorizer;
import com.unascribed.sup.agent.auth.BasicAuthorizer;
import com.unascribed.sup.agent.auth.BearerAuthorizer;
import com.unascribed.sup.agent.pieces.QDIni;
import com.unascribed.sup.agent.signing.SigProvider;
import com.unascribed.sup.data.ColorChoice;
import com.unascribed.sup.data.SourceFormat;
import com.unascribed.sup.data.SysPropDefs;
import com.unascribed.sup.data.SysProps;
import com.unascribed.sup.data.SysProps.Behavior;
import com.unascribed.sup.data.SysProps.PuppetMode;
import com.unascribed.sup.util.Bases;
import com.unascribed.sup.util.Multimap;
import com.unascribed.sup.util.Strings;

import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;

/**
 * An immutable snapshot of an "effective configuration", pulled from the config file (unsup.ini),
 * system properties, heuristics, etc.
 */
@Desugar
public record Config(
		boolean enforceSecureHashes,
		boolean useEnvs, String detectedEnv, Set<String> validEnvs,
		Behavior behavior, boolean offerChangeFlavors,
		SourceFormat format, URI source, boolean serverAuthority,
		boolean updateMMCPack, boolean noGui,
		String initialSubtitle,
		List<AuthorizerSpec> authorizers,
		Function<OkHttpClient, Dns> dnsBuilder,
		Map<String, String> defaultFlavors,
		SigProvider packSig, SigProvider altPackSig,
		Multimap<String, String> mmcComponentMap,
		Map<ColorChoice, String> colorChoices,
		Map<String, String> strings,
		Optional<String> modpackName, Optional<String> brandingIcon,
		Geometry flavorDialogGeom, double flavorDialogBias,
		PuppetMode puppetMode
	) {
	
	public Dns dns(OkHttpClient client) {
		return dnsBuilder.apply(client);
	}
	
	@SuppressWarnings("deprecation")
	public static Config parse(QDIni config, String arg) {
		boolean useEnvs = false;
		boolean noGui = determineNoGui(config);
		boolean enforceSecureHashes = config.getBoolean("enforce_secure_hashes", false);
		SourceFormat format = config.getEnum("source_format", SourceFormat.class, null);
		URI source = null;
		boolean serverAuthority = config.getBoolean("server_authority", false);
		boolean updateMMCPack = config.getBoolean("update_mmc_pack", false);
		List<AuthorizerSpec> authorizers = new ArrayList<>();
		Behavior behavior = Behavior.MANUAL;
		SigProvider packSig = null;
		SigProvider altPackSig = null;
		String detectedEnv = null;
		Set<String> validEnvs = new HashSet<>();
		Function<OkHttpClient, Dns> dnsBuilder = client -> Dns.SYSTEM;
		EnumMap<ColorChoice, String> colorChoices = new EnumMap<>(ColorChoice.class);
		Geometry flavorDialogGeom = new Geometry(600, 400);
		double flavorDialogBias = config.getDouble("flavor_dialog_bias", 0.5);
		boolean offerChangeFlavors = config.getBoolean("offer_change_flavors", false);
		PuppetMode puppetMode = PuppetMode.AUTO;
		String initialSubtitle = config.get("subtitle", "");
		Map<String, String> defaultFlavors = new HashMap<>();
		Multimap<String, String> mmcComponentMap = new Multimap<>();
		Map<String, String> strings = new HashMap<>();
		Optional<String> modpackName = Optional.ofNullable(config.get("branding.modpack_name"));
		Optional<String> brandingIcon = Optional.ofNullable(config.get("branding.icon"));
		
		try {
			source = new URI(config.get("source"));
		} catch (URISyntaxException e) {
			Log.error("Config error: source URL is malformed! "+e.getMessage()+". Exiting.");
			throw Agent.exit(Agent.EXIT_CONFIG_ERROR);
		}
		
		if (System.getProperty(SysPropDefs.DISABLE_RECONCILIATION) != null || System.getProperty(SysPropDefs.BEHAVIOR) != null) {
			behavior = SysProps.BEHAVIOR;
		} else {
			behavior = config.getEnum("behavior", Behavior.class, behavior);
		}
		
		if (config.containsKey("public_key")) {
			packSig = parsePackSig(config, "public_key");
			if (config.containsKey("alt_public_key")) {
				altPackSig = parsePackSig(config, "alt_public_key");
			}
		}
		
		switch (config.get("dns", "system")) {
			case "system":
				Log.debug("Using system DNS for DNS queries");
				dnsBuilder = client -> Dns.SYSTEM;
				break;
			case "quad9": {
				List<InetAddress> quad9Hosts;
				try {
					quad9Hosts = Arrays.asList(
						InetAddress.getByName("9.9.9.10"),
						InetAddress.getByName("2620:fe::10"),
						InetAddress.getByName("149.112.112.10"),
						InetAddress.getByName("2620:fe::fe:10")
					);
				} catch (UnknownHostException e) {
					// not a possible throw for a well-formed IP string
					throw new AssertionError(e);
				}
				dnsBuilder = client -> new DnsOverHttps.Builder()
						.url(HttpUrl.get("https://dns10.quad9.net/dns-query"))
						.bootstrapDnsHosts(quad9Hosts)
						.client(client)
						.build();
				Log.debug("Using Quad9 for DNS queries");
				break;
			}
			default: {
				String dnsStr = config.get("dns");
				if (dnsStr.startsWith("https://")) {
					dnsBuilder = client -> new DnsOverHttps.Builder()
							.url(HttpUrl.get(dnsStr))
							.client(client)
							.build();
					Log.debug("Using "+dnsStr+" for DNS queries");
				} else {
					Log.error("Config file error: dns is not valid at "+config.getBlame("dns")+" - expected 'system', 'quad9', or an HTTPS URL, but got '"+dnsStr+"'! Exiting.");
					throw Agent.exit(Agent.EXIT_CONFIG_ERROR);
				}
				break;
			}
		}
		
		for (var cc : ColorChoice.values()) {
			colorChoices.put(cc, config.get("colors."+cc.configName, Bases.intToHex(cc.defaultValue)));
		}
		
		if (config.containsKey("flavor_dialog_geom")) {
			flavorDialogGeom = Geometry.parse(config.get("flavor_dialog_geom"));
		}
		
		Multimap<String, String> envMarkers = new Multimap<>();
		for (var k : config.keySet()) {
			var spl = k.split("\\.", 2);
			var sect = spl[0];
			var subkey = spl.length == 2 ? spl[1] : null;
			var v = config.get(k);
			switch (sect) {
				case "strings" -> strings.put(subkey, v);
				case "authorization" -> {
					String[] vspl = v.split(" ", 2);
					Authorizer a = switch (vspl[0]) {
						case "Basic" -> {
							if (vspl[1].contains(":")) {
								yield BasicAuthorizer.fromStapled(vspl[1]);
							}
							yield BasicAuthorizer.fromToken(vspl[1]);
						}
						case "Bearer" -> new BearerAuthorizer(vspl[1]);
						case "AWS4-HMAC-SHA256" -> {
							String[] pieces = vspl[1].split(":", 3);
							yield new AWS4Authorizer(pieces[0], pieces[1], pieces.length >= 3 ? pieces[2] : "us-east-1");
						}
						default -> null;
					};
					if (a != null) {
						authorizers.add(new AuthorizerSpec(subkey, a));
					} else {
						Log.error("Config error: authorizer for "+subkey+" is malformed! Exiting.");
						throw Agent.exit(Agent.EXIT_CONFIG_ERROR);
					}
				}
				case "env" -> {
					if (subkey != null && subkey.endsWith(".marker")) {
						String env = subkey.substring(0, subkey.length()-7);
						validEnvs.add(env);
						envMarkers.put(env, v);
					}
				}
				case "mmc-component-map" -> {
					mmcComponentMap.put(subkey, v);
				}
			}
		}
		
		if (!SysProps.IGNORE_ENVS && config.getBoolean("use_envs", useEnvs)) {
			useEnvs = true;
			String forcedEnv = arg == null ? config.get("force_env") : arg;
			if (Agent.standalone && forcedEnv == null) {
				Log.error("Cannot sync an env-based config in standalone mode unless an argument is given specifying the env! Exiting.");
				throw Agent.exit(Agent.EXIT_CONFIG_ERROR);
			}
			List<String> checkedMarkers = new ArrayList<>();
			String ourEnv = forcedEnv;
			if (ourEnv == null) {
				for (var en : envMarkers.mapEntries()) {
					for (String possibility : en.getValue()) {
						if (possibility.equals("*")) {
							ourEnv = en.getKey();
							break;
						} else {
							checkedMarkers.add(possibility);
							if (ClassLoader.getSystemClassLoader().getResource(possibility.replace('.', '/')+".class") != null) {
								ourEnv = en.getKey();
								break;
							}
						}
					}
				}
			}
			if (validEnvs.isEmpty()) {
				Log.error("use_envs is true, but found no env declarations! Exiting.");
				throw Agent.exit(Agent.EXIT_CONFIG_ERROR);
			}
			if (ourEnv == null) {
				Log.error("use_envs is true, and we found no env markers! Checked for the following markers:");
				for (String s : checkedMarkers) {
					Log.error("- "+s);
				}
				Log.error("Exiting.");
				throw Agent.exit(Agent.EXIT_CONFIG_ERROR);
			}
			if (!validEnvs.contains(ourEnv)) {
				Log.error("Invalid env specified: \""+ourEnv+"\"! Valid envs:");
				for (String s : validEnvs) {
					Log.error("- "+s);
				}
				Log.error("Exiting.");
				throw Agent.exit(Agent.EXIT_CONFIG_ERROR);
			}
			if (Agent.standalone) {
				Log.info("Declared env is "+ourEnv);
			} else {
				Log.info("Detected env is "+ourEnv);
			}
			detectedEnv = ourEnv;
		}
		
		return new Config(enforceSecureHashes, useEnvs, detectedEnv, validEnvs, behavior,
				offerChangeFlavors, format, source, serverAuthority, updateMMCPack, noGui,
				initialSubtitle, Collections.unmodifiableList(authorizers), dnsBuilder,
				Collections.unmodifiableMap(defaultFlavors), packSig, altPackSig,
				mmcComponentMap.unmodifiable(), Collections.unmodifiableMap(colorChoices),
				Collections.unmodifiableMap(strings), modpackName, brandingIcon,
				flavorDialogGeom, flavorDialogBias, puppetMode);
	}

	private static SigProvider parsePackSig(QDIni ini, String key) {
		try {
			return SigProvider.parse(ini.get(key));
		} catch (Throwable t) {
			Log.error("Config file error: "+key+" is not valid at "+ini.getBlame(key)+"! Exiting.", t);
			Agent.exit(Agent.EXIT_CONFIG_ERROR);
			return null;
		}
	}

	private static boolean determineNoGui(QDIni ini) {
		if (Agent.standalone) return !SysProps.GUI_IN_STANDALONE;
		if (ini.containsKey("no_gui")) return ini.getBoolean("no_gui", false);
		if (ini.getBoolean("recognize_nogui", false)) {
			String cmd = System.getProperty("sun.java.command");
			if (cmd != null) {
				return Strings.containsWholeWord(cmd, "nogui") || Strings.containsWholeWord(cmd, "--nogui");
			}
		}
		return false;
	}

	@Desugar
	public record Geometry(int width, int height) {
		
		public static Geometry parse(String s) {
			String[] spl = s.split("x", 2);
			if (spl.length != 2) throw new IllegalArgumentException("Cannot parse "+s+" as geometry: Wrong number of components");
			int w, h;
			try {
				w = Integer.parseInt(spl[0]);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Cannot parse "+s+" as geometry: Width is not a number");
			}
			try {
				h = Integer.parseInt(spl[1]);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Cannot parse "+s+" as geometry: Height is not a number");
			}
			return new Geometry(w, h);
		}
		
		@Override
		public String toString() {
			return width+"x"+height;
		}
	}
	
	@Desugar
	public record AuthorizerSpec(String urlPrefix, Authorizer auth) {}

}

/*
 * This file is part of unsup.
 * Copyright © 2025 Exa Skye
 * https://git.sleeping.town/exa/unsup
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

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
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
import com.unascribed.sup.agent.auth.Authorizer;
import com.unascribed.sup.agent.auth.Authorizer.AuthorizerSpec;
import com.unascribed.sup.agent.pieces.QDIni;
import com.unascribed.sup.agent.signing.SigProvider;
import com.unascribed.sup.agent.util.SimpleProxySelector;
import com.unascribed.sup.ann.NotNull;
import com.unascribed.sup.ann.Nullable;
import com.unascribed.sup.data.ColorChoice;
import com.unascribed.sup.data.SourceFormat;
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
		boolean useEnvs, @Nullable String detectedEnv, @NotNull Set<String> validEnvs,
		@NotNull Behavior behavior, boolean offerChangeFlavors,
		@NotNull SourceFormat format, @NotNull URI source, boolean serverAuthority,
		boolean updateMMCPack, boolean noGui,
		@NotNull String initialSubtitle,
		@NotNull List<AuthorizerSpec> authorizers,
		@NotNull Function<OkHttpClient, @NotNull Dns> dnsBuilder,
		@NotNull Map<String, String> defaultFlavors,
		@Nullable SigProvider packSig, @Nullable SigProvider altPackSig,
		@NotNull Multimap<String, String> mmcComponentMap,
		@NotNull Map<ColorChoice, String> colorChoices,
		@NotNull Map<String, String> strings,
		@NotNull Optional<String> modpackName, @NotNull Optional<String> brandingIcon,
		@NotNull Geometry flavorDialogGeom, double flavorDialogBias,
		@NotNull PuppetMode puppetMode, @NotNull String lang,
		@Nullable ProxySelector proxySelector, @NotNull List<X509Certificate> additionalCaCerts,
		@NotNull List<String> insecureHosts,
		boolean usePlatformCaCerts, boolean useBuiltinCaCerts
	) {
	
	public Config() {
		this(
			/*boolean enforceSecureHashes*/false,
			/*boolean useEnvs*/false,
			/*@Nullable String detectedEnv*/null,
			/*Set<String> validEnvs*/Collections.emptySet(),
			/*Behavior behavior*/Behavior.MANUAL,
			/*boolean offerChangeFlavors*/false,
			/*SourceFormat format*/SourceFormat.NONE,
			/*URI source*/URI.create("invalid://invalid.invalid"),
			/*boolean serverAuthority*/false,
			/*boolean updateMMCPack*/false,
			/*boolean noGui*/false,
			/*String initialSubtitle*/"",
			/*List<AuthorizerSpec> authorizers*/Collections.emptyList(),
			/*Function<OkHttpClient, @NotNull Dns> dnsBuilder*/c -> Dns.SYSTEM,
			/*Map<String, String> defaultFlavors*/Collections.emptyMap(),
			/*@Nullable SigProvider packSig*/null,
			/*@Nullable SigProvider altPackSig*/null,
			/*Multimap<String, String> mmcComponentMap*/new Multimap<String, String>().unmodifiable(),
			/*Map<ColorChoice, String> colorChoices*/Collections.emptyMap(),
			/*Map<String, String> strings*/Collections.emptyMap(),
			/*Optional<String> modpackName*/Optional.empty(),
			/*Optional<String> brandingIcon*/Optional.empty(),
			/*Geometry flavorDialogGeom*/new Geometry(600, 400),
			/*double flavorDialogBias*/0.5,
			/*PuppetMode puppetMode*/PuppetMode.AUTO,
			/*String lang*/SysProps.LANGUAGE.orBias(),
			/*ProxySelector proxySelector*/ProxySelector.getDefault(),
			/*List<X509Certificate> additionalCaCerts*/Collections.emptyList(),
			/*List<String> insecureHosts*/Collections.emptyList(),
			/*boolean usePlatformCaCerts*/true,
			/*boolean useBuiltinCaCerts*/true
		);
	}

	public Dns dns(OkHttpClient client) {
		return dnsBuilder.apply(client);
	}
	
	public static Config parse(QDIni config, @Nullable String arg, String lang) {
		// this is kind of a mess, but it's also very direct.
		// could smother it in reflection to make it more "elegant" but who fucking cares man
		// this whole codebase is kind of about stringing together a bunch of disparate nonsense
		// at some point we've got to accept it's just gonna look like this.
		// times we have wanted to rewrite this, tickled it a bit and then gave up: ||||
		
		boolean useEnvs = false;
		URI source = null;
		List<AuthorizerSpec> authorizers = new ArrayList<>();
		String detectedEnv = null;
		Set<String> validEnvs = new HashSet<>();
		EnumMap<ColorChoice, String> colorChoices = new EnumMap<>(ColorChoice.class);
		Map<String, String> defaultFlavors = new HashMap<>();
		Multimap<String, String> mmcComponentMap = new Multimap<>();
		Map<String, String> strings = new HashMap<>();

		Behavior behavior = SysProps.BEHAVIOR.orElse(config.getEnum("behavior", Behavior.class, Behavior.MANUAL));
		PuppetMode puppetMode = SysProps.PUPPET_MODE.orElse(config.getEnum("puppet_mode", PuppetMode.class, PuppetMode.AUTO));
		SigProvider packSig = parsePackSig(config, "public_key");
		SigProvider altPackSig = parsePackSig(config, "alt_public_key");
		Function<OkHttpClient, Dns> dnsBuilder = parseDns(config.get("dns", "system"))
			.orElseGet(() -> {
				Log.error("Config file error: dns is not valid at "+config.getBlame("dns")+" - expected 'system', 'quad9', or an HTTPS URL, but got '"+config.get("dns")+"'! Exiting.");
				throw ExitCode.CONFIG_ERROR.exit();
			});
		boolean noGui = determineNoGui(config);
		boolean enforceSecureHashes = config.getBoolean("enforce_secure_hashes", false);
		SourceFormat format = config.getEnum("source_format", SourceFormat.class, null);
		boolean serverAuthority = config.getBoolean("server_authority", false);
		boolean updateMMCPack = config.getBoolean("update_mmc_pack", false);
		Geometry flavorDialogGeom = Optional.ofNullable(config.get("flavor_dialog_geom"))
				.map(Geometry::parse).orElse(new Geometry(600, 400));
		double flavorDialogBias = config.getDouble("flavor_dialog_bias", 0.5);
		boolean offerChangeFlavors = config.getBoolean("offer_change_flavors", false);
		String initialSubtitle = config.get("subtitle", "");
		Optional<String> modpackName = Optional.ofNullable(config.get("branding.modpack_name"));
		Optional<String> brandingIcon = Optional.ofNullable(config.get("branding.icon"));
		ProxySelector proxySelector = ProxySelector.getDefault();
		List<X509Certificate> additionalCaCerts = new ArrayList<>();
		List<String> insecureHosts = new ArrayList<>();
		boolean usePlatformCaCerts = config.getBoolean("http.use_platform_cacerts", true);
		boolean useBuiltinCaCerts = config.getBoolean("http.use_builtin_cacerts", true);
		
		try {
			source = new URI(config.get("source"));
		} catch (URISyntaxException e) {
			Log.error("Config error: source URL is malformed! "+e.getMessage()+". Exiting.");
			throw ExitCode.CONFIG_ERROR.exit();
		}
		
		if (source.getRawUserInfo() != null) {
			Log.error("Config error: source URL is malformed! Authorization in the URL is ambiguous and must be specified by prefix in the [authorization] section. Exiting.");
			throw ExitCode.CONFIG_ERROR.exit();
		}
		
		for (var cc : ColorChoice.values()) {
			colorChoices.put(cc, config.get("colors."+cc.configName, Bases.intToHex(cc.defaultValue)));
		}
		
		var envMarkers = new Multimap<String, String>();
		for (var k : config.keySet()) {
			var spl = k.split("\\.", 2);
			var sect = spl[0];
			var subkey = spl.length == 2 ? spl[1] : null;
			var v = config.get(k);
			switch (sect) {
				case "strings" -> strings.put(subkey, v);
				case "mmc-component-map" -> mmcComponentMap.put(subkey, v);
				case "flavors" -> defaultFlavors.put(subkey, v);
				case "authorization" -> {
					authorizers.add(Authorizer.parseSpec(subkey, v).orElseGet(() -> {
						Log.error("Config error: authorizer for "+subkey+" is malformed! Exiting.");
						throw ExitCode.CONFIG_ERROR.exit();
					}));
				}
				case "env" -> {
					if (subkey != null && subkey.endsWith(".marker")) {
						String env = subkey.substring(0, subkey.length()-7);
						validEnvs.add(env);
						for (var av : config.getAll(k)) {
							envMarkers.put(env, av);
						}
					}
				}
				case "http" -> {
					if ("additional_cacert".equals(subkey)) {
						try {
							additionalCaCerts.add((X509Certificate)CertificateFactory.getInstance("X.509")
									.generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(v))));
						} catch (CertificateException e) {
							Log.error("Config error: CA certificate data for "+subkey+" is malformed! Exiting.", e);
							throw ExitCode.CONFIG_ERROR.exit();
						}
					} else if ("insecure_host".equals(subkey)) {
						insecureHosts.add(v);
					}
				}
			}
		}
		
		if (config.getBoolean("use_envs", useEnvs) && !SysProps.IGNORE_ENVS.orBias()) {
			useEnvs = true;
			String forcedEnv = arg == null ? config.get("force_env") : arg;
			if (Agent.standalone && forcedEnv == null) {
				Log.error("Cannot sync an env-based config in standalone mode unless an argument is given specifying the env! Exiting.");
				throw ExitCode.CONFIG_ERROR.exit();
			}
			List<String> checkedMarkers = new ArrayList<>();
			String ourEnv = forcedEnv;
			if (ourEnv == null) {
				glass: for (var en : envMarkers.mapEntries()) {
					for (String possibility : en.getValue()) {
						if (possibility.equals("*")) {
							ourEnv = en.getKey();
							break glass;
						} else {
							checkedMarkers.add(possibility);
							if (!possibility.contains("/")) {
								possibility = possibility.replace('.', '/')+".class";
							}
							var cl = Bootstrap.class.getClassLoader();
							assert cl != null;
							if (cl.getResource(possibility) != null) {
								ourEnv = en.getKey();
								break glass;
							}
						}
					}
				}
			}
			if (validEnvs.isEmpty()) {
				Log.error("use_envs is true, but found no env declarations! Exiting.");
				throw ExitCode.CONFIG_ERROR.exit();
			}
			if (ourEnv == null) {
				Log.error("use_envs is true, and we found no env markers! Checked for the following markers:");
				for (String s : checkedMarkers) {
					Log.error("- "+s);
				}
				Log.error("Exiting.");
				throw ExitCode.CONFIG_ERROR.exit();
			}
			if (!validEnvs.contains(ourEnv)) {
				Log.error("Invalid env specified: \""+ourEnv+"\"! Valid envs:");
				for (String s : validEnvs) {
					Log.error("- "+s);
				}
				Log.error("Exiting.");
				throw ExitCode.CONFIG_ERROR.exit();
			}
			if (forcedEnv != null) {
				Log.info("Declared env is "+ourEnv);
			} else {
				Log.info("Detected env is "+ourEnv);
			}
			detectedEnv = ourEnv;
		}

		var proxyStr = config.get("http.proxy");
		if (proxyStr != null) {
			if ("none".equals(proxyStr)) {
				Log.debug("Using no proxy");
				proxySelector = new SimpleProxySelector(Proxy.NO_PROXY);
			} else if ("default".equals(proxyStr)) {
				Log.debug("Using default proxy");
				proxySelector = ProxySelector.getDefault();
			} else {
				try {
					URI proxyUri = new URI(proxyStr);
					if (proxyUri.getRawUserInfo() != null) {
						Log.error("Config error: HTTP proxy URI is malformed! Authentication is not supported. Exiting.");
						throw ExitCode.CONFIG_ERROR.exit();
					}
					if ((proxyUri.getRawPath() != null && proxyUri.getRawPath().length() > 1) || proxyUri.getRawQuery() != null || proxyUri.getRawFragment() != null) {
						Log.error("Config error: HTTP proxy URI is malformed! A path must not be specified. Exiting.");
						throw ExitCode.CONFIG_ERROR.exit();
					}
					int defaultPort;
					Proxy.Type type;
					switch (proxyUri.getScheme()) {
						case "http" -> {
							defaultPort = 80;
							type = Proxy.Type.HTTP;
						}
						case "socks", "socks4", "socks5" -> {
							defaultPort = 1080;
							type = Proxy.Type.SOCKS;
						}
						default -> {
							Log.error("Config error: HTTP proxy URI is malformed! Unknown scheme "+proxyUri.getScheme()+". Exiting.");
							throw ExitCode.CONFIG_ERROR.exit();
						}
					};
					int port = proxyUri.getPort();
					if (port == -1) port = defaultPort;
					Log.debug("Using "+type.name()+" proxy at "+proxyUri.getHost()+":"+port);
					proxySelector = new SimpleProxySelector(new Proxy(type, new InetSocketAddress(proxyUri.getHost(), port)));
				} catch (URISyntaxException e) {
					Log.error("Config error: HTTP proxy URI is malformed! "+e.getMessage()+". Exiting.");
					throw ExitCode.CONFIG_ERROR.exit();
				}
			}
		} else {
			Log.debug("Using default proxy");
		}
		
		return new Config(enforceSecureHashes, useEnvs, detectedEnv, validEnvs, behavior,
				offerChangeFlavors, format, source, serverAuthority, updateMMCPack, noGui,
				initialSubtitle, Collections.unmodifiableList(authorizers), dnsBuilder,
				Collections.unmodifiableMap(defaultFlavors), packSig, altPackSig,
				mmcComponentMap.unmodifiable(), Collections.unmodifiableMap(colorChoices),
				Collections.unmodifiableMap(strings), modpackName, brandingIcon,
				flavorDialogGeom, flavorDialogBias, puppetMode, lang, proxySelector,
				Collections.unmodifiableList(additionalCaCerts), Collections.unmodifiableList(insecureHosts),
				usePlatformCaCerts, useBuiltinCaCerts);
	}

	private static Optional<Function<OkHttpClient, @NotNull Dns>> parseDns(String v) {
		switch (v) {
			case "system" -> {
				Log.debug("Using system DNS for DNS queries");
				return Optional.of(client -> Dns.SYSTEM);
			}
			case "quad9" -> {
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
				Log.debug("Using Quad9 for DNS queries");
				return Optional.of(client -> new DnsOverHttps.Builder()
						.url(HttpUrl.get("https://dns10.quad9.net/dns-query"))
						.bootstrapDnsHosts(quad9Hosts)
						.client(client)
						.build());
			}
			default -> {
				if (v.startsWith("https://")) {
					Log.debug("Using "+v+" for DNS queries");
					return Optional.of(client -> new DnsOverHttps.Builder()
							.url(HttpUrl.get(v))
							.client(client)
							.build());
				} else {
					return Optional.empty();
				}
			}
		}
	}

	private static @Nullable SigProvider parsePackSig(QDIni config, String key) {
		if (config.containsKey(key)) {
			try {
				return SigProvider.parse(config.get(key));
			} catch (Throwable t) {
				Log.error("Config file error: "+key+" is not valid at "+config.getBlame(key)+"! Exiting.", t);
				throw ExitCode.CONFIG_ERROR.exit();
			}
		} else {
			return null;
		}
	}

	private static boolean determineNoGui(QDIni ini) {
		if (Agent.standalone) return !SysProps.GUI_IN_STANDALONE.orBias();
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

}

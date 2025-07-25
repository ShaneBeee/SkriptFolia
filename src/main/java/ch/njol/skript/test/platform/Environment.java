package ch.njol.skript.test.platform;

import ch.njol.skript.test.utils.TestResults;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Test environment information.
 */
public class Environment {

	private static final Gson gson = new Gson();

	/**
	 * Name of this environment. For example, spigot-1.14.
	 */
	private final String name;

	/**
	 * Resource that needs to be downloaded for the environment.
	 */
	public static class Resource {

		/**
		 * Where to get this resource.
		 */
		private final String source;

		/**
		 * Path under platform root where it should be placed.
		 * Directories created as needed.
		 */
		private final String target;

		public Resource(String url, String target) {
			this.source = url;
			this.target = target;
		}

		public String getSource() {
			return source;
		}

		public String getTarget() {
			return target;
		}

	}

	public static class PaperResource extends Resource {

		private final String version;
		@Nullable
		private transient String source;

		public PaperResource(String version, String target) {
			super(null, target);
			this.version = version;
		}

		@Override
		public String getSource() {
			try {
				generateSource();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			if (source == null)
				throw new IllegalStateException();
			return source;
		}

		private void generateSource() throws IOException, InterruptedException {
			if (source != null)
				return;

			HttpClient client = HttpClient.newHttpClient();
			HttpRequest buildRequest = HttpRequest.newBuilder()
				.uri(URI.create("https://fill.papermc.io/v3/projects/paper/versions/" + version + "/builds/latest"))
				.header("User-Agent", "SkriptLang/Skript/{@version} (admin@skriptlang.org)")
				.GET()
				.build();
			HttpResponse<InputStream> buildResponse = client.send(buildRequest, BodyHandlers.ofInputStream());
			JsonObject buildObject;
			try (InputStreamReader reader = new InputStreamReader(buildResponse.body(), StandardCharsets.UTF_8)) {
				buildObject = gson.fromJson(reader, JsonObject.class);
			}
			String downloadURL = buildObject.getAsJsonObject("downloads")
				.getAsJsonObject("server:default")
				.get("url").getAsString();
			assert downloadURL != null && !downloadURL.isEmpty();
			source = downloadURL;
		}
	}

	/**
	 * Resources that need to be copied.
	 */
	private final List<Resource> resources;

	/**
	 * Resources that need to be downloaded.
	 */
	@Nullable
	private final List<Resource> downloads;

	/**
	 * Paper resources that need to be downloaded.
	 */
	@Nullable
	private final List<PaperResource> paperDownloads;

	/**
	 * Where Skript should be placed under platform root.
	 * Directories created as needed.
	 */
	private final String skriptTarget;

	/**
	 * Added after platform's own JVM flags.
	 */
	private final String[] commandLine;

	public Environment(String name, List<Resource> resources, @Nullable List<Resource> downloads, @Nullable List<PaperResource> paperDownloads, String skriptTarget, String... commandLine) {
		this.name = name;
		this.resources = resources;
		this.downloads = downloads;
		this.paperDownloads = paperDownloads;
		this.skriptTarget = skriptTarget;
		this.commandLine = commandLine;
	}

	public String getName() {
		return name;
	}

	public void initialize(Path dataRoot, Path runnerRoot, boolean remake) throws IOException {
		Path env = runnerRoot.resolve(name);
		boolean onlyCopySkript = Files.exists(env) && !remake;

		// Copy Skript to platform
		Path skript = env.resolve(skriptTarget);
		Files.createDirectories(skript.getParent());
		try {
			Files.copy(new File(getClass().getProtectionDomain().getCodeSource().getLocation()
				.toURI()).toPath(), skript, StandardCopyOption.REPLACE_EXISTING);
		} catch (URISyntaxException e) {
			throw new AssertionError(e);
		}

		if (onlyCopySkript) {
			return;
		}

		// Copy resources
		for (Resource resource : resources) {
			Path source = dataRoot.resolve(resource.getSource());
			Path target = env.resolve(resource.getTarget());
			Files.createDirectories(target.getParent());
			Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
		}

		List<Resource> downloads = new ArrayList<>();
		if (this.downloads != null)
			downloads.addAll(this.downloads);
		if (this.paperDownloads != null)
			downloads.addAll(this.paperDownloads);
		// Download additional resources
		for (Resource resource : downloads) {
			assert resource != null;
			String source = resource.getSource();
			URL url = new URL(source);
			Path target = env.resolve(resource.getTarget());
			Files.createDirectories(target.getParent());
			try (InputStream is = url.openStream()) {
				Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

	@Nullable
	public TestResults runTests(Path runnerRoot, Path testsRoot, boolean devMode, boolean genDocs, boolean jUnit, boolean debug,
	                            String verbosity, long timeout, Set<String> jvmArgs) throws IOException, InterruptedException {
		
		Path env = runnerRoot.resolve(name);
		Path resultsPath = env.resolve("test_results.json");
		Files.deleteIfExists(resultsPath);
		List<String> args = new ArrayList<>();
		args.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
		args.add("-ea");
		args.add("-Dskript.testing.enabled=true");
		args.add("-Dskript.testing.dir=" + testsRoot);
		args.add("-Dskript.testing.devMode=" + devMode);
		args.add("-Dskript.testing.genDocs=" + genDocs);
		args.add("-Dskript.testing.junit=" + jUnit);
		if (!verbosity.equalsIgnoreCase("null"))
			args.add("-Dskript.testing.verbosity=" + verbosity);
		if (genDocs)
			args.add("-Dskript.forceregisterhooks=true");
		args.add("-Dskript.testing.results=test_results.json");
		args.add("-Ddisable.watchdog=true");
		if (debug)
			args.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8000");
		args.add("-Duser.language=en");
		args.add("-Duser.country=US");
		args.addAll(jvmArgs);
		args.addAll(Arrays.asList(commandLine));

		Process process = new ProcessBuilder(args)
				.directory(env.toFile())
				.redirectOutput(Redirect.INHERIT)
				.redirectError(Redirect.INHERIT)
				.redirectInput(Redirect.INHERIT)
				.start();

		// When we exit, try to make them exit too
		Runtime.getRuntime().addShutdownHook(new Thread(process::destroy));

		// Catch tests running for abnormally long time
		if (!devMode && timeout > 0) {
			new Timer("runner watchdog", true).schedule(new TimerTask() {
				@Override
				public void run() {
					if (process.isAlive()) {
						System.err.println("Test environment is taking too long, failing...");
						System.exit(1);
					}
				}
			}, timeout);
		}

		int code = process.waitFor();
		if (code != 0)
			throw new IOException("environment returned with code " + code);

		// Read test results
		if (!Files.exists(resultsPath))
			return null;
		TestResults results = new Gson().fromJson(new String(Files.readAllBytes(resultsPath)), TestResults.class);
		assert results != null;
		return results;
	}

}

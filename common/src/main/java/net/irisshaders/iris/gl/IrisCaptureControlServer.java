package net.irisshaders.iris.gl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

public final class IrisCaptureControlServer {
	private static final Gson GSON = new Gson();
	private static HttpServer server;
	private static String token;

	private IrisCaptureControlServer() {
	}

	public static synchronized void start() {
		if (server != null) {
			return;
		}

		try {
			token = UUID.randomUUID().toString();
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/status", IrisCaptureControlServer::handleStatus);
			server.createContext("/reload_shader", IrisCaptureControlServer::handleReloadShader);
			server.createContext("/capture_pass", IrisCaptureControlServer::handleCapturePass);
			server.createContext("/capture_multi", IrisCaptureControlServer::handleCaptureMulti);
			server.setExecutor(Executors.newCachedThreadPool(r -> {
				Thread thread = new Thread(r, "Iris Capture Control");
				thread.setDaemon(true);
				return thread;
			}));
			server.start();
			writeControlFile(server.getAddress().getPort(), token);
			Iris.logger.info("Started Iris capture control server on 127.0.0.1:{}", server.getAddress().getPort());
		} catch (IOException e) {
			throw new RuntimeException("Failed to start Iris capture control server", e);
		}
	}

	private static void handleStatus(HttpExchange exchange) throws IOException {
		try {
			if (!checkAuth(exchange)) {
				return;
			}
			IrisCaptureManager.CaptureStatus status = IrisCaptureManager.getStatus();
			JsonObject response = new JsonObject();
			response.addProperty("pending", status.pending());
			response.addProperty("active", status.active());
			response.addProperty("saving", status.saving());
			response.addProperty("lastOutputPath", status.lastOutputPath() == null ? null : status.lastOutputPath().toString());
			response.addProperty("lastError", status.lastError());
			sendJson(exchange, 200, response);
		} catch (Exception e) {
			sendError(exchange, e);
		}
	}

	private static void handleReloadShader(HttpExchange exchange) throws IOException {
		try {
			if (!checkAuth(exchange)) {
				return;
			}
			runOnClientThread(() -> {
				try {
					Iris.reload();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
			sendOk(exchange);
		} catch (Exception e) {
			sendError(exchange, e);
		}
	}

	private static void handleCapturePass(HttpExchange exchange) throws IOException {
		try {
			if (!checkAuth(exchange)) {
				return;
			}
			JsonObject request = readJson(exchange);
			String pass = requireString(request, "pass");
			Path path = optionalPath(request, "path", IrisCaptureManager.defaultOutputPath(pass));
			runOnClientThread(() -> IrisRenderSystem.prepareCapture(path, pass));
			sendQueued(exchange, path);
		} catch (Exception e) {
			sendError(exchange, e);
		}
	}

	private static void handleCaptureMulti(HttpExchange exchange) throws IOException {
		try {
			if (!checkAuth(exchange)) {
				return;
			}
			JsonObject request = readJson(exchange);
			String type = requireString(request, "type");
			Path path = optionalPath(request, "path", IrisCaptureManager.defaultOutputPath(type));
			runOnClientThread(() -> IrisRenderSystem.prepareMultiCapture(path, type));
			sendQueued(exchange, path);
		} catch (Exception e) {
			sendError(exchange, e);
		}
	}

	private static void runOnClientThread(Runnable runnable) {
		CompletableFuture<Void> future = new CompletableFuture<>();
		Minecraft.getInstance().execute(() -> {
			try {
				runnable.run();
				future.complete(null);
			} catch (Throwable t) {
				future.completeExceptionally(t);
			}
		});
		future.join();
	}

	private static boolean checkAuth(HttpExchange exchange) throws IOException {
		String auth = exchange.getRequestHeaders().getFirst("Authorization");
		if (!("Bearer " + token).equals(auth)) {
			sendText(exchange, 401, "Unauthorized");
			return false;
		}
		return true;
	}

	private static JsonObject readJson(HttpExchange exchange) throws IOException {
		String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		if (body.isBlank()) {
			return new JsonObject();
		}
		return JsonParser.parseString(body).getAsJsonObject();
	}

	private static String requireString(JsonObject object, String key) {
		if (!object.has(key) || object.get(key).isJsonNull()) {
			throw new IllegalArgumentException("Missing required field: " + key);
		}
		return object.get(key).getAsString();
	}

	private static Path optionalPath(JsonObject object, String key, Path defaultPath) {
		if (!object.has(key) || object.get(key).isJsonNull() || object.get(key).getAsString().isBlank()) {
			return defaultPath;
		}
		return Path.of(object.get(key).getAsString());
	}

	private static void sendOk(HttpExchange exchange) throws IOException {
		JsonObject response = new JsonObject();
		response.addProperty("ok", true);
		sendJson(exchange, 200, response);
	}

	private static void sendQueued(HttpExchange exchange, Path path) throws IOException {
		JsonObject response = new JsonObject();
		response.addProperty("ok", true);
		response.addProperty("path", path.toString());
		sendJson(exchange, 200, response);
	}

	private static void sendJson(HttpExchange exchange, int status, JsonObject object) throws IOException {
		sendText(exchange, status, GSON.toJson(object));
	}

	private static void sendError(HttpExchange exchange, Exception e) throws IOException {
		JsonObject response = new JsonObject();
		response.addProperty("ok", false);
		response.addProperty("error", e.getMessage());
		sendJson(exchange, 500, response);
	}

	private static void sendText(HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream output = exchange.getResponseBody()) {
			output.write(bytes);
		}
	}

	private static void writeControlFile(int port, String token) throws IOException {
		JsonObject object = new JsonObject();
		object.addProperty("host", "127.0.0.1");
		object.addProperty("port", port);
		object.addProperty("token", token);
		Files.writeString(Path.of("iris-capture-control.json"), GSON.toJson(object), StandardCharsets.UTF_8);
	}
}

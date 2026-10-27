package com.example.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ExampleModClient implements ClientModInitializer {

    private static final String API_KEY = "AQ.Ab8RN6Kpjd_1dH6mfyKrah9lHASI0JD5gkfsZLNUFlTFRrhYEw";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @Override
    public void onInitializeClient() {
        // Registriert den Befehl direkt im Client-Command-System von Fabric
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("ai")
                .then(ClientCommandManager.argument("prompt", StringArgumentType.greedyString())
                    .executes(context -> {
                        String prompt = StringArgumentType.getString(context, "prompt");

                        // Rückmeldung im Chat
                        if (MinecraftClient.getInstance().player != null) {
                            MinecraftClient.getInstance().player.sendMessage(
                                Text.literal("[Gemini] Denk nach...").formatted(Formatting.GRAY), false
                            );
                        }

                        // Anfrage senden
                        sendToGemini(prompt);

                        return 1;
                    })
                )
            );
        });
    }

    private void sendToGemini(String prompt) {
        String jsonBody = "{\"contents\":[{\"parts\":[{\"text\":\"" + prompt.replace("\"", "\\\"") + "\"}]}]}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + API_KEY))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    MinecraftClient.getInstance().execute(() -> {
                        if (MinecraftClient.getInstance().player != null) {
                            if (response.statusCode() == 200) {
                                String aiReply = extractTextFromJson(response.body());
                                MinecraftClient.getInstance().player.sendMessage(
                                    Text.literal("[Gemini] " + aiReply).formatted(Formatting.GOLD), false
                                );
                            } else {
                                MinecraftClient.getInstance().player.sendMessage(
                                    Text.literal("[Gemini] Fehler: HTTP " + response.statusCode()).formatted(Formatting.RED), false
                                );
                            }
                        }
                    });
                }).exceptionally(ex -> {
                    MinecraftClient.getInstance().execute(() -> {
                        if (MinecraftClient.getInstance().player != null) {
                            MinecraftClient.getInstance().player.sendMessage(
                                Text.literal("[Gemini] Verbindungsfehler!").formatted(Formatting.RED), false
                            );
                        }
                    });
                    return null;
                });
    }

    private String extractTextFromJson(String json) {
        try {
            int textIndex = json.indexOf("\"text\": \"");
            if (textIndex != -1) {
                int startIndex = textIndex + 9;
                int endIndex = json.indexOf("\"", startIndex);
                StringBuilder result = new StringBuilder();
                boolean escaped = false;
                for (int i = startIndex; i < endIndex; i++) {
                    char c = json.charAt(i);
                    if (escaped) {
                        if (c == 'n') result.append("\n");
                        else result.append(c);
                        escaped = false;
                    } else if (c == '\\') {
                        escaped = true;
                    } else {
                        result.append(c);
                    }
                }
                return result.toString().replace("\\n", " ");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Konnte Antwort nicht lesen.";
    }
}

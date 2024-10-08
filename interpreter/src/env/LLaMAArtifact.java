package env;

import cartago.Artifact;
import cartago.OPERATION;
import cartago.OpFeedbackParam;
import jason.asSyntax.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.json.JSONObject;
import org.json.JSONArray;

public class LLaMAArtifact extends Artifact {
    private final String API_URL = "http://localhost:11434/api/chat"; // URL dell'API LLaMA
    // private final String API_KEY = "LA-bd84cae59a644303b5786ad04b47d864fb90e20c4a63453f96c384381a76c2b5"; // La tua chiave API per LLaMA
    private final HttpClient client = HttpClient.newHttpClient();

    @OPERATION
    public void classify_performative(String msg, OpFeedbackParam<Literal> performative){
        String answer = send_llama("classifier", msg);
        JSONObject json = new JSONObject(answer);
        Literal performativeAtom = Literal.parseLiteral(json.getString("performative"));
        performative.set(performativeAtom);
    }

    @OPERATION
    public void extract_entities(String performative, String msg, OpFeedbackParam<Literal> kqml_msg) {
        String answer = send_llama("extractor", msg);
        JSONObject json = new JSONObject(answer);
        JSONObject data = json.getJSONObject("data");
        Literal llama_msg = Literal.parseLiteral(data.getString("msg"));
        kqml_msg.set(llama_msg);
    }

    @OPERATION
    public void translate_achieve(String msg, Object triggers, OpFeedbackParam<Literal> translated_msg) {
        JSONObject input = new JSONObject();
        input.put("msg", msg);
        input.put("triggers", new JSONArray(triggers));
        String answer = send_llama("translator_achieve", input.toString());
        JSONObject json = new JSONObject(answer);
        String translated = json.getString("action");
        translated_msg.set(Literal.parseLiteral(translated));
    }

    @OPERATION
    public void translate_tell(String msg, Object beliefs, OpFeedbackParam<Literal> translated_msg) {
        JSONObject input = new JSONObject();
        input.put("msg", msg);
        input.put("beliefs", new JSONArray(beliefs));
        String answer = send_llama("translator_tell", input.toString());
        JSONObject json = new JSONObject(answer);
        String translated = json.getString("msg");
        translated_msg.set(Literal.parseLiteral(translated));
    }

    @OPERATION
    public void translate_ask(String msg, Object literals, OpFeedbackParam<Literal> translated_msg) {
        JSONObject input = new JSONObject();
        input.put("msg", msg);
        input.put("literals", new JSONArray(literals));
        String answer = send_llama("translator_ask", input.toString());
        JSONObject json = new JSONObject(answer);
        String translated = json.getString("info");
        translated_msg.set(Literal.parseLiteral(translated));
    }

    @OPERATION
    public void kqml2nl(String msg, String performative, Object literals, OpFeedbackParam<String> nl_msg) {
        JSONObject input = new JSONObject();
        input.put("content", msg);
        input.put("hints", new JSONArray(literals));
        input.put("performative", performative);
        String answer = send_llama("tell2nl", input.toString());
        JSONObject json = new JSONObject(answer);
        String nl = json.getString("msg");
        nl_msg.set(nl);
    }

    private String send_llama( String model, String message ) {
        try {
            // Costruisci il JSON del corpo della richiesta
            JSONObject json = new JSONObject();
            json.put("model", model);
            json.put("stream", false);
            JSONArray messages = new JSONArray();
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", message);
            messages.put(userMessage);
            json.put("messages", messages);
            System.out.println( "[DEBUG] JSON: " + json.toString());
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                // .header("Authorization", "Bearer " + API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .build();
            
            HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = httpResponse.body();
            JSONObject jsonResponse = new JSONObject(body);
            System.out.println(jsonResponse.toString());
            JSONObject answer = jsonResponse.getJSONObject("message");
            // JSONObject choice = choices.getJSONObject(0);
            String chatResponse = answer.getString("content");
           return chatResponse;

        } catch (Exception e) {
            failed("Error calling LLaMA API: " + e.getMessage());
        }
        return null;
    }

    @OPERATION
    public void send_llama(String message, OpFeedbackParam<String> response) {
        try {
            // Costruisci il JSON del corpo della richiesta
            JSONObject json = new JSONObject();
            json.put("model", "llama3.1");
            json.put("stream", false);
            JSONArray messages = new JSONArray();
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", message);
            messages.put(userMessage);
            json.put("messages", messages);
            System.out.println( "[DEBUG] JSON: " + json.toString());
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                // .header("Authorization", "Bearer " + API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .build();
            
            HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = httpResponse.body();
            JSONObject jsonResponse = new JSONObject(body);
            System.out.println(jsonResponse.toString());
            JSONObject answer = jsonResponse.getJSONObject("message");
            // JSONObject choice = choices.getJSONObject(0);
            String chatResponse = answer.getString("content");
            response.set(chatResponse);

        } catch (Exception e) {
            failed("Error calling LLaMA API: " + e.getMessage());
        }
    }
}

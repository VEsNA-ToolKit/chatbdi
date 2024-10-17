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
    private final String API_URL = "http://localhost:11434/api/chat"; 
    private final HttpClient client = HttpClient.newHttpClient();

    @OPERATION
    public void classify_performative(String msg, OpFeedbackParam<Literal> performative){
        String answer = send_llama("nl2performative", msg);
        performative.set(Literal.parseLiteral(answer));
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
        input.put("hints", new JSONArray(triggers));
        String answer = send_llama("nl2kqml", input.toString());
        translated_msg.set(Literal.parseLiteral(answer));
    }

    @OPERATION
    public void translate_ask(String msg, Object beliefs, OpFeedbackParam<Literal> translated_msg) {
        JSONObject input = new JSONObject();
        input.put("msg", msg);
        input.put("hints", new JSONArray(beliefs));
        String answer = send_llama("nl2kqml", input.toString());
        translated_msg.set(Literal.parseLiteral(answer));
    }

    @OPERATION
    public void translate_tell(String msg, Object literals, OpFeedbackParam<Literal> translated_msg) {
        JSONObject input = new JSONObject();
        input.put("msg", msg);
        input.put("hints", new JSONArray(literals));
        String answer = send_llama("nl2kqml", input.toString());
        translated_msg.set(Literal.parseLiteral(answer));
    }

    @OPERATION
    public void kqml2nl(String msg, String performative, Object literals, OpFeedbackParam<String> nl_msg) {
        JSONObject input = new JSONObject();
        input.put("content", msg);
        input.put("performative", performative);
        String answer = send_llama("kqml2nl", input.toString());
        nl_msg.set(answer);
    }

    private String send_llama( String model, String message ) {
        try {
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
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .build();
            
            HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = httpResponse.body();
            JSONObject jsonResponse = new JSONObject(body);
            System.out.println(jsonResponse.toString());
            JSONObject answer = jsonResponse.getJSONObject("message");
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
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .build();
            
            HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = httpResponse.body();
            JSONObject jsonResponse = new JSONObject(body);
            System.out.println(jsonResponse.toString());
            JSONObject answer = jsonResponse.getJSONObject("message");
            String chatResponse = answer.getString("content");
            response.set(chatResponse);

        } catch (Exception e) {
            failed("Error calling LLaMA API: " + e.getMessage());
        }
    }
}

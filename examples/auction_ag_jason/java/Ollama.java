import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.ConnectException;
import java.net.URI;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

import java.nio.file.Files;
import java.nio.file.Path;

import org.json.JSONObject;
import org.json.JSONArray;

public class Ollama {
	private final String URL;
	private final HttpClient client = HttpClient.newHttpClient();

	public Ollama( String url ) {
		this.URL = url;
		if ( !is_online() )
			System.out.println("[ERROR] Ollama is offline");
	}

	private boolean is_online() {
		try {
		HttpRequest req = HttpRequest.newBuilder()
			.uri( URI.create( URL.replaceAll( "/api/", "" ) ) )
			.header( "Content-Type", "application/json" )
			.GET()
			.build();

			HttpResponse<String> res = client.send( req, HttpResponse.BodyHandlers.ofString() );
			return res.statusCode() == 200;
		} catch( ConnectException e ) {
			return false;
		} catch( Exception e ) {
			e.printStackTrace();
		}
		return false;
	}

	public List<Double> embed( String model, String str ) {
		JSONObject json = new JSONObject();
		json.put( "model", model );
		json.put( "input", str );
		json.put( "stream", false );

		HttpRequest req = HttpRequest.newBuilder()
			.uri( URI.create( URL + "embed" ) )
			.header( "Content-Type", "application/json" )
			.POST( HttpRequest.BodyPublishers.ofString( json.toString() ) )
			.build();

		try {
			HttpResponse<String> res = client.send( req, HttpResponse.BodyHandlers.ofString() );
			JSONObject emb_json = new JSONObject( res.body() );
			JSONArray vec = emb_json.getJSONArray( "embeddings" ).getJSONArray(0);
			System.out.println( "Embedding generated: " + vec );
			List<Double> list = new ArrayList<>();
			for( int i = 0; i < vec.length(); i++ ) {
				list.add( vec.getDouble( i ) );
			}
			return list;
		} catch( Exception e ) {
			e.printStackTrace();
		}
		return null;
	}

	public String generate( String model, String str ) {
		JSONObject json = new JSONObject();
		json.put( "model", model );
		json.put( "prompt", str );
		json.put( "stream", false );

		HttpRequest req = HttpRequest.newBuilder()
			.uri( URI.create( URL + "generate" ) )
			.header( "Content-Type", "application/json" )
			.POST( HttpRequest.BodyPublishers.ofString( json.toString() ) )
			.build();

		try {
			HttpResponse<String> res = client.send( req, HttpResponse.BodyHandlers.ofString() );
			return res.body();
		} catch( Exception e ) {
			e.printStackTrace();
		}
		return null;
	}

	public String generate( String model, String str, JSONObject format ) {
		JSONObject json = new JSONObject();
		json.put( "model", model );
		json.put( "prompt", str );
		json.put( "format", format );
		json.put( "stream", false );

		System.out.println( json.toString() );

		HttpRequest req = HttpRequest.newBuilder()
			.uri( URI.create( URL + "generate" ) )
			.header( "Content-Type", "application/json" )
			.POST( HttpRequest.BodyPublishers.ofString( json.toString() ) )
			.build();

		try {
			HttpResponse<String> res = client.send( req, HttpResponse.BodyHandlers.ofString() );
			return res.body();
		} catch( Exception e ) {
			e.printStackTrace();
		}
		return null;
	}

	public void create( String from, String model, float t, String sys_file ) {
		JSONObject params = new JSONObject();
		params.put( "temperature", t );
		JSONObject json = new JSONObject();
		json.put( "from", from );
		json.put( "model", model );
		json.put( "stream", false );
		json.put( "parameters", params );
		try {
			json.put( "system", Files.readString( Path.of(sys_file ) ) );
			HttpRequest request = HttpRequest.newBuilder()
				.uri( URI.create( URL + "create" ) )
				.header( "Content-Type", "application/json" )
				.POST( HttpRequest.BodyPublishers.ofString( json.toString() ) )
				.build();

			HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
		} catch ( Exception e ) {
			e.printStackTrace();
		}
	}

	public void create( String from, String model, float t, String sys_file, int seed ) {
		JSONObject params = new JSONObject();
		params.put( "temperature", t );
		params.put( "seed", seed );
		JSONObject json = new JSONObject();
		json.put( "from", from );
		json.put( "model", model );
		json.put( "stream", false );
		json.put( "parameters", params );
		try {
			json.put( "system", Files.readString( Path.of(sys_file ) ) );
			HttpRequest request = HttpRequest.newBuilder()
				.uri( URI.create( URL + "create" ) )
				.header( "Content-Type", "application/json" )
				.POST( HttpRequest.BodyPublishers.ofString( json.toString() ) )
				.build();

			HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
		} catch ( Exception e ) {
			e.printStackTrace();
		}
	}
}

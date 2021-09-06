import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.client.fluent.Content;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Properties;

public class CheckerCorporate {
    public static boolean isActive(String INN) {
        if (INN == null || INN.length() < 8 || INN.length() > 12) {
            return false;
        }
        try {
            String rootPath = Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResource("")).getPath();
            String appConfigPath = rootPath + "checker.properties";
            Properties appProps = new Properties();
            appProps.load(new FileInputStream(appConfigPath));
            String token = appProps.getProperty("token");

            Content postResultForm = Request.Post("https://suggestions.dadata.ru/suggestions/api/4_1/rs/findById/party")
                    .bodyString("{ \"query\": \"" + INN + "\" }", ContentType.APPLICATION_JSON)
                    .setHeader("Authorization", "Token " + token)
                    .setHeader("Content-Type", "application/json; charset=UTF-8")
                    .execute().returnContent();
            JsonElement jsonElement = JsonParser.parseString(postResultForm.asString());
            JsonObject rootObject = jsonElement.getAsJsonObject(); // чтение главного объекта
            JsonArray suggestions = rootObject.getAsJsonArray("suggestions");
            JsonObject asJsonObject = suggestions.get(0).getAsJsonObject();
            JsonObject data = asJsonObject.get("data").getAsJsonObject();
            JsonObject state = data.get("state").getAsJsonObject();
            if (state.get("status").toString().equals("\"ACTIVE\"")) {
                return true;
            }
        } catch (IOException | IndexOutOfBoundsException ignored) {
            return false;
        }
        return false;
    }
}

package com.example.fn;

import com.fnproject.fn.api.FnConfiguration;
import com.fnproject.fn.api.Headers;
import com.fnproject.fn.api.InputEvent;

import com.fnproject.fn.api.RuntimeContext;
import com.fnproject.fn.api.httpgateway.HTTPGatewayContext;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class HelloFunction {

    String authConfig = "";

    @FnConfiguration
    public void setUp(RuntimeContext ctx) throws Exception {
        authConfig = ctx.getConfigurationByKey("CONFIG").orElse(System.getenv().getOrDefault("CONFIG", ""));
    }

    public String handleRequest(final HTTPGatewayContext hctx, final InputEvent input) {

        String ret = "";
        String body = "";
        String auth_basic = "";

        boolean LOCAL = false;
        boolean FOUND = false;

        System.out.println("==== FUNC ====\n");
        try {
            List<String> lines = Files.readAllLines(Paths.get("/func.yaml")).stream().limit(3).collect(Collectors.toList());
            lines.forEach(System.out::println);
        } catch (Exception e) {
            System.out.println("Error reading func.yaml: " + e.getMessage());
        }
        System.out.println("==============");

        System.out.println("======= CONFIG ======");
        //hctx.getHeaders().getAll().forEach((key, value) -> System.out.println(">>>>>" + key + ": " + value));
        //input.getHeaders().getAll().forEach((key, value) -> System.out.println(">>>>>" + key + ": " + value));
        //System.out.println("AUTH: " + authConfig);
        System.out.println("=====================");

        String url = input.getHeaders().get("Fn-Http-Request-Url").orElse("");
        if(url.contains("localhost"))
        {
            System.out.println("======== LOCAL FUNC ========");
            body = hctx.getHeaders().get("Authorization").orElse("") + "\"}";
            LOCAL = true;
        } else {
            System.out.println("======== OCI FUNC ==========");
            body = input.consumeBody((InputStream is) -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                    return reader.lines().collect(Collectors.joining());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        System.out.println("Body: " + body);

        String[] configTokens = authConfig.split(",");
        List<String> tokenizedConfig = Arrays.stream(configTokens).map(String::trim).collect(Collectors.toList());

        // Tokenize body//[Code-Assist (3)]
        if(body.length() > 0) {
            String[] bodyTokens = body.split(",");
            List<String> tokenizedBody = Arrays.stream(bodyTokens).map(String::trim).collect(Collectors.toList());

            for (String configToken : tokenizedConfig) {
                // Iterate thru tokenizedBody//[Code-Assist (12)]
                for (String token : tokenizedBody) {
                    if (token.indexOf("Basic ") > -1 && configToken.length() > 0) {
                        String auth_token = token.substring(token.indexOf("Basic ") + 6, token.indexOf("\"}"));
                        if (auth_token.equals(configToken)) {
                            System.out.println("AUTH SUCCESS " + auth_token + " == " + configToken);
                            auth_basic = token.substring(token.indexOf("Basic "), token.indexOf("\"}"));
                            FOUND = true;
                        } else {
                            System.out.println("AUTH NO MATCH " + auth_token + " <> " + configToken);
                        }
                    }
                }
            }
        }

        if(LOCAL) {
            if(FOUND)
            {
                ret = "auth success";
            }  else {
                hctx.setResponseHeader("WWW-Authenticate","Basic realm=\"fnsimplejava.com\"");
                hctx.setStatusCode(401);
                ret = "auth failed";
                //hctx.setResponseHeader("Location","http://example.com");
                //hctx.setStatusCode(302);
            }
        } else {
            if(FOUND) {
                ret = "{ " +
                        "\"active\": true," +
                        "\"principal\": \"myprincipal\"," +
                        "\"scope\": [\"fnsimplejava\"]," +
                        "\"callId\": \"" + input.getCallID() + "\"," +
                        "\"expiresAt\": \"2025-12-31T00:00:00+00:00\"," +
                        "\"context\": { \"Authorization\": \"" + auth_basic + "\" }" +
                        " }";
            } else {
                ret = "{ " +
                        "\"active\": false," +
                        "\"wwwAuthenticate\": \"Basic realm=\\\"fnsimplejava.com\\\"\"" +
                        " }";
            }
        }

        System.out.println(ret);
        return ret;
    }

}
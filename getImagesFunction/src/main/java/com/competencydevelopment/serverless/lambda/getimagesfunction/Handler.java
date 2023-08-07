package com.competencydevelopment.serverless.lambda.getimagesfunction;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.entities.Subsegment;
import com.amazonaws.xray.strategy.sampling.AllSamplingStrategy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class Handler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(Handler.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, String> HEADERS = new HashMap<>();

    static {
        HEADERS.put("Content-Type", "application/json");
        HEADERS.put("Access-Control-Allow-Origin", "*");

        AWSXRay.setGlobalRecorder(AWSXRayRecorderBuilder.standard()
                .withSamplingStrategy(new AllSamplingStrategy())
                .build());
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        Subsegment subsegment = AWSXRay.beginSubsegment("Get Images");
        APIGatewayProxyResponseEvent response;
        try {
            LOGGER.info("Environment: {}", GSON.toJson(System.getenv()));
            LOGGER.info("Event: {}", GSON.toJson(event));

            // var client = DynamoDbAsyncClient.builder()
            //         .overrideConfiguration(ClientOverrideConfiguration.builder()
            //                 .addExecutionInterceptor(new TracingInterceptor())
            //                 .build())
            //         .build();

            var tableName = System.getenv("IMAGES_TABLE_NAME");
//      var attributes = new HashMap<String, AttributeValue>();
            var userName = getUserFromEvent(event);
            subsegment.putAnnotation("UserId", userName);

            LOGGER.info("body: {}", GSON.toJson(event.getBody()));
            // var note = gson.fromJson(event.getBody(), Note.class);
//      attributes.put("UserId", AttributeValue.builder().s(userName).build());
//      attributes.put("NoteId", AttributeValue.builder().n(note.getNoteId()).build());
//      attributes.put("Note", AttributeValue.builder().s(note.getNote()).build());

            // var request = PutItemRequest.builder()
            //         .tableName(tableName)
            //        .item(attributes)
            //         .build();

            // client.putItem(request).join();
            response = new APIGatewayProxyResponseEvent().withStatusCode(200)
                    .withHeaders(HEADERS)
                    .withIsBase64Encoded(false)
                    .withBody("Hello from Lambda!");

        } catch (Exception e) {
            subsegment.addException(e);
            throw e;
        } finally {
            AWSXRay.endSubsegment();
        }
        return response;
    }

    private String getUserFromEvent(APIGatewayProxyRequestEvent event) {
        var userName = "unknown";
        var authorizer = event.getRequestContext().getAuthorizer();
        if (authorizer == null) {
            LOGGER.warn("null authorizer - cannot determine username");
            return userName;
        }
        var claims = (Map<String, String>) authorizer.get("claims");
        if (claims == null) {
            LOGGER.warn("no JWT in authorizer - cannot determine username");
            return userName;
        }
        return claims.get("cognito:username");
    }

}
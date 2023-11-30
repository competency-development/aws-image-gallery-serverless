package com.competencydevelopment.serverless.lambda.getimagesfunction;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.entities.Subsegment;
import com.amazonaws.xray.interceptors.TracingInterceptor;
import com.amazonaws.xray.strategy.sampling.AllSamplingStrategy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
            LOGGER.info("body: {}", GSON.toJson(event.getBody()));

            DynamoDbClient client = DynamoDbClient.builder()
                     .overrideConfiguration(ClientOverrideConfiguration.builder()
                             .addExecutionInterceptor(new TracingInterceptor())
                             .build())
                     .build();

            String tableName = System.getenv("TABLE_NAME");
            ScanResponse imagesResponse = client.scan(ScanRequest.builder().tableName(tableName).attributesToGet("url").build());
            List<String> listOfImages = imagesResponse.items().stream().flatMap(m -> m.values().stream().map(AttributeValue::s)).collect(Collectors.toList());

            response = new APIGatewayProxyResponseEvent().withStatusCode(200)
                    .withHeaders(HEADERS)
                    .withIsBase64Encoded(false)
                    .withBody(GSON.toJson(listOfImages));

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
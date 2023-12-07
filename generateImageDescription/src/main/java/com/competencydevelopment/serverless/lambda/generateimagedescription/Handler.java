package com.competencydevelopment.serverless.lambda.generateimagedescription;

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
import org.apache.commons.codec.binary.Base64;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Handler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String TABLE_NAME = System.getenv("TABLE_NAME");
    private static final String S3_BUCKET_NAME = System.getenv("S3_BUCKET_NAME");

    private static final Logger LOGGER = LoggerFactory.getLogger(Handler.class);
    private static final Map<String, String> HEADERS = new HashMap<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static {
        HEADERS.put("Content-Type", "application/json");
        HEADERS.put("Access-Control-Allow-Origin", "*");

        AWSXRay.setGlobalRecorder(AWSXRayRecorderBuilder.standard()
                .withSamplingStrategy(new AllSamplingStrategy())
                .build());
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        Subsegment subsegment = AWSXRay.beginSubsegment("generate image description");
        APIGatewayProxyResponseEvent response;
        try {
            LOGGER.info("Environment: {}", GSON.toJson(System.getenv()));
            LOGGER.info("Event: {}", GSON.toJson(event));
            LOGGER.info("body: {}", GSON.toJson(event.getBody()));

            String imageKey = new String(Base64.decodeBase64(event.getBody()));
            String description = generateDescription(imageKey);

            updateImage(imageKey, description);

            response = new APIGatewayProxyResponseEvent().withStatusCode(200)
                    .withHeaders(HEADERS)
                    .withIsBase64Encoded(false)
                    .withBody(GSON.toJson(description));
        } catch (Exception e) {
            subsegment.addException(e);
            throw e;
        } finally {
            AWSXRay.endSubsegment();
        }
        return response;

    }

    private void updateImage(String imageKey, String description) {
        Subsegment subsegment = AWSXRay.beginSubsegment("Update image description in the DB");
        DynamoDbTable<Image> imageTable = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(DynamoDbClient.builder().build())
                .build()
                .table(TABLE_NAME, TableSchema.fromBean(Image.class));

        Image image = imageTable
                .getItem(Key.builder().partitionValue(imageKey).build());
        image.setDescription(description);

        imageTable.updateItem(UpdateItemEnhancedRequest.<Image>builder(Image.class).item(image).build());

        subsegment.putAnnotation("Generated description", description);

        LOGGER.info("Image's description updated");
        AWSXRay.endSubsegment();
    }

    private String generateDescription(String imageName) {
        Subsegment subsegment = AWSXRay.beginSubsegment("Generate description using Rekognition");
        DetectLabelsRequest request = DetectLabelsRequest.builder()
                .image(software.amazon.awssdk.services.rekognition.model.Image.builder()
                        .s3Object(S3Object.builder()
                                .bucket(S3_BUCKET_NAME)
                                .name(imageName)
                                .build())
                        .build())
                .maxLabels(10)
                .minConfidence(75F)
                .build();

        try (RekognitionClient rekognitionClient = RekognitionClient.builder().build()) {
            DetectLabelsResponse response = rekognitionClient.detectLabels(request);
            List<Label> labels = response.labels();

            LOGGER.info("Detected labels for " + imageName);

            return labels.stream().map(Label::name).collect(Collectors.joining(", "));
        } catch (RekognitionException e) {
            subsegment.addException(e);
            throw e;
        } finally {
            AWSXRay.endSubsegment();
        }
    }

}
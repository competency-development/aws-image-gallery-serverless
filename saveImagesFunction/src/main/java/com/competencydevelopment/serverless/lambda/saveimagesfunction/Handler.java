package com.competencydevelopment.serverless.lambda.saveimagesfunction;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.entities.Subsegment;
import com.amazonaws.xray.strategy.sampling.AllSamplingStrategy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Handler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String TABLE_NAME = System.getenv("IMAGES_TABLE_NAME");
    private static final String S3_BUCKET_NAME = System.getenv("S3_BUCKET_NAME");

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
        Subsegment subsegment = AWSXRay.beginSubsegment("Save Image");
        APIGatewayProxyResponseEvent response;
        try {
            String imageName = UUID.randomUUID().toString();
            String imageUrl = saveBytesToS3(imageName, event.getBody().getBytes());

            LOGGER.info("Image uploaded to S3");

            Image image = new Image(imageName, imageUrl, null);
            saveToDynamoDb(image);

            LOGGER.info("Image uploaded to DynamoDB: {}", imageName);

            subsegment.putAnnotation("Image uploaded: ", GSON.toJson(image));

            response = new APIGatewayProxyResponseEvent().withStatusCode(200)
                    .withHeaders(HEADERS)
                    .withIsBase64Encoded(false)
                    .withBody(GSON.toJson(image));
        } catch (Exception e) {
            subsegment.addException(e);
            throw e;
        } finally {
            AWSXRay.endSubsegment();
        }
        return response;
    }

    private String saveBytesToS3(String key, byte[] fileBytes) {
        try (S3Client s3Client = S3Client.builder().build()) {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder().bucket(S3_BUCKET_NAME).key(key).build();
            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(Base64.decodeBase64(fileBytes)));

            GetUrlRequest getUrlRequest = GetUrlRequest.builder().bucket(S3_BUCKET_NAME).key(key).build();
            return s3Client.utilities().getUrl(getUrlRequest).toString();
        }
    }

    private void saveToDynamoDb(Image image) {
        DynamoDbEnhancedClient.builder()
                .dynamoDbClient(DynamoDbClient.builder().build())
                .build()
                .table(TABLE_NAME, TableSchema.fromBean(Image.class))
                .putItem(image);
    }

    public static void main(String[] args) {
        Segment mySegment = AWSXRay.beginSegment("do-startup-operation");
        DynamoDbEnhancedClient.builder()
                .dynamoDbClient(DynamoDbClient.builder().build())
                .build()
                .table("images", TableSchema.fromBean(Image.class))
                .putItem(new Image("aa1", null, null));
        AWSXRay.endSegment();
    }

}
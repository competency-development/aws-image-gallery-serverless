package com.competencydevelopment.serverless.lambda.synthesizedescription;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.entities.Subsegment;
import com.amazonaws.xray.strategy.sampling.AllSamplingStrategy;
import org.apache.commons.lang3.RandomStringUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.model.OutputFormat;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechRequest;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechResponse;
import software.amazon.awssdk.services.polly.model.VoiceId;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class Handler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String TABLE_NAME = System.getenv("TABLE_NAME");
    private static final String S3_BUCKET_NAME = System.getenv("S3_BUCKET_NAME");

    private static final Logger LOGGER = LoggerFactory.getLogger(Handler.class);
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
        String text = new JSONObject(event.getBody()).getString("text");
        InputStream audioStream = synthesize(text);

        String audioName = RandomStringUtils.randomAlphanumeric(24) + ".mp3";

        String audioUrl = saveAudioToS3(audioName, audioStream);

        return new APIGatewayProxyResponseEvent().withStatusCode(200)
                .withHeaders(HEADERS)
                .withIsBase64Encoded(false)
                .withBody(audioUrl);
    }

    private String saveAudioToS3(String key, InputStream audioStream) {
        Subsegment subsegment = AWSXRay.beginSubsegment("Save audio to S3");
        try (S3Client s3Client = S3Client.builder().build()) {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder().bucket(S3_BUCKET_NAME).key(key).build();
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(audioStream, audioStream.available()));

            GetUrlRequest getUrlRequest = GetUrlRequest.builder().bucket(S3_BUCKET_NAME).key(key).build();
            String imageUrl = s3Client.utilities().getUrl(getUrlRequest).toString();
            subsegment.putAnnotation("Audio URL", imageUrl);

            LOGGER.info("Synthesized audio uploaded to S3");

            return imageUrl;
        } catch (Exception e) {
            subsegment.addException(e);
            try {
                throw e;
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        } finally {
            AWSXRay.endSubsegment();
        }
    }

    private ResponseInputStream<SynthesizeSpeechResponse> synthesize(String text) {
        Subsegment subsegment = AWSXRay.beginSubsegment("Synthesize audio");
        subsegment.putAnnotation("Text", text);

        try (PollyClient polly = PollyClient.builder().build()) {
            SynthesizeSpeechRequest synthReq = SynthesizeSpeechRequest.builder()
                    .text(text)
                    .voiceId(VoiceId.DORA)
                    .outputFormat(OutputFormat.MP3)
                    .build();

            return polly.synthesizeSpeech(synthReq);
        }catch (Exception e) {
            subsegment.addException(e);
            throw e;
        }finally {
            AWSXRay.endSubsegment();
        }
    }

}
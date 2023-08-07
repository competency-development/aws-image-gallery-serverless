package com.competencydevelopment.serverless.lambda.saveimagesfunction;

import org.apache.commons.lang3.builder.ToStringBuilder;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

/**
 * Represents a row for DynamoDB with all mappings. NOTE: setters and default constructor are required!
 */
@DynamoDbBean
public class Image {

    private String key;

    private String url;

    private String description;

    public Image() {
        // default constructor
    }

    public Image(String key, String url, String description) {
        this.key = key;
        this.url = url;
        this.description = description;
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("key")
    public String getKey() {
        return key;
    }

    @DynamoDbAttribute("url")
    public String getUrl() {
        return url;
    }

    @DynamoDbAttribute("description")
    public String getDescription() {
        return description;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("key", key)
                .append("url", url)
                .append("description", description)
                .toString();
    }

}

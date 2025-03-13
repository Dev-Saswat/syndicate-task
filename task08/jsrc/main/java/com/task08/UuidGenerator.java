package com.task08;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.events.RuleEventSource;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(
    lambdaName = "uuid_generator",
	roleName = "uuid_generator-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@RuleEventSource(
	targetRule = "uuid_trigger"
)

@EnvironmentVariables(value = {
	@EnvironmentVariable(key = "region", value = "${region}"),
	@EnvironmentVariable(key = "target_bucket", value = "${target_bucket}")
})
public class UuidGenerator implements RequestHandler<Object, Map<String, Object>> {

	private final AmazonS3 s3Client;
    private final ObjectMapper objectMapper;
	private final String bucketName;

    public UuidGenerator() {
        this.s3Client = AmazonS3Client.builder().withRegion(System.getenv("region")).build();
        this.objectMapper = new ObjectMapper();
		this.bucketName = System.getenv("target_bucket");
    }

	public Map<String, Object> handleRequest(Object request, Context context) {
		context.getLogger().log("Received input: " + request.toString());
		try {
			List<String> uuids = new ArrayList<>();
			for (int i = 0; i < 10; i++) {
				uuids.add(UUID.randomUUID().toString());
			}

			Payload payload = new Payload(uuids);

			String jsonUUIDS = convertObjectToJson(payload);

			String timestamp = DateTimeFormatter.ISO_INSTANT
                    .withZone(ZoneOffset.UTC)
                    .format(Instant.now());
            String filename = timestamp;

            s3Client.putObject(bucketName, (String) filename, jsonUUIDS);

			context.getLogger().log("uuids saved to bucket.");
			// Prepare success response
			Map<String, Object> response = new HashMap<>();
			response.put("statusCode", 201);
			response.put("body", "uuids has been saved");
			return response;
		} catch (Exception e) {
			context.getLogger().log("Error processing request: " + e.getMessage());

			// Prepare error response
			Map<String, Object> errorResponse = new HashMap<>();
			errorResponse.put("statusCode", 500);
			errorResponse.put("error", "Internal Server Error: " + e.getMessage());
			return errorResponse;
		}
		
	}

	private String convertObjectToJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Object cannot be converted to JSON: " + object);
        }
    }

	private static class Payload {
		private List<String> ids;

        public Payload(List<String> ids) {
            this.ids = ids;
        }

        public List<String> getIds() {
            return ids;
        }

        public void setIds(List<String> ids) {
            this.ids = ids;
        }
	}
}

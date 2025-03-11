	package com.task08;

	import software.amazon.awssdk.services.s3.S3Client;
	import software.amazon.awssdk.services.s3.model.PutObjectRequest;
	import software.amazon.awssdk.core.sync.RequestBody;
	import com.amazonaws.services.lambda.runtime.Context;
	import com.amazonaws.services.lambda.runtime.RequestHandler;
	import com.fasterxml.jackson.databind.ObjectMapper;
	import com.syndicate.deployment.annotations.lambda.LambdaHandler;
	import com.syndicate.deployment.annotations.events.RuleEventSource;
	import com.syndicate.deployment.model.RetentionSetting;

	import java.time.Instant;
	import java.util.List;
	import java.util.UUID;
	import java.util.stream.Collectors;

	@LambdaHandler(
			lambdaName = "uuid_generator",
			roleName = "uuid_generator-role",
			isPublishVersion = true,
			aliasName = "${lambdas_alias_name}",
			logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
	)
	@RuleEventSource(targetRule = "uuid_trigger")
	public class UuidGenerator implements RequestHandler<Object, String> {

		private static final String BUCKET_NAME = "uuid-storage";
		private final S3Client s3Client = S3Client.create(); // AWS SDK v2 client

		@Override
		public String handleRequest(Object request, Context context) {
			try {
				context.getLogger().log("Lambda function triggered...");

				// Generate 10 UUIDs
				List<String> uuids = generateUUIDs();

				// Convert UUIDs to JSON
				String jsonContent = new ObjectMapper().writeValueAsString(new UUIDData(uuids));

				// Generate a timestamped filename
				String fileName = "uuids-" + Instant.now().toEpochMilli() + ".json";

				// Store JSON data in S3 using AWS SDK v2
				PutObjectRequest putObjectRequest = PutObjectRequest.builder()
						.bucket(BUCKET_NAME)
						.key(fileName)
						.contentType("application/json")
						.build();

				s3Client.putObject(putObjectRequest, RequestBody.fromString(jsonContent));

				context.getLogger().log("Successfully stored file: " + fileName);

				return "File stored: " + fileName;
			} catch (Exception e) {
				context.getLogger().log("Error: " + e.getMessage());
				return "Failure";
			}
		}

		private List<String> generateUUIDs() {
			return java.util.stream.IntStream.range(0, 10)
					.mapToObj(i -> UUID.randomUUID().toString())
					.collect(Collectors.toList());
		}

		private static class UUIDData {
			public List<String> ids;
			public UUIDData(List<String> ids) {
				this.ids = ids;
			}
		}
	}

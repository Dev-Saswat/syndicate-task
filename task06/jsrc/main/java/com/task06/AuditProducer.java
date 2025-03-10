package com.task06;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.events.DynamoDbTriggerEventSource;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.DeploymentRuntime;
import com.syndicate.deployment.model.RetentionSetting;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(
		lambdaName = "audit_producer",
		roleName = "audit_producer-role",
		runtime = DeploymentRuntime.JAVA11,
		aliasName = "${lambdas_alias_name}",
		logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@DynamoDbTriggerEventSource(targetTable = "Configuration", batchSize = 1)
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "target_table", value = "${target_table}")
})
public class AuditProducer implements RequestHandler<DynamodbEvent, Void> {

	private final DynamoDbClient dynamoDbClient;
	private final String auditTableName;
	private final ObjectMapper objectMapper;

	public AuditProducer() {
		this.dynamoDbClient = DynamoDbClient.create();
		this.auditTableName = System.getenv("target_table");
		this.objectMapper = new ObjectMapper();
	}

	@Override
	public Void handleRequest(DynamodbEvent dynamodbEvent, Context context) {
		for (DynamodbEvent.DynamodbStreamRecord record : dynamodbEvent.getRecords()) {
			if ("INSERT".equals(record.getEventName())) {
				processInsert(record);
			} else if ("MODIFY".equals(record.getEventName())) {
				processModify(record);
			}
		}
		return null;
	}

	private void processInsert(DynamodbEvent.DynamodbStreamRecord record) {
		Map<String, AttributeValue> newItem = convertDynamoDBMap(record.getDynamodb().getNewImage());

		Map<String, AttributeValue> auditEntry = new HashMap<>();
		auditEntry.put("id", AttributeValue.builder().s(UUID.randomUUID().toString()).build());
		auditEntry.put("itemKey", newItem.getOrDefault("key", AttributeValue.builder().s("UNKNOWN_KEY").build()));
		auditEntry.put("modificationTime", AttributeValue.builder().s(Instant.now().toString()).build());

		// Store newValue as an object only for INSERT
		Map<String, AttributeValue> newValueMap = new HashMap<>();
		newValueMap.put("key", newItem.get("key"));
		newValueMap.put("value", newItem.get("value"));
		auditEntry.put("newValue", AttributeValue.builder().m(newValueMap).build());

		saveAuditRecord(auditEntry);
	}

	private void processModify(DynamodbEvent.DynamodbStreamRecord record) {
		Map<String, AttributeValue> newItem = convertDynamoDBMap(record.getDynamodb().getNewImage());
		Map<String, AttributeValue> oldItem = convertDynamoDBMap(record.getDynamodb().getOldImage());

		Map<String, AttributeValue> auditEntry = new HashMap<>();
		auditEntry.put("id", AttributeValue.builder().s(UUID.randomUUID().toString()).build());
		auditEntry.put("itemKey", newItem.getOrDefault("key", AttributeValue.builder().s("UNKNOWN_KEY").build()));
		auditEntry.put("modificationTime", AttributeValue.builder().s(Instant.now().toString()).build());
		auditEntry.put("updatedAttribute", AttributeValue.builder().s("value").build());

		// Store oldValue and newValue as raw numbers for MODIFY events
		auditEntry.put("oldValue", oldItem.get("value"));
		auditEntry.put("newValue", newItem.get("value"));

		saveAuditRecord(auditEntry);
	}

	private void saveAuditRecord(Map<String, AttributeValue> auditEntry) {
		PutItemRequest request = PutItemRequest.builder()
				.tableName(auditTableName)
				.item(auditEntry)
				.build();
		dynamoDbClient.putItem(request);
	}

	private Map<String, AttributeValue> convertDynamoDBMap(Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue> dynamoDBMap) {
		Map<String, AttributeValue> resultMap = new HashMap<>();
		for (Map.Entry<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue> entry : dynamoDBMap.entrySet()) {
			com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue oldValue = entry.getValue();

			AttributeValue.Builder newValue = AttributeValue.builder();

			if (oldValue.getS() != null) {
				newValue.s(oldValue.getS());
			} else if (oldValue.getN() != null) {
				newValue.n(oldValue.getN());
			} else if (oldValue.getBOOL() != null) {
				newValue.bool(oldValue.getBOOL());
			} else if (oldValue.getL() != null) {
				newValue.l(oldValue.getL().stream()
						.map(v -> AttributeValue.builder().s(v.getS()).build())
						.toList());
			} else if (oldValue.getM() != null) {
				newValue.m(convertDynamoDBMap(oldValue.getM()));
			}

			resultMap.put(entry.getKey(), newValue.build());
		}
		return resultMap;
	}
}

package com.task02;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.lambda.url.AuthType;

import java.util.HashMap;
import java.util.Map;


@LambdaHandler(
		lambdaName = "hello_world",
		roleName = "hello_world-role",
		isPublishVersion = true,
		aliasName = "${lambdas_alias_name}",
		logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)

@LambdaUrlConfig(authType = AuthType.NONE)
public class HelloWorld implements RequestHandler<Map<String , Object>, Map<String, Object>> {

	public Map<String, Object> handleRequest(Map<String , Object> event, Context context) {
		System.out.println("Hello from lambda");
		String path = (String) event.getOrDefault("rawPath", event.get("path"));
		Map<String, Object> requestContext = (Map<String, Object>) event.get("requestContext");
		Map<String, Object> http = requestContext != null ? (Map<String, Object>) requestContext.get("http") : null;
		String method = http != null ? (String) http.get("method") : (String) event.get("httpMethod");

		if ("/hello".equals(path) && "GET".equalsIgnoreCase(method)) {
			return createSuccessResponse();
		} else {
			return createErrorResponse("Bad request syntax or unsupported method. Request path: " + path + ". HTTP method: " + method);
		}
	}
	private Map<String, Object> createSuccessResponse() {
		Map<String, Object> response = new HashMap<>();
		response.put("statusCode", 200);
		response.put("headers", Map.of("Content-Type", "application/json"));
		response.put("body", "{\"statusCode\": 200, \"message\": \"Hello from Lambda\"}");
		return response;
	}

	private Map<String, Object> createErrorResponse(String message) {
		Map<String, Object> response = new HashMap<>();
		response.put("statusCode", 400);
		response.put("headers", Map.of("Content-Type", "application/json"));
		response.put("body", String.format("{\"statusCode\": %d, \"message\": \"%s\"}", 400, message));
		return response;
	}
}

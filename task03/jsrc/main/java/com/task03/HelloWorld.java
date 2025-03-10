package com.task03;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.lambda.url.AuthType;
import java.util.Map;
@LambdaHandler(
		lambdaName = "hello_world",
		roleName = "hello_world-role",
		isPublishVersion = true,
		aliasName = "${lambdas_alias_name}",
		logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@LambdaUrlConfig(authType = AuthType.NONE)
public class HelloWorld implements RequestHandler<Map<String, Object>, Map<String, Object>> {
	@Override
	public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
		// Ensure path & method are correctly retrieved
		String path = (String) event.getOrDefault("rawPath", event.get("path"));
		// If the request is a GET to /hello, return success response
		return Map.of(
				"statusCode", 200,
				"message", "Hello from Lambda"
		);
	}
}

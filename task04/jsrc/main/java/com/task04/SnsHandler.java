package com.task04;

import com.amazonaws.services.lambda.runtime.Context;

import com.amazonaws.services.lambda.runtime.RequestHandler;

import com.syndicate.deployment.annotations.events.SnsEventSource;

import com.syndicate.deployment.annotations.lambda.LambdaHandler;

import com.syndicate.deployment.model.RetentionSetting;

import java.util.HashMap;

import java.util.Map;

@LambdaHandler(

		lambdaName = "sns_handler",

		roleName = "sns_handler-role",

		isPublishVersion = true,

		aliasName = "learn",

		logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED

)

@SnsEventSource(

		targetTopic = "lambda_topic"

)

public class SnsHandler implements RequestHandler<Object, Map<String, Object>> {

	public Map<String, Object> handleRequest(Object request, Context context) {

		context.getLogger().log("Received event: " + request.toString() + "\n");

		Map<String, Object> resultMap = new HashMap<>();

		resultMap.put("statusCode", 200);

		resultMap.put("body", "Processed SNS Messages Successfully");

		return resultMap;

	}

}

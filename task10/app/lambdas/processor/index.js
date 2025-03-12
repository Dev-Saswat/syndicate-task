const AWS = require("aws-sdk");
const axios = require("axios");
const { v4: uuidv4 } = require("uuid");

// Enable AWS X-Ray tracing
const AWSXRay = require("aws-xray-sdk");
AWSXRay.captureAWS(AWS);

// Load environment variables
const dynamoDB = new AWS.DynamoDB.DocumentClient();
const TABLE_NAME = process.env.WEATHER_TABLE || "Weather";

// Open-Meteo API URL
const WEATHER_API_URL =
  "https://api.open-meteo.com/v1/forecast?latitude=50.4375&longitude=30.5&hourly=temperature_2m&timezone=auto";

exports.handler = async (event) => {
  try {
    console.log("Fetching weather data...");

    // Fetch weather forecast
    const response = await axios.get(WEATHER_API_URL);
    const weatherData = response.data;

    // Create a new weather record
    const weatherRecord = {
      id: uuidv4(),
      forecast: {
        elevation: weatherData.elevation,
        generationtime_ms: weatherData.generationtime_ms,
        hourly: weatherData.hourly,
        hourly_units: weatherData.hourly_units,
        latitude: weatherData.latitude,
        longitude: weatherData.longitude,
        timezone: weatherData.timezone,
        timezone_abbreviation: weatherData.timezone_abbreviation,
        utc_offset_seconds: weatherData.utc_offset_seconds,
      },
    };

    console.log("Saving to DynamoDB:", weatherRecord);

    // Save to DynamoDB
    await dynamoDB
      .put({
        TableName: TABLE_NAME,
        Item: weatherRecord,
      })
      .promise();

    console.log("Weather data saved successfully!");

    return {
      statusCode: 200,
      body: JSON.stringify({ message: "Weather data stored", data: weatherRecord }),
    };
  } catch (error) {
    console.error("Error fetching/storing weather data:", error);

    return {
      statusCode: 500,
      body: JSON.stringify({ error: "Failed to fetch/store weather data" }),
    };
  }
};

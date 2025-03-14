import AWS from 'aws-sdk';
import { v4 as uuidv4 } from 'uuid';

// Initialize AWS services
const dynamodb = new AWS.DynamoDB.DocumentClient();
const cognito = new AWS.CognitoIdentityServiceProvider();

// Get configuration from environment variables
const USER_POOL_ID = process.env.cup_id;
const CLIENT_ID = process.env.cup_client_id;
const TABLES_TABLE = process.env.tables_table;
const RESERVATIONS_TABLE = process.env.reservations_table;

// Main handler function
export const handler = async (event, context) => {
  console.log("Event:", JSON.stringify({
      path: event.path,
      httpMethod: event.httpMethod,
      headers: event.headers?.Authorization,
      body: event.body
  }));
  try {
    const { resource: path, httpMethod } = event;
    const routes = {
      "POST /signup": handleSignup,
      "POST /signin": handleSignin,
      "GET /tables": handleGetTables,
      "POST /tables": handleCreateTable,
      "GET /tables/{tableId}": handleGetTableById,
      "GET /reservations": handleGetReservations,
      "POST /reservations": handleCreateReservation,
    };
    const routeKey = `${httpMethod} ${path}`;
    const response = routes[routeKey]
      ? await routes[routeKey](event)
      : {
          statusCode: 404,
          headers: corsHeaders(),
          body: JSON.stringify({ message: "Not Found" }),
        };
    return response;
  } catch (error) {
    console.error("Error:", error);
    return {
      statusCode: 500,
      headers: corsHeaders(),
      body: JSON.stringify({
        message: "Internal Server Error",
        error: error.message,
      }),
    };
  }
};

// Helper functions for CORS headers
function corsHeaders() {
  return {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type,Authorization',
    'Access-Control-Allow-Methods': 'OPTIONS,POST,GET',
    'Content-Type': 'application/json'
  };
}

// Helper function for formatting responses
function formatResponse(statusCode, body) {
  return {
    statusCode: statusCode,
    headers: corsHeaders(),
    body: JSON.stringify(body)
  };
}

// SignUp handler
async function handleSignup(event) {
  try {
    const { firstName, lastName, email, password } = JSON.parse(event.body);
    if (!firstName || !lastName || !email || !password) {
      return formatResponse(400, { error: "All fields are required." });
    }
    if (!/^[\w.%+-]+@[\w.-]+\.[a-zA-Z]{2,}$/.test(email)) {
      return formatResponse(400, { error: "Invalid email format." });
    }
    if (!/^(?=.*[A-Za-z])(?=.*\d)(?=.*[$%^*-_])[A-Za-z\d$%^*-_]{12,}$/.test(password)) {
      return formatResponse(400, { error: "Invalid password format." });
    }
    await cognito.adminCreateUser({
      UserPoolId: USER_POOL_ID,
      Username: email,
      UserAttributes: [
        { Name: "given_name", Value: firstName },
        { Name: "family_name", Value: lastName },
        { Name: "email", Value: email },
        { Name: "email_verified", Value: "true" }
      ],
      TemporaryPassword: password,
      MessageAction: "SUPPRESS",
    }).promise();
    await cognito.adminSetUserPassword({
      UserPoolId: USER_POOL_ID,
      Username: email,
      Password: password,
      Permanent: true
    }).promise();
    return formatResponse(200, { message: "User created successfully." });
  } catch (error) {
    console.error("Signup error:", error);
    if (error.code === "UsernameExistsException") {
      return formatResponse(400, { error: "Email already exists." });
    }
    return formatResponse(502, { error: "Signup failed." });
  }
}

async function handleSignin(event) {
  try {
    const { email, password } = JSON.parse(event.body);
    console.log("Received signin request for:", email);
    const params = {
      AuthFlow: "ADMIN_USER_PASSWORD_AUTH",
      UserPoolId: USER_POOL_ID,
      ClientId: CLIENT_ID,
      AuthParameters: {
        USERNAME: email,
        PASSWORD: password
      }
    };
    const authResponse = await cognito.adminInitiateAuth(params).promise();
    console.log("Auth Response:", JSON.stringify(authResponse));
    if (!authResponse.AuthenticationResult) {
      console.error("AuthenticationResult is missing in response.");
      return formatResponse(400, { error: "Authentication failed. Try again." });
    }
    return formatResponse(200, {
      idToken: authResponse.AuthenticationResult.IdToken // ✅ Corrected key name
    });
  } catch (error) {
    console.error("Sign-in error:", error);
    if (error.code === "NotAuthorizedException") {
      return formatResponse(400, { error: "Invalid email or password." });
    }
    return formatResponse(400, { error: "Authentication failed." });
  }
}

// Table View
async function handleGetTables(event) {
  const username = getUsernameFromToken(event);
  if (!username) {
    return formatResponse(401, { message: "Unauthorized" });
  }
  const params = {
    TableName: TABLES_TABLE,
  };
  try {
    const result = await dynamodb.scan(params).promise();
    const tables = result.Items.map((table) => ({
      id: Number(table.id),
      number: table.number,
      places: table.places,
      isVip: table.isVip,
      minOrder: table.minOrder || 0,
    }));
    return formatResponse(200, { tables });
  } catch (error) {
    console.error("Error fetching tables:", error);
    return formatResponse(500, { message: "Internal Server Error" });
  }
}

//Create Tables
async function handleCreateTable(event) {
  const username = getUsernameFromToken(event);
  if (!username) {
    return formatResponse(401, { message: 'Unauthorized' });
  }
  const table = JSON.parse(event.body);
  if (typeof table.number !== "number" ||
      typeof table.places !== "number" ||
      typeof table.isVip !== "boolean") {
    return formatResponse(400, {
      message: 'Table number, capacity, and location are required'
    });
  }
  let tableId = table.id || uuidv4();
  const tableData = {
        id: String(tableId),
        number: table.number,
        places: table.places,
        isVip: table.isVip,
        minOrder: table.minOrder ?? 0,
      };
  const params = {
    TableName: TABLES_TABLE,
    Item: tableData
  };
  await dynamodb.put(params).promise();
  return formatResponse(200, { id: tableId });
}

//Get Table detailed by Id
async function handleGetTableById(event) {
  const username = getUsernameFromToken(event);
  if (!username) {
    return formatResponse(401, { message: "Unauthorized" });
  }
  const tableId = event.pathParameters.tableId;
  const params = {
    TableName: TABLES_TABLE,
    Key: { id: tableId },
  };
  try {
    const result = await dynamodb.get(params).promise();
    if (!result.Item) {
      return formatResponse(404, { message: "Table not found" });
    }
    const table = {
      id: Number(result.Item.id),
      number: result.Item.number,
      places: result.Item.places,
      isVip: result.Item.isVip,
      minOrder: result.Item.minOrder || 0,
    };
    return formatResponse(200, table);
  } catch (error) {
    console.error("Error fetching table by ID:", error);
    return formatResponse(500, { message: "Internal Server Error" });
  }
}

// View Reservation
async function handleGetReservations(event) {
  const username = getUsernameFromToken(event);
  if (!username) {
    return formatResponse(401, { message: 'Unauthorized' });
  }
  const queryParams = event.queryStringParameters || {};
  let params = {
    TableName: RESERVATIONS_TABLE
  };
  if (queryParams.user) {
    params.FilterExpression = "username = :username";
    params.ExpressionAttributeValues = {
      ":username": queryParams.user
    };
  }
  const result = await dynamodb.scan(params).promise();
  const transformedReservations = result.Items.map(item => ({
    tableNumber: item.tableNumber,
    clientName: item.clientName,
    phoneNumber: item.phoneNumber,
    date: item.date,
    slotTimeStart: item.time,
    slotTimeEnd: item.slotTimeEnd
  }));
  return formatResponse(200, {
    reservations: transformedReservations
  });
}

//Create Reservation
async function handleCreateReservation(event) {
  try {
    const username = getUsernameFromToken(event);
    if (!username) {
      return formatResponse(401, { message: 'Unauthorized' });
    }
    const body = JSON.parse(event.body);
    console.log(body);
    const { tableNumber, clientName, phoneNumber, date, slotTimeStart, slotTimeEnd } = body;
    if (!tableNumber || !date || !slotTimeStart || !slotTimeEnd) {
      return formatResponse(400, {
        message: 'Table number, date, slotTimeStart, and slotTimeEnd are required'
      });
    }
    const tableParams = {
      TableName: TABLES_TABLE,
      FilterExpression: "#num = :tableNumber",
      ExpressionAttributeNames: {
        "#num": "number"
      },
      ExpressionAttributeValues: {
        ":tableNumber": tableNumber
      }
    };
    const tableResult = await dynamodb.scan(tableParams).promise();
    if (tableResult.Items.length === 0) {
      return formatResponse(400, { message: 'Table not found' });
    }
    const table = tableResult.Items[0];
    const tableId = table.id;
    const reservationCheckParams = {
      TableName: RESERVATIONS_TABLE,
      FilterExpression: "tableId = :tableId AND #date = :date AND (#time BETWEEN :start AND :end OR :start BETWEEN #time AND slotTimeEnd)",
      ExpressionAttributeNames: {
        "#date": "date",
        "#time": "time"
      },
      ExpressionAttributeValues: {
        ":tableId": tableId,
        ":date": date,
        ":start": slotTimeStart,
        ":end": slotTimeEnd
      }
    };
    const existingReservations = await dynamodb.scan(reservationCheckParams).promise();
    if (existingReservations.Items.length > 0) {
      return formatResponse(400, {
        message: 'Table is already reserved for the selected date and time'
      });
    }
    const reservation = {
      id: uuidv4(),
      tableId: tableId,
      tableNumber: table.number,
      clientName: clientName ,
      phoneNumber: phoneNumber ,
      username: username,
      date: date,
      time: slotTimeStart,
      slotTimeEnd: slotTimeEnd,
      createdAt: new Date().toISOString()
    };
    const reservationParams = {
      TableName: RESERVATIONS_TABLE,
      Item: reservation
    };
    await dynamodb.put(reservationParams).promise();
    return formatResponse(200, {
      reservationId: reservation.id,
      message: 'Reservation created successfully'
    });
  } catch (error) {
    return formatResponse(500, { message: "Internal Server Error" });
  }
}

// Helper function to extract username from token
function getUsernameFromToken(event) {
  try {
    if (event.requestContext && event.requestContext.authorizer &&
        event.requestContext.authorizer.claims) {
      const username = event.requestContext.authorizer.claims['cognito:username'];
      return username;
    }
    if (event.headers && event.headers.Authorization) {
      console.log('Auth header present, but not processed through requestContext.authorizer');
    }
    return null;
  } catch (error) {
    console.error('Error extracting username from token:', error);
    return null;
  }
}
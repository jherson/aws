AWS Lambda Function to retrieve log events from AWS CloudWatch and send to Loggly using the REST endpoint
- LOGGLY_API_KEY included in source for demo purposeses but shoud be handled in a secure way
- Lambda function is setup to execute every 5 minutes 

Steps:
 1. Describe all Log Groups in CloudWatch
 2. Iterate over log groups and describe Log Streams
 3. Iterate over log streams getting Log Events
 4. Build LogEntry string
 5. Execute HTTP GET to Loggly REST API
 6. (Optional) If the GET to Loggly is successful (200) delete LogStream

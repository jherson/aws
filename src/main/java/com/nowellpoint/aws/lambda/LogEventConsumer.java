package com.nowellpoint.aws.lambda;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.List;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.logs.model.DeleteLogStreamRequest;
import com.amazonaws.services.logs.model.DescribeLogGroupsResult;
import com.amazonaws.services.logs.model.DescribeLogStreamsRequest;
import com.amazonaws.services.logs.model.DescribeLogStreamsResult;
import com.amazonaws.services.logs.model.GetLogEventsRequest;
import com.amazonaws.services.logs.model.GetLogEventsResult;
import com.amazonaws.services.logs.model.OutputLogEvent;

/**
 * AWS Lambda Function to retrieve log events from AWS CloudWatch and send to Loggly using the REST endpoint
 * - LOGGLY_API_KEY included in source for demo purposeses but shoud be handled in a secure way
 * - Lambda function is setup to execute every 5 minutes 
 * - Steps
 * - 1. Describe all Log Groups in CloudWatch
 * - 2. Iterate over log groups and describe Log Streams
 * - 3. Iterate over log streams getting Log Events
 * - 4. Build LogEntry string
 * - 5. Execute HTTP GET to Loggly REST API
 * - 6. (Optional) If the GET to Loggly is successful (200) delete LogStream
 * */

public class LogEventConsumer {
	
	private static AWSLogs logsClient = new AWSLogsClient();
	private static final String LOGGLY_API_KEY = "xxxxxxxxxxxxxx";

	public void invokeService(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
		
		/**
		 * 
		 * Get log groups 
		 * Get log streams for each log group
		 * Get log events for each log stream
		 * Build log entry
		 * 
		 */
				
		DescribeLogGroupsResult describeLogGroupsResult = logsClient.describeLogGroups();
		describeLogGroupsResult.getLogGroups().forEach(logGroup -> {
			
			DescribeLogStreamsRequest describeLogStreamsRequest = new DescribeLogStreamsRequest().withLogGroupName(logGroup.getLogGroupName());
			DescribeLogStreamsResult describeLogStreamsResult = logsClient.describeLogStreams(describeLogStreamsRequest);
			describeLogStreamsResult.getLogStreams().forEach(stream -> {
				
				StringBuilder logEntry = new StringBuilder();
				
				GetLogEventsRequest getLogEventsRequest = new GetLogEventsRequest().withStartFromHead(Boolean.TRUE)
						.withLogGroupName(describeLogStreamsRequest.getLogGroupName())
						.withLogStreamName(stream.getLogStreamName());
				
				while (true) {
					
					GetLogEventsResult getLogEventsResult = logsClient.getLogEvents(getLogEventsRequest);
					List<OutputLogEvent> events = getLogEventsResult.getEvents();
					
					if (events.size() == 0) {
						break;
					}
					
					events.forEach(event -> {
						logEntry.append(describeLogStreamsRequest.getLogGroupName())
								.append(" ")
								.append(stream.getLogStreamName())
								.append(" - ")
								.append(new Date(event.getTimestamp()))
								.append(": ")
								.append(event.getMessage())
								.append(": ")
								.append(new Date(event.getIngestionTime()))
								.append(System.getProperty("line.separator"));
					});
					
					getLogEventsRequest = new GetLogEventsRequest().withNextToken(getLogEventsResult.getNextForwardToken())
							.withLogGroupName(describeLogStreamsRequest.getLogGroupName())
							.withLogStreamName(stream.getLogStreamName());
						
					getLogEventsResult = logsClient.getLogEvents(getLogEventsRequest);
				}
				
				/**
				 * 
				 * execute the GET to loggly
				 * 
				 */
				
				try {
					HttpURLConnection connection = (HttpURLConnection) new URL("http://logs-01.loggly.com/inputs/"
							.concat(configuration.getLogglyApiKey())
							.concat("/tag/")
							.concat(describeLogStreamsRequest.getLogGroupName())
							.concat("/")).openConnection();
							
					connection.setRequestMethod("GET");
					connection.setRequestProperty("content-type", "text/plain");
					connection.setDoOutput(true);
					
					byte[] outputInBytes = logEntry.toString().getBytes("UTF-8");
					OutputStream os = connection.getOutputStream();
					os.write( outputInBytes );    
					os.close();
					
					connection.connect();
					
					if (connection.getResponseCode() == 200) {
						DeleteLogStreamRequest deleteLogEventsRequest = new DeleteLogStreamRequest().withLogGroupName(describeLogStreamsRequest.getLogGroupName()).withLogStreamName(stream.getLogStreamName());
						logsClient.deleteLogStream(deleteLogEventsRequest);
					}
					
				} catch (Exception e) {
					logger.log(e.getMessage());
				}
			});
		});
	}
}

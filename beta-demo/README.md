## Retry Demo 🔁

This is a demo of the retry mechanism available from the Kresil as a plugin for Ktor Client.

### Running the demo ▶️

To run the demo, follow these steps:

1. Start the server:
```shell
cd ..
./gradlew :beta-demo:retry:jvm-server:run
```
2. Start the client:
```shell
cd ..
./gradlew :beta-demo:retry:js-client:browserDevelopmentRun
```
3. A browser window will open with the demo. 

> [!IMPORTANT]
> Make sure the client is only started after the server is running,
> as it will steal the localhost port `8080` if started first. This is because the client also runs a server to serve the frontend.


### Description 📝

The demo consists of two windows one for each client, with and without the retry mechanism.
At the center of the screen,
there is a button that sends a request to the server to alter the error rate of the server.
The error rate dictates how many requests will fail (e.g., 0.5 means there is a 50% chance of failure).
Both clients display the number of requests sent,
the number of errors received, and the error rate of the server in their perspective.
The objective is to show how the retry mechanism can help to recover from these failures,
as the client with the retry mechanism should have a better success rate than the client without it.

> [!NOTE]
> The demo is a simplified version of a real-world scenario,
> where the server would be a service that could fail due to network issues or other transient errors.
> 

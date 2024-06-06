## Retry Demo 🔁

This is a demo of the Retry mechanism functionality 
available from the Kresil library as a plugin for Ktor Client.

### Running the demo ▶️

To run the demo, follow these steps:

1. Go to the root directory of the project:

```shell
cd ..
```

2. Start the server:

```shell
./gradlew :beta-demo:retry:jvm-server:run
```

3. Start the client:

```shell
./gradlew :beta-demo:retry:js-client:browserDevelopmentRun
```

4. A browser window will open with the demo.

> [!IMPORTANT]
> Make sure the client is only started after the server is running,
> as it will steal the localhost port `8080` if started first. This is because the client also runs a server to serve
> the frontend.

### Description 📝

The demo consists of two windows one for each client, with and without the Retry mechanism.
At the center of the screen,
there is a button that sends a request to the server to alter the error rate of the server.
The error rate dictates how many requests will fail (e.g., 0.5 means there is a 50% chance of failure).
Both clients display the number of requests sent,
the number of errors received, and the error rate of the server in their perspective.
The objective is to show how the Retry mechanism can help to recover from these failures,
as the client with the Retry mechanism should have a better success rate than the client without it.

> [!NOTE]
> The demo is a simplified version of a real-world scenario,
> where the server would be a service that could fail due to network issues or other transient errors.

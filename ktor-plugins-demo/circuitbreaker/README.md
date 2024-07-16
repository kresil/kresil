## Circuit Breaker Demo ⛔

This is a demo of the Circuit Breaker mechanism functionality 
available from the Kresil library as a plugin for Ktor Client.

### Running the demo ▶️

To run the demo, follow these steps:

1. Go to the root directory of the project:

```shell
cd ..
```

2. Start the server:

```shell
./gradlew :ktor-plugins-demo:circuitbreaker:jvm-server:run
```

3. Start the client:

```shell
./gradlew :ktor-plugins-demo:circuitbreaker:js-client:browserDevelopmentRun
```

4. A browser window will open with the demo.

> [!IMPORTANT]
> Ensure that the client is started only after the server is running, as it will occupy the localhost port 8080 if started first. This happens because the client also runs a server to serve the frontend.

### Description 📝

The demo consists of two windows, one for each client, with and without the Circuit Breaker mechanism.
Both clients make requests to an unreliable server.
Each cycle of requests has 6 requests, each with a 1s delay to simulate server response time:
- The first 2 are successful.
- The next 2 are unsuccessful because the server is down.
- The last 2 are also unsuccessful because the server is overloaded and takes 4s to respond.

The client with the Circuit Breaker enabled will open the circuit after the 2nd unsuccessful request
and will not send any more requests until the circuit is closed (i.e., the test requests are successful).
This allows the client to fail fast and not try to send requests to a server that is possibly down or overloaded.

The client without the Circuit Breaker will keep sending requests even if the server is down or overloaded,
which can lead to a slow response time and possibly more server issues.

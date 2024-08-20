## Rate Limiter Plugin Demo ⛔

This is a demo of the Rate Limiter mechanism functionality
available from the Kresil library as a plugin for Ktor Server.

### Running the demo ▶️

To run the demo, follow these steps:

1. Go to the root directory of the project:

```shell
cd ..
```

2. Start the server:

```shell
./gradlew :ktor-plugins-demo:ratelimiter:jvm-server:run
```

3. Start the client:

```shell
./gradlew :ktor-plugins-demo:ratelimiter:js-client:browserDevelopmentRun
```

4. A browser window will open with the demo.

> [!IMPORTANT]
> Ensure that the client is started only after the server is running, as it will occupy the localhost port 8080 if
> started first. This happens because the client also runs a server to serve the frontend.

### Description 📝

![Ktor Rate Limiter Plugin Demo](../../docs/images/ktor-plugin-demos/ktor-ratelimiter-plugin-demo.png)

### Video 🎥

- TODO(use embedded video)

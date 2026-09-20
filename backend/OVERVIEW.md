# Backend Overview

Current state: scaffold only. Three Java sources, one properties file.

- Spring Boot 4.1.1, Java 21 (`java.version` in `pom.xml`)
- Dependencies: `spring-boot-starter-webmvc`, devtools (runtime), `spring-boot-starter-webmvc-test`. No persistence, no security yet.
- Runs on port 8080; the Vite dev server proxies `/api/*` to it.

## Sources

### `src/main/java/com/hanamizuki/backend/BackendApplication.java`

```java
package com.hanamizuki.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
```

### `src/main/java/com/hanamizuki/backend/api/HelloController.java`

The only endpoint: `GET /api/hello`.

```java
package com.hanamizuki.backend.api;

import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Smoke-test endpoint. The frontend (Vite) proxies /api/* to port 8080.
 * Add one controller per feature under this package.
 */
@RestController
@RequestMapping("/api")
public class HelloController {

    @GetMapping("/hello")
    public Map<String, String> hello() {
        return Map.of(
                "message", "Hello from Spring Boot",
                "serverTime", OffsetDateTime.now().toString());
    }
}
```

### `src/test/java/com/hanamizuki/backend/BackendApplicationTests.java`

Context-load smoke test only.

```java
package com.hanamizuki.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
```

### `src/main/resources/application.properties`

```properties
spring.application.name=backend
server.port=8080
```

## Notes

- Returning `Map.of(...)` directly works because `@RestController` serializes it with Jackson. Introduce DTO records once the response shape is worth pinning down with types.
- `@SpringBootTest` boots the whole DI container. For a single controller, `@WebMvcTest(HelloController.class)` is faster — the gap widens as dependencies grow.
- `spring-boot-starter-webmvc` is the Boot 4 rename of `spring-boot-starter-web`; the test starter split out as `spring-boot-starter-webmvc-test`.
- devtools is on the runtime classpath, so recompiling a class during `mvnw spring-boot:run` triggers an automatic restart.

## Running

```
./mvnw spring-boot:run
```

Verified on 2026-09-20: starts in ~1s on Tomcat 11.0.24, and `GET /api/hello` returns 200.

## Open question

DESIGN.md specifies nine screens, but there is no persistence layer at all. That is the next decision: in-memory maps, H2 + JPA, or a file-backed database.

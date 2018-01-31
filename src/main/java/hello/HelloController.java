package hello;

import io.micrometer.core.annotation.Timed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@Timed
public class HelloController {

    @Autowired
    private io.opentracing.Tracer tracer;

    @Autowired
    Environment environment;

    @Timed
    @RequestMapping("/")
    public String index() {
        RestTemplate template = new RestTemplate();
        String port = environment.getProperty("local.server.port");
        String name = template.getForObject(String.format("http://localhost:%s/user", port), String.class);
        return String.format("<h2>Hello, %s!</h2>", name);
    }

    @Timed
    @RequestMapping("/user")
    public String getUser() {
        return "John Doe";
    }
}

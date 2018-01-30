package hello;

import io.micrometer.core.annotation.Timed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@Timed
public class HelloController {

    @Autowired
    private io.opentracing.Tracer tracer;

    @Timed
    @RequestMapping("/")
    public String index() {
        RestTemplate template = new RestTemplate();
        String name = template.getForObject("/user", String.class);
        return String.format("Hello, %s!", name);
    }

    @Timed
    @RequestMapping("/user")
    public String getUser() {
        return "John Doe!";
    }
}

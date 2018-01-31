package hello;

import io.micrometer.core.annotation.Timed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.text.MessageFormat;

@RestController
@Timed
public class HelloController {

    @Autowired
    private io.opentracing.Tracer tracer;

    @Autowired
    Environment environment;

    @Value("${templates.index}")
    private String indexTemplate;

    @Autowired
    private RestTemplate restTemplate;

    @Timed
    @RequestMapping("/")
    public String index() {
        String port = environment.getProperty("local.server.port");
        String name = restTemplate.getForObject(String.format("http://localhost:%s/user", port), String.class);
        return MessageFormat.format(indexTemplate, name);
    }

    @Timed
    @RequestMapping("/user")
    public String getUser() {
        return "John Doe";
    }
}

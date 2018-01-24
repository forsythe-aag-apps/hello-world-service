package hello;

import io.micrometer.core.annotation.Timed;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@Timed
public class HelloController {

    @Timed
    @RequestMapping("/")
    public String index() {
        return "Greetings from Spring Boot!";
    }

    
}

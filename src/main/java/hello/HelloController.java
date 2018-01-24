package hello;

import io.micrometer.core.annotation.Timed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Timed
public class HelloController {

    @Autowired
    private io.opentracing.Tracer tracer;

    @Timed
    @RequestMapping("/")
    public String index() {
        tracer.activeSpan().setBaggageItem("greetings", "hello");
        return "Greetings from Spring Boot!";
    }


}

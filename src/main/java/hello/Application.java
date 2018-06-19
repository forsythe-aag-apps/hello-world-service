package hello;

import com.uber.jaeger.Configuration;
import com.uber.jaeger.samplers.ConstSampler;
import io.micrometer.core.instrument.binder.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.JvmGcMetrics;
import io.micrometer.core.instrument.binder.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.ProcessorMetrics;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder) {
        return restTemplateBuilder.build();
    }

    @Bean
    public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
        return args -> {

            System.out.println("Let's inspect the beans provided by Spring Boot:");

            String[] beanNames = ctx.getBeanDefinitionNames();
            Arrays.sort(beanNames);
            for (String beanName : beanNames) {
                System.out.println(beanName);
            }

        };
    }

    @Bean
    public MeterBinder processMemoryMetrics() {
        return new ClassLoaderMetrics();
    }

    @Bean
    public MeterBinder processThreadMetrics() {
        return new ProcessorMetrics();
    }

    @Bean
    public MeterBinder jvmGcMetrics() {
        return new JvmGcMetrics();
    }

    @Bean
    public MeterBinder jvmThreadMetrics() {
        return new JvmThreadMetrics();
    }

    @Bean
    public io.opentracing.Tracer jaegerTracer() {
        return new Configuration("hello-world-service",
                new Configuration.SamplerConfiguration(ConstSampler.TYPE, 1),
                new Configuration.ReporterConfiguration(true,
                        "jaeger-agent.cicd-tools", 5775, 1000, 1000))
                .getTracer();
    }
}

package com.example.adoptions;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.beans.factory.BeanRegistry;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.annotation.Id;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.registry.ImportHttpServices;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Import(MyBeanRegistrar.class)
@EnableResilientMethods
@ImportHttpServices(CatFactsClient.class)
@SpringBootApplication
public class AdoptionsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdoptionsApplication.class, args);
    }

}

class MyBeanRegistrar implements BeanRegistrar {

    @Override
    public void register(@NonNull BeanRegistry registry,
                         @NonNull Environment env) {

        for (var i = 0; i < 5; i++) {
            var indx = i;
            registry.registerBean(MyRunner.class, spec -> spec
                    .supplier(_ -> new MyRunner(indx)));
        }


    }
}

class MyRunner implements ApplicationRunner {

    private final int count;

    MyRunner(int count) {
        this.count = count;
    }

    @Override
    public void run(@NonNull ApplicationArguments args) throws Exception {
        IO.println("hello #" + this.count);
    }
}


interface DogRepository extends ListCrudRepository<Dog, Integer> {
}

// look mom, no Lombok !!
record Dog(@Id int id, String name, String owner, String description) {
}

record CatFact(String fact) {
}

record CatFacts(Collection<CatFact> facts) {
}

interface CatFactsClient {

    @GetExchange("https://www.catfacts.net/api")
    CatFacts facts();
}


@Controller
@ResponseBody
class CatFactsController {

    private final AtomicInteger counter = new AtomicInteger(0);

    private final CatFactsClient catFactsClient;

    CatFactsController(CatFactsClient catFactsClient) {
        this.catFactsClient = catFactsClient;
    }

    @ConcurrencyLimit(10)
    @Retryable(includes = IllegalStateException.class, maxRetries = 5)
    @GetMapping("/cats")
    CatFacts facts() {

        if (this.counter.getAndIncrement() < 4) {
            IO.println("oops!");
            throw new IllegalStateException("oops");
        }

        IO.println("facts");
        return catFactsClient.facts();
    }

}

@Controller
@ResponseBody
class DogsController {

    private final DogRepository repository;

    DogsController(DogRepository repository) {
        this.repository = repository;
    }

    @GetMapping(value = "/dogs", version = "1.1")
    Collection<Dog> dogs() {
        return this.repository.findAll();
    }

    @GetMapping(value = "/dogs", version = "1.0")
    Collection<Map<String, Object>> all() {
        return repository.findAll()
                .stream()
                .map(dog -> Map.of("id", (Object) dog.id(), "fullName", dog.name()))
                .toList();
    }
}

@Controller
@ResponseBody
class AdoptionsController {

    private final DogRepository repository;

    AdoptionsController(DogRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/dogs/{dogId}/adoptions")
    void adopt(@PathVariable int dogId, @RequestParam String owner) {
        this.repository.findById(dogId).ifPresent(dog -> {
            var updated = this.repository.save(new Dog(dog.id(), dog.name(), owner, dog.description()));
            IO.println("adopted " + updated);

        });
    }

}
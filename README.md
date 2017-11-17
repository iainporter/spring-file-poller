A simple Spring Boot application that demonstrates how to set up File polling using the Spring Integration DSL and how to test it

The application consists of an Integration flow that polls a directory for files that match a given regex expression.

On finding a file it is transformed to a String and passed to a message handling flow that writes it out to another directory.

It can be used as an entry point to a bigger application.

The tests show best practices for testing such a component.

See blog post: http://porterhead.blogspot.co.uk/2016/07/file-polling-using-spring-integration.html

To build it:

```$code
> mvn clean install
```

To run it:
```$code

> mvn spring-boot:run
```



